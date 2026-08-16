package com.plagod.vo.user;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserPasswordReplaceResultVO {

    private String status;

    private Boolean replayed;

    public UserPasswordReplaceResultVO(String status, Boolean replayed) {
        this.status = status;
        this.replayed = replayed;
    }

    public static UserPasswordReplaceResultVO replaced(boolean replayed) {
        return new UserPasswordReplaceResultVO("REPLACED", replayed);
    }

    public static UserPasswordReplaceResultVO unchanged() {
        return new UserPasswordReplaceResultVO("UNCHANGED", false);
    }

    public static UserPasswordReplaceResultVO conflict() {
        return new UserPasswordReplaceResultVO("CONFLICT", false);
    }

    public static UserPasswordReplaceResultVO fingerprintConflict() {
        return new UserPasswordReplaceResultVO("FINGERPRINT_CONFLICT", false);
    }
}
