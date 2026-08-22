package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.monitor.AccessRule;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.mapper.AccessRuleMapper;
import com.plagod.mapper.AlertEventMapper;
import com.plagod.security.MonitorTenantScope;
import com.plagod.security.TrustedContextType;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class MonitorPaginationContractTest {

    @Test
    void rulePageUsesSharedBoundsAndStableSecondarySort() {
        AccessRuleMapper mapper = mock(AccessRuleMapper.class);
        when(mapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> emptyPage(
                        invocation.getArgument(0)));
        AccessRuleServiceImpl service = new AccessRuleServiceImpl();
        ReflectionTestUtils.setField(service, "accessRuleMapper", mapper);

        service.pageRules(-1L, Long.MAX_VALUE, null, null, null);

        ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<QueryWrapper> query =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(page.capture(), query.capture());
        assertEquals(1L, page.getValue().getCurrent());
        assertEquals(100L, page.getValue().getSize());
        assertStableCreateTimeAndIdSort(query.getValue());
    }

    @Test
    void alertPageUsesSharedBoundsAndStableSecondarySort() {
        AlertEventMapper mapper = mock(AlertEventMapper.class);
        when(mapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> emptyPage(
                        invocation.getArgument(0)));
        AlertEventServiceImpl service = new AlertEventServiceImpl();
        ReflectionTestUtils.setField(service, "alertEventMapper", mapper);
        ReflectionTestUtils.setField(
                service,
                "tenantScope",
                new MonitorTenantScope());

        service.pageAlerts(
                tenantContext(),
                -1L,
                Long.MAX_VALUE,
                null,
                null,
                null,
                null,
                null);

        ArgumentCaptor<Page> page = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<QueryWrapper> query =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(page.capture(), query.capture());
        assertEquals(1L, page.getValue().getCurrent());
        assertEquals(100L, page.getValue().getSize());
        assertStableCreateTimeAndIdSort(query.getValue());
        assertTrue(
                query.getValue().getSqlSegment()
                        .replaceAll("\\s+", "")
                        .toLowerCase(Locale.ROOT)
                        .contains("tenant_id="),
                query.getValue().getSqlSegment());
    }

    private <T> Page<T> emptyPage(Page<T> page) {
        page.setRecords(Collections.<T>emptyList());
        return page;
    }

    private void assertStableCreateTimeAndIdSort(
            QueryWrapper<?> query) {
        String sql = query.getSqlSegment()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("orderbycreate_timedesc,iddesc"), sql);
    }

    private TrustedRequestContext tenantContext() {
        return TrustedRequestContext.user(
                TrustedSource.GATEWAY_USER,
                7L,
                1,
                "session-pagination",
                "token-pagination",
                TrustedContextType.TENANT,
                "11",
                "tenant-a",
                "TENANT_ADMIN",
                1L,
                1L,
                Collections.emptyList(),
                "request-pagination");
    }
}
