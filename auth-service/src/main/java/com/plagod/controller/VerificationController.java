package com.plagod.controller;


import com.plagod.dto.ApiResponse;
import com.plagod.dto.SendVerifyCodeDTO;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.RequestIpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/auth")
public class VerificationController {

    @Autowired
    private VerificationCodeService verificationCodeService;

    @PostMapping("/codes")
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendVerifyCodeDTO sendVerifyCodeDTO,
                                      HttpServletRequest request) {
        String ip = RequestIpUtils.getClientIP(request);
        verificationCodeService.sendCode(
                sendVerifyCodeDTO.getTarget(),
                sendVerifyCodeDTO.getScene(),
                ip
        );

        return ApiResponse.success("验证码已经发送",null);
    }







}
