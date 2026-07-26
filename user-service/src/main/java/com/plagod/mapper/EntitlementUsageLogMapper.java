package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.EntitlementUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntitlementUsageLogMapper extends BaseMapper<EntitlementUsageLog> {
    // 用于识别重试请求，必须在锁住用户权益后再次检测
    @Select("select * from t_entitlement_usage_log where request_id = #{requestId} order by line_no")
    List<EntitlementUsageLog> selectByRequestId(@Param("requestId") String requestId);
}