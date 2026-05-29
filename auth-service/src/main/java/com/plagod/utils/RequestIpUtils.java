package com.plagod.utils;

import javax.servlet.http.HttpServletRequest;

public class RequestIpUtils {

    //获取客户端IP
    public static String getClientIP(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-forwarded-for");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
