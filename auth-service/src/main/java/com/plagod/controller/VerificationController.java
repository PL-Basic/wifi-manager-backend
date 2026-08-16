package com.plagod.controller;

import com.plagod.dto.ApiResponse;
import com.plagod.dto.SendVerifyCodeDTO;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.RequestIpUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

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

        verificationCodeService.sendCode(
                requestDTO.getTarget(),
                requestDTO.getScene(),
                RequestIpUtils.getClientIP(request));
        return ResponseEntity.ok(
                ApiResponse.success("验证码已经发送", null));
    }
}
