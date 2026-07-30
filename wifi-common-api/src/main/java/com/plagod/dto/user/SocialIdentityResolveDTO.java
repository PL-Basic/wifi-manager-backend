package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class SocialIdentityResolveDTO {

    @NotBlank(message = "provider不能为空")
    @Size(max = 16, message = "provider不能超过16个字符")
    private String provider;

    @NotBlank(message = "purpose不能为空")
    @Size(max = 16, message = "purpose不能超过16个字符")
    private String purpose;

    @NotBlank(message = "providerSubject不能为空")
    @Size(max = 128, message = "providerSubject不能超过128个字符")
    private String providerSubject;

    @Size(max = 128, message = "providerUnionId不能超过128个字符")
    private String providerUnionId;

    @Size(max = 128, message = "providerUsername不能超过128个字符")
    private String providerUsername;

    @Size(max = 128, message = "displayName不能超过128个字符")
    private String displayName;

    @Size(max = 512, message = "avatarUrl不能超过512个字符")
    private String avatarUrl;

    @Email(message = "verifiedEmail格式不正确")
    @Size(max = 128, message = "verifiedEmail不能超过128个字符")
    private String verifiedEmail;

    @NotNull(message = "emailVerified不能为空")
    private Boolean emailVerified;

    private Long bindUserId;
}