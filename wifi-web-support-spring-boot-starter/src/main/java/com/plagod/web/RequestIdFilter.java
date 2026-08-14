package com.plagod.web;

import com.plagod.request.RequestId;
import com.plagod.security.TrustedHeaderNames;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String inbound = request.getHeader(RequestId.HEADER_NAME);
        String requestId = isTrusted(request) && RequestId.isValid(inbound)
                ? inbound
                : RequestId.generate();

        request.setAttribute(RequestId.REQUEST_ATTRIBUTE, requestId);
        response.setHeader(RequestId.HEADER_NAME, requestId);
        MDC.put(RequestIdContext.MDC_KEY, requestId);

        try {
            filterChain.doFilter(
                    new SanitizedRequestIdRequest(request, requestId),
                    response);
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY);
        }
    }

    private boolean isTrusted(HttpServletRequest request) {
        Object source = request.getAttribute(
                TrustedHeaderNames.TRUSTED_SOURCE_ATTRIBUTE);
        return TrustedHeaderNames.SOURCE_GATEWAY.equals(source)
                || TrustedHeaderNames.SOURCE_INTERNAL.equals(source);
    }

    private static final class SanitizedRequestIdRequest
            extends HttpServletRequestWrapper {

        private final String requestId;

        private SanitizedRequestIdRequest(
                HttpServletRequest request,
                String requestId) {
            super(request);
            this.requestId = requestId;
        }

        @Override
        public String getHeader(String name) {
            if (RequestId.HEADER_NAME.equalsIgnoreCase(name)) {
                return requestId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (RequestId.HEADER_NAME.equalsIgnoreCase(name)) {
                return Collections.enumeration(
                        Collections.singletonList(requestId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            if (original != null) {
                while (original.hasMoreElements()) {
                    String name = original.nextElement();
                    if (!RequestId.HEADER_NAME.equalsIgnoreCase(name)) {
                        names.add(name);
                    }
                }
            }
            names.add(RequestId.HEADER_NAME);
            return Collections.enumeration(names);
        }
    }
}
