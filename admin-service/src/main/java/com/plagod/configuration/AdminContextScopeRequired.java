package com.plagod.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 BFF Controller 或方法要求的精确可信上下文。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminContextScopeRequired {

    Scope value();

    enum Scope {
        PLATFORM,
        PLATFORM_TENANT
    }
}
