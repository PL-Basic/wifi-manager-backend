package com.plagod.configuration;

import com.plagod.dto.ApiResponse;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldPreserveNotFoundAndConflictStatuses() {
        assertStatus(HttpStatus.NOT_FOUND);
        assertStatus(HttpStatus.CONFLICT);
    }

    @Test
    void shouldHideInternalAuthenticationFailureAsServiceUnavailable() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(HttpStatus.UNAUTHORIZED.value());

        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getBody().getCode());
    }

    @Test
    void shouldMapUnknownDownstreamFailureToServiceUnavailable() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(-1);

        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getBody().getCode());
    }

    private void assertStatus(HttpStatus status) {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(status.value());

        ResponseEntity<ApiResponse<Void>> response = handler.handleFeignException(exception);

        assertEquals(status, response.getStatusCode());
        assertEquals(status.value(), response.getBody().getCode());
    }
}
