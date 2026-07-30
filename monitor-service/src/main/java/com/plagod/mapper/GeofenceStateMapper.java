package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.monitor.GeofenceState;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GeofenceStateMapper extends BaseMapper<GeofenceState> {

    @Select("select * from t_geofence_state " +
            "where fence_id = #{fenceId} " +
            "and session_id = #{sessionId} " +
            "limit 1 for update")
    GeofenceState selectForUpdate(@Param("fenceId") Long fenceId, @Param("sessionId") Long sessionId);

    @Insert("insert ignore into t_geofence_state(" +
            "fence_id,session_id,user_id,node_id,device_code,mac," +
            "inside_state,last_location_id,last_report_time) values(" +
            "#{state.fenceId},#{state.sessionId},#{state.userId}," +
            "#{state.nodeId},#{state.deviceCode},#{state.mac}," +
            "#{state.insideState},#{state.lastLocationId}," +
            "#{state.lastReportTime})")
    int insertIgnore(@Param("state") GeofenceState state);

    @Update("update t_geofence_state set " +
            "user_id=#{state.userId},node_id=#{state.nodeId}," +
            "device_code=#{state.deviceCode},mac=#{state.mac}," +
            "inside_state=#{state.insideState}," +
            "last_location_id=#{state.lastLocationId}," +
            "last_report_time=#{state.lastReportTime}," +
            "update_time=current_timestamp " +
            "where state_id=#{state.stateId}")
    int updateState(@Param("state") GeofenceState state);

    @Delete("delete from t_geofence_state where fence_id=#{fenceId}")
    int deleteByFenceId(@Param("fenceId") Long fenceId);

    @Delete("delete from t_geofence_state where user_id=#{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}