package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_alert_event")
public class AlertEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer level;

    private String ruleCode;

    private String title;

    private String mac;

    private Long userId;

    private String detail;

    private Integer status;

    @TableField("handle_user_id")
    private Long handleUserId;

    @TableField("handle_time")
    private LocalDateTime handleTime;

    private LocalDateTime createTime;
}
