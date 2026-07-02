package com.plagod.dto.auth;


import lombok.Data;

@Data
public class AuthResultDTO {
    private String token;
    private String username;
    private Integer role;
    private String nickname;
    private String avatar;
}
