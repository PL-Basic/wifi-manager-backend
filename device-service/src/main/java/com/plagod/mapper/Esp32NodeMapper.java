package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.Esp32Node;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface Esp32NodeMapper extends BaseMapper<Esp32Node> {

    Esp32Node selectByDeviceCodeIncludeDeleted(@Param("deviceCode") String deviceCode);
}
