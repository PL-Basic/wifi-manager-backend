package com.plagod.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    @NotBlank
    @Size(max = 512)
    private String brokerUrl;

    @NotBlank
    @Size(max = 128)
    private String clientId;

    private String username;
    private String password;

    @NotNull
    @Min(0)
    @Max(2)
    private Integer qos = 1;

    private boolean retained = false;

    @NotBlank
    @Size(max = 256)
    private String statusTopic;

    @NotBlank
    @Size(max = 256)
    private String trafficTopic;

    @NotBlank
    @Size(max = 256)
    private String commandResultTopic;

    @NotBlank
    @Size(max = 256)
    private String clientSignalTopic;

    @NotBlank
    @Size(max = 256)
    private String clientDisconnectTopic;

    @Override
    public String toString() {
        return "MqttProperties{"
                + "brokerUrlConfigured=" + hasText(brokerUrl)
                + ", clientIdConfigured=" + hasText(clientId)
                + ", credentialsConfigured="
                + (hasText(username) || hasText(password))
                + ", qos=" + qos
                + ", retained=" + retained
                + ", topicsConfigured=" + topicsConfigured()
                + '}';
    }

    private boolean topicsConfigured() {
        return hasText(statusTopic)
                && hasText(trafficTopic)
                && hasText(commandResultTopic)
                && hasText(clientSignalTopic)
                && hasText(clientDisconnectTopic);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
