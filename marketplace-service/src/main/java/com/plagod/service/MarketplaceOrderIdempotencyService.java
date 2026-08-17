package com.plagod.service;

import com.plagod.entity.MarketplaceFulfillment;
import com.plagod.entity.MarketplaceOrder;
import com.plagod.exception.ApiStatusException;
import com.plagod.mapper.MarketplaceFulfillmentMapper;
import com.plagod.mapper.MarketplaceOrderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class MarketplaceOrderIdempotencyService {

    private static final Pattern CLIENT_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$");

    private final MarketplaceOrderMapper orderMapper;
    private final MarketplaceFulfillmentMapper fulfillmentMapper;
    private final TransactionOperations transactions;

    public MarketplaceOrderIdempotencyService(
            MarketplaceOrderMapper orderMapper,
            MarketplaceFulfillmentMapper fulfillmentMapper,
            PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.fulfillmentMapper = fulfillmentMapper;
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions = transactionTemplate;
    }

    public MarketplaceOrderReplayResult createOrReplay(
            MarketplaceOrderCreation creation) {
        Objects.requireNonNull(creation, "订单原子写入输入不能为空");
        MarketplaceOrder requestedOrder = creation.getOrder();
        MarketplaceFulfillment requestedFulfillment =
                creation.getFulfillment();
        validateRequest(requestedOrder);
        validateFulfillmentInput(
                requestedOrder,
                requestedFulfillment);

        try {
            MarketplaceOrderReplayResult result = transactions.execute(status -> {
                MarketplaceOrder existing = findExisting(requestedOrder);
                if (existing != null) {
                    return replay(existing, requestedOrder.getRequestFingerprint());
                }

                persistOrderAndFulfillment(
                        requestedOrder,
                        requestedFulfillment);
                return new MarketplaceOrderReplayResult(
                        requestedOrder,
                        false);
            });
            return requireResult(result);
        } catch (DuplicateKeyException duplicateKeyException) {
            MarketplaceOrderReplayResult result = transactions.execute(status -> {
                MarketplaceOrder existing = findExisting(requestedOrder);
                if (existing == null) {
                    throw duplicateKeyException;
                }
                return replay(existing, requestedOrder.getRequestFingerprint());
            });
            return requireResult(result);
        }
    }

    private void persistOrderAndFulfillment(
            MarketplaceOrder order,
            MarketplaceFulfillment fulfillment) {
        int insertedOrder = orderMapper.insert(order);
        requireSingleInsert(insertedOrder, "订单");
        if (order.getOrderId() == null
                || !StringUtils.hasText(order.getOrderNo())) {
            throw new IllegalStateException("订单写入后缺少持久化标识");
        }

        fulfillment.setTenantId(order.getTenantId());
        fulfillment.setOrderId(order.getOrderId());
        fulfillment.setStatus("PENDING");
        int insertedFulfillment = fulfillmentMapper.insert(fulfillment);
        requireSingleInsert(insertedFulfillment, "履约 Outbox");
        if (fulfillment.getFulfillmentId() == null) {
            throw new IllegalStateException(
                    "履约 Outbox 写入后缺少持久化标识");
        }
    }

    private MarketplaceOrder findExisting(MarketplaceOrder order) {
        return orderMapper.selectByIdempotencyKey(
                order.getTenantId(),
                order.getSubjectType(),
                subjectKey(order),
                order.getClientRequestId());
    }

    private MarketplaceOrderReplayResult replay(
            MarketplaceOrder existing,
            String requestFingerprint) {
        if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
            throw ApiStatusException.idempotencyConflict(
                    "clientRequestId 已用于不同的订单请求");
        }
        return new MarketplaceOrderReplayResult(existing, true);
    }

    private void validateRequest(MarketplaceOrder order) {
        Objects.requireNonNull(order, "订单幂等请求不能为空");
        requirePositive(order.getTenantId(), "tenantId");
        requirePositive(order.getActorUserId(), "actorUserId");
        subjectKey(order);

        if (!StringUtils.hasText(order.getClientRequestId())
                || !CLIENT_REQUEST_ID.matcher(order.getClientRequestId()).matches()) {
            throw new IllegalArgumentException("clientRequestId 格式非法");
        }
        if (!StringUtils.hasText(order.getRequestFingerprint())
                || order.getRequestFingerprint().length() != 64) {
            throw new IllegalArgumentException("requestFingerprint 必须为 64 字符");
        }
    }

    private void validateFulfillmentInput(
            MarketplaceOrder order,
            MarketplaceFulfillment fulfillment) {
        Objects.requireNonNull(
                fulfillment,
                "履约 Outbox 输入不能为空");
        if (fulfillment.getFulfillmentId() != null
                || fulfillment.getOrderId() != null
                || fulfillment.getTenantId() != null) {
            throw new IllegalArgumentException(
                    "履约 Outbox 输入不得预置持久化归属");
        }
        requirePositive(fulfillment.getOrderItemId(), "orderItemId");
        requireText(
                fulfillment.getEventKey(),
                128,
                "eventKey");
        requireText(
                fulfillment.getTargetBusinessKey(),
                128,
                "targetBusinessKey");
        if (!"TARGET_DOMAIN".equals(
                fulfillment.getFulfillmentMode())) {
            throw new IllegalArgumentException(
                    "订单原子写入仅支持 TARGET_DOMAIN 履约");
        }
        if (!"PERSONAL_ENTITLEMENT".equals(
                fulfillment.getFulfillmentType())
                && !"SAAS_SUBSCRIPTION".equals(
                fulfillment.getFulfillmentType())) {
            throw new IllegalArgumentException(
                    "履约类型不属于 TARGET_DOMAIN");
        }
        if (StringUtils.hasText(fulfillment.getStatus())
                || fulfillment.getResultReference() != null
                || fulfillment.getWorkerId() != null
                || fulfillment.getLeaseUntil() != null) {
            throw new IllegalArgumentException(
                    "新履约 Outbox 不得预置处理状态");
        }
    }

    private void requireText(
            String value,
            int maxLength,
            String field) {
        if (!StringUtils.hasText(value)
                || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " 必须为 1 到 " + maxLength + " 字符");
        }
    }

    private void requireSingleInsert(int inserted, String objectName) {
        if (inserted != 1) {
            throw new IllegalStateException(
                    objectName + " 写入行数异常");
        }
    }

    private String subjectKey(MarketplaceOrder order) {
        if ("USER".equals(order.getSubjectType())) {
            requirePositive(order.getUserId(), "userId");
            return "USER:" + order.getUserId();
        }
        if ("TENANT".equals(order.getSubjectType()) && order.getUserId() == null) {
            return "TENANT";
        }
        throw new IllegalArgumentException("订单 subjectType/userId 组合非法");
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
    }

    private MarketplaceOrderReplayResult requireResult(
            MarketplaceOrderReplayResult result) {
        if (result == null) {
            throw new IllegalStateException("订单幂等事务未返回结果");
        }
        return result;
    }
}
