package com.plagod.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditAspect(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @Around("@annotation(com.plagod.audit.Audited)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            persist(joinPoint, result);
        } catch (Throwable ex) {
            // 审计是横切关注点，写入失败不影响业务返回
            log.warn("audit write failed: {}", ex.getMessage());
        }
        return result;
    }

    private void persist(ProceedingJoinPoint joinPoint, Object result) {
        Method method = auditedMethod(joinPoint);
        Audited annotation = method.getAnnotation(Audited.class);
        if (annotation == null) {
            return;
        }

        HttpServletRequest request = currentRequest();
        AuditScope scope = AuditScopeResolver.resolve(
                annotation,
                method,
                joinPoint.getArgs(),
                request);
        auditWriter.append(new AuditWriteRecord(
                scope.getTenantId(),
                scope.getScopeType(),
                resolveOperatorId(request),
                resolveOperatorName(annotation, request),
                annotation.action(),
                resolveTarget(annotation, joinPoint.getArgs()),
                serializeDetail(annotation, joinPoint.getArgs(), result),
                request == null ? null : request.getRemoteAddr()));
    }

    private Method auditedMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature =
                (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        if (target != null) {
            method = AopUtils.getMostSpecificMethod(
                    method,
                    target.getClass());
        }
        return BridgeMethodResolver.findBridgedMethod(method);
    }

    private String resolveOperatorName(Audited annotation, HttpServletRequest request) {
        if (annotation.operatorName() != null && !annotation.operatorName().isEmpty()) {
            return annotation.operatorName();
        }
        if (request != null) {
            String header = request.getHeader("X-User-Name");
            if (header != null && !header.isEmpty()) {
                return header;
            }
        }
        return "system";
    }

    private Long resolveOperatorId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader("X-User-Id");
        if (header == null || header.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveTarget(Audited annotation, Object[] args) {
        if (annotation.target() != null && !annotation.target().isEmpty()) {
            return annotation.target();
        }
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String || arg instanceof Number) {
                return String.valueOf(arg);
            }
        }
        return null;
    }

    private String serializeDetail(Audited annotation, Object[] args, Object result) {

        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();

            if (annotation.includeArgs()) {
                body.put("args", args);
            }

            if (annotation.includeResult()) {
                body.put("result", result);
            }

            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
}
