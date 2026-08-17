package com.plagod.mapper;

import com.plagod.ai.task.AiReviewTaskInputSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiReviewTaskInputMapper {

    @Select("select content.title, content.body_text, content.content_hash " +
            "from t_announcement_content_version content " +
            "join t_announcement announcement " +
            "on announcement.announcement_id = content.announcement_id " +
            "where content.announcement_id = #{businessId} " +
            "and content.content_version = #{contentVersion} " +
            "and content.review_request_id = #{reviewRequestId} " +
            "and content.content_hash = #{contentHash} " +
            "and ((#{tenantId} is null " +
            "and announcement.scope_type = 'PLATFORM' " +
            "and announcement.tenant_id is null) " +
            "or (#{tenantId} is not null " +
            "and announcement.scope_type = 'TENANT' " +
            "and announcement.tenant_id = #{tenantId}))")
    @Results(
            id = "aiReviewTaskInputSnapshot",
            value = {
                    @Result(column = "title", property = "title"),
                    @Result(column = "body_text", property = "body"),
                    @Result(column = "content_hash", property = "contentHash")
            })
    AiReviewTaskInputSnapshot selectAnnouncementVersion(
            @Param("businessId") Long businessId,
            @Param("tenantId") Long tenantId,
            @Param("contentVersion") Integer contentVersion,
            @Param("reviewRequestId") String reviewRequestId,
            @Param("contentHash") String contentHash);

    @Select("select title, content_text as body_text, content_hash " +
            "from t_support_submission " +
            "where submission_id = #{businessId} " +
            "and tenant_id = #{tenantId} " +
            "and content_version = #{contentVersion} " +
            "and review_request_id = #{reviewRequestId} " +
            "and content_hash = #{contentHash}")
    @ResultMap("aiReviewTaskInputSnapshot")
    AiReviewTaskInputSnapshot selectSupportSubmission(
            @Param("businessId") Long businessId,
            @Param("tenantId") Long tenantId,
            @Param("contentVersion") Integer contentVersion,
            @Param("reviewRequestId") String reviewRequestId,
            @Param("contentHash") String contentHash);
}
