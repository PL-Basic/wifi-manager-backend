package com.plagod.vo.user;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class UserAccountCreateResultVO {

    private String status;
    private String message;
    private Boolean replayed;
    private Set<String> conflictFields = new LinkedHashSet<>();
    private UserAccountSnapshotVO account;

    public static UserAccountCreateResultVO success(
            UserAccountSnapshotVO account,
            boolean replayed) {
        UserAccountCreateResultVO result = new UserAccountCreateResultVO();
        result.setStatus("SUCCESS");
        result.setMessage("注册成功");
        result.setAccount(account);
        result.setReplayed(replayed);
        return result;
    }

    public static UserAccountCreateResultVO conflict(Set<String> conflictFields) {
        UserAccountCreateResultVO result = new UserAccountCreateResultVO();
        result.setStatus("CONFLICT");
        result.setMessage("部分信息已被占用");
        result.setConflictFields(conflictFields);
        result.setReplayed(false);
        return result;
    }

    public static UserAccountCreateResultVO fingerprintConflict() {
        UserAccountCreateResultVO result = new UserAccountCreateResultVO();
        result.setStatus("FINGERPRINT_CONFLICT");
        result.setMessage("幂等键已用于其他注册参数");
        result.setReplayed(false);
        return result;
    }
}
