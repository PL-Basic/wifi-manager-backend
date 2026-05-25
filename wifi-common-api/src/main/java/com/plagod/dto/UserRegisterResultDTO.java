package com.plagod.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserRegisterResultDTO {
    private boolean success;
    private String message;
    private List<String> conflictFields;

    public static UserRegisterResultDTO success(String message) {
        UserRegisterResultDTO result = new UserRegisterResultDTO();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public static UserRegisterResultDTO conflict(String message, List<String> conflictFields) {
        UserRegisterResultDTO result = new UserRegisterResultDTO();
        result.setSuccess(false);
        result.setMessage(message);
        result.setConflictFields(conflictFields);
        return result;
    }
}
