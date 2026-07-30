package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.device.ClientSignalRecord;
import com.plagod.vo.device.SignalAnalyticsSourceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ClientSignalMapper
        extends BaseMapper<ClientSignalRecord> {

    @Select("select id, rssi, report_time from t_client_signal " +
            "where node_id = #{nodeId} and mac = #{mac} " +
            "and report_time >= #{startTime} " +
            "and report_time <= #{endTime} " +
            "order by report_time desc, id desc limit #{sampleLimit}")
    List<SignalAnalyticsSourceVO.SignalSample> selectLatestSamples(@Param("nodeId") Long nodeId,
                                                                   @Param("mac") String mac,
                                                                   @Param("startTime") LocalDateTime startTime,
                                                                   @Param("endTime") LocalDateTime endTime,
                                                                   @Param("sampleLimit") Integer sampleLimit);

    @Select("select from_unixtime(" +
            "floor(unix_timestamp(report_time) / #{bucketSeconds}) " +
            "* #{bucketSeconds}) as bucket_time, " +
            "count(*) as sample_count, avg(rssi) as average_rssi, " +
            "min(rssi) as min_rssi, max(rssi) as max_rssi " +
            "from t_client_signal " +
            "where node_id = #{nodeId} and mac = #{mac} " +
            "and report_time >= #{startTime} " +
            "and report_time <= #{endTime} " +
            "group by bucket_time order by bucket_time")
    List<SignalAnalyticsSourceVO.SignalTrendBucket> selectTrendBuckets(@Param("nodeId") Long nodeId,
                                                                       @Param("mac") String mac,
                                                                       @Param("startTime") LocalDateTime startTime,
                                                                       @Param("endTime") LocalDateTime endTime,
                                                                       @Param("bucketSeconds") Integer bucketSeconds);
}