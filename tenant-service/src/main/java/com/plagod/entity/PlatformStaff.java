package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_platform_staff")
public class PlatformStaff {

    @TableId(type = IdType.AUTO)
    private Long staffId;
    private Long userId;
    private String authority;
    private String status;
    private Long grantedBy;
    private String reason;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
