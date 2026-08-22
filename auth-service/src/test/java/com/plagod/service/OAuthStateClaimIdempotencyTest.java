package com.plagod.service;

import com.plagod.entity.auth.OAuthStateRecord;
import com.plagod.mapper.OAuthStateMapper;
import com.plagod.dto.OAuthStateContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuthStateClaimIdempotencyTest {

    private OAuthStateMapper mapper;
    private OAuthStateTransactionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OAuthStateMapper.class);
        service = new OAuthStateTransactionService();
        ReflectionTestUtils.setField(service, "oauthStateMapper", mapper);
    }

    @Test
    void completedStateReplaysStoredResultForSameAuthorizationCode() {
        OAuthStateRecord record = completedRecord("authorization-code");
        when(mapper.selectByHashForUpdate(sha256("raw-state")))
                .thenReturn(record);

        OAuthStateContext replay =
                service.claim("github", "raw-state", "authorization-code");

        assertTrue(replay.isReplayed());
        assertEquals("LOGIN_READY", replay.getResultStatus());
        assertEquals(7L, replay.getResultUserId());
        assertEquals("登录成功", replay.getResultMessage());
    }

    @Test
    void completedStateRejectsDifferentAuthorizationCode() {
        OAuthStateRecord record = completedRecord("authorization-code");
        when(mapper.selectByHashForUpdate(sha256("raw-state")))
                .thenReturn(record);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(
                        "github",
                        "raw-state",
                        "different-code"));
    }

    private OAuthStateRecord completedRecord(String authorizationCode) {
        OAuthStateRecord record = new OAuthStateRecord();
        record.setStateId(11L);
        record.setProvider("github");
        record.setPurpose("LOGIN");
        record.setStatus(2);
        record.setAuthorizationCodeHash(sha256(authorizationCode));
        record.setResultStatus("LOGIN_READY");
        record.setResultUserId(7L);
        record.setResultMessage("登录成功");
        return record;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
