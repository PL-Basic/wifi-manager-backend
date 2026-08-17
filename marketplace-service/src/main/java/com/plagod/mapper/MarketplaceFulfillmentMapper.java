package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.MarketplaceFulfillment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MarketplaceFulfillmentMapper extends BaseMapper<MarketplaceFulfillment> {

    @Select("select fulfillment_id from t_market_fulfillment " +
            "where fulfillment_mode = 'TARGET_DOMAIN' " +
            "and ((status = 'PENDING' and next_attempt_time <= #{now}) " +
            "or (status = 'PROCESSING' and lease_until <= #{now})) " +
            "order by fulfillment_id asc limit #{limit}")
    List<Long> selectDispatchableIds(
            @Param("now") LocalDateTime now,
            @Param("limit") Integer limit);

    @Update("update t_market_fulfillment " +
            "set status = 'FAILED', worker_id = null, lease_until = null, " +
            "next_attempt_time = #{now}, last_error_key = #{errorKey}, " +
            "version = version + 1, update_time = #{now} " +
            "where fulfillment_id = #{fulfillmentId} " +
            "and fulfillment_mode = 'TARGET_DOMAIN' " +
            "and attempt_count >= #{maxAttempts} " +
            "and ((status = 'PENDING' and next_attempt_time <= #{now}) " +
            "or (status = 'PROCESSING' and lease_until <= #{now}))")
    int failExhausted(
            @Param("fulfillmentId") Long fulfillmentId,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("errorKey") String errorKey);

    @Update("update t_market_fulfillment " +
            "set status = 'PROCESSING', worker_id = #{workerId}, " +
            "lease_until = #{leaseUntil}, attempt_count = attempt_count + 1, " +
            "last_error_key = null, version = version + 1, update_time = #{now} " +
            "where fulfillment_id = #{fulfillmentId} " +
            "and fulfillment_mode = 'TARGET_DOMAIN' " +
            "and attempt_count < #{maxAttempts} " +
            "and ((status = 'PENDING' and next_attempt_time <= #{now}) " +
            "or (status = 'PROCESSING' and lease_until <= #{now}))")
    int claim(
            @Param("fulfillmentId") Long fulfillmentId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("maxAttempts") Integer maxAttempts);

    @Update("update t_market_fulfillment " +
            "set status = 'SUCCEEDED', result_reference = #{resultReference}, " +
            "worker_id = null, lease_until = null, next_attempt_time = #{now}, " +
            "last_error_key = null, version = version + 1, update_time = #{now} " +
            "where fulfillment_id = #{fulfillmentId} " +
            "and status = 'PROCESSING' and worker_id = #{workerId} " +
            "and lease_until > #{now}")
    int completeSuccess(
            @Param("fulfillmentId") Long fulfillmentId,
            @Param("workerId") String workerId,
            @Param("resultReference") String resultReference,
            @Param("now") LocalDateTime now);

    @Update("update t_market_fulfillment " +
            "set status = case when attempt_count >= #{maxAttempts} " +
            "then 'FAILED' else 'PENDING' end, " +
            "worker_id = null, lease_until = null, " +
            "next_attempt_time = case when attempt_count >= #{maxAttempts} " +
            "then #{now} else #{retryAt} end, " +
            "last_error_key = #{errorKey}, version = version + 1, " +
            "update_time = #{now} " +
            "where fulfillment_id = #{fulfillmentId} " +
            "and status = 'PROCESSING' and worker_id = #{workerId} " +
            "and lease_until > #{now}")
    int completeFailure(
            @Param("fulfillmentId") Long fulfillmentId,
            @Param("workerId") String workerId,
            @Param("errorKey") String errorKey,
            @Param("now") LocalDateTime now,
            @Param("retryAt") LocalDateTime retryAt,
            @Param("maxAttempts") Integer maxAttempts);
}
