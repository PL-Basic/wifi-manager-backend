package com.plagod.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.monitor.AuditLog;
import com.plagod.mapper.AuditLogMapper;
import com.plagod.vo.monitor.AuditLogPageResult;
import com.plagod.vo.monitor.AuditLogVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AuditLogQueryServiceImplTest {

    private AuditLogMapper auditLogMapper;
    private AuditLogQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        service = new AuditLogQueryServiceImpl();
        ReflectionTestUtils.setField(service, "auditLogMapper", auditLogMapper);
    }

    @Test
    void preservesPaginationBoundsAndIgnoresBlankFilters() {
        AuditLog entity = new AuditLog();
        entity.setId(7L);
        entity.setAction("device.update");
        Page<AuditLog> mappedPage = new Page<>(1, 10);
        mappedPage.setTotal(1);
        mappedPage.setRecords(Collections.singletonList(entity));
        when(auditLogMapper.selectAuditPage(
                any(Page.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()))
                .thenReturn(mappedPage);

        AuditLogPageResult result =
                service.pageAudits(0, 0, " ", "", "\t", null, null);

        ArgumentCaptor<Page<AuditLog>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        verify(auditLogMapper).selectAuditPage(
                pageCaptor.capture(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull());
        assertEquals(1, pageCaptor.getValue().getCurrent());
        assertEquals(10, pageCaptor.getValue().getSize());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(Long.valueOf(7L), result.getRecords().get(0).getId());
    }

    @Test
    void passesExistingFiltersAndCapsPageSize() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 13, 0, 0);
        Page<AuditLog> mappedPage = new Page<>(2, 100);
        when(auditLogMapper.selectAuditPage(
                any(Page.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(mappedPage);

        service.pageAudits(
                2,
                1000,
                "login",
                "operator",
                "user-7",
                start,
                end);

        ArgumentCaptor<Page<AuditLog>> pageCaptor =
                ArgumentCaptor.forClass(Page.class);
        verify(auditLogMapper).selectAuditPage(
                pageCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("login"),
                org.mockito.ArgumentMatchers.eq("operator"),
                org.mockito.ArgumentMatchers.eq("user-7"),
                org.mockito.ArgumentMatchers.eq(start),
                org.mockito.ArgumentMatchers.eq(end));
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(100, pageCaptor.getValue().getSize());
    }

    @Test
    void readsExistingAuditById() {
        AuditLog entity = new AuditLog();
        entity.setId(9L);
        entity.setAction("account.login");
        when(auditLogMapper.selectAuditById(9L)).thenReturn(entity);

        AuditLogVO result = service.getAudit(9L);

        assertEquals(Long.valueOf(9L), result.getId());
        assertEquals("account.login", result.getAction());
    }

    @Test
    void reportsMissingAuditById() {
        when(auditLogMapper.selectAuditById(11L)).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.getAudit(11L));

        assertEquals("审计记录不存在", error.getMessage());
    }
}
