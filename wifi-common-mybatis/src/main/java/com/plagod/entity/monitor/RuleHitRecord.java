package com.plagod.entity.monitor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_rule_hit")
public class RuleHitRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String deviceCode;
    private Long nodeId;
    private Long sessionId;
    private Long userId;
    private String mac;
    private Long ruleId;
    private String ruleCode;
    private Integer ruleType;
    private Integer actionType;
    private Integer level;
    private Integer suppressed;
    private Long alertId;
    private LocalDateTime hitTime;
    private LocalDateTime createTime;
}