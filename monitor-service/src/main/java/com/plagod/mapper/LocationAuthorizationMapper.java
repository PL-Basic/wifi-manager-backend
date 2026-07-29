package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.LocationAuthorization;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LocationAuthorizationMapper
        extends BaseMapper<LocationAuthorization> {

    // 首次授权或上报前保证用户拥有一行可加锁的授权记录。
    @Insert("insert ignore into t_location_authorization (user_id, enabled, create_time, update_time) values (#{userId}, 0, current_timestamp, current_timestamp)")
    int ensureAuthorizationRow(@Param("userId") Long userId);

    // 串行处理同一用户的授权状态和位置上报。
    @Select("select * from t_location_authorization where user_id = #{userId} for update")
    LocationAuthorization selectByUserIdForUpdate(@Param("userId") Long userId);
}