package com.plagod.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.plagod.configuration.PhoneVerificationProperties;
import com.plagod.configuration.VerificationCodeProperties;
import com.plagod.entity.auth.VerifyCode;
import com.plagod.exception.VerificationDeliveryException;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.ratelimit.VerificationCodeRedisRateLimiter;
import com.plagod.sender.VerifyCodeSender;
import com.plagod.sender.phone.PhoneVerificationProvider;
import com.plagod.sender.phone.PhoneVerificationProviderRegistry;
import com.plagod.service.VerificationCodeStateService;
import com.plagod.transaction.TestTransactionManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeSensitiveDataTest {

    @Test
    void providerFailureDoesNotReachLogOrSendError() {
        String canary = "provider-secret-canary";
        VerifyCodeMapper mapper = mock(VerifyCodeMapper.class);
        PhoneVerificationProviderRegistry registry =
                mock(PhoneVerificationProviderRegistry.class);
        PhoneVerificationProvider provider =
                mock(PhoneVerificationProvider.class);
        VerificationCodeRedisRateLimiter rateLimiter =
                mock(VerificationCodeRedisRateLimiter.class);
        when(rateLimiter.acquire(
                anyString(),
                anyString(),
                anyString(),
                any())).thenReturn(true);
        when(mapper.insert(any(VerifyCode.class))).thenReturn(1);
        when(mapper.finalizeSendFailure(any(VerifyCode.class)))
                .thenReturn(1);
        when(registry.current()).thenReturn(provider);
        when(provider.providerName()).thenReturn("aliyun-number-auth");
        when(provider.send(
                anyString(),
                anyString(),
                anyString())).thenThrow(new IllegalStateException(canary));

        VerificationCodeServiceImpl service =
                new VerificationCodeServiceImpl(
                        new VerificationCodeProperties(),
                        new PhoneVerificationProperties(
                                new MockEnvironment()),
                        mapper,
                        mock(VerifyCodeSender.class),
                        registry,
                        mock(VerificationCodeStateService.class),
                        rateLimiter,
                        new TestTransactionManager());

        Logger logger = (Logger) LoggerFactory.getLogger(
                VerificationCodeServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            VerificationDeliveryException failure = assertThrows(
                    VerificationDeliveryException.class,
                    () -> service.sendCode(
                            "13800000000",
                            "login",
                            "127.0.0.1"));
            assertEquals(
                    "验证码发送服务暂时不可用",
                    failure.getMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ArgumentCaptor<VerifyCode> record =
                ArgumentCaptor.forClass(VerifyCode.class);
        verify(mapper).finalizeSendFailure(record.capture());

        assertFalse(record.getValue().getSendError().contains(canary));
        assertTrue(record.getValue().getSendError().contains(
                "message=[REDACTED]"));
        assertEquals(
                "PROVIDER_EXCEPTION",
                record.getValue().getProviderSendCode());

        String rendered = appender.list.get(0).getFormattedMessage();
        assertFalse(rendered.contains(canary));
        assertTrue(rendered.contains(
                IllegalStateException.class.getName()));
        assertNull(appender.list.get(0).getThrowableProxy());
    }
}
