package com.plagod.vo.user;

import lombok.Data;

@Data
public class SocialLoginPrincipalVO {

    private Long userId;
    private String username;
    private Integer role;
    private String nickname;
    private String avatar;
}