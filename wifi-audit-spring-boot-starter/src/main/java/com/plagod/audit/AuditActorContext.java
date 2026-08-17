package com.plagod.audit;

import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedSource;

import java.util.regex.Pattern;

/**
 * 非浏览器调用显式提供的服务或设备审计身份。
 */
public final class AuditActorContext {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    private final TrustedRequestContext trustedContext;
    private final ActorType actorType;
    private final String actorId;
    private final String eventId;

    private AuditActorContext(
            TrustedRequestContext trustedContext,
            ActorType actorType,
            String actorId,
            String eventId) {
        if (trustedContext == null) {
            throw new IllegalArgumentException(
                    "trustedContext 不能为空");
        }
        this.trustedContext = trustedContext;
        this.actorType = actorType;
        this.actorId = requireIdentifier(actorId, "actorId");
        this.eventId = optionalIdentifier(eventId, "eventId");
    }

    public static AuditActorContext service(
            TrustedRequestContext trustedContext,
            String serviceName,
            String eventId) {
        TrustedSource source = trustedContext == null
                ? null
                : trustedContext.getTrustedSource();
        if (source != TrustedSource.INTERNAL_SERVICE
                && source != TrustedSource.SCHEDULED_SERVICE) {
            throw new IllegalArgumentException(
                    "服务审计身份必须来自内部或调度上下文");
        }
        return new AuditActorContext(
                trustedContext,
                ActorType.SERVICE,
                serviceName,
                eventId);
    }

    public static AuditActorContext device(
            TrustedRequestContext trustedContext,
            String deviceCode,
            String eventId) {
        if (trustedContext == null
                || trustedContext.getTrustedSource()
                != TrustedSource.DEVICE_EVENT) {
            throw new IllegalArgumentException(
                    "设备审计身份必须来自设备事件上下文");
        }
        return new AuditActorContext(
                trustedContext,
                ActorType.DEVICE,
                deviceCode,
                eventId);
    }

    private static String requireIdentifier(
            String value,
            String label) {
        if (value == null
                || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " 格式错误");
        }
        return value;
    }

    private static String optionalIdentifier(
            String value,
            String label) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return requireIdentifier(value.trim(), label);
    }

    TrustedRequestContext getTrustedContext() {
        return trustedContext;
    }

    ActorType getActorType() {
        return actorType;
    }

    String getActorId() {
        return actorId;
    }

    String getEventId() {
        return eventId;
    }

    enum ActorType {
        SERVICE,
        DEVICE
    }
}
