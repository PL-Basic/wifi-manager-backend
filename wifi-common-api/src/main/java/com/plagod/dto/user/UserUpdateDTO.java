package com.plagod.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserUpdateDTO {
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer role;
    private Integer maxConnections;
    private Integer dailyQuotaMinutes;
    private LocalDateTime expireTime;
}
