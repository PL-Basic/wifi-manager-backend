package com.plagod.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 service 方法上，方法成功返回后由 AuditAspect 写一条 t_audit_log。
 *
 * - action：必填，业务编码（如 "rule.create"、"device.kick"、"alert.handle"）。
 * - operatorName：可选，显式覆盖操作人名称；不填则从请求头 X-User-Name 取，再 fallback 到 "system"。
 *   对于非 controller 触发的内部调用（如 MQTT 事件回调里调的 service），用这个字段显式标 "monitor-auto" 之类。
 * - target：可选，目标资源描述；不填时切面会取第一个 String/Long 参数的 toString。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String operatorName() default "";

    String target() default "";
}
