package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_market_product")
public class MarketplaceProduct {

    @TableId(type = IdType.AUTO)
    private Long productId;
    private String productCode;
    private String productType;
    private String name;
    private String summary;
    private String descriptionText;
    private String status;
    private Integer displayOrder;
    private LocalDateTime publishTime;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
