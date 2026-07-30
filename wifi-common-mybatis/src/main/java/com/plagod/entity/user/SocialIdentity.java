package com.plagod.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_social_identity")
public class SocialIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "identity_id", type = IdType.AUTO)
    private Long identityId;

    private Long userId;
    private String provider;
    private String providerSubject;
    private String providerUnionId;
    private String providerUsername;
    private String displayName;
    private String avatarUrl;
    private String email;
    private Integer emailVerified;
    private LocalDateTime bindTime;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}