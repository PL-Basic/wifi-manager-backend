package com.plagod.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer role;
    private Integer status;
    private Integer maxConnections;
    private Integer dailyQuotaMinutes;
    private LocalDateTime expireTime;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
