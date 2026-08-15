package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.plagod.mapper.ClientSignalMapper;
import com.plagod.mapper.DeviceCommandRecordMapper;
import com.plagod.mapper.Esp32NodeMapper;
import com.plagod.mapper.MacBlacklistMapper;
import com.plagod.mapper.SessionRecordMapper;
import com.plagod.mapper.TrafficLogMapper;
import com.plagod.support.PageBounds;
import com.plagod.utils.DevicePageBounds;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DevicePaginationContractTest {

    @Test
    void legacyLongParametersUseSharedPageBoundsForNegativeAndOversizeValues() {
        PageBounds negative = DevicePageBounds.normalize(-7L, -3L);
        PageBounds oversized =
                DevicePageBounds.normalize(Long.MAX_VALUE, Long.MAX_VALUE);

        assertEquals(1, negative.getCurrent());
        assertEquals(10, negative.getSize());
        assertEquals(Integer.MAX_VALUE, oversized.getCurrent());
        assertEquals(100, oversized.getSize());
    }

    @Test
    void devicePagesUseSharedBoundsAndStablePrimaryKeyTiebreakers() {
        Esp32NodeMapper nodeMapper = mock(Esp32NodeMapper.class);
        MacBlacklistMapper blacklistMapper = mock(MacBlacklistMapper.class);
        when(nodeMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(blacklistMapper.selectPage(
                any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCommandServiceImpl service = new DeviceCommandServiceImpl();
        ReflectionTestUtils.setField(service, "esp32NodeMapper", nodeMapper);
        ReflectionTestUtils.setField(
                service, "macBlacklistMapper", blacklistMapper);

        service.pageDevices(7L, -1L, 101L, null);
        service.pageBlacklist(7L, -1L, 101L, null);

        CapturedPage nodePage = capturePage(nodeMapper);
        CapturedPage blacklistPage = capturePage(blacklistMapper);
        assertBounds(nodePage.page);
        assertBounds(blacklistPage.page);
        assertOrder(nodePage.wrapper, "create_time", "node_id");
        assertOrder(blacklistPage.wrapper, "create_time", "id");
    }

    @Test
    void sessionPageUsesSharedBoundsAndStableSessionIdTiebreaker() {
        SessionRecordMapper mapper = mock(SessionRecordMapper.class);
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SessionQueryServiceImpl service = new SessionQueryServiceImpl();
        ReflectionTestUtils.setField(service, "sessionRecordMapper", mapper);

        service.pageSessions(7L, -1L, 101L, null, null, null, null);

        CapturedPage captured = capturePage(mapper);
        assertBounds(captured.page);
        assertOrder(captured.wrapper, "login_time", "session_id");
    }

    @Test
    void telemetryPagesKeepStableUniqueOrderingWhileUsingSharedBounds() {
        ClientSignalMapper signalMapper = mock(ClientSignalMapper.class);
        TrafficLogMapper trafficMapper = mock(TrafficLogMapper.class);
        when(signalMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(trafficMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClientSignalQueryServiceImpl signalService =
                new ClientSignalQueryServiceImpl();
        ReflectionTestUtils.setField(
                signalService, "clientSignalMapper", signalMapper);
        TrafficQueryServiceImpl trafficService =
                new TrafficQueryServiceImpl();
        ReflectionTestUtils.setField(
                trafficService, "trafficLogMapper", trafficMapper);

        signalService.pageClientSignals(
                7L, -1L, 101L, null, null, null, null, null, null, null);
        trafficService.pageTraffic(
                7L, -1L, 101L, null, null, null, null, null);

        CapturedPage signalPage = capturePage(signalMapper);
        CapturedPage trafficPage = capturePage(trafficMapper);
        assertBounds(signalPage.page);
        assertBounds(trafficPage.page);
        assertOrder(signalPage.wrapper, "report_time", "id");
        assertOrder(trafficPage.wrapper, "log_time", "id");
    }

    @Test
    void commandPageUsesSharedBoundsAndUniqueCommandIdOrdering() {
        DeviceCommandRecordMapper mapper =
                mock(DeviceCommandRecordMapper.class);
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCommandQueryServiceImpl service =
                new DeviceCommandQueryServiceImpl();
        ReflectionTestUtils.setField(
                service, "deviceCommandRecordMapper", mapper);

        service.pageCommands(
                7L, -1L, 101L, null, null, null, null, null, null, null);

        CapturedPage captured = capturePage(mapper);
        assertBounds(captured.page);
        String sql = captured.wrapper.getSqlSegment()
                .toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("order by command_id desc"), sql);
    }

    private CapturedPage capturePage(Object mapper) {
        ArgumentCaptor<IPage> pageCaptor =
                ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);

        if (mapper instanceof Esp32NodeMapper) {
            verify((Esp32NodeMapper) mapper).selectPage(
                    pageCaptor.capture(), wrapperCaptor.capture());
        } else if (mapper instanceof MacBlacklistMapper) {
            verify((MacBlacklistMapper) mapper).selectPage(
                    pageCaptor.capture(), wrapperCaptor.capture());
        } else if (mapper instanceof SessionRecordMapper) {
            verify((SessionRecordMapper) mapper).selectPage(
                    pageCaptor.capture(), wrapperCaptor.capture());
        } else if (mapper instanceof ClientSignalMapper) {
            verify((ClientSignalMapper) mapper).selectPage(
                    pageCaptor.capture(), wrapperCaptor.capture());
        } else if (mapper instanceof TrafficLogMapper) {
            verify((TrafficLogMapper) mapper).selectPage(
                    pageCaptor.capture(), wrapperCaptor.capture());
        } else if (mapper instanceof DeviceCommandRecordMapper) {
            verify((DeviceCommandRecordMapper) mapper).selectPage(
                    pageCaptor.capture(), wrapperCaptor.capture());
        } else {
            throw new AssertionError("unsupported mapper " + mapper);
        }

        return new CapturedPage(
                pageCaptor.getValue(), wrapperCaptor.getValue());
    }

    private void assertBounds(IPage<?> page) {
        assertEquals(1L, page.getCurrent());
        assertEquals(100L, page.getSize());
    }

    private void assertOrder(
            Wrapper<?> wrapper, String primary, String secondary) {
        String sql = wrapper.getSqlSegment()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ",")
                .trim();
        String expectedOrder =
                "order by " + primary + " desc," + secondary + " desc";

        assertTrue(sql.contains(expectedOrder), sql);
    }

    private static final class CapturedPage {
        private final IPage<?> page;
        private final Wrapper<?> wrapper;

        private CapturedPage(IPage<?> page, Wrapper<?> wrapper) {
            this.page = page;
            this.wrapper = wrapper;
        }
    }
}
