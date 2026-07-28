package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.SessionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SessionRecordMapper extends BaseMapper<SessionRecord> {

    @Select("select * from t_session where session_id = #{sessionId} limit 1 for update")
    SessionRecord selectByIdForUpdate(@Param("sessionId") Long sessionId);

    // WAITING_REPLACEMENT 也占用连接名额。
    @Select("select count(*) from t_session where user_id = #{userId} and status in (1, 2, 3) and mac <> #{excludedMac}")
    long countAllocatedSessionsExcludingMac(@Param("userId") Long userId, @Param("excludedMac") String excludedMac);

    // 锁住最旧的真实开放 Session，作为强制登录替换目标。
    @Select("select * from t_session "
            + "where user_id = #{userId} "
            + "and status in (1, 2) "
            + "and mac <> #{excludedMac} "
            + "order by login_time asc, session_id asc "
            + "limit 1 for update")
    SessionRecord selectOldestOpenSessionForUpdate(@Param("userId") Long userId, @Param("excludedMac") String excludedMac);

    // command-result 到达时锁住等待该旧 Session 的新 Session。
    @Select("select * from t_session where replaced_session_id = #{replacedSessionId} and status = 3 order by session_id asc limit 1 for update")
    SessionRecord selectWaitingReplacementForUpdate(@Param("replacedSessionId") Long replacedSessionId);

    // 按固定顺序锁住该 MAC 的全部已分配 Session，避免批量关闭产生死锁。
    @Select("select * from t_session where mac = #{mac} and status in (1, 2, 3) order by session_id asc for update")
    List<SessionRecord> selectAllocatedByMacForUpdate(@Param("mac") String mac);
}