package com.plagod.vo.device;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceCommandResult {
    private String topic;
    private String payload;
}
