package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.*;

@Data
public class EntitlementLeaseRequest {
    @NotBlank(message = "requestId 不能为空")
    @Size(max = 64, message = "requestId 不能超过64")
    private String requestId;

    @NotNull(message = "userId 不能为空")
    @Min(value = 1, message = "userId 必须大于0")
    private Long userId;

    @NotNull(message = "sessionId 不能为空")
    @Min(value = 1, message = "sessionId 必须大于0")
    private Long sessionId;

    // 首次授权为0，后续按实际在线时间结算，单次最多10秒
    @NotNull(message = "usageSeconds 不能为空")
    @Min(value = 0, message = "usageSeconds 不能小于 0")
    @Max(value = 10, message = "单次不能超过 10 秒")
    private Long usageSeconds;

    // 后端申请的滚动 TTL，user-service 仍会按权益进行截断
    @NotNull(message = "requestedTtlSeconds 不能为空")
    @Min(value = 1, message = "requestedTtlSeconds 必须大于 0")
    @Max(value = 60, message = "requestedTtlSeconds 不能超过 60")
    private Integer requestedTtlSeconds;
}