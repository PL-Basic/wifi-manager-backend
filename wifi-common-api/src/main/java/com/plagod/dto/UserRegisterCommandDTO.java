package com.plagod.dto;

import lombok.Data;

@Data
public class UserRegisterCommandDTO {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
}
