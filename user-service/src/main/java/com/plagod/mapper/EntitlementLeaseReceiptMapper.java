package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.EntitlementLeaseReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EntitlementLeaseReceiptMapper
        extends BaseMapper<EntitlementLeaseReceipt> {

    @Select("select * from t_entitlement_lease_receipt "
            + "where tenant_id = #{tenantId} and request_id = #{requestId} "
            + "limit 1")
    EntitlementLeaseReceipt selectByRequest(
            @Param("tenantId") Long tenantId,
            @Param("requestId") String requestId);
}
