package com.plagod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DisconnectMacCommand {
    private String requestId;
    private String mac;
    private Long alertId;
}
