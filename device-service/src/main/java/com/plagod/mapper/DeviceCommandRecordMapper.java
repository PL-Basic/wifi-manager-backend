package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.DeviceCommandRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceCommandRecordMapper extends BaseMapper<DeviceCommandRecord> {

    // 处理 command-result 时锁定命令，防止重复 MQTT 消息并发修改。
    @Select("select * from t_device_command where request_id = #{requestId} limit 1 for update")
    DeviceCommandRecord selectByRequestIdForUpdate(@Param("requestId") String requestId);
}