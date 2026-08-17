package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.SupportContentReviewOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupportContentReviewOutboxMapper extends BaseMapper<SupportContentReviewOutbox> {

    @Select("select event_id from t_support_content_review_outbox "
            + "where scene = 'SUPPORT_SUBMISSION_REVIEW' "
            + "and business_type = 'SUPPORT_SUBMISSION' "
            + "and next_retry_time <= #{now} "
            + "and ((status = 'PENDING' and worker_id is null "
            + "and lease_until is null) "
            + "or (status = 'PROCESSING' and worker_id is not null "
            + "and lease_until <= #{now})) "
            + "order by event_id asc limit #{limit}")
    List<Long> selectDispatchableEventIds(
            @Param("now") LocalDateTime now,
            @Param("limit") Integer limit);

    @Update("update t_support_content_review_outbox "
            + "set status = 'PROCESSING', worker_id = #{workerId}, "
            + "lease_until = #{leaseUntil}, last_error_code = null, "
            + "version = version + 1 "
            + "where event_id = #{eventId} "
            + "and scene = 'SUPPORT_SUBMISSION_REVIEW' "
            + "and business_type = 'SUPPORT_SUBMISSION' "
            + "and next_retry_time <= #{now} "
            + "and ((status = 'PENDING' and worker_id is null "
            + "and lease_until is null) "
            + "or (status = 'PROCESSING' and lease_until <= #{now}))")
    int claim(
            @Param("eventId") Long eventId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("update t_support_content_review_outbox "
            + "set status = 'SENT', worker_id = null, lease_until = null, "
            + "last_error_code = null, version = version + 1 "
            + "where event_id = #{eventId} and status = 'PROCESSING' "
            + "and worker_id = #{workerId} and lease_until > #{now}")
    int markSent(
            @Param("eventId") Long eventId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now);

    @Update("update t_support_content_review_outbox "
            + "set status = case when retry_count + 1 >= #{maxAttempts} "
            + "then 'FAILED' else 'PENDING' end, "
            + "next_retry_time = case when retry_count + 1 >= #{maxAttempts} "
            + "then #{now} else #{nextRetryTime} end, "
            + "last_error_code = case when retry_count + 1 >= #{maxAttempts} "
            + "then #{terminalErrorCode} else #{errorCode} end, "
            + "retry_count = retry_count + 1, "
            + "worker_id = null, lease_until = null, "
            + "version = version + 1 "
            + "where event_id = #{eventId} and status = 'PROCESSING' "
            + "and worker_id = #{workerId} and lease_until > #{now}")
    int releaseAfterFailure(
            @Param("eventId") Long eventId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("maxAttempts") int maxAttempts,
            @Param("errorCode") String errorCode,
            @Param("terminalErrorCode") String terminalErrorCode);
}
