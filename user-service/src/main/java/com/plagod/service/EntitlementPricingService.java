package com.plagod.service;

import com.plagod.configuration.EntitlementProductProperties;
import com.plagod.constant.EntitlementTradeConstants;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class EntitlementPricingService {

    private final EntitlementProductProperties properties;

    public EntitlementPricingService(EntitlementProductProperties properties) {
        this.properties = properties;
    }

    public PricingSnapshot resolve(EntitlementProductProperties.Product product) {
        PricingSnapshot snapshot = new PricingSnapshot();
        snapshot.setPricingVersion(properties.effectivePricingVersion());
        snapshot.setGrantSeconds(product.getGrantSeconds());
        snapshot.setGrantMonths(product.getGrantMonths());
        snapshot.setAmountCents(product.getAmountCents());

        if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(
                properties.normalizeMode(product.getMode()))) {
            long reference = minimumFixedDurationAmount(
                    Math.multiplyExact(product.getGrantMonths(), 30 * 24));
            long oneMonthAmount = requireOneMonthAmount();
            long linearAmount = Math.multiplyExact(oneMonthAmount, product.getGrantMonths());

            snapshot.setReferenceAmountCents(reference);
            snapshot.setSubscriptionRatioBps(ratioBps(product.getAmountCents(), reference));
            snapshot.setPeriodDiscountBps(ratioBps(product.getAmountCents(), linearAmount));
        }
        return snapshot;
    }

    public void validateCatalog() {
        List<EntitlementProductProperties.Product> subscriptions = new ArrayList<>();
        for (EntitlementProductProperties.Product product : properties.getEnabledProducts()) {
            if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(
                    properties.normalizeMode(product.getMode()))) {
                subscriptions.add(product);
            }
            resolve(product);
        }

        subscriptions.sort(Comparator.comparing(EntitlementProductProperties.Product::getGrantMonths));
        long previousMonthlyScaled = Long.MAX_VALUE;
        int previousRatio = Integer.MAX_VALUE;
        for (EntitlementProductProperties.Product product : subscriptions) {
            PricingSnapshot snapshot = resolve(product);
            long monthlyScaled = Math.multiplyExact(product.getAmountCents(), 10000L)
                    / product.getGrantMonths();
            if (monthlyScaled >= previousMonthlyScaled
                    || snapshot.getSubscriptionRatioBps() >= previousRatio) {
                throw new IllegalStateException("订阅周期越长时月均价和等价直购比例必须严格下降");
            }
            previousMonthlyScaled = monthlyScaled;
            previousRatio = snapshot.getSubscriptionRatioBps();
        }
    }

    private long requireOneMonthAmount() {
        for (EntitlementProductProperties.Product product : properties.getEnabledProducts()) {
            if (EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(
                    properties.normalizeMode(product.getMode()))
                    && Integer.valueOf(1).equals(product.getGrantMonths())) {
                return product.getAmountCents();
            }
        }
        throw new IllegalStateException("缺少1个月订阅商品");
    }

    private long minimumFixedDurationAmount(int targetHours) {
        List<EntitlementProductProperties.Product> durationProducts = new ArrayList<>();
        int maxHours = 0;
        for (EntitlementProductProperties.Product product : properties.getEnabledProducts()) {
            if (!EntitlementTradeConstants.MODE_DURATION.equals(
                    properties.normalizeMode(product.getMode()))) {
                continue;
            }
            if (product.getGrantSeconds() % 3600 != 0) {
                throw new IllegalStateException("固定时长商品必须按整小时配置");
            }
            int hours = Math.toIntExact(product.getGrantSeconds() / 3600);
            maxHours = Math.max(maxHours, hours);
            durationProducts.add(product);
        }
        if (durationProducts.isEmpty()) {
            throw new IllegalStateException("缺少固定时长商品，无法计算订阅参照价");
        }

        int limit = Math.addExact(targetHours, maxHours);
        long unreachable = Long.MAX_VALUE / 4;
        long[] costs = new long[limit + 1];
        java.util.Arrays.fill(costs, unreachable);
        costs[0] = 0L;

        for (int hour = 1; hour <= limit; hour++) {
            for (EntitlementProductProperties.Product product : durationProducts) {
                int productHours = Math.toIntExact(product.getGrantSeconds() / 3600);
                if (hour >= productHours && costs[hour - productHours] != unreachable) {
                    costs[hour] = Math.min(
                            costs[hour],
                            Math.addExact(costs[hour - productHours], product.getAmountCents()));
                }
            }
        }

        long result = unreachable;
        for (int hour = targetHours; hour <= limit; hour++) {
            result = Math.min(result, costs[hour]);
        }
        if (result == unreachable) {
            throw new IllegalStateException("无法计算订阅参照直购金额");
        }
        return result;
    }

    private int ratioBps(long amount, long reference) {
        if (amount <= 0 || reference <= 0 || amount > reference) {
            throw new IllegalStateException("订阅金额或参照金额无效");
        }
        return Math.toIntExact(
                Math.addExact(Math.multiplyExact(amount, 10000L), reference / 2) / reference);
    }

    @Data
    public static class PricingSnapshot {
        private Integer pricingVersion;
        private Long grantSeconds;
        private Integer grantMonths;
        private Long amountCents;
        private Long referenceAmountCents;
        private Integer subscriptionRatioBps;
        private Integer periodDiscountBps;
    }
}
