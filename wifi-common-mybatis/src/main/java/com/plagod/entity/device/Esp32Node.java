package com.plagod.entity.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_esp32_node")
public class Esp32Node implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "node_id", type = IdType.AUTO)
    private Long nodeId;

    private String deviceCode;

    private String name;

    private String location;

    private String ip;

    private String firmwareVersion;

    private String wifiStatus;

    private Integer status;

    @TableField("max_clients")
    private Integer maxClients;

    @TableField("current_clients")
    private Integer currentClients;

    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    // 节点在一米距离处的标定 RSSI。
    private Integer rssiAtOneMeter;

    // 当前部署环境的路径损耗指数。
    private BigDecimal pathLossExponent;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField("del_flag")
    @TableLogic
    private Integer delFlag;
}
