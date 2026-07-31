package com.plagod.sender.phone;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.plagod.configuration.PhoneVerificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class AliyunNumberAuthVerificationProvider
        implements PhoneVerificationProvider {

    private final PhoneVerificationProperties properties;

    public AliyunNumberAuthVerificationProvider(PhoneVerificationProperties properties) {

        this.properties = properties;
    }

    @Override
    public String providerName() {
        return "aliyun-number-auth";
    }

    @Override
    public PhoneVerificationSendResult send(String phone, String scene, String outId) {

        PhoneVerificationProperties.Aliyun config;

        try {
            config = requireConfiguration();
        } catch (IllegalStateException exception) {
            return sendFailure(outId, "CONFIGURATION_ERROR", exception.getMessage());
        }

        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    // CountryCode 已单独传递，这里只传国内 11 位手机号。
                    .setPhoneNumber(cleanRequiredPhone(phone))
                    .setCountryCode(config.getCountryCode())
                    .setOutId(outId)
                    .setSchemeName(clean(config.getSchemeName()))
                    .setSignName(config.getSignName())
                    .setTemplateCode(config.getTemplateCode())
                    .setTemplateParam(config.getTemplateParam())
                    .setCodeLength(config.getCodeLength())
                    .setCodeType(config.getCodeType())
                    .setDuplicatePolicy(config.getDuplicatePolicy())
                    .setInterval(config.getIntervalSeconds())
                    .setValidTime(config.getValidSeconds())
                    .setAutoRetry(0L)
                    .setReturnVerifyCode(false);

            SendSmsVerifyCodeResponse response = createClient(config).sendSmsVerifyCodeWithOptions(request, runtimeOptions(config));

            SendSmsVerifyCodeResponseBody body = response == null ? null : response.getBody();

            SendSmsVerifyCodeResponseBody.SendSmsVerifyCodeResponseBodyModel model = body == null ? null : body.getModel();

            boolean successful = body != null
                    && Boolean.TRUE.equals(body.getSuccess())
                    && "OK".equalsIgnoreCase(body.getCode())
                    && model != null
                    && outId.equals(model.getOutId());

            return PhoneVerificationSendResult.builder()
                    .successful(successful)
                    .provider(providerName())
                    .outId(outId)
                    .requestId(resolveRequestId(body, model))
                    .bizId(model == null ? null : model.getBizId())
                    .providerCode(body == null ? null : body.getCode())
                    .message(body == null ? "阿里云没有返回发送结果" : body.getMessage())
                    .build();
        } catch (Exception exception) {
            log.error("阿里云短信发送调用异常，exceptionType={}, message={}", exception.getClass().getName(), exception.getMessage(), exception);

            return sendFailure(outId, "PROVIDER_EXCEPTION", "阿里云短信认证服务暂时不可用");
        }
    }
    @Override
    public PhoneVerificationCheckResult verify(String phone, String outId, String submittedCode, String storedCodeHash) {

        PhoneVerificationProperties.Aliyun config;

        try {
            config = requireConfiguration();
        } catch (IllegalStateException exception) {
            return checkFailure(outId, "CONFIGURATION_ERROR", exception.getMessage());
        }

        try {
            CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(cleanRequiredPhone(phone))
                    .setCountryCode(config.getCountryCode())
                    .setSchemeName(clean(config.getSchemeName()))
                    .setOutId(outId)
                    .setVerifyCode(submittedCode)
                    .setCaseAuthPolicy(1L);

            CheckSmsVerifyCodeResponse response = createClient(config).checkSmsVerifyCodeWithOptions(request, runtimeOptions(config));

            CheckSmsVerifyCodeResponseBody body = response == null ? null : response.getBody();

            CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel model = body == null ? null : body.getModel();

            boolean requestSuccessful = body != null
                    && Boolean.TRUE.equals(body.getSuccess())
                    && "OK".equalsIgnoreCase(body.getCode())
                    && model != null
                    && outId.equals(model.getOutId());

            String verifyResult = model == null ? null : model.getVerifyResult();

            return PhoneVerificationCheckResult.builder()
                    .requestSuccessful(requestSuccessful)
                    .verified(requestSuccessful && "PASS".equalsIgnoreCase(verifyResult))
                    .provider(providerName())
                    .outId(outId)
                    .providerCode(body == null ? null : body.getCode())
                    .providerResult(verifyResult)
                    .message(body == null ? "阿里云没有返回核验结果" : body.getMessage())
                    .build();
        } catch (TeaException exception) {
            /*
             * 阿里云将验证码不匹配表示为 HTTP 400 + “验证失败”。
             * 这是正常业务拒绝，不是供应商不可用。
             */
            if (isVerificationRejected(exception)) {
                return PhoneVerificationCheckResult.builder()
                        .requestSuccessful(true)
                        .verified(false)
                        .provider(providerName())
                        .outId(outId)
                        .providerCode(resolveTeaCode(exception))
                        .providerResult("UNKNOWN")
                        .message("验证码错误")
                        .build();
            }

            log.error("阿里云短信核验调用异常，providerCode={}, statusCode={}, message={}", exception.getCode(), exception.getStatusCode(), exception.getMessage(), exception);

            return checkFailure(outId, resolveTeaCode(exception), "阿里云短信核验服务暂时不可用");
        } catch (Exception exception) {
            log.error("阿里云短信核验调用异常，exceptionType={}, message={}", exception.getClass().getName(), exception.getMessage(), exception);

            return checkFailure(outId, "PROVIDER_EXCEPTION", "阿里云短信核验服务暂时不可用");
        }
    }

    private Client createClient(PhoneVerificationProperties.Aliyun config) throws Exception {

        Config clientConfig = new Config()
                .setAccessKeyId(config.getAccessKeyId())
                .setAccessKeySecret(config.getAccessKeySecret())
                .setEndpoint(config.getEndpoint());

        return new Client(clientConfig);
    }

    private RuntimeOptions runtimeOptions(PhoneVerificationProperties.Aliyun config) {

        return new RuntimeOptions()
                .setAutoretry(false)
                .setMaxAttempts(1)
                .setConnectTimeout(config.getConnectTimeoutMillis())
                .setReadTimeout(config.getReadTimeoutMillis());
    }

    private PhoneVerificationProperties.Aliyun requireConfiguration() {
        PhoneVerificationProperties.Aliyun config = properties.getAliyun();

        if (config == null
                || !StringUtils.hasText(config.getAccessKeyId())
                || !StringUtils.hasText(config.getAccessKeySecret())
                || !StringUtils.hasText(config.getEndpoint())
                || !StringUtils.hasText(config.getSignName())
                || !StringUtils.hasText(config.getTemplateCode())
                || !StringUtils.hasText(config.getTemplateParam())) {

            throw new IllegalStateException("阿里云短信认证配置不完整");
        }

        return config;
    }

    private String resolveRequestId(SendSmsVerifyCodeResponseBody body, SendSmsVerifyCodeResponseBody.SendSmsVerifyCodeResponseBodyModel model) {

        if (body != null && StringUtils.hasText(body.getRequestId())) {
            return body.getRequestId();
        }

        return model == null ? null : model.getRequestId();
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }


    private String cleanRequiredPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }

        // 国家码由 CountryCode 参数承载，不能拼进 PhoneNumber。
        return phone.trim();
    }

    /**
     * 只识别本次真实验收确认过的阿里云验证码错误。
     * 其他 400 错误可能是参数或配置问题，不能计入用户输错次数。
     */
    private boolean isVerificationRejected(TeaException exception) {
        boolean badRequest = Integer.valueOf(400).equals(exception.getStatusCode()) || "400".equals(exception.getCode());

        return badRequest && StringUtils.hasText(exception.getMessage()) && exception.getMessage().contains("验证失败");
    }

    /**
     * 优先保存阿里云业务错误码，没有时才回退到 HTTP 状态码。
     */
    private String resolveTeaCode(TeaException exception) {
        if (StringUtils.hasText(exception.getCode())) {
            return exception.getCode();
        }

        if (exception.getStatusCode() != null) {
            return String.valueOf(exception.getStatusCode());
        }

        return "PROVIDER_EXCEPTION";
    }


    private PhoneVerificationSendResult sendFailure(String outId, String providerCode, String message) {

        return PhoneVerificationSendResult.builder()
                .successful(false)
                .provider(providerName())
                .outId(outId)
                .providerCode(providerCode)
                .message(message)
                .build();
    }

    private PhoneVerificationCheckResult checkFailure(String outId, String providerCode, String message) {

        return PhoneVerificationCheckResult.builder()
                .requestSuccessful(false)
                .verified(false)
                .provider(providerName())
                .outId(outId)
                .providerCode(providerCode)
                .message(message)
                .build();
    }
}