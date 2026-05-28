package com.plagod.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_verify_code")
public class VerifyCode {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private String target;
    private String targetType;
    private String scene;
    private String code;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime verifyTime;
    private String sendIp;
    private String verifyIp;
    private LocalDateTime createTime;
}
