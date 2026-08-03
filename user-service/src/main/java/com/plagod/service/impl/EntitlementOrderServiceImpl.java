package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.configuration.EntitlementProductProperties;
import com.plagod.constant.EntitlementTradeConstants;
import com.plagod.dto.entitlement.EntitlementOrderCreateRequest;
import com.plagod.entity.entitlement.EntitlementOrder;
import com.plagod.entity.entitlement.PaymentRecord;
import com.plagod.entity.entitlement.TradeStatusLog;
import com.plagod.entity.user.User;
import com.plagod.mapper.EntitlementOrderMapper;
import com.plagod.mapper.PaymentRecordMapper;
import com.plagod.mapper.TradeStatusLogMapper;
import com.plagod.mapper.UserMapper;
import com.plagod.service.EntitlementOrderService;
import com.plagod.vo.entitlement.EntitlementOrderPageResult;
import com.plagod.vo.entitlement.EntitlementOrderVO;
import com.plagod.vo.entitlement.EntitlementProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EntitlementOrderServiceImpl implements EntitlementOrderService {

    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Set<String> ORDER_STATUSES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    EntitlementTradeConstants.ORDER_PENDING_PAYMENT,
                    EntitlementTradeConstants.ORDER_PAID,
                    EntitlementTradeConstants.ORDER_FULFILLED,
                    EntitlementTradeConstants.ORDER_CANCELLED,
                    EntitlementTradeConstants.ORDER_CLOSED,
                    EntitlementTradeConstants.ORDER_REFUNDING,
                    EntitlementTradeConstants.ORDER_PARTIALLY_REFUNDED,
                    EntitlementTradeConstants.ORDER_REFUNDED
            )));

    @Autowired
    private EntitlementProductProperties productProperties;

    @Autowired
    private EntitlementOrderMapper orderMapper;

    @Autowired
    private TradeStatusLogMapper statusLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PaymentRecordMapper paymentMapper;

    @Override
    public List<EntitlementProductVO> listProducts() {
        List<EntitlementProductVO> result = new ArrayList<>();

        for (EntitlementProductProperties.Product product : productProperties.getEnabledProducts()) {

            EntitlementProductVO vo = new EntitlementProductVO();
            vo.setProductCode(productProperties.normalizeProductCode(product.getCode()));
            vo.setName(product.getName());
            vo.setEntitlementMode(productProperties.normalizeMode(product.getMode()));
            vo.setGrantSeconds(product.getGrantSeconds());
            vo.setAmountCents(product.getAmountCents());
            vo.setCustomAmountAllowed(false);
            result.add(vo);
        }

        if (productProperties.isCustomDurationEnabled()) {
            EntitlementProductVO custom = new EntitlementProductVO();
            custom.setProductCode(EntitlementProductProperties.CUSTOM_DURATION_PRODUCT_CODE);
            custom.setName("自定义网络时长");
            custom.setEntitlementMode(EntitlementTradeConstants.MODE_DURATION);
            custom.setCustomAmountAllowed(true);
            custom.setMinAmountCents(productProperties.getCustomDurationMinAmountCents());
            custom.setMaxAmountCents(productProperties.getCustomDurationMaxAmountCents());
            custom.setSecondsPerCent(productProperties.getCustomDurationSecondsPerCent());
            result.add(custom);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EntitlementOrderVO createOrder(Long userId, EntitlementOrderCreateRequest request) {

        requireAvailableUser(userId);

        if (request == null) {
            throw new IllegalArgumentException("订单参数不能为空");
        }

        String clientRequestId = normalizeRequestId(request.getClientRequestId());

        EntitlementProductProperties.Product product = productProperties.requireOrderProduct(
                request.getProductCode(),
                request.getCustomAmountCents()
        );

        LocalDateTime now = LocalDateTime.now();

        EntitlementOrder candidate = new EntitlementOrder();
        candidate.setOrderNo(generateOrderNo(now));
        candidate.setUserId(userId);
        candidate.setClientRequestId(clientRequestId);
        candidate.setProductCode(productProperties.normalizeProductCode(product.getCode()));
        candidate.setOrderType("PURCHASE");
        candidate.setEntitlementMode(productProperties.normalizeMode(product.getMode()));
        candidate.setGrantSeconds(product.getGrantSeconds());
        candidate.setAmountCents(product.getAmountCents());
        candidate.setPaidAmountCents(0L);
        candidate.setRefundedAmountCents(0L);
        candidate.setStatus(EntitlementTradeConstants.ORDER_PENDING_PAYMENT);
        candidate.setExpireTime(now.plusMinutes(productProperties.effectiveOrderExpireMinutes()));
        candidate.setVersion(0);
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);

        orderMapper.insertOrResolveExisting(candidate);

        EntitlementOrder stored = orderMapper.selectByUserRequestForUpdate(userId, clientRequestId);

        if (stored == null) {
            throw new IllegalStateException("订单创建结果无法确认");
        }

        if (!candidate.getProductCode().equals(stored.getProductCode())
                || !candidate.getAmountCents().equals(stored.getAmountCents())
                || !candidate.getGrantSeconds().equals(stored.getGrantSeconds())) {
            throw new IllegalArgumentException("clientRequestId 已被其他订单参数使用");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, stored.getOrderNo(), "CREATE:" + stored.getClientRequestId(), null, EntitlementTradeConstants.ORDER_PENDING_PAYMENT, EntitlementTradeConstants.OPERATOR_USER, userId, "用户创建权益订单");
        return toOrderVO(stored);
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementOrderPageResult pageOwnOrders(Long userId, long current, long size, String status) {

        requireUserId(userId);

        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<EntitlementOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);

        if (StringUtils.hasText(status)) {
            wrapper.eq("status", normalizeOrderStatus(status));
        }

        wrapper.orderByDesc("create_time");

        Page<EntitlementOrder> page = orderMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);

        List<EntitlementOrderVO> records = new ArrayList<>();
        for (EntitlementOrder order : page.getRecords()) {
            records.add(toOrderVO(order));
        }

        EntitlementOrderPageResult result = new EntitlementOrderPageResult();

        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementOrderVO getOwnOrder(Long userId, String orderNo) {

        requireUserId(userId);
        String normalizedOrderNo = normalizeOrderNo(orderNo);

        EntitlementOrder order = orderMapper.selectOwnedOrder(normalizedOrderNo, userId);

        if (order == null) {
            throw new IllegalArgumentException("订单不存在或不属于当前用户");
        }

        return toOrderVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EntitlementOrderVO cancelOwnOrder(Long userId, String orderNo) {

        requireUserId(userId);
        String normalizedOrderNo = normalizeOrderNo(orderNo);

        // 固定锁顺序：订单 -> 支付。
        EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(normalizedOrderNo);

        if (order == null || !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("订单不存在或不属于当前用户");
        }

        PaymentRecord payment = paymentMapper.selectByOrderNoForUpdate(normalizedOrderNo);

        if (EntitlementTradeConstants.ORDER_CANCELLED.equals(order.getStatus())) {
            // 同时修复旧数据中“订单已取消、支付仍可操作”的状态。
            closeCreatedPayment(payment, "USER_CANCELLED", EntitlementTradeConstants.OPERATOR_USER, userId, LocalDateTime.now());
            return toOrderVO(order);
        }

        if (!EntitlementTradeConstants.ORDER_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前订单状态不允许取消");
        }

        LocalDateTime now = LocalDateTime.now();

        if (order.getExpireTime() == null || !order.getExpireTime().isAfter(now)) {

            closePendingOrder(order, EntitlementTradeConstants.ORDER_CLOSED, "PAYMENT_TIMEOUT", EntitlementTradeConstants.OPERATOR_SYSTEM, null, "TIMEOUT:" + order.getOrderNo(), now);

            closeCreatedPayment(payment, "PAYMENT_TIMEOUT", EntitlementTradeConstants.OPERATOR_SYSTEM, null, now);
        } else {
            closePendingOrder(order, EntitlementTradeConstants.ORDER_CANCELLED, "USER_CANCELLED", EntitlementTradeConstants.OPERATOR_USER, userId, "CANCEL:" + order.getOrderNo(), now);

            closeCreatedPayment(payment, "USER_CANCELLED", EntitlementTradeConstants.OPERATOR_USER, userId, now);
        }

        return toOrderVO(order);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredOrders(int batchSize) {
        int limit = batchSize <= 0 ? 100 : Math.min(batchSize, 500);
        LocalDateTime now = LocalDateTime.now();

        List<String> orderNos = orderMapper.selectExpiredPendingOrderNos(now, limit);

        int closed = 0;

        for (String orderNo : orderNos) {
            EntitlementOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);

            if (order == null || !EntitlementTradeConstants.ORDER_PENDING_PAYMENT.equals(order.getStatus()) || order.getExpireTime() == null || order.getExpireTime().isAfter(now)) {
                continue;
            }
            PaymentRecord payment = paymentMapper.selectByOrderNoForUpdate(orderNo);

            closePendingOrder(order, EntitlementTradeConstants.ORDER_CLOSED, "PAYMENT_TIMEOUT", EntitlementTradeConstants.OPERATOR_SYSTEM, null, "TIMEOUT:" + order.getOrderNo(), now);

            closeCreatedPayment(payment, "PAYMENT_TIMEOUT", EntitlementTradeConstants.OPERATOR_SYSTEM, null, now);

            closed++;
        }

        return closed;
    }

    private void closePendingOrder(EntitlementOrder order, String targetStatus, String closeReason, String operatorType, Long operatorId, String eventKey, LocalDateTime now) {

        String previousStatus = order.getStatus();

        int changed = orderMapper.closePendingOrder(order.getOrderNo(), EntitlementTradeConstants.ORDER_PENDING_PAYMENT, targetStatus, now, closeReason);

        if (changed != 1) {
            throw new IllegalStateException("订单状态更新失败");
        }

        order.setStatus(targetStatus);
        order.setCloseTime(now);
        order.setCloseReason(closeReason);
        order.setVersion(order.getVersion() == null ? 1 : order.getVersion() + 1);
        order.setUpdateTime(now);

        appendStatusLog(EntitlementTradeConstants.BUSINESS_ORDER, order.getOrderNo(), eventKey, previousStatus, targetStatus, operatorType, operatorId, closeReason);
    }

    private void appendStatusLog(String businessType, String businessNo, String eventKey, String fromStatus, String toStatus, String operatorType, Long operatorId, String remark) {

        TradeStatusLog log = new TradeStatusLog();
        log.setBusinessType(businessType);
        log.setBusinessNo(businessNo);
        log.setEventKey(eventKey);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());

        statusLogMapper.insertIgnore(log);
    }

    private void requireAvailableUser(Long userId) {
        requireUserId(userId);

        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new IllegalArgumentException("用户不存在或不可用");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            throw new IllegalArgumentException("客户端请求号不能为空");
        }

        String value = requestId.trim();
        if (value.length() > 64) {
            throw new IllegalArgumentException("客户端请求号不能超过64个字符");
        }

        return value;
    }

    private String normalizeOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new IllegalArgumentException("订单号不能为空");
        }

        String value = orderNo.trim().toUpperCase(Locale.ROOT);
        if (value.length() > 64) {
            throw new IllegalArgumentException("订单号不能超过64个字符");
        }

        return value;
    }

    private String normalizeOrderStatus(String status) {
        String value = status.trim().toUpperCase(Locale.ROOT);

        if (!ORDER_STATUSES.contains(value)) {
            throw new IllegalArgumentException("订单状态无效");
        }

        return value;
    }

    private String generateOrderNo(LocalDateTime now) {
        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);

        return "ORD" + ORDER_TIME_FORMAT.format(now) + randomPart;
    }

    private EntitlementOrderVO toOrderVO(EntitlementOrder order) {
        EntitlementOrderVO vo = new EntitlementOrderVO();

        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setProductCode(order.getProductCode());
        vo.setOrderType(order.getOrderType());
        vo.setEntitlementMode(order.getEntitlementMode());
        vo.setGrantSeconds(order.getGrantSeconds());
        vo.setAmountCents(order.getAmountCents());
        vo.setPaidAmountCents(order.getPaidAmountCents());
        vo.setRefundedAmountCents(order.getRefundedAmountCents());
        vo.setStatus(order.getStatus());
        vo.setExpireTime(order.getExpireTime());
        vo.setPaidTime(order.getPaidTime());
        vo.setFulfilledTime(order.getFulfilledTime());
        vo.setCloseTime(order.getCloseTime());
        vo.setCloseReason(order.getCloseReason());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());

        return vo;
    }

    private void closeCreatedPayment(PaymentRecord payment, String reason, String operatorType, Long operatorId, LocalDateTime now) {

        // 订单还没有创建支付记录时无需处理。
        if (payment == null) {
            return;
        }

        // 已关闭或已失败的支付没有继续支付的可能。
        if (EntitlementTradeConstants.PAYMENT_CLOSED.equals(payment.getStatus()) || EntitlementTradeConstants.PAYMENT_FAILED.equals(payment.getStatus())) {
            return;
        }

        /*
         * 已成功支付却关联待支付订单属于资金状态损坏。
         * 此时必须回滚订单关闭，不能把成功支付覆盖成 CLOSED。
         */
        if (!EntitlementTradeConstants.PAYMENT_CREATED.equals(payment.getStatus())) {
            throw new IllegalStateException("订单关闭时支付记录已进入不可关闭状态");
        }

        String previousStatus = payment.getStatus();

        payment.setStatus(EntitlementTradeConstants.PAYMENT_CLOSED);
        payment.setFailureCode(reason);
        payment.setFailureMessage("关联订单已关闭：" + reason);
        payment.setVersion(payment.getVersion() == null ? 1 : Math.addExact(payment.getVersion(), 1));
        payment.setUpdateTime(now);

        if (paymentMapper.updateById(payment) != 1) {
            throw new IllegalStateException("关联支付记录关闭失败");
        }

        appendStatusLog(EntitlementTradeConstants.BUSINESS_PAYMENT, payment.getPaymentNo(), "ORDER_CLOSE:" + payment.getOrderNo(), previousStatus, EntitlementTradeConstants.PAYMENT_CLOSED, operatorType, operatorId, reason);
    }
}
