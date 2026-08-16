package com.plagod.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantScopedMapperContractTest {

    @Test
    void userTradeLoadsFilterTenantAndOwnerBeforeLocking()
            throws Exception {
        assertSelectContains(
                EntitlementOrderMapper.class.getMethod(
                        "selectOwnedOrderForUpdate",
                        Long.class,
                        String.class,
                        Long.class),
                "tenant_id",
                "user_id",
                "for update");
        assertSelectContains(
                NetworkEntitlementMapper.class.getMethod(
                        "selectOwnedEntitlement",
                        Long.class,
                        Long.class,
                        Long.class),
                "tenant_id",
                "user_id",
                "entitlement_id");
        assertSelectContains(
                RefundRecordMapper.class.getMethod(
                        "selectByTenantAndRefundNoForUpdate",
                        Long.class,
                        String.class),
                "tenant_id",
                "refund_no",
                "for update");
    }

    private void assertSelectContains(
            Method method,
            String... fragments) {
        Select select = method.getAnnotation(Select.class);
        assertTrue(select != null, method.getName());
        String sql = String.join(" ", Arrays.asList(select.value()))
                .toLowerCase();
        for (String fragment : fragments) {
            assertTrue(
                    sql.contains(fragment),
                    method.getName() + " missing " + fragment);
        }
    }
}
