package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.OAuthStateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OAuthStateMapper
        extends BaseMapper<OAuthStateRecord> {

    @Select("select * from t_oauth_state where state_hash = #{stateHash} limit 1 for update")
    OAuthStateRecord selectByHashForUpdate(@Param("stateHash") String stateHash);

    @Select("select * from t_oauth_state where state_id = #{stateId} limit 1 for update")
    OAuthStateRecord selectByIdForUpdate(@Param("stateId") Long stateId);
}