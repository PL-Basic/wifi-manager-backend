package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.dto.*;
import com.plagod.entity.User;
import com.plagod.enums.ConflictFieldEnum;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.mapper.UserMapper;
import com.plagod.service.UserService;
import com.plagod.utils.JwtUtils;
import com.plagod.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    @Audited(action = "auth.register")
    public RegisterResult register(RegisterDTO registerDTO){
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("del_flag", 0);

        queryWrapper.and(wrapper->{
            //用户名一定会被输入因此直接作为判断规则
            wrapper.eq("username", registerDTO.getUsername());
            //邮箱和手机号都是可选且唯一，因此先进行判断是否存在
            //如果存在则作为判断规则
            if (StringUtils.hasText(registerDTO.getEmail())){
                wrapper.or().eq("email", registerDTO.getEmail());
            }
            if (StringUtils.hasText(registerDTO.getPhone())){
                wrapper.or().eq("phone", registerDTO.getPhone());
            }
        });

        //查询数据库中与之冲突的账户
        List<User> users = userMapper.selectList(queryWrapper);
        Set<ConflictFieldEnum> conflictField = EnumSet.noneOf(ConflictFieldEnum.class);
        //收集冲突的字段
        if (!users.isEmpty()) {
            for (User user : users) {
                if (Objects.equals(user.getUsername(), registerDTO.getUsername())) {
                    conflictField.add(ConflictFieldEnum.USERNAME);
                }
                if (StringUtils.hasText(user.getEmail())
                        && user.getEmail().equals(registerDTO.getEmail())) {
                    conflictField.add(ConflictFieldEnum.EMAIL);
                }
                if (StringUtils.hasText(user.getPhone())
                        && user.getPhone().equals(registerDTO.getPhone())) {
                    conflictField.add(ConflictFieldEnum.PHONE);
                }
            }
            return RegisterResult.conflict(conflictField);
        }

        //进行注册
        User user = new User();
        //直接将dto中的数据拷贝到user,并且忽略重要属性项
        BeanUtils.copyProperties(registerDTO, user,"role","status","delFlag");
        user.setRole(2);
        user.setStatus(1);
        user.setDelFlag(0);
        user.setPassword(PasswordUtils.encode(registerDTO.getPassword()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 记录日志供运维排查
            log.warn("注册并发冲突，用户信息：{}", registerDTO.getUsername(), e);
            return RegisterResult.conflict(EnumSet.noneOf(ConflictFieldEnum.class), "注册信息冲突，请稍后重试");
        }
        return RegisterResult.success();
    }


    @Override
    public LoginResult login(LoginDTO loginDTO){
        //判断使用的是什么登录，写出指定规则
        String account = loginDTO.getAccount();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("del_flag", 0);
        queryWrapper.and(wrapper->{
            wrapper.eq("username", account)
                    .or().eq("email", account)
                    .or().eq("phone", account);
        });
        //使用指定规则找出账号
        User user = userMapper.selectOne(queryWrapper);
        //判断输入的内容是否是正确的，存在的
        if (user == null) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_NOT_FOUND,"账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_DISABLED,"账号被禁用");
        }
        if(!PasswordUtils.matches(loginDTO.getPassword(),user.getPassword())){
            return LoginResult.fail(LoginStatusEnum.PASSWORD_ERROR,"密码错误");
        }

        //获取token，判断用的参数
        String token = JwtUtils.generateToken(user.getUserId(),user.getUsername(),user.getRole());

        //将该用户的信息取出并返回到controller
        AuthResultDTO authResultDTO = new AuthResultDTO();
        authResultDTO.setToken(token);
        authResultDTO.setUsername(user.getUsername());
        authResultDTO.setNickname(user.getNickname());
        authResultDTO.setAvatar(user.getAvatar());

        return LoginResult.success(authResultDTO);
    }


}
