package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.monitor.Geofence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeofenceMapper extends BaseMapper<Geofence> {

    @Select("select * from t_geofence " +
            "where enabled = 1 and del_flag = 0 " +
            "order by fence_id")
    List<Geofence> selectEnabled();
}