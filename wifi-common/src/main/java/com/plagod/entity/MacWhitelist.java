package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("wifi_mac_whitelist")
public class MacWhitelist implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "whitelist_id", type = IdType.AUTO)
    private Long whitelistId;

    private Long deviceId;

    private Long userId;

    private String clientMac;

    private String clientName;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime lastAccessTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField("del_flag")
    @TableLogic
    private Integer delFlag;
}
