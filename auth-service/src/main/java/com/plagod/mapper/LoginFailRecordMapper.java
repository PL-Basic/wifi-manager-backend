package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.LoginFailRecord;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface LoginFailRecordMapper extends BaseMapper<LoginFailRecord> {
}
