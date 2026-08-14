package com.plagod.web;

import com.plagod.request.RequestId;
import org.slf4j.MDC;

public final class RequestIdContext {

    public static final String MDC_KEY = "requestId";

    private RequestIdContext() {
    }

    public static String current() {
        String current = MDC.get(MDC_KEY);
        return RequestId.isValid(current) ? current : null;
    }

    public static String currentOrGenerate() {
        String current = current();
        return current == null ? RequestId.generate() : current;
    }
}
