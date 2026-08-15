package com.plagod.filter;

import com.plagod.request.RequestId;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayRequestIdWebFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {
        String inbound = exchange.getRequest().getHeaders()
                .getFirst(RequestId.HEADER_NAME);
        String requestId = RequestId.isValid(inbound)
                ? inbound : RequestId.generate();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(RequestId.HEADER_NAME);
                    headers.set(RequestId.HEADER_NAME, requestId);
                })
                .build();
        ServerWebExchange normalized = exchange.mutate()
                .request(request)
                .build();

        normalized.getAttributes().put(
                RequestId.REQUEST_ATTRIBUTE,
                requestId);
        normalized.getResponse().getHeaders().set(
                RequestId.HEADER_NAME,
                requestId);
        normalized.getResponse().beforeCommit(() -> {
            normalized.getResponse().getHeaders().set(
                    RequestId.HEADER_NAME,
                    requestId);
            return Mono.empty();
        });

        return chain.filter(normalized)
                .subscriberContext(context -> context.put(
                        RequestId.REQUEST_ATTRIBUTE,
                        requestId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
