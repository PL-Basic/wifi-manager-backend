package com.plagod.configuration;

import com.plagod.ws.AlertWebSocketHandler;
import com.plagod.ws.AlertWebSocketHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    @Autowired
    private AlertWebSocketHandshakeInterceptor alertWebSocketHandshakeInterceptor;

    @Value("${wifi.websocket.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Value("${wifi.websocket.allowed-origin-alt:http://127.0.0.1:5173}")
    private String allowedOriginAlt;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {

        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .addInterceptors(alertWebSocketHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigin, allowedOriginAlt);
    }
}