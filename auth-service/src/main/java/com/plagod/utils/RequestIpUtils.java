package com.plagod.utils;

import javax.servlet.http.HttpServletRequest;

public class RequestIpUtils {

    private RequestIpUtils() {

    }

    public static String getClientIP(HttpServletRequest request) {
        /*
         * X-Client-IP 由 Gateway 删除客户端原值后重新生成，
         * Auth 不再直接信任客户端提供的代理 Header。
         */
        String trustedClientIp = request.getHeader("X-Client-IP");
        if (trustedClientIp != null && !trustedClientIp.trim().isEmpty()) {
            return trustedClientIp.trim();
        }

        return request.getRemoteAddr();
    }
}
