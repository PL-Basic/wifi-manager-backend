package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.SupportSubmission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SupportSubmissionMapper extends BaseMapper<SupportSubmission> {

    @Select("select * from t_support_submission "
            + "where tenant_id = #{tenantId} and user_id = #{userId} "
            + "and client_request_id = #{clientRequestId} limit 1")
    SupportSubmission selectByRequestKey(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId);

    @Select("select * from t_support_submission "
            + "where tenant_id = #{tenantId} and user_id = #{userId} "
            + "and client_request_id = #{clientRequestId} "
            + "limit 1 for update")
    SupportSubmission selectByRequestKeyForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("clientRequestId") String clientRequestId);

    @Update("update t_support_submission "
            + "set ai_review_task_id = #{reviewTaskId}, version = version + 1 "
            + "where submission_id = #{submissionId} "
            + "and review_request_id = #{reviewRequestId} "
            + "and status = 'REVIEW_PENDING' "
            + "and ai_review_task_id is null "
            + "and version = #{expectedVersion}")
    int attachAiReviewTask(
            @Param("submissionId") Long submissionId,
            @Param("reviewRequestId") String reviewRequestId,
            @Param("reviewTaskId") Long reviewTaskId,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("update t_support_submission "
            + "set status = 'MANUAL_REVIEW', review_decision = 'MANUAL', "
            + "outcome_reason_code = #{reasonCode}, version = version + 1 "
            + "where submission_id = #{submissionId} "
            + "and review_request_id = #{reviewRequestId} "
            + "and status = 'REVIEW_PENDING' "
            + "and ai_review_task_id is null "
            + "and version = #{expectedVersion}")
    int moveToManualReview(
            @Param("submissionId") Long submissionId,
            @Param("reviewRequestId") String reviewRequestId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("reasonCode") String reasonCode);
}
