package com.plagod.configuration;

import com.plagod.constant.EntitlementTradeConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "wifi.entitlement")
public class EntitlementProductProperties {

    public static final String CUSTOM_DURATION_PRODUCT_CODE = "DURATION_CUSTOM";
    private static final Set<Integer> ALLOWED_SUBSCRIPTION_MONTHS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(1, 3, 6, 12)));

    private int orderExpireMinutes = 15;
    private int pricingVersion = 1;
    private List<Product> products = new ArrayList<>();
    private boolean customDurationEnabled = true;
    private long customDurationMinAmountCents = 100L;
    private long customDurationMaxAmountCents = 100000L;
    private long customDurationSecondsPerCent = 180L;

    public Product requireOrderProduct(String rawProductCode, Long customAmountCents) {
        String productCode = normalizeProductCode(rawProductCode);

        if (CUSTOM_DURATION_PRODUCT_CODE.equals(productCode)) {
            return createCustomDurationProduct(customAmountCents);
        }

        if (customAmountCents != null) {
            throw new IllegalArgumentException("固定商品不能指定自定义金额");
        }

        return requireEnabledProduct(productCode);
    }

    public Product createCustomDurationProduct(Long amountCents) {
        validateCustomDurationConfiguration();

        if (!customDurationEnabled) {
            throw new IllegalArgumentException("自定义时长充值暂不可用");
        }

        if (amountCents == null) {
            throw new IllegalArgumentException("自定义金额不能为空");
        }

        if (amountCents < customDurationMinAmountCents || amountCents > customDurationMaxAmountCents) {
            throw new IllegalArgumentException("自定义金额超出可购买范围");
        }

        Product product = new Product();
        product.setCode(CUSTOM_DURATION_PRODUCT_CODE);
        product.setName("自定义网络时长");
        product.setMode(EntitlementTradeConstants.MODE_DURATION);
        product.setPricingVersion(effectivePricingVersion());
        product.setAmountCents(amountCents);

        try {
            product.setGrantSeconds(Math.multiplyExact(amountCents, customDurationSecondsPerCent));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("自定义金额对应时长超出范围");
        }

        product.setEnabled(true);
        return product;
    }

    private void validateCustomDurationConfiguration() {
        if (customDurationMinAmountCents <= 0
                || customDurationMaxAmountCents < customDurationMinAmountCents
                || customDurationSecondsPerCent <= 0) {
            throw new IllegalStateException("自定义时长商品配置无效");
        }
    }

    public Product requireEnabledProduct(String rawProductCode) {
        String productCode = normalizeProductCode(rawProductCode);

        for (Product product : products) {
            if (product != null && product.isEnabled() && productCode.equals(normalizeProductCode(product.getCode()))) {

                validateProduct(product);
                return product;
            }
        }

        throw new IllegalArgumentException("商品不存在或暂不可用");
    }

    public List<Product> getEnabledProducts() {
        if (products == null) {
            return Collections.emptyList();
        }

        List<Product> enabled = products.stream()
                .filter(product -> product != null && product.isEnabled())
                .peek(this::validateProduct)
                .collect(Collectors.toList());
        validateSubscriptionCatalog(enabled);
        return enabled;
    }

    public int effectiveOrderExpireMinutes() {
        if (orderExpireMinutes <= 0) {
            return 15;
        }
        return Math.min(orderExpireMinutes, 1440);
    }

    public int effectivePricingVersion() {
        if (pricingVersion <= 0) {
            throw new IllegalStateException("权益商品定价版本必须大于0");
        }
        return pricingVersion;
    }

    private void validateProduct(Product product) {
        String mode = normalizeMode(product.getMode());

        if (!EntitlementTradeConstants.MODE_DURATION.equals(mode)
                && !EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(mode)) {
            throw new IllegalStateException("权益商品模式配置无效");
        }

        if (EntitlementTradeConstants.MODE_DURATION.equals(mode)) {
            if (product.getGrantSeconds() == null || product.getGrantSeconds() <= 0) {
                throw new IllegalStateException("时长商品发放秒数配置无效");
            }
            if (product.getGrantMonths() != null) {
                throw new IllegalStateException("时长商品不能配置自然月");
            }
        } else {
            if (product.getGrantMonths() == null
                    || !ALLOWED_SUBSCRIPTION_MONTHS.contains(product.getGrantMonths())) {
                throw new IllegalStateException("订阅商品自然月只能为1、3、6或12");
            }
            if (product.getGrantSeconds() != null && product.getGrantSeconds() != 0) {
                throw new IllegalStateException("自然月订阅不能再配置固定秒数");
            }
        }

        if (product.getAmountCents() == null || product.getAmountCents() <= 0) {
            throw new IllegalStateException("权益商品金额配置无效");
        }
    }

    private void validateSubscriptionCatalog(List<Product> enabled) {
        Set<Integer> months = new HashSet<>();
        for (Product product : enabled) {
            if (!EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(normalizeMode(product.getMode()))) {
                continue;
            }
            if (!months.add(product.getGrantMonths())) {
                throw new IllegalStateException("同一定价版本不能重复配置订阅月数");
            }
        }
        if (!months.isEmpty() && !months.equals(ALLOWED_SUBSCRIPTION_MONTHS)) {
            throw new IllegalStateException("订阅商品必须完整配置1、3、6、12个月");
        }
    }

    public String normalizeProductCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("商品编码不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    @Data
    public static class Product {

        private String code;
        private String name;
        private String mode;
        private Integer pricingVersion;
        private Long grantSeconds;
        private Integer grantMonths;
        private Long amountCents;
        private boolean enabled = true;
    }
}
