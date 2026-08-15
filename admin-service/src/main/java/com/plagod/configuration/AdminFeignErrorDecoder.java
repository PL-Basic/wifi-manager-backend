package com.plagod.configuration;

import com.plagod.client.AdminDownstreamException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 只提取 BFF 映射需要的状态和 Retry-After，不读取下游响应正文。
 */
@Component
public class AdminFeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        return new AdminDownstreamException(
                response.status(),
                retryAfterSeconds(response.headers()));
    }

    private Long retryAfterSeconds(
            Map<String, Collection<String>> headers) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, Collection<String>> entry
                : headers.entrySet()) {
            if (entry.getKey() != null
                    && HttpHeaders.RETRY_AFTER.equalsIgnoreCase(
                    entry.getKey())) {
                return singlePositiveSeconds(entry.getValue());
            }
        }
        return null;
    }

    private Long singlePositiveSeconds(Collection<String> values) {
        if (values == null || values.size() != 1) {
            return null;
        }
        String value = values.iterator().next();
        if (value == null || !value.matches("[0-9]+")) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value);
            return seconds > 0L ? seconds : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
