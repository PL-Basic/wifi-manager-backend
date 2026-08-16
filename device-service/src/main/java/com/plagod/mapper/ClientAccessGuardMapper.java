package com.plagod.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClientAccessGuardMapper {

    // 首次访问时建立锁行；已经存在时保持幂等。
    @Insert("insert ignore into t_client_access_guard(mac, tenant_id) values(#{mac}, #{tenantId})")
    int ensureGuardRow(@Param("tenantId") Long tenantId,
                       @Param("mac") String mac);

    // 锁住同一 MAC 的授权、黑名单和 Session 关闭流程。
    @Select("select mac from t_client_access_guard where tenant_id = #{tenantId} and mac = #{mac} for update")
    String selectMacForUpdate(@Param("tenantId") Long tenantId,
                              @Param("mac") String mac);
}
