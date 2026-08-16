package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_market_order_item")
public class MarketplaceOrderItem {

    @TableId(type = IdType.AUTO)
    private Long orderItemId;
    private Long orderId;
    private Integer lineNo;
    private Long skuId;
    private String skuCode;
    private String productCode;
    private String productName;
    private String productType;
    private String specificationSnapshotJson;
    private Long unitPriceCents;
    private Integer quantity;
    private Long subtotalCents;
    private String entitlementProductCode;
    private Long saasPlanVersionId;
    private String hardwareModelCode;
    private LocalDateTime createTime;
}
