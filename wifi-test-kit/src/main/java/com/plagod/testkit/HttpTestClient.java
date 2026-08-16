package com.plagod.testkit;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class HttpTestClient implements AutoCloseable {

    private static final int DEFAULT_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Set<String> SUPPORTED_METHODS =
            new java.util.HashSet<>(Arrays.asList(
                    "GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE"));
    private static final Set<String> READ_ONLY_METHODS =
            new java.util.HashSet<>(Arrays.asList("GET", "HEAD", "OPTIONS"));

    private final URI baseUri;
    private final TestAccessMode accessMode;
    private final CloseableHttpClient client;

    private HttpTestClient(
            URI baseUri,
            TestAccessMode accessMode,
            CloseableHttpClient client) {
        this.baseUri = baseUri;
        this.accessMode = accessMode;
        this.client = client;
    }

    public static HttpTestClient create(
            Map<String, String> environment,
            TestAccessMode accessMode) {

        Objects.requireNonNull(accessMode, "accessMode");
        TestEnvironmentGuard.requireEnabled(environment, "WIFI_TEST_HTTP_ENABLED");
        TestEnvironmentGuard.requireVariables(environment, "WIFI_TEST_HTTP_BASE_URL");
        if (accessMode.allowsWrites()) {
            TestEnvironmentGuard.requireDedicatedWriteAccess(environment);
        }

        URI baseUri = TestEnvironmentGuard.requireHttpBaseUri(
                environment.get("WIFI_TEST_HTTP_BASE_URL"));
        int timeout = boundedTimeout(environment.get("WIFI_TEST_HTTP_TIMEOUT_MILLIS"));
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .setDefaultRequestConfig(requestConfig)
                .build();
        return new HttpTestClient(baseUri, accessMode, client);
    }

    public HttpTestResponse get(String path, Map<String, String> headers)
            throws IOException {
        return request("GET", path, null, headers);
    }

    public HttpTestResponse request(
            String method,
            String path,
            String body,
            Map<String, String> headers) throws IOException {

        String normalizedMethod = normalizeMethod(method);
        if (!READ_ONLY_METHODS.contains(normalizedMethod)
                && !accessMode.allowsWrites()) {
            throw new IllegalStateException(
                    normalizedMethod + " requires WRITE test access");
        }

        URI requestUri = resolvePath(path);
        HttpRequestBase request = buildRequest(normalizedMethod, requestUri, body);
        applyHeaders(request, headers);

        try (CloseableHttpResponse response = client.execute(request)) {
            return new HttpTestResponse(
                    response.getStatusLine().getStatusCode(),
                    readBounded(response.getEntity()),
                    copyHeaders(response.getAllHeaders()));
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private URI resolvePath(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("HTTP test path must be root-relative");
        }

        URI resolved = baseUri.resolve(path);
        if (!sameOrigin(baseUri, resolved)) {
            throw new IllegalArgumentException("HTTP test path cannot change endpoint origin");
        }
        return resolved;
    }

    private static HttpRequestBase buildRequest(
            String method,
            URI uri,
            String body) {

        GenericHttpRequest request = new GenericHttpRequest(method, uri);
        if (body != null) {
            request.setEntity(new StringEntity(
                    body,
                    ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)));
        }
        return request;
    }

    private static void applyHeaders(
            HttpRequestBase request,
            Map<String, String> headers) {

        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = requireHeaderText(entry.getKey(), "header name");
            String value = requireHeaderText(entry.getValue(), "header value");
            if (containsLineBreak(name) || containsLineBreak(value)) {
                throw new IllegalArgumentException("HTTP test headers cannot contain line breaks");
            }
            request.setHeader(name, value);
        }
    }

    private static byte[] readBounded(HttpEntity entity) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        if (entity.getContentLength() > MAX_RESPONSE_BYTES) {
            throw new IOException("HTTP test response exceeds 1 MiB");
        }

        try (InputStream input = entity.getContent();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("HTTP test response exceeds 1 MiB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static Map<String, List<String>> copyHeaders(Header[] headers) {
        Map<String, List<String>> copied = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Header header : headers) {
            copied.computeIfAbsent(header.getName(), ignored -> new ArrayList<>())
                    .add(header.getValue());
        }
        Map<String, List<String>> immutable = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, List<String>> entry : copied.entrySet()) {
            immutable.put(
                    entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static int boundedTimeout(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        int timeout;
        try {
            timeout = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "WIFI_TEST_HTTP_TIMEOUT_MILLIS must be an integer");
        }
        if (timeout < 1 || timeout > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException(
                    "WIFI_TEST_HTTP_TIMEOUT_MILLIS must be between 1 and 30000");
        }
        return timeout;
    }

    private static String normalizeMethod(String method) {
        String normalized = method == null
                ? ""
                : method.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported HTTP test method");
        }
        return normalized;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String requireHeaderText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private static final class GenericHttpRequest
            extends HttpEntityEnclosingRequestBase {

        private final String method;

        private GenericHttpRequest(String method, URI uri) {
            this.method = method;
            setURI(uri);
        }

        @Override
        public String getMethod() {
            return method;
        }
    }

    public static final class HttpTestResponse {

        private final int statusCode;
        private final byte[] body;
        private final Map<String, List<String>> headers;

        private HttpTestResponse(
                int statusCode,
                byte[] body,
                Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = Arrays.copyOf(body, body.length);
            this.headers = headers;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public byte[] getBody() {
            return Arrays.copyOf(body, body.length);
        }

        public String getBodyAsUtf8() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public String getFirstHeader(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }
}
