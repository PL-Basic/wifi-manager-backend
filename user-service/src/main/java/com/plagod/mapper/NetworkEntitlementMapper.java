package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.NetworkEntitlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NetworkEntitlementMapper extends BaseMapper<NetworkEntitlement> {

    // 锁定用户权益，必须在数据库事务中调用。
    @Select("select * from t_network_entitlement where tenant_id = #{tenantId} and user_id = #{userId} limit 1 for update")
    NetworkEntitlement selectByUserIdForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("userId") Long userId);

    @Select("select * from t_network_entitlement where tenant_id = #{tenantId} " +
            "and user_id = #{userId} and entitlement_id = #{entitlementId} limit 1")
    NetworkEntitlement selectOwnedEntitlement(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("entitlementId") Long entitlementId);

    // 原子扣减余额，返回0表示余额不足或权益无效。
    int deductRemainingSeconds(@Param("tenantId") Long tenantId,
                               @Param("entitlementId") Long entitlementId,
                               @Param("seconds") Long seconds);
}
