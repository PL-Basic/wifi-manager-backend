package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.dto.AlertRuleAnalyticsQueryCriteria;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.vo.monitor.AlertRuleAnalyticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    AlertRuleAnalyticsVO.AlertSummary selectAnalyticsSummary(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);


    List<AlertRuleAnalyticsVO.CountBucket> selectLevelDistribution(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);


    List<AlertRuleAnalyticsVO.CountBucket> selectStatusDistribution(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);
}