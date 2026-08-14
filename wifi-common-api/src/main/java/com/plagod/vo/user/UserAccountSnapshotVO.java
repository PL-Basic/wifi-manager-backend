package com.plagod.vo.user;

import lombok.Data;

@Data
public class UserAccountSnapshotVO {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer role;
    private Integer status;
    private Boolean membershipReady;
}
