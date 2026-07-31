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
     * 核验事务必须锁住最新记录，防止两个请求同时调用供应商核验。
     */
    @Select("select * from t_verify_code " +
            "where target = #{target} " +
            "and scene = #{scene} " +
            "and send_status = 1 " +
            "and status = 0 " +
            "order by id desc limit 1 for update")
    VerifyCode selectLatestUsableForUpdate(@Param("target") String target,
                                           @Param("scene") String scene);

    /**
     * 只有已核验、未消费且未过期的记录可以消费成功。
     */
    @Update("update t_verify_code " +
            "set status = 1, " +
            "consume_time = #{consumeTime}, " +
            "verify_ip = #{verifyIp} " +
            "where id = #{id} " +
            "and send_status = 1 " +
            "and verify_status = 1 " +
            "and status = 0 " +
            "and expire_time > #{consumeTime}")
    int consumeVerifiedCode(@Param("id") Long id,
                            @Param("consumeTime") LocalDateTime consumeTime,
                            @Param("verifyIp") String verifyIp);
}