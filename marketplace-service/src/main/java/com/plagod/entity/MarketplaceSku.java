package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_market_sku")
public class MarketplaceSku {

    @TableId(type = IdType.AUTO)
    private Long skuId;
    private String skuCode;
    private Long productId;
    private String targetType;
    private String specificationJson;
    private Long priceCents;
    private String entitlementProductCode;
    private Long saasPlanVersionId;
    private String hardwareModelCode;
    private String status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
