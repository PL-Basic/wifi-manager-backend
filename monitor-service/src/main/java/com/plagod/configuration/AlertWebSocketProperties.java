package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "wifi.websocket")
public class AlertWebSocketProperties {
    private List<String> allowedOrigins = Arrays.asList(
            "http://portal.test:5173",
            "http://localhost:5173"
    );
}
