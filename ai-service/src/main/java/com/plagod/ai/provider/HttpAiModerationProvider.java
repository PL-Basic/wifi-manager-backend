package com.plagod.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.ai.model.AiModerationRequest;
import com.plagod.ai.model.AiModerationResult;
import com.plagod.ai.model.AiProviderAvailability;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpAiModerationProvider implements AiModerationProvider {

    private final HttpAiProviderSettings settings;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiModerationResultParser resultParser;

    public HttpAiModerationProvider(
            HttpAiProviderSettings settings,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AiModerationResultParser resultParser) {
        if (settings == null
                || restTemplate == null
                || objectMapper == null
                || resultParser == null) {
            throw new IllegalArgumentException("HTTP Provider 依赖不能为空");
        }
        this.settings = settings;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.resultParser = resultParser;
    }

    @Override
    public String providerCode() {
        return settings.getProviderCode() == null
                ? "unconfigured"
                : settings.getProviderCode();
    }

    @Override
    public AiProviderAvailability availability() {
        return settings.availability();
    }

    @Override
    public AiModerationResult review(AiModerationRequest request) {
        if (availability() != AiProviderAvailability.AVAILABLE) {
            throw AiProviderException.unavailable();
        }

        try {
            byte[] requestBytes =
                    objectMapper.writeValueAsBytes(requestBody(request));
            String rawResponse = restTemplate.execute(
                    settings.getEndpoint(),
                    HttpMethod.POST,
                    clientRequest -> {
                        HttpHeaders headers = clientRequest.getHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setBearerAuth(settings.getApiKey());
                        headers.setContentLength(requestBytes.length);
                        clientRequest.getBody().write(requestBytes);
                    },
                    clientResponse -> readBoundedResponse(clientResponse));
            return resultParser.parse(rawResponse, request.getScene());
        } catch (AiProviderException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw AiProviderException.callFailed(exception);
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw AiProviderException.timeout(exception);
            }
            throw AiProviderException.callFailed(exception);
        } catch (RestClientException exception) {
            throw AiProviderException.callFailed(exception);
        }
    }

    private String readBoundedResponse(
            org.springframework.http.client.ClientHttpResponse response)
            throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw AiProviderException.callFailed(null);
        }
        InputStream input = response.getBody();
        if (input == null) {
            throw AiProviderException.responseInvalid();
        }
        int maximum = settings.getMaxResponseBytes();
        ByteArrayOutputStream output =
                new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) {
                throw AiProviderException.responseTooLarge();
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private Map<String, Object> requestBody(AiModerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", AiModerationResultParser.SCHEMA_VERSION);
        body.put("scene", request.getScene().name());
        body.put("reviewRequestId", request.getReviewRequestId());
        body.put("policyVersion", request.getPolicyVersion());
        body.put("contentHash", request.getContentHash());
        body.put("language", request.getLanguage());
        body.put("title", request.getTitle());
        body.put("body", request.getBody());
        body.put("model", settings.getModel());
        return body;
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
