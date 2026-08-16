package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    int insertOrResolveExisting(RefundRecord refund);

    @Select("select * from t_refund_record " +
            "where refund_no = #{refundNo} limit 1")
    RefundRecord selectByRefundNo(@Param("refundNo") String refundNo);

    @Select("select * from t_refund_record " +
            "where refund_no = #{refundNo} limit 1 for update")
    RefundRecord selectByRefundNoForUpdate(@Param("refundNo") String refundNo);

    @Select("select * from t_refund_record " +
            "where tenant_id = #{tenantId} and refund_no = #{refundNo} limit 1")
    RefundRecord selectByTenantAndRefundNo(
            @Param("tenantId") Long tenantId,
            @Param("refundNo") String refundNo);

    @Select("select * from t_refund_record " +
            "where tenant_id = #{tenantId} and refund_no = #{refundNo} limit 1 for update")
    RefundRecord selectByTenantAndRefundNoForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("refundNo") String refundNo);

    @Select("select * from t_refund_record where tenant_id = #{tenantId} " +
            "and user_id = #{userId} and request_id = #{requestId} limit 1")
    RefundRecord selectByUserRequest(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("requestId") String requestId);

    @Select("select * from t_refund_record " +
            "where tenant_id = #{tenantId} and user_id = #{userId} and request_id = #{requestId} " +
            "limit 1 for update")
    RefundRecord selectByUserRequestForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId,
                                              @Param("requestId") String requestId);

    @Select("select * from t_refund_record " +
            "where tenant_id = #{tenantId} and refund_no = #{refundNo} and user_id = #{userId} limit 1")
    RefundRecord selectOwnedRefund(@Param("tenantId") Long tenantId,
                                   @Param("refundNo") String refundNo,
                                   @Param("userId") Long userId);

    @Select("select * from t_refund_record " +
            "where tenant_id = #{tenantId} and order_no = #{orderNo} " +
            "and status in ('REQUESTED', 'PROCESSING') " +
            "order by refund_id desc limit 1 for update")
    RefundRecord selectActiveByOrderForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("orderNo") String orderNo);
}
