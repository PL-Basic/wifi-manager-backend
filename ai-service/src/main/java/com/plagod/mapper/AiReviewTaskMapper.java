package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.AiReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiReviewTaskMapper extends BaseMapper<AiReviewTask> {

    @Select("select * from t_ai_review_task " +
            "where review_request_id = #{reviewRequestId}")
    AiReviewTask selectByReviewRequestId(
            @Param("reviewRequestId") String reviewRequestId);

    @Select("select review_task_id from t_ai_review_task " +
            "where (task_status = 'QUEUED' " +
            "and (next_retry_time is null or next_retry_time <= #{now})) " +
            "or (task_status = 'RUNNING' and lease_until <= #{now}) " +
            "order by review_task_id asc limit #{limit}")
    List<Long> selectDispatchableIds(
            @Param("now") LocalDateTime now,
            @Param("limit") Integer limit);

    @Update("update t_ai_review_task " +
            "set task_status = 'MANUAL_REQUIRED', decision_code = 'MANUAL', " +
            "confidence_bps = 0, reason_code = #{errorCode}, " +
            "worker_id = null, lease_until = null, claimed_time = null, " +
            "next_retry_time = null, last_error_code = #{errorCode}, " +
            "completed_time = #{now}, version = version + 1, " +
            "update_time = #{now} " +
            "where review_task_id = #{reviewTaskId} " +
            "and attempt_count >= #{maxAttempts} " +
            "and ((task_status = 'QUEUED' " +
            "and (next_retry_time is null or next_retry_time <= #{now})) " +
            "or (task_status = 'RUNNING' and lease_until <= #{now}))")
    int finalizeExhausted(
            @Param("reviewTaskId") Long reviewTaskId,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("errorCode") String errorCode);

    @Update("update t_ai_review_task " +
            "set task_status = 'RUNNING', worker_id = #{workerId}, " +
            "lease_until = #{leaseUntil}, claimed_time = #{now}, " +
            "attempt_count = attempt_count + 1, next_retry_time = null, " +
            "last_error_code = null, " +
            "started_time = coalesce(started_time, #{now}), " +
            "version = version + 1, update_time = #{now} " +
            "where review_task_id = #{reviewTaskId} " +
            "and attempt_count < #{maxAttempts} " +
            "and ((task_status = 'QUEUED' " +
            "and (next_retry_time is null or next_retry_time <= #{now})) " +
            "or (task_status = 'RUNNING' and lease_until <= #{now}))")
    int claim(
            @Param("reviewTaskId") Long reviewTaskId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("maxAttempts") Integer maxAttempts);

    @Update("update t_ai_review_task " +
            "set task_status = #{taskStatus}, decision_code = #{decisionCode}, " +
            "confidence_bps = #{confidenceBps}, " +
            "risk_labels_json = #{riskLabelsJson}, " +
            "reason_code = #{reasonCode}, " +
            "provider_request_id = #{providerRequestId}, " +
            "model_identifier = #{modelIdentifier}, duration_ms = #{durationMs}, " +
            "worker_id = null, lease_until = null, claimed_time = null, " +
            "next_retry_time = null, last_error_code = null, " +
            "completed_time = #{now}, version = version + 1, " +
            "update_time = #{now} " +
            "where review_task_id = #{reviewTaskId} " +
            "and task_status = 'RUNNING' and worker_id = #{workerId} " +
            "and lease_until > #{now} and version = #{expectedVersion}")
    int completeResult(
            @Param("reviewTaskId") Long reviewTaskId,
            @Param("workerId") String workerId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("taskStatus") String taskStatus,
            @Param("decisionCode") String decisionCode,
            @Param("confidenceBps") Integer confidenceBps,
            @Param("riskLabelsJson") String riskLabelsJson,
            @Param("reasonCode") String reasonCode,
            @Param("providerRequestId") String providerRequestId,
            @Param("modelIdentifier") String modelIdentifier,
            @Param("durationMs") Long durationMs,
            @Param("now") LocalDateTime now);

    @Update("update t_ai_review_task " +
            "set task_status = case when attempt_count >= #{maxAttempts} " +
            "then 'MANUAL_REQUIRED' else 'QUEUED' end, " +
            "decision_code = case when attempt_count >= #{maxAttempts} " +
            "then 'MANUAL' else null end, " +
            "confidence_bps = case when attempt_count >= #{maxAttempts} " +
            "then 0 else null end, risk_labels_json = null, " +
            "reason_code = case when attempt_count >= #{maxAttempts} " +
            "then #{errorCode} else null end, provider_request_id = null, " +
            "model_identifier = null, duration_ms = null, " +
            "worker_id = null, lease_until = null, claimed_time = null, " +
            "next_retry_time = case when attempt_count >= #{maxAttempts} " +
            "then null else #{retryAt} end, last_error_code = #{errorCode}, " +
            "completed_time = case when attempt_count >= #{maxAttempts} " +
            "then #{now} else null end, version = version + 1, " +
            "update_time = #{now} " +
            "where review_task_id = #{reviewTaskId} " +
            "and task_status = 'RUNNING' and worker_id = #{workerId} " +
            "and lease_until > #{now} and version = #{expectedVersion}")
    int completeFailure(
            @Param("reviewTaskId") Long reviewTaskId,
            @Param("workerId") String workerId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("errorCode") String errorCode,
            @Param("now") LocalDateTime now,
            @Param("retryAt") LocalDateTime retryAt,
            @Param("maxAttempts") Integer maxAttempts);
}
