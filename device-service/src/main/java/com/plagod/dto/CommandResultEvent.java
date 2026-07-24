package com.plagod.dto;

import lombok.Data;

@Data
public class CommandResultEvent {
    private String deviceCode;
    private String requestId;
    private String type;
    private Boolean success;
    private String message;
}
