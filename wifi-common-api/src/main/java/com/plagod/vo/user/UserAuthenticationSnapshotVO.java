package com.plagod.vo.user;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserAuthenticationSnapshotVO extends UserAccountSnapshotVO {

    private String passwordHash;
}
