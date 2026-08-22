package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DefaultTenantMembershipOutboxMapper
        extends BaseMapper<DefaultTenantMembershipOutbox> {

    @Update("update t_default_tenant_membership_outbox "
            + "set status = 'DEAD', next_retry_time = null, "
            + "worker_id = null, lease_until = null, "
            + "last_error = coalesce(last_error, #{lastError}) "
            + "where retry_count >= #{maxRetryCount} and ("
            + "status in ('PENDING', 'RETRY') "
            + "or (status = 'PROCESSING' and lease_until < #{now}))")
    int finalizeExhausted(
            @Param("now") LocalDateTime now,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("lastError") String lastError);

    @Select("select outbox_id from t_default_tenant_membership_outbox "
            + "where retry_count < #{maxRetryCount} and ("
            + "(status in ('PENDING', 'RETRY') and next_retry_time <= #{now}) "
            + "or (status = 'PROCESSING' and lease_until < #{now})) "
            + "order by next_retry_time, outbox_id limit #{limit}")
    List<Long> selectDispatchableIds(
            @Param("now") LocalDateTime now,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("limit") int limit);

    @Update("update t_default_tenant_membership_outbox "
            + "set status = 'PROCESSING', worker_id = #{workerId}, "
            + "claimed_time = #{claimedTime}, lease_until = #{leaseUntil} "
            + "where outbox_id = #{outboxId} "
            + "and retry_count < #{maxRetryCount} and ("
            + "(status in ('PENDING', 'RETRY') "
            + "and next_retry_time <= #{claimedTime}) "
            + "or (status = 'PROCESSING' "
            + "and lease_until < #{claimedTime}))")
    int claim(
            @Param("outboxId") Long outboxId,
            @Param("workerId") String workerId,
            @Param("claimedTime") LocalDateTime claimedTime,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("maxRetryCount") int maxRetryCount);

    @Update("update t_default_tenant_membership_outbox "
            + "set status = 'SUCCEEDED', next_retry_time = null, "
            + "worker_id = null, lease_until = null, last_error = null "
            + "where outbox_id = #{outboxId} and status = 'PROCESSING' "
            + "and worker_id = #{workerId}")
    int finalizeSucceeded(
            @Param("outboxId") Long outboxId,
            @Param("workerId") String workerId);

    @Update("update t_default_tenant_membership_outbox set "
            + "status = case when retry_count + 1 >= #{maxRetryCount} "
            + "then 'DEAD' else 'RETRY' end, "
            + "next_retry_time = case when retry_count + 1 >= #{maxRetryCount} "
            + "then null else #{nextRetryTime} end, "
            + "worker_id = null, lease_until = null, "
            + "last_error = #{lastError}, retry_count = retry_count + 1 "
            + "where outbox_id = #{outboxId} and status = 'PROCESSING' "
            + "and worker_id = #{workerId}")
    int finalizeFailed(
            @Param("outboxId") Long outboxId,
            @Param("workerId") String workerId,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("maxRetryCount") int maxRetryCount);
}
