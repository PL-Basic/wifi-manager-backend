package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.DurationPurchase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DurationPurchaseMapper extends BaseMapper<DurationPurchase> {

    // 必须在事务中调用，按照购买时间依次消费
    @Select("select * from t_duration_purchase " +
            "where user_id = #{userId} and status = 1 " +
            "and remaining_seconds > 0 " +
            "order by create_time, purchase_id for update")
    List<DurationPurchase> selectUsableLotsForUpdate(@Param("userId") Long userId);

    @Select("select * from t_duration_purchase where purchase_id = #{purchaseId} limit 1 for update")
    DurationPurchase selectByIdForUpdate(@Param("purchaseId") Long purchaseId);

    @Select("select * from t_duration_purchase where order_no = #{orderNo} limit 1")
    DurationPurchase selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("select * from t_duration_purchase " +
            "where user_id = #{userId} and status = 4 " +
            "order by purchase_id limit 1 for update")
    DurationPurchase selectRefundReservedByUserForUpdate(@Param("userId") Long userId);
}
