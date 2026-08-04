package com.plagod.vo.tenant;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantMemberVO {
    private String memberId;
    private String tenantId;
    private String userId;
    private String username;
    private String nickname;
    private Integer globalRole;
    private Integer globalStatus;
    private String tenantRole;
    private String status;
    private Boolean defaultTenant;
    private Long contextVersion;
    private LocalDateTime joinTime;
}
