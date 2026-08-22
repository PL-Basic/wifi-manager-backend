package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.AiPolicyVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiPolicyVersionMapper extends BaseMapper<AiPolicyVersion> {

    @Select("select version.* from t_ai_policy policy " +
            "inner join t_ai_policy_version version " +
            "on version.policy_id = policy.policy_id " +
            "where policy.scene = #{scene} " +
            "and policy.status = 'ENABLED' " +
            "and version.status = 'ACTIVE' limit 1")
    AiPolicyVersion selectActiveByScene(@Param("scene") String scene);
}
