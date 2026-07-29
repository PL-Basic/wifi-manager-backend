package com.plagod.entity.entitlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_network_entitlement")
public class NetworkEntitlement implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "entitlement_id", type = IdType.AUTO)
    private Long entitlementId;

    private Long userId;
    private String mode;
    private LocalDateTime subscriptionStartTime;
    private LocalDateTime subscriptionEndTime;
    private Long remainingSeconds;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
