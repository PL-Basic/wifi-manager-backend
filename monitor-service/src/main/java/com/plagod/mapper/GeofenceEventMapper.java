package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.entity.monitor.GeofenceEvent;
import com.plagod.vo.monitor.GeofenceEventVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface GeofenceEventMapper
        extends BaseMapper<GeofenceEvent> {

    @Insert("insert ignore into t_geofence_event(" +
            "fence_id,location_id,user_id,session_id,node_id," +
            "device_code,mac,event_type,event_time) values(" +
            "#{event.fenceId},#{event.locationId},#{event.userId}," +
            "#{event.sessionId},#{event.nodeId},#{event.deviceCode}," +
            "#{event.mac},#{event.eventType},#{event.eventTime})")
    int insertIgnore(@Param("event") GeofenceEvent event);

    Page<GeofenceEventVO> selectEventPage(Page<GeofenceEventVO> page,
                                          @Param("fenceId") Long fenceId,
                                          @Param("userId") Long userId,
                                          @Param("sessionId") Long sessionId,
                                          @Param("mac") String mac,
                                          @Param("eventType") String eventType,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    @Delete("delete from t_geofence_event where user_id=#{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}