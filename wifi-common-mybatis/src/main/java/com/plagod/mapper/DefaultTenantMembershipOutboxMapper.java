package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.DefaultTenantMembershipOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DefaultTenantMembershipOutboxMapper extends BaseMapper<DefaultTenantMembershipOutbox> {
}
