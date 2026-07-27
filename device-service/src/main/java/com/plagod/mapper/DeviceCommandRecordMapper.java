package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.DeviceCommandRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeviceCommandRecordMapper extends BaseMapper<DeviceCommandRecord> {

    // 处理 command-result 时锁定命令，防止重复 MQTT 消息并发修改。
    @Select("select * from t_device_command where request_id = #{requestId} limit 1 for update")
    DeviceCommandRecord selectByRequestIdForUpdate(@Param("requestId") String requestId);

    // Dispatcher 发布时锁住命令。
    // 快速 command-result 会等待本事务把状态改成 PUBLISHED。
    @Select("select * from t_device_command where command_id = #{commandId} limit 1 for update")
    DeviceCommandRecord selectByCommandIdForUpdate(@Param("commandId") Long commandId);

    // 只扫描当前到期、可以尝试发布的 PENDING 命令。
    @Select("select command_id from t_device_command " +
            "where status = #{status} " +
            "and (next_retry_time is null or next_retry_time <= #{now}) " +
            "order by command_id asc limit #{limit}")
    List<Long> selectDispatchableCommandIds(@Param("status") Integer status, @Param("now") LocalDateTime now, @Param("limit") Integer limit);

    // 扫描已经超过 command-result 截止时间的 PUBLISHED 命令。
    @Select("select command_id from t_device_command " +
            "where status = #{status} " +
            "and deadline_time is not null " +
            "and deadline_time <= #{now} " +
            "order by command_id asc limit #{limit}")
    List<Long> selectTimedOutCommandIds(@Param("status") Integer status, @Param("now") LocalDateTime now, @Param("limit") Integer limit);

    // 查询该 Session 最新的 Portal 或续租 ALLOW。
    // command_id 自增，因此最大 command_id 代表最后入队的命令。
    @Select("select command_id from t_device_command " +
            "where session_id = #{sessionId} " +
            "and command_type = 'ALLOW' " +
            "and purpose in ('PORTAL_AUTHORIZE', 'LEASE_RENEW') " +
            "order by command_id desc limit 1")
    Long selectLatestSessionAllowCommandId(@Param("sessionId") Long sessionId);

    // 撤销命令发布前，检查同一 Session 是否还有更早入队、尚未发布的 ALLOW。
    // ALLOW 一旦已经进入 PUBLISHED，说明 MQTT Broker 已先收到它，REVOKE 可以随后发布。
    @Select("select count(*) from t_device_command " +
            "where session_id = #{sessionId} " +
            "and command_id < #{commandId} " +
            "and command_type = 'ALLOW' " +
            "and purpose in ('PORTAL_AUTHORIZE', 'LEASE_RENEW') " +
            "and status = #{pendingStatus}")
    long countEarlierPendingSessionAllowCommands(@Param("sessionId") Long sessionId, @Param("commandId") Long commandId, @Param("pendingStatus") Integer pendingStatus);
}