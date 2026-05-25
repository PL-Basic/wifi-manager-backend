package com.plagod.dto;

import lombok.Data;

@Data
public class UserStatsVO {
    private Long totalUsers;
    private Long enabledUsers;
    private Long disabledUsers;
    private Long adminUsers;
}
