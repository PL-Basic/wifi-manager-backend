package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.MarketplaceOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MarketplaceOrderMapper extends BaseMapper<MarketplaceOrder> {

    @Select("select * from t_market_order " +
            "where tenant_id = #{tenantId} " +
            "and subject_type = #{subjectType} " +
            "and subject_key = #{subjectKey} " +
            "and client_request_id = #{clientRequestId} " +
            "limit 1")
    MarketplaceOrder selectByIdempotencyKey(
            @Param("tenantId") Long tenantId,
            @Param("subjectType") String subjectType,
            @Param("subjectKey") String subjectKey,
            @Param("clientRequestId") String clientRequestId);
}
