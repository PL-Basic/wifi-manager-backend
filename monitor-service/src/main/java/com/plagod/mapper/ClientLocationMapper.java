package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.monitor.ClientLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ClientLocationMapper extends BaseMapper<ClientLocation> {

    @Select("select * from t_client_location " +
            "where user_id = #{userId} " +
            "and session_id = #{sessionId} " +
            "and trusted_binding = 1 " +
            "order by report_time desc, id desc limit 1")
    ClientLocation selectLatestTrustedPoint(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    List<ClientLocation> selectTrustedPointsForGis(@Param("sessionId") Long sessionId,
                                                   @Param("userId") Long userId,
                                                   @Param("nodeId") Long nodeId,
                                                   @Param("mac") String mac,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime,
                                                   @Param("limit") Integer limit);
}