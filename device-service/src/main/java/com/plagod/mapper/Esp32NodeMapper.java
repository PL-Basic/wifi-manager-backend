package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.Esp32Node;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface Esp32NodeMapper extends BaseMapper<Esp32Node> {

    Esp32Node selectByDeviceCodeIncludeDeleted(@Param("deviceCode") String deviceCode);

    Esp32Node selectByNodeIdIncludeDeleted(@Param("nodeId") Long nodeId);

    int restoreRetiredById(@Param("nodeId") Long nodeId);


    int markTimedOutNodesOffline(@Param("cutoff") LocalDateTime cutoff,
                                 @Param("onlineStatus") Integer onlineStatus,
                                 @Param("offlineStatus") Integer offlineStatus);

    // Portal 授权时锁住节点，防止同一节点的并发请求重复创建 Session。
    @Select("select * from t_esp32_node " +
            "where device_code = #{deviceCode} " +
            "limit 1 for update")
    Esp32Node selectByDeviceCodeForUpdateIncludeDeleted(@Param("deviceCode") String deviceCode);
}