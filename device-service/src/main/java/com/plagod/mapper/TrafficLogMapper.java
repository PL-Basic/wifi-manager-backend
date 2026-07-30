package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.dto.TrafficAnalyticsQueryCriteria;
import com.plagod.entity.device.TrafficLog;
import com.plagod.vo.device.TrafficAnalyticsSourceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrafficLogMapper extends BaseMapper<TrafficLog> {

    int insertIgnore(TrafficLog trafficLog);

    @Select("select * from t_traffic_log where device_code = #{deviceCode} and event_id = #{eventId} limit 1 for update")
    TrafficLog selectByEventIdentityForUpdate(@Param("deviceCode") String deviceCode,
                                              @Param("eventId") String eventId);

    TrafficAnalyticsSourceVO.Summary selectAnalyticsSummary(@Param("criteria") TrafficAnalyticsQueryCriteria criteria);

    List<TrafficAnalyticsSourceVO.TimeBucket> selectAnalyticsTrend(@Param("criteria") TrafficAnalyticsQueryCriteria criteria);

    List<TrafficAnalyticsSourceVO.RankBucket> selectAnalyticsRanking(@Param("criteria") TrafficAnalyticsQueryCriteria criteria,
                                                                     @Param("dimension") String dimension);
}