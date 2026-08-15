package com.plagod.configuration;

import com.plagod.exception.ApiStatusException;
import com.plagod.web.ApiErrorResponseFactory;
import com.plagod.web.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerContractTest {

    private static final String CANARY_SECRET = "tenant-canary-secret";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorResponseFactory()))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void classifiedErrorUsesSharedEnvelopeAndGeneratedRequestId() throws Exception {
        mockMvc.perform(get("/test/not-found")
                        .header("X-Request-Id", "forged"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        "X-Request-Id",
                        matchesPattern("^[A-Za-z0-9_-]{16,64}$")))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.errorKey").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId")
                        .value(matchesPattern("^[A-Za-z0-9_-]{16,64}$")));
    }

    @Test
    void illegalArgumentDoesNotEchoSensitiveValue() throws Exception {
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorKey").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString(CANARY_SECRET))));
    }

    @RestController
    private static class FailureController {

        @GetMapping("/test/not-found")
        public void notFound() {
            throw ApiStatusException.notFound("租户不存在");
        }

        @GetMapping("/test/invalid")
        public void invalid() {
            throw new IllegalArgumentException(CANARY_SECRET);
        }
    }
}
