package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.SendVerifyCodeDTO;
import com.plagod.exception.VerificationCodeRateLimitException;
import com.plagod.exception.VerificationDeliveryException;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.RequestIpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/auth")
public class VerificationController {

    private final VerificationCodeService verificationCodeService;

    public VerificationController(VerificationCodeService verificationCodeService) {

        this.verificationCodeService = verificationCodeService;
    }

    @PostMapping("/codes")
    public ResponseEntity<ApiResponse<Void>> sendCode(@Valid @RequestBody SendVerifyCodeDTO requestDTO,
                                                      HttpServletRequest request) {

        try {
            verificationCodeService.sendCode(requestDTO.getTarget(), requestDTO.getScene(), RequestIpUtils.getClientIP(request));

            return ResponseEntity.ok(ApiResponse.success("验证码已经发送", null));
        } catch (VerificationCodeRateLimitException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.fail(429, exception.getMessage(), null));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(400, exception.getMessage(), null));
        } catch (VerificationDeliveryException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail(503, exception.getMessage(), null));
        } catch (RuntimeException exception) {
            log.error("验证码发送接口发生未预期异常", exception);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(500, "验证码发送服务异常，请稍后重试", null));
        }
    }
}