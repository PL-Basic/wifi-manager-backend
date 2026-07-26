package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.SessionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SessionRecordMapper extends BaseMapper<SessionRecord> {

    @Select("select * from t_session where session_id = #{sessionId} limit 1 for update")
    SessionRecord selectByIdForUpdate(@Param("sessionId")Long sessionId);


    // 统计该用户除当前 MAC 外的活跃 Session。
    // 排除当前 MAC，是为了允许同一客户端更换 ESP32 节点，旧 Session 随后会被 PORTAL_REPLACED 关闭。
    @Select("select count(*) from t_session where user_id = #{userId} and status = 1 and mac <> #{excludedMac}")
    long countActiveSessionsExcludingMac(@Param("userId") Long userId, @Param("excludedMac") String excludedMac);
}
