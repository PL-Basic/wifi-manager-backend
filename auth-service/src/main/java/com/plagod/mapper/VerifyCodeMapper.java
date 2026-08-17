package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.VerifyCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface VerifyCodeMapper extends BaseMapper<VerifyCode> {

    /**
     * 短事务锁定最新可用记录，只执行本地状态检查。
     */
    @Select("select * from t_verify_code " +
            "where target = #{target} " +
            "and scene = #{scene} " +
            "and send_status = 1 " +
            "and status = 0 " +
            "order by id desc limit 1 for update")
    VerifyCode selectLatestUsableForUpdate(@Param("target") String target,
                                           @Param("scene") String scene);

    @Select("select * from t_verify_code where id = #{id} for update")
    VerifyCode selectByIdForUpdate(@Param("id") Long id);

    @Update("update t_verify_code " +
            "set verify_claim_owner = #{claimOwner}, " +
            "verify_lease_until = #{leaseUntil}, " +
            "verify_claimed_time = #{claimedTime} " +
            "where id = #{id} " +
            "and send_status = 1 " +
            "and verify_status = 0 " +
            "and status = 0 " +
            "and expire_time > #{claimedTime} " +
            "and (verify_claim_owner is null " +
            "or verify_lease_until <= #{claimedTime})")
    int tryClaimVerification(@Param("id") Long id,
                             @Param("claimOwner") String claimOwner,
                             @Param("claimedTime") LocalDateTime claimedTime,
                             @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("update t_verify_code " +
            "set verify_claim_owner = null, " +
            "verify_lease_until = null, " +
            "verify_claimed_time = null " +
            "where id = #{id} " +
            "and verify_claim_owner = #{claimOwner}")
    int releaseVerificationClaim(@Param("id") Long id,
                                 @Param("claimOwner") String claimOwner);

    @Update("update t_verify_code " +
            "set provider_biz_id = #{record.providerBizId}, " +
            "provider_request_id = #{record.providerRequestId}, " +
            "provider_send_code = #{record.providerSendCode}, " +
            "code_hash = #{record.codeHash}, " +
            "send_status = 1, " +
            "send_time = #{record.sendTime}, " +
            "send_error = #{record.sendError} " +
            "where id = #{record.id} " +
            "and verification_provider = #{record.verificationProvider} " +
            "and provider_out_id <=> #{record.providerOutId} " +
            "and send_status = 0")
    int finalizeSendSuccess(@Param("record") VerifyCode record);

    @Update("update t_verify_code " +
            "set provider_send_code = #{record.providerSendCode}, " +
            "send_status = 2, " +
            "send_time = #{record.sendTime}, " +
            "send_error = #{record.sendError} " +
            "where id = #{record.id} " +
            "and verification_provider = #{record.verificationProvider} " +
            "and provider_out_id <=> #{record.providerOutId} " +
            "and send_status = 0")
    int finalizeSendFailure(@Param("record") VerifyCode record);

    /**
     * 只有已核验、未消费且未过期的记录可以消费成功。
     */
    @Update("update t_verify_code " +
            "set status = 1, " +
            "consume_time = #{consumeTime}, " +
            "consume_request_key = #{consumeRequestKey}, " +
            "verify_ip = #{verifyIp} " +
            "where id = #{id} " +
            "and send_status = 1 " +
            "and verify_status = 1 " +
            "and status = 0 " +
            "and expire_time > #{consumeTime}")
    int consumeVerifiedCode(@Param("id") Long id,
                            @Param("consumeTime") LocalDateTime consumeTime,
                            @Param("verifyIp") String verifyIp,
                            @Param("consumeRequestKey") String consumeRequestKey);

}
