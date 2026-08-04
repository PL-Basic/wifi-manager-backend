package com.plagod.configuration;

import com.plagod.constant.EntitlementTradeConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Data
@Component
@ConfigurationProperties(prefix = "wifi.entitlement")
public class EntitlementProductProperties {

    public static final String CUSTOM_DURATION_PRODUCT_CODE = "DURATION_CUSTOM";

    private int orderExpireMinutes = 15;
    private List<Product> products = new ArrayList<>();
    private boolean customDurationEnabled = true;
    private long customDurationMinAmountCents = 100L;
    private long customDurationMaxAmountCents = 100000L;
    private long customDurationSecondsPerCent = 36L;

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

        return products.stream()
                .filter(product -> product != null && product.isEnabled())
                .peek(this::validateProduct)
                .collect(Collectors.toList());
    }

    public int effectiveOrderExpireMinutes() {
        if (orderExpireMinutes <= 0) {
            return 15;
        }
        return Math.min(orderExpireMinutes, 1440);
    }

    private void validateProduct(Product product) {
        String mode = normalizeMode(product.getMode());

        if (!EntitlementTradeConstants.MODE_DURATION.equals(mode)
                && !EntitlementTradeConstants.MODE_SUBSCRIPTION.equals(mode)) {
            throw new IllegalStateException("权益商品模式配置无效");
        }

        if (product.getGrantSeconds() == null || product.getGrantSeconds() <= 0) {
            throw new IllegalStateException("权益商品发放时长配置无效");
        }

        if (product.getAmountCents() == null || product.getAmountCents() <= 0) {
            throw new IllegalStateException("权益商品金额配置无效");
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
        private Long grantSeconds;
        private Long amountCents;
        private boolean enabled = true;
    }
}
