package com.plagod.audit;

import com.plagod.mapper.AuditLogMapper;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;

/**
 * 审计能力自动装配入口。任一条件不满足则整个 starter 不参与启动：
 *   - classpath 上存在 servlet HttpServletRequest（gateway 等 webflux 服务自然不满足，安全跳过）
 *   - 容器内存在 AuditLogMapper（业务服务已启用 MyBatis-Plus 扫描到该 mapper）
 *   - wifi.audit.enabled 未显式置 false（默认开启）
 *
 * 用 @AutoConfigureAfter(name = ...) 强制本配置在 MyBatis-Plus 完成 mapper 扫描之后再评估，
 * 否则 @ConditionalOnBean 在 mapper bean 还没注册时会判为 false，切面静默不生效。
 * 用字符串名而非 Class 引用，避免 starter 硬依赖 mybatis-plus-boot-starter，保持松耦合。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass(HttpServletRequest.class)
@ConditionalOnBean(AuditLogMapper.class)
@ConditionalOnProperty(prefix = "wifi.audit", name = "enabled", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    public AuditAspect auditAspect(AuditLogMapper auditLogMapper) {
        return new AuditAspect(auditLogMapper);
    }
}


