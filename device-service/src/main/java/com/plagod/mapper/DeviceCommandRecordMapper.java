package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.device.DeviceCommandRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeviceCommandRecordMapper extends BaseMapper<DeviceCommandRecord> {

    // 处理 command-result 时锁定命令，防止重复 MQTT 消息并发修改。
    @Select("select * from t_device_command where tenant_id = #{tenantId} and request_id = #{requestId} limit 1 for update")
    DeviceCommandRecord selectByRequestIdForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("requestId") String requestId);

    // Dispatcher 发布时锁住命令。
    // 快速 command-result 会等待本事务把状态改成 PUBLISHED。
    @Select("select * from t_device_command where command_id = #{commandId} limit 1 for update")
    DeviceCommandRecord selectByCommandIdForUpdate(@Param("commandId") Long commandId);

    @Select("select * from t_device_command "
            + "where tenant_id = #{tenantId} "
            + "and actor_user_id = #{actorUserId} "
            + "and purpose = 'PORTAL_AUTHORIZE' "
            + "and client_request_id = #{clientRequestId} "
            + "limit 1")
    DeviceCommandRecord selectPortalAuthorizationReceipt(
            @Param("tenantId") Long tenantId,
            @Param("actorUserId") Long actorUserId,
            @Param("clientRequestId") String clientRequestId);

    @Update("update t_device_command set "
            + "dispatch_worker_id = #{workerId}, "
            + "dispatch_claimed_time = #{now}, "
            + "dispatch_lease_until = #{leaseUntil}, "
            + "retry_count = coalesce(retry_count, 0) + 1, "
            + "update_time = #{now} "
            + "where command_id = #{commandId} "
            + "and status = #{pendingStatus} "
            + "and (next_retry_time is null or next_retry_time <= #{now}) "
            + "and (dispatch_lease_until is null or dispatch_lease_until <= #{now}) "
            + "and coalesce(retry_count, 0) < #{publishMaxAttempts}")
    int claimForDispatch(
            @Param("commandId") Long commandId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("publishMaxAttempts") Integer publishMaxAttempts);

    @Update("update t_device_command set "
            + "status = #{publishedStatus}, "
            + "publish_time = #{now}, "
            + "deadline_time = #{deadlineTime}, "
            + "next_retry_time = null, "
            + "dispatch_worker_id = null, "
            + "dispatch_lease_until = null, "
            + "dispatch_claimed_time = null, "
            + "result_message = null, "
            + "update_time = #{now} "
            + "where command_id = #{commandId} "
            + "and status = #{pendingStatus} "
            + "and dispatch_worker_id = #{workerId} "
            + "and dispatch_lease_until > #{now}")
    int finalizePublished(
            @Param("commandId") Long commandId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("deadlineTime") LocalDateTime deadlineTime,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("publishedStatus") Integer publishedStatus);

    @Update("update t_device_command set "
            + "status = #{nextStatus}, "
            + "next_retry_time = #{nextRetryTime}, "
            + "dispatch_worker_id = null, "
            + "dispatch_lease_until = null, "
            + "dispatch_claimed_time = null, "
            + "publish_time = null, "
            + "deadline_time = null, "
            + "result_time = #{resultTime}, "
            + "result_message = #{resultMessage}, "
            + "update_time = #{now} "
            + "where command_id = #{commandId} "
            + "and status = #{pendingStatus} "
            + "and dispatch_worker_id = #{workerId} "
            + "and dispatch_lease_until > #{now}")
    int finalizePublishFailure(
            @Param("commandId") Long commandId,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("nextStatus") Integer nextStatus,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("resultTime") LocalDateTime resultTime,
            @Param("resultMessage") String resultMessage,
            @Param("pendingStatus") Integer pendingStatus);

    @Update("update t_device_command set "
            + "status = #{targetStatus}, "
            + "dispatch_worker_id = null, "
            + "dispatch_lease_until = null, "
            + "dispatch_claimed_time = null, "
            + "next_retry_time = null, "
            + "result_time = #{now}, "
            + "result_message = #{resultMessage}, "
            + "update_time = #{now} "
            + "where command_id = #{commandId} "
            + "and tenant_id = #{tenantId} "
            + "and (status = #{publishedStatus} "
            + "or (status = #{pendingStatus} "
            + "and dispatch_worker_id is not null "
            + "and dispatch_lease_until is not null))")
    int finalizeFromCommandResult(
            @Param("commandId") Long commandId,
            @Param("tenantId") Long tenantId,
            @Param("targetStatus") Integer targetStatus,
            @Param("publishedStatus") Integer publishedStatus,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("now") LocalDateTime now,
            @Param("resultMessage") String resultMessage);

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

    // command-result 提交后即使进程崩溃，成功撤销记录仍可重新驱动等待中的替换 Session。
    @Select("select c.tenant_id, c.session_id, min(c.command_id) as command_id "
            + "from t_device_command c "
            + "inner join t_session s "
            + "on s.tenant_id = c.tenant_id "
            + "and s.replaced_session_id = c.session_id "
            + "and s.status = #{waitingStatus} "
            + "where c.command_type = 'REVOKE_ACCESS' "
            + "and c.purpose = 'FORCE_LOGIN_REPLACE' "
            + "and c.status = #{succeededStatus} "
            + "group by c.tenant_id, c.session_id "
            + "order by command_id asc limit #{limit}")
    List<DeviceCommandRecord> selectRecoverableForceReplacementCommands(
            @Param("succeededStatus") Integer succeededStatus,
            @Param("waitingStatus") Integer waitingStatus,
            @Param("limit") Integer limit);

    // 查询该 Session 最新的 Portal 或续租 ALLOW。
    // command_id 自增，因此最大 command_id 代表最后入队的命令。
    @Select("select command_id from t_device_command " +
            "where tenant_id = #{tenantId} " +
            "and session_id = #{sessionId} " +
            "and command_type = 'ALLOW' " +
            "and purpose in ('PORTAL_AUTHORIZE', 'LEASE_RENEW') " +
            "order by command_id desc limit 1")
    Long selectLatestSessionAllowCommandId(@Param("tenantId") Long tenantId,
                                           @Param("sessionId") Long sessionId);

    // 撤销命令发布前，检查同一 Session 是否还有更早入队、尚未发布的 ALLOW。
    // ALLOW 一旦已经进入 PUBLISHED，说明 MQTT Broker 已先收到它，REVOKE 可以随后发布。
    @Select("select count(*) from t_device_command " +
            "where tenant_id = #{tenantId} " +
            "and session_id = #{sessionId} " +
            "and command_id < #{commandId} " +
            "and command_type = 'ALLOW' " +
            "and purpose in ('PORTAL_AUTHORIZE', 'LEASE_RENEW') " +
            "and status = #{pendingStatus}")
    long countEarlierPendingSessionAllowCommands(@Param("tenantId") Long tenantId,
                                                  @Param("sessionId") Long sessionId,
                                                  @Param("commandId") Long commandId,
                                                  @Param("pendingStatus") Integer pendingStatus);

    // Portal 查询当前 Session 最新的授权或续租命令。
    @Select("select * from t_device_command " +
            "where tenant_id = #{tenantId} " +
            "and session_id = #{sessionId} " +
            "and command_type = 'ALLOW' " +
            "and purpose in ('PORTAL_AUTHORIZE', 'LEASE_RENEW') " +
            "order by command_id desc limit 1")
    DeviceCommandRecord selectLatestSessionAllowCommand(@Param("tenantId") Long tenantId,
                                                         @Param("sessionId") Long sessionId);

    // 新 Session 尚未生成 ALLOW 时，查询它等待的旧 Session 撤销命令。
    @Select("select * from t_device_command " +
            "where tenant_id = #{tenantId} " +
            "and session_id = #{replacedSessionId} " +
            "and command_type = 'REVOKE_ACCESS' " +
            "and purpose = 'FORCE_LOGIN_REPLACE' " +
            "order by command_id desc limit 1")
    DeviceCommandRecord selectLatestForceReplacementCommand(@Param("tenantId") Long tenantId,
                                                             @Param("replacedSessionId") Long replacedSessionId);

    /**
     * 命令终结后清除加密载荷，减少凭据在数据库中的保留时间。
     */
    @Update("update t_device_command " +
            "set encrypted_payload = null, " +
            "update_time = #{now} " +
            "where command_id = #{commandId} " +
            "and tenant_id = #{tenantId} " +
            "and encrypted_payload is not null")
    int clearEncryptedPayload(@Param("tenantId") Long tenantId,
                              @Param("commandId") Long commandId,
                              @Param("now") LocalDateTime now);
}
