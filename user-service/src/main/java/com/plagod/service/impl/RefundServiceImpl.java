package com.plagod.service.impl;

import com.plagod.audit.Audited;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.RefundApplyRequest;
import com.plagod.dto.entitlement.RefundReviewRequest;
import com.plagod.dto.entitlement.VerifiedRefundResult;
import com.plagod.entity.entitlement.*;
import com.plagod.entity.user.User;
import com.plagod.mapper.*;
import com.plagod.service.RefundService;
import com.plagod.vo.entitlement.RefundVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class RefundServiceImpl implements RefundService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private EntitlementOrderMapper orderMapper;
    @Autowired
    private PaymentRecordMapper paymentMapper;
    @Autowired
    private RefundRecordMapper refundMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NetworkEntitlementMapper entitlementMapper;
    @Autowired
    private DurationPurchaseMapper purchaseMapper;
    @Autowired
    private EntitlementUsageLogMapper usageLogMapper;
    @Autowired
    private TradeStatusLogMapper statusLogMapper;

    @Override
    @Audited(action = "refund.apply")
    @Transactional(rollbackFor = Exception.class)
    public RefundVO apply(Long userId, RefundApplyRequest request) {

        requireUserId(userId);
        if (request == null || request.getPurchaseId() == null || request.getPurchaseId() <= 0) {
            throw new IllegalArgumentException("退款参数无效");
        }

        String requestId = normalize(request.getRequestId(), "退款请求号", 56);
        String reason = normalize(request.getReason(), "退款原因", 255);

        /*
         * 普通读取只用于确定加锁起点，不能据此修改业务数据。
         * 重复请求优先使用原退款单的订单号。
         */
        RefundRecord existingHint = refundMapper.selectByUserRequest(userId, requestId);

        DurationPurchase purchaseHint = null;
        String orderNo;

        if (existingHint != null) {
            orderNo = existingHint.getOrderNo();
        } else {
            purchaseHint = purchaseMapper.selectById(request.getPurchaseId());

            if (purchaseHint == null || !userId.equals(purchaseHint.getUserId())) {
                throw new IllegalArgumentException(
                        "购买批次不存在或不属于当前用户");
            }
            orderNo = purchaseHint.getOrderNo();
        }

        // 固定锁顺序：订单 -> 支付 -> 退款 -> 用户 -> 权益 -> 购买批次。
        EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);

        if (order == null || !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("退款关联订单不存在或不属于当前用户");
        }

        PaymentRecord payment = paymentMapper.selectByOrderNoForUpdate(orderNo);

        if (payment == null || !userId.equals(payment.getUserId())) {
            throw new IllegalStateException("退款关联支付记录不存在");
        }

        RefundRecord existing = refundMapper.selectByUserRequestForUpdate(userId, requestId);

        if (existing != null) {
            validateDuplicate(existing, request.getPurchaseId(), reason);
            return toVO(existing);
        }

        RefundRecord active = refundMapper.selectActiveByPurchaseForUpdate(request.getPurchaseId());

        if (active != null) {
            throw new IllegalArgumentException("该购买批次已有正在处理的退款");
        }

        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或不可用");
        }

        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(userId);

        if (entitlement == null
                || !Integer.valueOf(1).equals(entitlement.getStatus())
                || !EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode())) {
            throw new IllegalArgumentException("当前没有可退款的购买时长权益");
        }

        /*
         * 与消费和管理员扣减使用相同顺序锁定全部可用批次，
         * 同时校验批次汇总和权益汇总没有失配。
         */
        List<DurationPurchase> usablePurchases = purchaseMapper.selectUsableLotsForUpdate(userId);

        DurationPurchase purchase = null;
        long lotTotal = 0L;

        for (DurationPurchase item : usablePurchases) {
            long remaining = requirePositive(item.getRemainingSeconds(), "购买批次剩余时长");

            lotTotal = Math.addExact(lotTotal, remaining);

            if (Objects.equals(request.getPurchaseId(), item.getPurchaseId())) {purchase = item;
            }
        }

        long entitlementBefore = requireNonNegative(
                entitlement.getRemainingSeconds(), "权益剩余时长");

        if (lotTotal != entitlementBefore) {
            throw new IllegalStateException("购买批次余额与汇总权益不一致");
        }

        if (purchase == null) {
            throw new IllegalArgumentException("购买批次已经耗尽、冻结或不可退款");
        }

        validateTrade(order, payment, purchase, userId);

        long requestedSeconds = purchase.getRemainingSeconds();
        long requestedAmount = calculateRefundAmount(purchase);

        validateRefundLimit(order, payment, purchase, requestedAmount);

        LocalDateTime now = LocalDateTime.now();

        RefundRecord candidate = new RefundRecord();
        candidate.setRefundNo(generateNo(now));
        candidate.setOrderNo(order.getOrderNo());
        candidate.setPaymentNo(payment.getPaymentNo());
        candidate.setPurchaseId(purchase.getPurchaseId());
        candidate.setUserId(userId);
        candidate.setRequestId(requestId);
        candidate.setChannel(payment.getChannel());
        candidate.setStatus(EntitlementTradeConstants.REFUND_REQUESTED);
        candidate.setReason(reason);
        candidate.setRequestedSeconds(requestedSeconds);
        candidate.setRequestedAmountCents(requestedAmount);
        candidate.setVersion(0);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);

        refundMapper.insertOrResolveExisting(candidate);

        RefundRecord stored = refundMapper.selectByUserRequestForUpdate(userId, requestId);

        if (stored == null) {
            throw new IllegalStateException("退款申请创建结果无法确认");
        }

        /*
         * 唯一键并发冲突可能返回另一个线程已经创建的记录。
         * 此时不再冻结第二次，只验证请求语义并返回已有结果。
         */
        if (!candidate.getRefundNo().equals(stored.getRefundNo())) {
            validateDuplicate(stored, request.getPurchaseId(), reason);
            return toVO(stored);
        }

        long entitlementAfter = Math.subtractExact(entitlementBefore, requestedSeconds);

        if (entitlementAfter < 0) {
            throw new IllegalStateException("退款冻结后权益不能为负数");
        }

        entitlement.setRemainingSeconds(entitlementAfter);
        entitlement.setVersion(nextVersion(entitlement.getVersion()));
        entitlement.setUpdateTime(now);

        if (entitlementMapper.updateById(entitlement) != 1) {
            throw new IllegalStateException("退款权益冻结失败");
        }

        purchase.setRemainingSeconds(0L);
        purchase.setStatus(EntitlementTradeConstants.PURCHASE_REFUND_RESERVED);
        purchase.setUpdateTime(now);

        if (purchaseMapper.updateById(purchase) != 1) {
            throw new IllegalStateException("购买批次冻结失败");
        }

        String previousOrderStatus = order.getStatus();

        order.setStatus(EntitlementTradeConstants.ORDER_REFUNDING);
        order.setVersion(nextVersion(order.getVersion()));
        order.setUpdateTime(now);

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("退款订单状态更新失败");
        }

        insertReserveUsageLog(entitlement, stored, entitlementBefore, entitlementAfter, now);

        appendStatusLog(EntitlementTradeConstants.BUSINESS_REFUND, stored.getRefundNo(), "REQUEST:" + requestId, null, EntitlementTradeConstants.REFUND_REQUESTED, EntitlementTradeConstants.OPERATOR_USER, userId, "用户申请购买时长退款", now);

        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), "REFUND_REQUEST:" + stored.getRefundNo(), previousOrderStatus, EntitlementTradeConstants.ORDER_REFUNDING, EntitlementTradeConstants.OPERATOR_USER, userId, "退款申请已冻结剩余时长", now);

        return toVO(stored);
    }

    @Override
    @Audited(action = "refund.review")
    @Transactional(rollbackFor = Exception.class)
    public RefundVO review(String rawRefundNo, Long reviewerId, String reviewerName, RefundReviewRequest request) {

        if (reviewerId == null || reviewerId <= 0) {
            throw new IllegalArgumentException("审核人身份无效");
        }

        String refundNo = normalize(rawRefundNo, "退款单号", 64).toUpperCase(Locale.ROOT);
        String normalizedReviewerName = normalize(reviewerName, "审核人名称", 64);

        if (request == null || !StringUtils.hasText(request.getDecision())) {
            throw new IllegalArgumentException("退款审核参数无效");
        }

        String decision = request.getDecision().trim().toUpperCase(Locale.ROOT);

        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new IllegalArgumentException("退款审核决定无效");
        }

        String comment = StringUtils.hasText(request.getComment()) ? normalize(request.getComment(), "审核意见", 255) : null;

        if ("REJECT".equals(decision) && !StringUtils.hasText(comment)) {
            throw new IllegalArgumentException("拒绝退款必须填写原因");
        }

        RefundRecord hint = refundMapper.selectByRefundNo(refundNo);
        if (hint == null) {
            throw new IllegalArgumentException("退款单不存在");
        }

        // 固定锁顺序：订单 -> 支付 -> 退款 -> 用户 -> 权益 -> 购买批次。
        EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(hint.getOrderNo());

        if (order == null) {
            throw new IllegalStateException("退款关联订单不存在");
        }

        PaymentRecord payment = paymentMapper.selectByOrderNoForUpdate(order.getOrderNo());

        if (payment == null) {
            throw new IllegalStateException("退款关联支付记录不存在");
        }

        RefundRecord refund = refundMapper.selectByRefundNoForUpdate(refundNo);

        if (refund == null) {
            throw new IllegalArgumentException("退款单不存在");
        }

        User user = userMapper.selectByIdForUpdate(refund.getUserId());
        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(refund.getUserId());
        DurationPurchase purchase = purchaseMapper.selectByIdForUpdate(refund.getPurchaseId());

        validateRefundAssociation(refund, order, payment, user, entitlement, purchase);

        if ("APPROVE".equals(decision)) {
            return approveRefund(refund, purchase, reviewerId, normalizedReviewerName, comment);
        }

        return rejectRefund(refund, order, entitlement, purchase, reviewerId, normalizedReviewerName, comment);
    }

    @Override
    @Audited(action = "refund.channel.result")
    @Transactional(rollbackFor = Exception.class)
    public RefundVO handleChannelResult(VerifiedRefundResult result) {

        validateChannelResult(result);

        RefundRecord hint = refundMapper.selectByRefundNo(result.getRefundNo());

        if (hint == null) {
            throw new IllegalArgumentException("退款单不存在");
        }

        EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(hint.getOrderNo());
        PaymentRecord payment = paymentMapper.selectByOrderNoForUpdate(hint.getOrderNo());
        RefundRecord refund = refundMapper.selectByRefundNoForUpdate(result.getRefundNo());
        if (order == null || payment == null || refund == null) {
            throw new IllegalStateException("退款关联交易记录不存在");
        }

        User user = userMapper.selectByIdForUpdate(refund.getUserId());
        NetworkEntitlement entitlement = entitlementMapper.selectByUserIdForUpdate(refund.getUserId());
        DurationPurchase purchase = purchaseMapper.selectByIdForUpdate(refund.getPurchaseId());

        validateRefundAssociation(refund, order, payment, user, entitlement, purchase);

        if (EntitlementTradeConstants.REFUND_SUCCEEDED.equals(refund.getStatus()) || EntitlementTradeConstants.REFUND_FAILED.equals(refund.getStatus())) {
            validateRepeatedChannelResult(refund, result);
            return toVO(refund);
        }

        if (!EntitlementTradeConstants.REFUND_PROCESSING.equals(refund.getStatus())) {
            throw new IllegalArgumentException("当前退款状态不能处理渠道结果");
        }

        validateFrozenPurchase(refund, purchase);

        if (!refund.getChannel().equalsIgnoreCase(result.getChannel())) {
            throw new IllegalArgumentException("退款渠道与结果渠道不一致");
        }

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(result.getSuccess())) {
            finishRefundSuccess(refund, order, payment, purchase, result, now);
        } else {
            finishRefundFailure(refund, order, entitlement, purchase, result, now);
        }

        return toVO(refund);
    }

    private void validateTrade(EntitlementOrder order, PaymentRecord payment, DurationPurchase purchase, Long userId) {

        if (!userId.equals(purchase.getUserId()) || !order.getOrderNo().equals(purchase.getOrderNo())) {
            throw new IllegalStateException("购买批次关联关系无效");
        }

        if (!EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(order.getEntitlementMode())) {
            throw new IllegalArgumentException("订阅式权益不可退款");
        }

        if (!EntitlementTradeConstants.ORDER_FULFILLED.equals(order.getStatus())
                && !EntitlementTradeConstants.ORDER_PARTIALLY_REFUNDED.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不能申请退款");
        }

        if (!order.getOrderNo().equals(payment.getOrderNo())
                || !EntitlementTradeConstants.PAYMENT_SUCCEEDED.equals(payment.getStatus())
                && !EntitlementTradeConstants.PAYMENT_PARTIALLY_REFUNDED.equals(payment.getStatus())) {
            throw new IllegalArgumentException("当前支付状态不能申请退款");
        }

        if (!Integer.valueOf(1).equals(purchase.getRefundable())
                || !Integer.valueOf(EntitlementTradeConstants.PURCHASE_USABLE).equals(purchase.getStatus())) {
            throw new IllegalArgumentException("该购买批次不可退款");
        }

        requirePositive(purchase.getPurchasedSeconds(), "原始购买时长");
        requirePositive(purchase.getPaidAmountCents(), "购买支付金额");
    }

    private long calculateRefundAmount(DurationPurchase purchase) {

        BigInteger amount = BigInteger.valueOf(purchase.getPaidAmountCents())
                .multiply(BigInteger.valueOf(purchase.getRemainingSeconds()))
                .divide(BigInteger.valueOf(purchase.getPurchasedSeconds()));

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("当前剩余时长折算退款金额不足1分");
        }

        try {
            return amount.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("退款金额超出系统范围");
        }
    }

    private void validateRefundLimit(EntitlementOrder order, PaymentRecord payment, DurationPurchase purchase, long requestedAmount) {

        long purchaseAvailable = subtractNonNegative(purchase.getPaidAmountCents(), purchase.getRefundedAmountCents(), "购买批次退款金额");

        long paymentAvailable = subtractNonNegative(payment.getPaidAmountCents(), payment.getRefundedAmountCents(), "支付可退款金额");

        long orderAvailable = subtractNonNegative(order.getPaidAmountCents(), order.getRefundedAmountCents(), "订单可退款金额");

        long available = Math.min(purchaseAvailable, Math.min(paymentAvailable, orderAvailable));

        if (requestedAmount > available) {
            throw new IllegalStateException("申请退款金额超过剩余可退款金额");
        }
    }

    private long subtractNonNegative(Long total, Long used, String fieldName) {
        long normalizedTotal = requireNonNegative(total, fieldName);
        long normalizedUsed = used == null ? 0L : requireNonNegative(used, fieldName);

        long result = Math.subtractExact(normalizedTotal, normalizedUsed);

        if (result < 0) {
            throw new IllegalStateException(fieldName + "数据无效");
        }
        return result;
    }

    private void insertReserveUsageLog(NetworkEntitlement entitlement, RefundRecord refund, long before, long after, LocalDateTime now) {

        EntitlementUsageLog log = new EntitlementUsageLog();
        log.setEntitlementId(entitlement.getEntitlementId());
        log.setUserId(refund.getUserId());
        log.setRequestId("REF:" + refund.getRefundNo());
        log.setLineNo(1);
        log.setPurchaseId(refund.getPurchaseId());
        log.setAuthorizationMode(EntitlementTradeConstants.MODE_DURATION);
        log.setSessionId(null);
        log.setChangeSeconds(-refund.getRequestedSeconds());
        log.setBeforeSeconds(before);
        log.setAfterSeconds(after);
        log.setReason("REFUND_RESERVE");
        log.setCreateTime(now);

        if (usageLogMapper.insert(log) != 1) {
            throw new IllegalStateException("退款冻结流水写入失败");
        }
    }

    private void appendStatusLog(String businessType, String businessNo, String eventKey, String fromStatus, String toStatus, String operatorType, Long operatorId, String remark, LocalDateTime now) {

        TradeStatusLog log = new TradeStatusLog();
        log.setBusinessType(businessType);
        log.setBusinessNo(businessNo);
        log.setEventKey(eventKey);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        log.setCreateTime(now);
        statusLogMapper.insertIgnore(log);
    }

    private void validateDuplicate(RefundRecord refund, Long purchaseId, String reason) {
        if (!Objects.equals(purchaseId, refund.getPurchaseId()) || !reason.equals(refund.getReason())) {
            throw new IllegalArgumentException("退款请求号已被其他请求使用");
        }
    }

    private RefundVO toVO(RefundRecord refund) {
        RefundVO vo = new RefundVO();
        vo.setRefundNo(refund.getRefundNo());
        vo.setOrderNo(refund.getOrderNo());
        vo.setPaymentNo(refund.getPaymentNo());
        vo.setPurchaseId(refund.getPurchaseId());
        vo.setUserId(refund.getUserId());
        vo.setRequestId(refund.getRequestId());
        vo.setChannel(refund.getChannel());
        vo.setStatus(refund.getStatus());
        vo.setReason(refund.getReason());
        vo.setRequestedSeconds(refund.getRequestedSeconds());
        vo.setRequestedAmountCents(refund.getRequestedAmountCents());
        vo.setRefundedSeconds(refund.getRefundedSeconds());
        vo.setRefundAmountCents(refund.getRefundAmountCents());
        vo.setReviewerId(refund.getReviewerId());
        vo.setReviewerName(refund.getReviewerName());
        vo.setReviewComment(refund.getReviewComment());
        vo.setReviewTime(refund.getReviewTime());
        vo.setChannelRefundNo(refund.getChannelRefundNo());
        vo.setFailureMessage(refund.getFailureMessage());
        vo.setCompleteTime(refund.getCompleteTime());
        vo.setCreateTime(refund.getCreateTime());
        vo.setUpdateTime(refund.getUpdateTime());
        return vo;
    }

    private long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(fieldName + "无效");
        }
        return value;
    }

    private long requireNonNegative(Long value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + "无效");
        }
        return value;
    }

    private int nextVersion(Integer version) {
        return version == null ? 1 : Math.addExact(version, 1);
    }

    private String generateNo(LocalDateTime now) {
        return "REF" + TIME_FORMAT.format(now)
                + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }

    private String normalize(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
    }

    private RefundVO approveRefund(RefundRecord refund, DurationPurchase purchase, Long reviewerId, String reviewerName, String comment) {

        if (EntitlementTradeConstants.REFUND_PROCESSING.equals(refund.getStatus()) || EntitlementTradeConstants.REFUND_SUCCEEDED.equals(refund.getStatus()) || EntitlementTradeConstants.REFUND_FAILED.equals(refund.getStatus())) {
            return toVO(refund);
        }

        if (EntitlementTradeConstants.REFUND_REJECTED.equals(refund.getStatus())) {
            throw new IllegalArgumentException("退款已经被拒绝，不能再次批准");
        }

        if (!EntitlementTradeConstants.REFUND_REQUESTED.equals(refund.getStatus())) {
            throw new IllegalArgumentException("当前退款状态不能批准");
        }

        validateFrozenPurchase(refund, purchase);

        LocalDateTime now = LocalDateTime.now();
        String previousStatus = refund.getStatus();

        refund.setStatus(EntitlementTradeConstants.REFUND_PROCESSING);
        refund.setReviewerId(reviewerId);
        refund.setReviewerName(reviewerName);
        refund.setReviewComment(comment);
        refund.setReviewTime(now);
        refund.setVersion(nextVersion(refund.getVersion()));
        refund.setUpdateTime(now);

        if (refundMapper.updateById(refund) != 1) {
            throw new IllegalStateException("退款审核状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_REFUND, refund.getRefundNo(), "REVIEW:APPROVE", previousStatus, EntitlementTradeConstants.REFUND_PROCESSING, EntitlementTradeConstants.OPERATOR_ADMIN, reviewerId, "管理员批准退款，等待渠道结果", now);

        return toVO(refund);
    }

    private RefundVO rejectRefund(RefundRecord refund, EntitlementOrder order, NetworkEntitlement entitlement, DurationPurchase purchase, Long reviewerId, String reviewerName, String comment) {

        if (EntitlementTradeConstants.REFUND_REJECTED.equals(refund.getStatus())) {
            return toVO(refund);
        }

        if (!EntitlementTradeConstants.REFUND_REQUESTED.equals(refund.getStatus())) {
            throw new IllegalArgumentException("当前退款状态不能拒绝");
        }

        LocalDateTime now = LocalDateTime.now();
        String previousRefundStatus = refund.getStatus();
        String previousOrderStatus = order.getStatus();

        releaseFrozenEntitlement(refund, entitlement, purchase, now);

        String restoredOrderStatus = restoredOrderStatus(order);

        order.setStatus(restoredOrderStatus);
        order.setVersion(nextVersion(order.getVersion()));
        order.setUpdateTime(now);

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("订单退款状态恢复失败");
        }

        refund.setStatus(EntitlementTradeConstants.REFUND_REJECTED);
        refund.setReviewerId(reviewerId);
        refund.setReviewerName(reviewerName);
        refund.setReviewComment(comment);
        refund.setReviewTime(now);
        refund.setCompleteTime(now);
        refund.setVersion(nextVersion(refund.getVersion()));
        refund.setUpdateTime(now);

        if (refundMapper.updateById(refund) != 1) {
            throw new IllegalStateException("退款拒绝状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_REFUND, refund.getRefundNo(), "REVIEW:REJECT", previousRefundStatus, EntitlementTradeConstants.REFUND_REJECTED, EntitlementTradeConstants.OPERATOR_ADMIN, reviewerId, "管理员拒绝退款：" + comment, now);

        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), "REFUND_REJECT:" + refund.getRefundNo(), previousOrderStatus, restoredOrderStatus, EntitlementTradeConstants.OPERATOR_ADMIN, reviewerId, "退款被拒绝，恢复订单状态", now);

        return toVO(refund);
    }

    private void validateFrozenPurchase(RefundRecord refund, DurationPurchase purchase) {

        if (purchase == null
                || !Objects.equals(refund.getPurchaseId(), purchase.getPurchaseId())
                || !Integer.valueOf(EntitlementTradeConstants.PURCHASE_REFUND_RESERVED).equals(purchase.getStatus())
                || purchase.getRemainingSeconds() == null
                || purchase.getRemainingSeconds() != 0L
                || refund.getRequestedSeconds() == null
                || refund.getRequestedSeconds() <= 0
                || refund.getRequestedAmountCents() == null
                || refund.getRequestedAmountCents() <= 0) {
            throw new IllegalStateException("退款冻结批次状态不一致");
        }
    }

    private void releaseFrozenEntitlement(RefundRecord refund, NetworkEntitlement entitlement, DurationPurchase purchase, LocalDateTime now) {

        validateFrozenPurchase(refund, purchase);

        long before = requireNonNegative(entitlement.getRemainingSeconds(), "权益剩余时长");
        long after = Math.addExact(before, refund.getRequestedSeconds());

        entitlement.setRemainingSeconds(after);
        entitlement.setVersion(nextVersion(entitlement.getVersion()));
        entitlement.setUpdateTime(now);

        if (entitlementMapper.updateById(entitlement) != 1) {
            throw new IllegalStateException("退款冻结权益恢复失败");
        }

        purchase.setRemainingSeconds(refund.getRequestedSeconds());
        purchase.setStatus(EntitlementTradeConstants.PURCHASE_USABLE);
        purchase.setUpdateTime(now);

        if (purchaseMapper.updateById(purchase) != 1) {
            throw new IllegalStateException("退款冻结批次恢复失败");
        }

        EntitlementUsageLog log = new EntitlementUsageLog();
        log.setEntitlementId(entitlement.getEntitlementId());
        log.setUserId(refund.getUserId());
        log.setRequestId("REFREL:" + refund.getRefundNo());
        log.setLineNo(1);
        log.setPurchaseId(refund.getPurchaseId());
        log.setAuthorizationMode(EntitlementTradeConstants.MODE_DURATION);
        log.setSessionId(null);
        log.setChangeSeconds(refund.getRequestedSeconds());
        log.setBeforeSeconds(before);
        log.setAfterSeconds(after);
        log.setReason("REFUND_RELEASE");
        log.setCreateTime(now);

        if (usageLogMapper.insert(log) != 1) {
            throw new IllegalStateException("退款恢复流水写入失败");
        }
    }

    private void validateRefundAssociation(RefundRecord refund, EntitlementOrder order, PaymentRecord payment, User user, NetworkEntitlement entitlement, DurationPurchase purchase) {

        if (user == null || entitlement == null || purchase == null) {
            throw new IllegalStateException("退款关联的用户或权益不存在");
        }

        if (!Objects.equals(refund.getUserId(), user.getUserId())
                || !Objects.equals(refund.getUserId(), order.getUserId())
                || !Objects.equals(refund.getUserId(), payment.getUserId())
                || !Objects.equals(refund.getUserId(), purchase.getUserId())
                || !refund.getOrderNo().equals(order.getOrderNo())
                || !refund.getOrderNo().equals(payment.getOrderNo())
                || !refund.getPaymentNo().equals(payment.getPaymentNo())
                || !refund.getOrderNo().equals(purchase.getOrderNo())
                || !Objects.equals(refund.getUserId(), entitlement.getUserId())) {
            throw new IllegalStateException("退款业务关联关系不一致");
        }

        if (!EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(order.getEntitlementMode()) || !EntitlementTradeConstants.MODE_DURATION.equalsIgnoreCase(entitlement.getMode())) {
            throw new IllegalStateException("退款关联权益模式无效");
        }
        if (!Objects.equals(refund.getChannel(), payment.getChannel())) {
            throw new IllegalStateException("退款渠道与支付渠道不一致");
        }

        boolean activeRefund = EntitlementTradeConstants.REFUND_REQUESTED.equals(refund.getStatus()) || EntitlementTradeConstants.REFUND_PROCESSING.equals(refund.getStatus());

        if (activeRefund && !EntitlementTradeConstants.ORDER_REFUNDING.equals(order.getStatus())) {
            throw new IllegalStateException("退款处理中但订单状态不是退款中");
        }

        if (activeRefund && !EntitlementTradeConstants.PAYMENT_SUCCEEDED.equals(payment.getStatus()) && !EntitlementTradeConstants.PAYMENT_PARTIALLY_REFUNDED.equals(payment.getStatus())) {
            throw new IllegalStateException("退款处理中但支付状态不可退款");
        }
    }

    private String restoredOrderStatus(EntitlementOrder order) {

        long refunded = order.getRefundedAmountCents() == null ? 0L : order.getRefundedAmountCents();

        return refunded > 0 ? EntitlementTradeConstants.ORDER_PARTIALLY_REFUNDED : EntitlementTradeConstants.ORDER_FULFILLED;
    }

    private void finishRefundSuccess(RefundRecord refund, EntitlementOrder order, PaymentRecord payment, DurationPurchase purchase, VerifiedRefundResult result, LocalDateTime now) {

        long amount = requirePositive(refund.getRequestedAmountCents(), "申请退款金额");
        long seconds = requirePositive(refund.getRequestedSeconds(), "申请退款时长");

        long purchaseRefunded = addRefundAmount(purchase.getRefundedAmountCents(), amount, purchase.getPaidAmountCents(), "购买批次");

        long paymentRefunded = addRefundAmount(payment.getRefundedAmountCents(), amount, payment.getPaidAmountCents(), "支付记录");

        long orderRefunded = addRefundAmount(order.getRefundedAmountCents(), amount, order.getPaidAmountCents(), "订单");

        String oldRefundStatus = refund.getStatus();
        String oldPaymentStatus = payment.getStatus();
        String oldOrderStatus = order.getStatus();

        purchase.setRemainingSeconds(0L);
        purchase.setRefundedAmountCents(purchaseRefunded);
        purchase.setRefundTime(now);
        purchase.setStatus(EntitlementTradeConstants.PURCHASE_REFUNDED);
        purchase.setUpdateTime(now);

        if (purchaseMapper.updateById(purchase) != 1) {
            throw new IllegalStateException("退款购买批次更新失败");
        }

        String paymentStatus = paymentRefunded == payment.getPaidAmountCents() ? EntitlementTradeConstants.PAYMENT_REFUNDED : EntitlementTradeConstants.PAYMENT_PARTIALLY_REFUNDED;
        payment.setRefundedAmountCents(paymentRefunded);
        payment.setStatus(paymentStatus);
        payment.setVersion(nextVersion(payment.getVersion()));
        payment.setUpdateTime(now);

        if (paymentMapper.updateById(payment) != 1) {
            throw new IllegalStateException("支付退款金额更新失败");
        }

        String orderStatus = orderRefunded == order.getPaidAmountCents() ? EntitlementTradeConstants.ORDER_REFUNDED : EntitlementTradeConstants.ORDER_PARTIALLY_REFUNDED;

        order.setRefundedAmountCents(orderRefunded);
        order.setStatus(orderStatus);
        order.setVersion(nextVersion(order.getVersion()));
        order.setUpdateTime(now);

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("订单退款金额更新失败");
        }

        applyChannelFields(refund, result);
        refund.setStatus(EntitlementTradeConstants.REFUND_SUCCEEDED);
        refund.setRefundedSeconds(seconds);
        refund.setRefundAmountCents(amount);
        refund.setFailureMessage(null);
        refund.setCompleteTime(now);
        refund.setVersion(nextVersion(refund.getVersion()));
        refund.setUpdateTime(now);

        if (refundMapper.updateById(refund) != 1) {
            throw new IllegalStateException("退款成功状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_REFUND, refund.getRefundNo(), "CHANNEL:" + result.getEventId(), oldRefundStatus, EntitlementTradeConstants.REFUND_SUCCEEDED, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "渠道退款成功", now);
        appendStatusLog(EntitlementTradeConstants.BUSINESS_PAYMENT, payment.getPaymentNo(), "REFUND:" + refund.getRefundNo(), oldPaymentStatus, paymentStatus, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "累计退款金额更新", now);
        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), "REFUND_SUCCESS:" + refund.getRefundNo(), oldOrderStatus, orderStatus, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "退款成功，更新订单状态", now);
    }

    private void finishRefundFailure(RefundRecord refund, EntitlementOrder order, NetworkEntitlement entitlement, DurationPurchase purchase, VerifiedRefundResult result, LocalDateTime now) {

        String oldRefundStatus = refund.getStatus();
        String oldOrderStatus = order.getStatus();

        releaseFrozenEntitlement(refund, entitlement, purchase, now);

        String orderStatus = restoredOrderStatus(order);

        order.setStatus(orderStatus);
        order.setVersion(nextVersion(order.getVersion()));
        order.setUpdateTime(now);

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException("渠道失败后订单恢复失败");
        }

        applyChannelFields(refund, result);
        refund.setStatus(EntitlementTradeConstants.REFUND_FAILED);
        refund.setFailureMessage(StringUtils.hasText(result.getFailureMessage()) ? result.getFailureMessage() : "渠道退款失败");
        refund.setCompleteTime(now);
        refund.setVersion(nextVersion(refund.getVersion()));
        refund.setUpdateTime(now);

        if (refundMapper.updateById(refund) != 1) {
            throw new IllegalStateException("退款失败状态更新失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_REFUND, refund.getRefundNo(), "CHANNEL:" + result.getEventId(), oldRefundStatus, EntitlementTradeConstants.REFUND_FAILED, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "渠道退款失败，已恢复冻结时长", now);
        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), "REFUND_FAILED:" + refund.getRefundNo(), oldOrderStatus, orderStatus, EntitlementTradeConstants.OPERATOR_CHANNEL, null, "渠道退款失败，恢复订单状态", now);
    }

    private long addRefundAmount(Long oldValue, long change, Long paidAmount, String fieldName) {
        long before = oldValue == null ? 0L : requireNonNegative(oldValue, fieldName);
        long paid = requirePositive(paidAmount, fieldName + "支付金额");
        long after = Math.addExact(before, change);

        if (after > paid) {
            throw new IllegalStateException(fieldName + "累计退款金额超过支付金额");
        }
        return after;
    }

    private void applyChannelFields(RefundRecord refund, VerifiedRefundResult result) {

        refund.setChannelRefundNo(result.getChannelRefundNo());
        refund.setChannelEventId(result.getEventId());
        refund.setChannelPayloadHash(result.getPayloadHash());
    }

    private void validateRepeatedChannelResult(RefundRecord refund, VerifiedRefundResult result) {

        boolean storedSuccess = EntitlementTradeConstants.REFUND_SUCCEEDED.equals(refund.getStatus());
        if (storedSuccess != Boolean.TRUE.equals(result.getSuccess())
                || !Objects.equals(refund.getChannel(), result.getChannel())
                || !Objects.equals(refund.getChannelEventId(), result.getEventId())
                || !Objects.equals(refund.getChannelRefundNo(), result.getChannelRefundNo())
                || !Objects.equals(refund.getChannelPayloadHash(), result.getPayloadHash())) {
            throw new IllegalArgumentException("重复退款渠道结果与首次结果不一致");
        }
    }

    private void validateChannelResult(VerifiedRefundResult result) {

        if (result == null
                || !StringUtils.hasText(result.getRefundNo())
                || !StringUtils.hasText(result.getChannel())
                || !StringUtils.hasText(result.getEventId())
                || !StringUtils.hasText(result.getChannelRefundNo())
                || !StringUtils.hasText(result.getPayloadHash())
                || result.getSuccess() == null) {
            throw new IllegalArgumentException("退款渠道结果无效");
        }
    }
}