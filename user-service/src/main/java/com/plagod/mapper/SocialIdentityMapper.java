package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.user.SocialIdentity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SocialIdentityMapper extends BaseMapper<SocialIdentity> {

    @Select("select * from t_social_identity where provider = #{provider} and provider_subject = #{providerSubject} limit 1 for update")
    SocialIdentity selectBySubjectForUpdate(@Param("provider") String provider, @Param("providerSubject") String providerSubject);

    @Select("select * from t_social_identity where provider = #{provider} and provider_union_id = #{providerUnionId} limit 1 for update")
    SocialIdentity selectByUnionIdForUpdate(@Param("provider") String provider, @Param("providerUnionId") String providerUnionId);

    @Select("select * from t_social_identity where user_id = #{userId} and provider = #{provider} limit 1 for update")
    SocialIdentity selectByUserAndProviderForUpdate(@Param("userId") Long userId, @Param("provider") String provider);

    @Select("select * from t_social_identity where identity_id = #{identityId} and user_id = #{userId} limit 1 for update")
    SocialIdentity selectOwnedByIdForUpdate(@Param("identityId") Long identityId, @Param("userId") Long userId);

    @Select("select * from t_social_identity where user_id = #{userId} order by bind_time asc, identity_id asc")
    List<SocialIdentity> selectByUserId(@Param("userId") Long userId);

    @Select("select count(*) from t_social_identity where user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Delete("delete from t_social_identity where identity_id = #{identityId} and user_id = #{userId}")
    int physicalDeleteOwned(@Param("identityId") Long identityId, @Param("userId") Long userId);

    @Delete("delete from t_social_identity where user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}