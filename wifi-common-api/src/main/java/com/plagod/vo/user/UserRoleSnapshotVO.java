package com.plagod.vo.user;

import lombok.Data;

@Data
public class UserRoleSnapshotVO {
    private String userId;
    private String username;
    private String nickname;
    private Integer role;
    private Integer status;
}
