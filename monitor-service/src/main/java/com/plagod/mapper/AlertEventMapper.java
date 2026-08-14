package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.dto.AlertRuleAnalyticsQueryCriteria;
import com.plagod.entity.monitor.AlertEvent;
import com.plagod.vo.monitor.AlertRuleAnalyticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    AlertRuleAnalyticsVO.AlertSummary selectAnalyticsSummary(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);


    List<AlertRuleAnalyticsVO.CountBucket> selectLevelDistribution(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);


    List<AlertRuleAnalyticsVO.CountBucket> selectStatusDistribution(@Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);

    AlertEvent selectByIdAndTenant(@Param("tenantId") Long tenantId,
                                   @Param("id") Long id);

    AlertEvent selectByIdAndTenantForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("id") Long id);

    int handleByTenantAndVersion(@Param("tenantId") Long tenantId,
                                 @Param("id") Long id,
                                 @Param("handleUserId") Long handleUserId,
                                 @Param("handleTime") LocalDateTime handleTime,
                                 @Param("expectedVersion") Integer expectedVersion);

    AlertRuleAnalyticsVO.AlertSummary selectAnalyticsSummaryByTenant(
            @Param("tenantId") Long tenantId,
            @Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);

    List<AlertRuleAnalyticsVO.CountBucket> selectLevelDistributionByTenant(
            @Param("tenantId") Long tenantId,
            @Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);

    List<AlertRuleAnalyticsVO.CountBucket> selectStatusDistributionByTenant(
            @Param("tenantId") Long tenantId,
            @Param("criteria") AlertRuleAnalyticsQueryCriteria criteria);
}
