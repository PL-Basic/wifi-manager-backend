package com.plagod.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MonitorHealthMapper {

    @Select("SELECT 1")
    Integer ping();
}