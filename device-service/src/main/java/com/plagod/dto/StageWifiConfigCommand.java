package com.plagod.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StageWifiConfigCommand {
    private String requestId;
    private String deviceCode;
    private String ssid;
    private String password;
    private Long configVersion;
}
