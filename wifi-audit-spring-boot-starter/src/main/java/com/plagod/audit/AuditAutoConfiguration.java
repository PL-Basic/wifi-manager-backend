package com.plagod.audit;

import com.plagod.security.TrustedHeaderNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.servlet.http.HttpServletRequest;

/**
 * 审计切面自动配置。
 *
 * 只有 Servlet Web 服务默认启用审计。
 * Starter 内部只提供追加写入能力，不暴露审计表查询或修改接口。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({
        HttpServletRequest.class,
        JdbcTemplate.class,
        TrustedHeaderNames.class
})
@ConditionalOnProperty(prefix = "wifi.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    public AuditWriter auditWriter(JdbcTemplate jdbcTemplate) {
        return new JdbcAuditWriter(jdbcTemplate);
    }

    @Bean
    public AuditAspect auditAspect(AuditWriter auditWriter) {
        return new AuditAspect(auditWriter);
    }
}
