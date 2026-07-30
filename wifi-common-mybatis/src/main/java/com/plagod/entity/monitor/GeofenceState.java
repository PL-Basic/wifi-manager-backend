package com.plagod.entity.monitor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_geofence_state")
public class GeofenceState implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "state_id", type = IdType.AUTO)
    private Long stateId;

    private Long fenceId;
    private Long sessionId;
    private Long userId;
    private Long nodeId;
    private String deviceCode;
    private String mac;
    private Integer insideState;
    private Long lastLocationId;
    private LocalDateTime lastReportTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}