package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.monitor.AccessRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccessRuleMapper extends BaseMapper<AccessRule> {

    @Select("select * from t_access_rule "
            + "where tenant_id = #{tenantId} and id = #{id} and del_flag = 0 limit 1")
    AccessRule selectByIdAndTenant(@Param("tenantId") Long tenantId,
                                   @Param("id") Long id);

    @Select("select * from t_access_rule "
            + "where tenant_id = #{tenantId} and id = #{id} limit 1 for update")
    AccessRule selectByIdAndTenantForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("id") Long id);

    @Select("select * from t_access_rule "
            + "where tenant_id = #{tenantId} and rule_code = #{ruleCode} limit 1")
    AccessRule selectByCodeAndTenant(@Param("tenantId") Long tenantId,
                                     @Param("ruleCode") String ruleCode);

    @Update("update t_access_rule set enabled = #{enabled}, version = version + 1 "
            + "where tenant_id = #{tenantId} and id = #{id} "
            + "and version = #{expectedVersion} and del_flag = 0")
    int updateEnabledByTenantAndVersion(@Param("tenantId") Long tenantId,
                                        @Param("id") Long id,
                                        @Param("enabled") Integer enabled,
                                        @Param("expectedVersion") Integer expectedVersion);
}
