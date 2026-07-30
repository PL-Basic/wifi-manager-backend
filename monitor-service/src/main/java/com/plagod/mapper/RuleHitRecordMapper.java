package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.dto.AlertRuleAnalyticsQueryCriteria;
import com.plagod.entity.monitor.RuleHitRecord;
import com.plagod.vo.monitor.AlertRuleAnalyticsVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RuleHitRecordMapper extends BaseMapper<RuleHitRecord> {

    int insertIgnore(RuleHitRecord record);


    @Update("update t_rule_hit set alert_id = #{alertId} where device_code = #{deviceCode} and event_id = #{eventId} and suppressed = 0 and alert_id is null")
    int bindAlert(@Param("deviceCode") String deviceCode, @Param("eventId") String eventId, @Param("alertId") Long alertId);


    AlertRuleAnalyticsVO.RuleHitSummary selectAnalyticsSummary(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);


    List<AlertRuleAnalyticsVO.RuleBucket> selectRuleRanking(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);


    List<AlertRuleAnalyticsVO.CountBucket> selectActionDistribution(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);
}