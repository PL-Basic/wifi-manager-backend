package com.plagod.dto;

import lombok.Data;

@Data
public class AuthUserDTO {
    private Long userId;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private Integer role;
    private Integer status;
}
