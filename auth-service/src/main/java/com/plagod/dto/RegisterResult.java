package com.plagod.dto;

import com.plagod.enums.ConflictFieldEnum;
import com.plagod.enums.RegisterStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterResult {
    private RegisterStatusEnum status;
    private String message;
    private Set<ConflictFieldEnum> conflictFields;

    public static RegisterResult success() {
        return new RegisterResult(RegisterStatusEnum.SUCCESS,"注册成功",null);
    }
    public static RegisterResult conflict(Set<ConflictFieldEnum> fields){
        return new RegisterResult(RegisterStatusEnum.CONFLICT,"部分信息已被占用",fields);
    }

    public static RegisterResult conflict(Set<ConflictFieldEnum> fields, String message) {
        return new RegisterResult(RegisterStatusEnum.CONFLICT, message, fields);
    }


}
