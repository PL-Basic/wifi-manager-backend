package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_client_location")
public class ClientLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String mac;

    @TableField("user_id")
    private Long userId;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal accuracy;

    @TableField("consent_time")
    private LocalDateTime consentTime;

    @TableField("report_time")
    private LocalDateTime reportTime;

    private String source;

    private String remark;

    @TableField("create_time")
    private LocalDateTime createTime;
}
