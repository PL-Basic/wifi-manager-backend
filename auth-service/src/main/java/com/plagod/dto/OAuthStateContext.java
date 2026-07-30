package com.plagod.dto;

import lombok.Data;

@Data
public class OAuthStateContext {

    private Long stateId;
    private String provider;
    private String purpose;
    private Long bindUserId;
    private String returnUri;
    private String codeHash;

    private boolean replayed;
    private String resultStatus;
    private Long resultUserId;
    private String resultMessage;
}