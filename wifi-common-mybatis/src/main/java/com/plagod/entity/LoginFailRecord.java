package com.plagod.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_login_fail_record")
public class LoginFailRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String account;

    private String loginType;

    private String requestIp;

    private Integer failCount;

    private LocalDateTime lockUntil;

    private LocalDateTime lastFailTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
