package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.TenantDomainOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantDomainOutboxMapper extends BaseMapper<TenantDomainOutbox> {
}
