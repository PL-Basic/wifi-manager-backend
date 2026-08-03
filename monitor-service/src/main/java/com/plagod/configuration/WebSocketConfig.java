package com.plagod.configuration;

import com.plagod.ws.AlertWebSocketHandler;
import com.plagod.ws.AlertWebSocketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    @Autowired
    private AlertWebSocketHandshakeInterceptor alertWebSocketHandshakeInterceptor;

    @Autowired
    private AlertWebSocketProperties webSocketProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .addInterceptors(alertWebSocketHandshakeInterceptor)
                .setAllowedOrigins(webSocketProperties.getAllowedOrigins().toArray(new String[0]));
    }
}
