package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.entitlement.TradeStatusLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeStatusLogMapper extends BaseMapper<TradeStatusLog> {

    int insertIgnore(TradeStatusLog log);
}