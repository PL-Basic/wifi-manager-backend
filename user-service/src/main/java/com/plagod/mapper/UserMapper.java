package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.user.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select * from sys_user where user_id = #{userId} limit 1 for update")
    User selectByIdForUpdate(@Param("userId") Long userId);

    /**
     * 绕过 MyBatis-Plus 逻辑删除过滤，防止已删除账号被社交登录接管。
     */
    @Select("select * from sys_user where user_id = #{userId} limit 1")
    User selectByIdIncludingDeleted(@Param("userId") Long userId);

    @Select("select * from sys_user where email = #{email} limit 1")
    User selectByEmailIncludingDeleted(@Param("email") String email);

    /**
     * 锁定邮箱对应账号；查询不到时由数据库唯一约束处理并发插入。
     */
    @Select("select * from sys_user where email = #{email} limit 1 for update")
    User selectByEmailIncludingDeletedForUpdate(@Param("email") String email);
}