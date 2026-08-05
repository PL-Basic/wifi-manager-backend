package com.plagod.service;

import com.plagod.configuration.AuthSessionProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.time.Duration;

@Service
public class RefreshCookieService {

    private final AuthSessionProperties properties;

    public RefreshCookieService(AuthSessionProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, String refreshToken) {
        write(response, refreshToken, properties.getRefreshAbsoluteTtl());
    }

    public void write(HttpServletResponse response, String refreshToken, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), refreshToken)
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite(properties.getSameSite())
                .path(properties.getCookiePath())
                .maxAge(maxAge == null || maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), "")
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite(properties.getSameSite())
                .path(properties.getCookiePath())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String getCookieName() {
        return properties.getCookieName();
    }
}
