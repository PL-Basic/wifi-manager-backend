package com.plagod.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明安全审计元数据。
 *
 * 参数和结果永不默认序列化；detail 只接受 {@link AuditDetail} 标记的安全标量。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String targetType() default "UNSPECIFIED";

    Scope scope();

    TenantIdSource tenantIdSource();

    /**
     * 兼容旧调用方。可信 actor 名称由切面生成，此值不再参与写入。
     */
    @Deprecated
    String operatorName() default "";

    /**
     * 固定目标 ID；动态目标应使用 {@link AuditTargetId}。
     */
    String target() default "";

    boolean recordDenied() default false;

    boolean recordFailed() default false;

    /**
     * 兼容旧源码，切面不会读取任意参数。
     */
    @Deprecated
    boolean includeArgs() default false;

    /**
     * 兼容旧源码，切面不会读取任意结果。
     */
    @Deprecated
    boolean includeResult() default false;

    enum Scope {
        PLATFORM,
        TENANT,
        CONTEXT
    }

    enum TenantIdSource {
        REQUEST,
        ARGUMENT
    }
}
