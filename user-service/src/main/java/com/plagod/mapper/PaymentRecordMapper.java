package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.PaymentRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    int insertOrResolveExisting(PaymentRecord payment);

    @Select("select * from t_payment_record where order_no = #{orderNo} limit 1 for update")
    PaymentRecord selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("select * from t_payment_record where user_id = #{userId} and request_id = #{requestId} limit 1 for update")
    PaymentRecord selectByUserRequestForUpdate(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("select * from t_payment_record where payment_no = #{paymentNo} and user_id = #{userId} limit 1")
    PaymentRecord selectOwnedPayment(@Param("paymentNo") String paymentNo, @Param("userId") Long userId);

    @Select("select p.* from t_payment_record p " +
            "inner join t_entitlement_order o on o.order_no = p.order_no " +
            "where p.user_id = #{userId} and p.status = 'CREATED' " +
            "and o.entitlement_mode <> #{targetMode} limit 1")
    PaymentRecord selectCreatedOtherModePayment(@Param("userId") Long userId, @Param("targetMode") String targetMode);

    @Select("select * from t_payment_record " +
            "where business_key = #{businessKey} limit 1")
    PaymentRecord selectByBusinessKey(@Param("businessKey") String businessKey);

    @Select("select * from t_payment_record " +
            "where payment_no = #{paymentNo} limit 1 for update")
    PaymentRecord selectByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    @Select("select * from t_payment_record " +
            "where channel = #{channel} and callback_event_id = #{eventId} limit 1")
    PaymentRecord selectByChannelEvent(@Param("channel") String channel, @Param("eventId") String eventId);

    @Select("select * from t_payment_record " +
            "where channel = #{channel} " +
            "and channel_transaction_no = #{transactionNo} limit 1")
    PaymentRecord selectByChannelTransaction(@Param("channel") String channel, @Param("transactionNo") String transactionNo);
}