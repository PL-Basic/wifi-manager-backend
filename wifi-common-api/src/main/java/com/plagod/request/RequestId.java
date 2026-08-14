package com.plagod.request;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 与 Servlet/WebFlux 无关的请求关联 ID 契约。
 */
public final class RequestId {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String REQUEST_ATTRIBUTE =
            RequestId.class.getName() + ".value";

    private static final Pattern FORMAT =
            Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

    private RequestId() {
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public static String generate() {
        return String.format(
                Locale.ROOT,
                "%016x%016x",
                System.currentTimeMillis(),
                ThreadLocalRandom.current().nextLong());
    }
}
