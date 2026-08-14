package com.plagod.entity.monitor;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_geofence")
public class Geofence implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "fence_id", type = IdType.AUTO)
    private Long fenceId;

    private Long tenantId;

    private String name;
    private BigDecimal centerLatitude;
    private BigDecimal centerLongitude;
    private BigDecimal radiusMeters;
    private Integer enabled;
    private Integer version;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
