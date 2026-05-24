package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_traffic_log")
public class TrafficLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String mac;

    private String dstIp;

    private Integer dstPort;

    private String sni;

    private String protocol;

    private Long bytesUp;

    private Long bytesDown;

    private LocalDateTime logTime;
}
