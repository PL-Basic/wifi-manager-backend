package com.plagod.service.impl;

import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.entity.auth.VerifyCode;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.ratelimit.VerificationCodeRedisRateLimiter;
import com.plagod.sender.VerifyCodeSender;
import com.plagod.sender.phone.PhoneVerificationProviderRegistry;
import com.plagod.service.VerificationCodeStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeCommandBindingTest {

    private VerifyCodeMapper mapper;
    private VerificationCodeStateService stateService;
    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(VerifyCodeMapper.class);
        stateService = mock(VerificationCodeStateService.class);
        service = new VerificationCodeServiceImpl(
                new VerificationCodeProperties(),
                new PhoneVerificationProperties(),
                mapper,
                mock(VerifyCodeSender.class),
                mock(PhoneVerificationProviderRegistry.class),
                stateService,
                mock(VerificationCodeRedisRateLimiter.class));
    }

    @Test
    void consumedCommandKeyMakesCheckAndConsumeReplayable() {
        when(mapper.selectOne(any())).thenReturn(consumedRecord());

        assertTrue(service.checkCodeForRequest(
                "alice@example.com",
                "register",
                "ABC123",
                "command-key"));
        service.consumeCodeForRequest(
                "alice@example.com",
                "register",
                "ABC123",
                "192.168.1.23",
                "command-key");

        verify(stateService, never()).verifyAndRemember(
                anyString(),
                anyString(),
                anyString());
        verify(mapper, never()).consumeVerifiedCode(
                any(),
                any(),
                anyString(),
                anyString());
    }

    @Test
    void concurrentConsumeAcceptsOnlyTheSameCommandKey() {
        when(mapper.selectOne(any()))
                .thenReturn(null, consumedRecord());
        when(stateService.verifyAndRemember(
                "alice@example.com",
                "register",
                "ABC123"))
                .thenReturn(VerificationCodeStateService.Decision.verified(7L));
        when(mapper.consumeVerifiedCode(
                any(),
                any(),
                anyString(),
                anyString())).thenReturn(0);

        service.consumeCodeForRequest(
                "alice@example.com",
                "register",
                "ABC123",
                "192.168.1.23",
                "command-key");

        when(mapper.selectOne(any())).thenReturn(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> service.consumeCodeForRequest(
                        "alice@example.com",
                        "register",
                        "ABC123",
                        "192.168.1.23",
                        "other-command-key"));
    }

    @Test
    void freshCheckReportsThatCommandIsNotYetConsumed() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(stateService.verifyAndRemember(
                "alice@example.com",
                "register",
                "ABC123"))
                .thenReturn(VerificationCodeStateService.Decision.verified(7L));

        assertFalse(service.checkCodeForRequest(
                "alice@example.com",
                "register",
                "ABC123",
                "command-key"));
    }

    private VerifyCode consumedRecord() {
        VerifyCode record = new VerifyCode();
        record.setId(7L);
        record.setStatus(1);
        record.setConsumeRequestKey("command-key");
        return record;
    }
}
