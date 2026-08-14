package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.monitor.LocationAuthorization;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface LocationAuthorizationMapper
        extends BaseMapper<LocationAuthorization> {

    // 首次授权或上报前保证用户拥有一行可加锁的授权记录。
    @Insert("insert ignore into t_location_authorization (user_id, enabled, create_time, update_time) values (#{userId}, 0, current_timestamp, current_timestamp)")
    int ensureAuthorizationRow(@Param("userId") Long userId);

    // 串行处理同一用户的授权状态和位置上报。
    @Select("select * from t_location_authorization where user_id = #{userId} for update")
    LocationAuthorization selectByUserIdForUpdate(@Param("userId") Long userId);

    @Insert("insert ignore into t_location_authorization "
            + "(tenant_id, user_id, enabled, version, create_time, update_time) "
            + "values (#{tenantId}, #{userId}, 0, 0, current_timestamp, current_timestamp)")
    int ensureAuthorizationRowByTenant(@Param("tenantId") Long tenantId,
                                       @Param("userId") Long userId);

    @Select("select * from t_location_authorization "
            + "where tenant_id = #{tenantId} and user_id = #{userId} for update")
    LocationAuthorization selectByUserIdForUpdateAndTenant(@Param("tenantId") Long tenantId,
                                                           @Param("userId") Long userId);

    @Select("select * from t_location_authorization "
            + "where tenant_id = #{tenantId} and user_id = #{userId} limit 1")
    LocationAuthorization selectByUserIdAndTenant(@Param("tenantId") Long tenantId,
                                                  @Param("userId") Long userId);

    @Update("update t_location_authorization set "
            + "enabled = #{enabled}, consent_time = #{consentTime}, "
            + "revoked_time = #{revokedTime}, last_report_time = #{lastReportTime}, "
            + "version = version + 1, update_time = current_timestamp "
            + "where tenant_id = #{tenantId} and user_id = #{userId} "
            + "and version = #{expectedVersion}")
    int updateByTenantAndVersion(@Param("tenantId") Long tenantId,
                                 @Param("userId") Long userId,
                                 @Param("enabled") Integer enabled,
                                 @Param("consentTime") LocalDateTime consentTime,
                                 @Param("revokedTime") LocalDateTime revokedTime,
                                 @Param("lastReportTime") LocalDateTime lastReportTime,
                                 @Param("expectedVersion") Integer expectedVersion);
}
