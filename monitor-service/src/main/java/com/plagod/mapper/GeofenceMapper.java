package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.monitor.Geofence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GeofenceMapper extends BaseMapper<Geofence> {

    @Select("select * from t_geofence " +
            "where enabled = 1 and del_flag = 0 " +
            "order by fence_id")
    List<Geofence> selectEnabled();

    @Select("select * from t_geofence "
            + "where tenant_id = #{tenantId} and enabled = 1 and del_flag = 0 "
            + "order by fence_id")
    List<Geofence> selectEnabledByTenant(@Param("tenantId") Long tenantId);

    @Select("select * from t_geofence "
            + "where tenant_id = #{tenantId} and fence_id = #{fenceId} "
            + "and del_flag = 0 limit 1")
    Geofence selectByIdAndTenant(@Param("tenantId") Long tenantId,
                                 @Param("fenceId") Long fenceId);

    @Select("select * from t_geofence "
            + "where tenant_id = #{tenantId} and fence_id = #{fenceId} "
            + "limit 1 for update")
    Geofence selectByIdAndTenantForUpdate(@Param("tenantId") Long tenantId,
                                          @Param("fenceId") Long fenceId);

    @Update("update t_geofence set enabled = #{enabled}, version = version + 1 "
            + "where tenant_id = #{tenantId} and fence_id = #{fenceId} "
            + "and version = #{expectedVersion} and del_flag = 0")
    int updateEnabledByTenantAndVersion(@Param("tenantId") Long tenantId,
                                        @Param("fenceId") Long fenceId,
                                        @Param("enabled") Integer enabled,
                                        @Param("expectedVersion") Integer expectedVersion);
}
