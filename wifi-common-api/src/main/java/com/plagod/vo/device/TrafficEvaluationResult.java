package com.plagod.vo.device;

import com.plagod.vo.RuleHitVO;
import lombok.Data;

import java.util.List;

@Data
public class TrafficEvaluationResult {
    private boolean hit;
    private List<RuleHitVO> hits;
    private Long alertId;
}
