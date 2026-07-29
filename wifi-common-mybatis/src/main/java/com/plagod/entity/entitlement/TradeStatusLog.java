package com.plagod.entity.entitlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_trade_status_log")
public class TradeStatusLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String businessType;
    private String businessNo;
    private String eventKey;
    private String fromStatus;
    private String toStatus;
    private String operatorType;
    private Long operatorId;
    private String remark;
    private LocalDateTime createTime;
}