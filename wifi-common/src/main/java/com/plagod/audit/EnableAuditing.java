package com.plagod.audit;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 加在 @SpringBootApplication 类上，开启 @Audited 切面。
 * 服务需要 MyBatis-Plus + datasource，才能让 AuditLogMapper 注入成功。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AuditingConfiguration.class)
public @interface EnableAuditing {
}
