package com.plagod.vo.entitlement;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EntitlementUsageLogVO {
    private Long id;
    private String requestId;
    private Integer lineNo;
    private Long purchaseId;
    private String authorizationMode;
    private Long sessionId;
    private Long changeSeconds;
    private Long beforeSeconds;
    private Long afterSeconds;
    private String reason;
    private LocalDateTime createTime;
}