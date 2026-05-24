package com.plagod.audit;

import com.plagod.mapper.AuditLogMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 由 {@link EnableAuditing} 通过 @Import 自动激活，只在需要审计的服务里启用。
 * 这样 gateway-service 这种 webflux 模块不会因为引入 wifi-common 而被强行实例化 Aspect。
 */
@Configuration(proxyBeanMethods = false)
public class AuditingConfiguration {

    @Bean
    public AuditAspect auditAspect(AuditLogMapper auditLogMapper) {
        return new AuditAspect(auditLogMapper);
    }
}
