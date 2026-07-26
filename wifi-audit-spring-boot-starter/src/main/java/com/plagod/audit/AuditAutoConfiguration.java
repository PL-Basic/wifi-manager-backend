package com.plagod.audit;

import com.plagod.mapper.AuditLogMapper;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;

/**
 * 审计切面自动配置。
 *
 * 只有 Servlet Web 服务默认启用审计。
 * 审计 Mapper 如果没有注册，应用应当启动失败，
 * 不能静默关闭整个审计功能。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpServletRequest.class, AuditLogMapper.class})
@ConditionalOnProperty(prefix = "wifi.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    public AuditAspect auditAspect(AuditLogMapper auditLogMapper) {
        return new AuditAspect(auditLogMapper);
    }
}