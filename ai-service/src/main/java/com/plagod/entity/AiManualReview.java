package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_manual_review")
public class AiManualReview {

    @TableId(type = IdType.AUTO)
    private Long manualReviewId;
    private Long reviewTaskId;
    private String eventKey;
    private String decisionCode;
    private Long reviewerUserId;
    private String reasonCode;
    private String remarkSummary;
    private LocalDateTime createTime;
}
