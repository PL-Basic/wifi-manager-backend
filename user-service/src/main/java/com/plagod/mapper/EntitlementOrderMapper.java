package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.EntitlementOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EntitlementOrderMapper extends BaseMapper<EntitlementOrder> {


    int insertOrResolveExisting(EntitlementOrder order);

    @Select("select * from t_entitlement_order where user_id = #{userId} and client_request_id = #{clientRequestId} limit 1 for update")
    EntitlementOrder selectByUserRequestForUpdate(@Param("userId") Long userId, @Param("clientRequestId") String clientRequestId);

    @Select("select * from t_entitlement_order where order_no = #{orderNo} limit 1 for update")
    EntitlementOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("select * from t_entitlement_order where order_no = #{orderNo} and user_id = #{userId} limit 1")
    EntitlementOrder selectOwnedOrder(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Select("select order_no from t_entitlement_order where status = 'PENDING_PAYMENT' and expire_time <= #{now} order by expire_time limit #{limit}")
    List<String> selectExpiredPendingOrderNos(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("update t_entitlement_order " +
            "set status = #{targetStatus}, close_time = #{closeTime}, " +
            "close_reason = #{closeReason}, version = version + 1, " +
            "update_time = current_timestamp " +
            "where order_no = #{orderNo} and status = #{expectedStatus}")
    int closePendingOrder(@Param("orderNo") String orderNo,
                          @Param("expectedStatus") String expectedStatus,
                          @Param("targetStatus") String targetStatus,
                          @Param("closeTime") LocalDateTime closeTime,
                          @Param("closeReason") String closeReason);
}