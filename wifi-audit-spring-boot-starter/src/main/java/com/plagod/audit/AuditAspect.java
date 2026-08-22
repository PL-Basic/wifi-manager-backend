package com.plagod.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plagod.exception.ApiErrorKey;
import com.plagod.exception.ApiStatusException;
import com.plagod.request.RequestId;
import com.plagod.security.TrustedRequestContext;
import com.plagod.security.TrustedRequestContextException;
import com.plagod.security.TrustedRequestContextResolver;
import com.plagod.security.TrustedRequestHeaders;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

@Aspect
public class AuditAspect {

    private static final Pattern SAFE_KEY = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9._-]{0,63}$");
    private static final int MAX_DETAIL_TEXT = 256;
    private static final String CONTRACT_VERSION = "audit-v1";

    private final AfterCommitAuditWriter successWriter;
    private final IndependentAuditWriter failureWriter;
    private final AuditWriteFailureReporter failureReporter;
    private final TrustedRequestContextResolver contextResolver;
    private final ObjectMapper objectMapper;
    private final String sourceService;

    public AuditAspect(
            AfterCommitAuditWriter successWriter,
            IndependentAuditWriter failureWriter,
            AuditWriteFailureReporter failureReporter,
            TrustedRequestContextResolver contextResolver,
            ObjectMapper objectMapper,
            String sourceService) {
        this.successWriter = successWriter;
        this.failureWriter = failureWriter;
        this.failureReporter = failureReporter;
        this.contextResolver = contextResolver;
        this.objectMapper = objectMapper;
        this.sourceService = normalizeSourceService(sourceService);
    }

    @Around("@annotation(com.plagod.audit.Audited)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        PreparedAudit prepared = prepareSafely(joinPoint);
        try {
            Object result = joinPoint.proceed();
            if (prepared != null) {
                if (result instanceof Future<?>
                        || result instanceof CompletionStage<?>) {
                    failureReporter.unsupportedAsyncResult();
                    return result;
                }
                AuditWriteRecord record = recordSafely(
                        prepared,
                        "SUCCESS",
                        null);
                if (record != null) {
                    successWriter.write(record);
                }
            }
            return result;
        } catch (Throwable failure) {
            writeFailure(prepared, failure);
            throw failure;
        }
    }

    private PreparedAudit prepareSafely(ProceedingJoinPoint joinPoint) {
        try {
            return prepare(joinPoint);
        } catch (RuntimeException exception) {
            failureReporter.invalidMetadata();
            return null;
        }
    }

    private PreparedAudit prepare(ProceedingJoinPoint joinPoint) {
        Method method = auditedMethod(joinPoint);
        Audited audited = method.getAnnotation(Audited.class);
        if (audited == null) {
            return null;
        }
        requireSafeKey(audited.action(), "action");
        requireSafeKey(audited.targetType(), "targetType");

        Object[] arguments = joinPoint.getArgs();
        HttpServletRequest request = currentRequest();
        ResolvedActor actor = resolveActor(arguments, request);
        AuditScope scope = AuditScopeResolver.resolve(
                audited,
                actor.trustedContext,
                method,
                arguments);
        String targetId = resolveTargetId(
                audited,
                method,
                arguments);
        String target = composeTarget(
                audited.targetType(),
                targetId);
        Map<String, Object> details = allowedDetails(
                method,
                arguments);

        return new PreparedAudit(
                audited,
                scope,
                actor,
                audited.action(),
                audited.targetType(),
                targetId,
                target,
                details,
                request == null ? null : bounded(
                        request.getRemoteAddr(),
                        45));
    }

    private ResolvedActor resolveActor(
            Object[] arguments,
            HttpServletRequest request) {
        AuditActorContext explicit = null;
        TrustedRequestContext trustedContext = null;
        if (arguments != null) {
            for (Object argument : arguments) {
                if (argument instanceof AuditActorContext) {
                    if (explicit != null) {
                        throw invalidMetadata();
                    }
                    explicit = (AuditActorContext) argument;
                } else if (argument instanceof TrustedRequestContext) {
                    if (trustedContext != null) {
                        throw invalidMetadata();
                    }
                    trustedContext =
                            (TrustedRequestContext) argument;
                }
            }
        }

        if (explicit != null) {
            if (trustedContext != null
                    && trustedContext
                    != explicit.getTrustedContext()) {
                throw invalidMetadata();
            }
            TrustedRequestContext context =
                    explicit.getTrustedContext();
            return new ResolvedActor(
                    context,
                    explicit.getActorType().name(),
                    explicit.getActorId(),
                    null,
                    bounded(
                            explicit.getActorType().name()
                                    .toLowerCase()
                                    + "#"
                                    + explicit.getActorId(),
                            64),
                    context.getTrustedSource().name(),
                    context.getContextType() == null
                            ? null
                            : context.getContextType().name(),
                    context.getRequestId(),
                    explicit.getEventId());
        }

        if (trustedContext == null && request != null) {
            trustedContext = contextResolver.resolve(request);
        }
        if (trustedContext != null
                && trustedContext.hasUserActor()) {
            String actorId = String.valueOf(
                    trustedContext.getUserId());
            return new ResolvedActor(
                    trustedContext,
                    "USER",
                    actorId,
                    trustedContext.getUserId(),
                    bounded("user#" + actorId, 64),
                    trustedContext.getTrustedSource().name(),
                    trustedContext.getContextType().name(),
                    trustedContext.getRequestId(),
                    null);
        }
        if (trustedContext != null) {
            throw invalidMetadata();
        }
        if (request != null && TrustedRequestHeaders.SOURCE_GATEWAY.equals(
                request.getAttribute(
                        TrustedRequestHeaders
                                .TRUSTED_SOURCE_ATTRIBUTE))) {
            return new ResolvedActor(
                    null,
                    "ANONYMOUS",
                    null,
                    null,
                    "anonymous",
                    "GATEWAY",
                    null,
                    requestId(request),
                    null);
        }
        throw invalidMetadata();
    }

    private void writeFailure(
            PreparedAudit prepared,
            Throwable failure) {
        if (prepared == null) {
            return;
        }
        FailureClassification classification =
                classify(failure);
        if (classification.denied
                && !prepared.audited.recordDenied()) {
            return;
        }
        if (!classification.denied
                && !prepared.audited.recordFailed()) {
            return;
        }
        AuditWriteRecord record = recordSafely(
                prepared,
                classification.denied ? "DENIED" : "FAILED",
                classification.errorKey);
        if (record != null) {
            failureWriter.write(record);
        }
    }

    private FailureClassification classify(Throwable failure) {
        if (failure instanceof ApiStatusException) {
            ApiStatusException status =
                    (ApiStatusException) failure;
            return new FailureClassification(
                    status.getHttpStatus() == 401
                            || status.getHttpStatus() == 403,
                    status.getErrorKey());
        }
        if (failure instanceof TrustedRequestContextException) {
            TrustedRequestContextException status =
                    (TrustedRequestContextException) failure;
            return new FailureClassification(
                    status.getHttpStatus() == 401
                            || status.getHttpStatus() == 403,
                    status.getErrorKey().value());
        }
        return new FailureClassification(
                false,
                ApiErrorKey.INTERNAL_ERROR.value());
    }

    private AuditWriteRecord recordSafely(
            PreparedAudit prepared,
            String outcome,
            String errorKey) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("contractVersion", CONTRACT_VERSION);
            detail.put("requestId", prepared.actor.requestId);
            putIfPresent(
                    detail,
                    "eventId",
                    prepared.actor.eventId);
            detail.put("actorType", prepared.actor.actorType);
            putIfPresent(
                    detail,
                    "actorId",
                    prepared.actor.actorId);
            detail.put(
                    "trustedSource",
                    prepared.actor.trustedSource);
            putIfPresent(
                    detail,
                    "contextType",
                    prepared.actor.contextType);
            detail.put(
                    "platformManaged",
                    prepared.scope.isPlatformManaged());
            detail.put("targetType", prepared.targetType);
            putIfPresent(detail, "targetId", prepared.targetId);
            detail.put("outcome", outcome);
            putIfPresent(detail, "errorKey", errorKey);
            detail.put("sourceService", sourceService);
            if (!prepared.details.isEmpty()) {
                detail.put("fields", prepared.details);
            }
            return new AuditWriteRecord(
                    prepared.scope.getTenantId(),
                    prepared.scope.getScopeType(),
                    prepared.actor.operatorId,
                    prepared.actor.operatorName,
                    prepared.action,
                    prepared.target,
                    objectMapper.writeValueAsString(detail),
                    prepared.ip);
        } catch (JsonProcessingException | RuntimeException exception) {
            failureReporter.invalidMetadata();
            return null;
        }
    }

    private Map<String, Object> allowedDetails(
            Method method,
            Object[] arguments) {
        Map<String, Object> details = new LinkedHashMap<>();
        Annotation[][] annotations =
                method.getParameterAnnotations();
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation.annotationType()
                        != AuditDetail.class) {
                    continue;
                }
                String key = ((AuditDetail) annotation).value();
                requireSafeKey(key, "detail key");
                if (details.containsKey(key)) {
                    throw invalidMetadata();
                }
                Object value = arguments == null
                        || index >= arguments.length
                        ? null
                        : arguments[index];
                details.put(key, safeScalar(value));
            }
        }
        return details;
    }

    private Object safeScalar(Object value) {
        if (value == null
                || value instanceof Boolean
                || value instanceof Number) {
            return value;
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof String) {
            return bounded((String) value, MAX_DETAIL_TEXT);
        }
        throw invalidMetadata();
    }

    private String resolveTargetId(
            Audited audited,
            Method method,
            Object[] arguments) {
        if (StringUtils.hasText(audited.target())) {
            return bounded(audited.target().trim(), 128);
        }
        Annotation[][] annotations =
                method.getParameterAnnotations();
        String targetId = null;
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation.annotationType()
                        != AuditTargetId.class) {
                    continue;
                }
                if (targetId != null
                        || arguments == null
                        || index >= arguments.length
                        || arguments[index] == null) {
                    throw invalidMetadata();
                }
                Object value = arguments[index];
                if (!(value instanceof String)
                        && !(value instanceof Number)
                        && !(value instanceof Enum<?>)) {
                    throw invalidMetadata();
                }
                targetId = bounded(
                        value instanceof Enum<?>
                                ? ((Enum<?>) value).name()
                                : String.valueOf(value),
                        128);
            }
        }
        return targetId;
    }

    private String composeTarget(
            String targetType,
            String targetId) {
        String target = targetId == null
                ? targetType
                : targetType + ":" + targetId;
        return bounded(target, 255);
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

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes =
                RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes)
                    .getRequest();
        }
        return null;
    }

    private String requestId(HttpServletRequest request) {
        Object current = request.getAttribute(
                RequestId.REQUEST_ATTRIBUTE);
        if (current instanceof String
                && RequestId.isValid((String) current)) {
            return (String) current;
        }
        String header = request.getHeader(RequestId.HEADER_NAME);
        String resolved = RequestId.isValid(header)
                ? header
                : RequestId.generate();
        request.setAttribute(RequestId.REQUEST_ATTRIBUTE, resolved);
        return resolved;
    }

    private static void requireSafeKey(
            String value,
            String label) {
        if (value == null || !SAFE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    label + " 格式错误");
        }
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String normalizeSourceService(String value) {
        String normalized = bounded(value, 64);
        return normalized == null ? "unknown-service" : normalized;
    }

    private static void putIfPresent(
            Map<String, Object> detail,
            String key,
            Object value) {
        if (value != null) {
            detail.put(key, value);
        }
    }

    private static IllegalArgumentException invalidMetadata() {
        return new IllegalArgumentException(
                "audit metadata is missing or invalid");
    }

    private static final class PreparedAudit {

        private final Audited audited;
        private final AuditScope scope;
        private final ResolvedActor actor;
        private final String action;
        private final String targetType;
        private final String targetId;
        private final String target;
        private final Map<String, Object> details;
        private final String ip;

        private PreparedAudit(
                Audited audited,
                AuditScope scope,
                ResolvedActor actor,
                String action,
                String targetType,
                String targetId,
                String target,
                Map<String, Object> details,
                String ip) {
            this.audited = audited;
            this.scope = scope;
            this.actor = actor;
            this.action = action;
            this.targetType = targetType;
            this.targetId = targetId;
            this.target = target;
            this.details = details;
            this.ip = ip;
        }
    }

    private static final class ResolvedActor {

        private final TrustedRequestContext trustedContext;
        private final String actorType;
        private final String actorId;
        private final Long operatorId;
        private final String operatorName;
        private final String trustedSource;
        private final String contextType;
        private final String requestId;
        private final String eventId;

        private ResolvedActor(
                TrustedRequestContext trustedContext,
                String actorType,
                String actorId,
                Long operatorId,
                String operatorName,
                String trustedSource,
                String contextType,
                String requestId,
                String eventId) {
            this.trustedContext = trustedContext;
            this.actorType = actorType;
            this.actorId = actorId;
            this.operatorId = operatorId;
            this.operatorName = operatorName;
            this.trustedSource = trustedSource;
            this.contextType = contextType;
            this.requestId = requestId;
            this.eventId = eventId;
        }
    }

    private static final class FailureClassification {

        private final boolean denied;
        private final String errorKey;

        private FailureClassification(
                boolean denied,
                String errorKey) {
            this.denied = denied;
            this.errorKey = errorKey;
        }
    }
}
