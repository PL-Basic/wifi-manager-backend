package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.VerifyCode;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface VerifyCodeMapper extends BaseMapper<VerifyCode> {
}
