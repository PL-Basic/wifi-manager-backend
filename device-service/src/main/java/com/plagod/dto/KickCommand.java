package com.plagod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KickCommand {
    private String requestId;
    private String deviceCode;
    private String reason;
}
