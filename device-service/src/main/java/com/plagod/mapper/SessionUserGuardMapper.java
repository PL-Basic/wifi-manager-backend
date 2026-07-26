package com.plagod.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SessionUserGuardMapper {

    // 保证用户拥有锁行。首次认证会插入；已有记录时 INSERT IGNORE 不报错。
    @Insert("insert ignore into t_session_user_guard(user_id) values(#{userId})")
    int ensureGuardRow(@Param("userId") Long userId);

    // 锁住该用户的 Session 名额分配流程。必须在 @Transactional 方法中调用。
    @Select("select user_id from t_session_user_guard where user_id = #{userId} for update")
    Long selectUserIdForUpdate(@Param("userId") Long userId);
}