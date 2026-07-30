package com.plagod.entity.monitor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_geofence_event")
public class GeofenceEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "event_id", type = IdType.AUTO)
    private Long eventId;

    private Long fenceId;
    private Long locationId;
    private Long userId;
    private Long sessionId;
    private Long nodeId;
    private String deviceCode;
    private String mac;
    private String eventType;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}