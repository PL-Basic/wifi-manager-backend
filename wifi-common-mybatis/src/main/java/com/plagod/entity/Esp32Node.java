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

    private Integer status;

    @TableField("max_clients")
    private Integer maxClients;

    @TableField("current_clients")
    private Integer currentClients;

    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField("del_flag")
    @TableLogic
    private Integer delFlag;
}
