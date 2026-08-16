package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.device.DeviceWifiConfigRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface DeviceWifiConfigRecordMapper
        extends BaseMapper<DeviceWifiConfigRecord> {

    @Select("select * from t_device_wifi_config where tenant_id = #{tenantId} and request_id = #{requestId} limit 1 for update")
    DeviceWifiConfigRecord selectByRequestIdForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("requestId") String requestId);

    @Select("select * from t_device_wifi_config where tenant_id = #{tenantId} and node_id = #{nodeId} order by config_version desc limit 1")
    DeviceWifiConfigRecord selectLatestByNodeId(@Param("tenantId") Long tenantId,
                                                @Param("nodeId") Long nodeId);

    @Select("select * from t_device_wifi_config where tenant_id = #{tenantId} and binary device_code = #{deviceCode} and binary request_id = #{requestId} limit 1")
    DeviceWifiConfigRecord selectByDeviceCodeAndRequestId(@Param("tenantId") Long tenantId,
                                                          @Param("deviceCode") String deviceCode,
                                                          @Param("requestId") String requestId);

    @Update("update t_device_wifi_config " +
            "set status = #{supersededStatus}, update_time = #{now} " +
            "where wifi_config_id = #{wifiConfigId} " +
            "and tenant_id = #{tenantId} " +
            "and status in (#{stagedStatus}, #{unknownStatus})")
    int supersedeReplaceable(@Param("tenantId") Long tenantId,
                             @Param("wifiConfigId") Long wifiConfigId,
                             @Param("stagedStatus") Integer stagedStatus,
                             @Param("unknownStatus") Integer unknownStatus,
                             @Param("supersededStatus") Integer supersededStatus,
                             @Param("now") LocalDateTime now);
}
