package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.EntitlementUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntitlementUsageLogMapper extends BaseMapper<EntitlementUsageLog> {
    // 用于识别重试请求，必须在锁住用户权益后再次检测
    @Select("select * from t_entitlement_usage_log where request_id = #{requestId} order by line_no")
    List<EntitlementUsageLog> selectByRequestId(@Param("requestId") String requestId);

    // 等待业务行锁后读取最新已提交结果，不使用事务开始时的旧快照。
    @Select("select * from t_entitlement_usage_log where request_id = #{requestId} order by line_no for update")
    List<EntitlementUsageLog> selectByRequestIdForUpdate(@Param("requestId") String requestId);
}