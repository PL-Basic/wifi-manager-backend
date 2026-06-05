package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.dto.*;
import com.plagod.entity.User;
import com.plagod.enums.ConflictFieldEnum;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.mapper.UserMapper;
import com.plagod.service.UserService;
import com.plagod.service.VerificationCodeService;
import com.plagod.utils.JwtUtils;
import com.plagod.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    @Transactional
    @Audited(action = "auth.register")
    public RegisterResult register(RegisterDTO registerDTO,String verifyIp){
        RegisterResult checkResult = checkRegisterContact(registerDTO);
        if (checkResult != null){
            return checkResult;
        }

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
                if (Objects.equals(user.getUsername(), registerDTO.getUsername())){
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
        BeanUtils.copyProperties(registerDTO, user,"role","status","delFlag", "emailCode", "phoneCode");
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

        consumeRegisterContact(registerDTO,verifyIp);

        return RegisterResult.success();
    }


    @Override
    public LoginResult login(LoginDTO loginDTO){
        //判断使用的是什么登录
        String account = loginDTO.getAccount();
        String loginType = loginDTO.getLoginType();
        User user = new User();
        if("username".equals(loginType)){
            user = findByField("username", account);
        }else if("contact".equals(loginType)){
            user = findContactLoginUser(account);
        }else {
            throw new RuntimeException("登录类型错误");
        }


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


        return buildLoginResult(user);
    }

    //验证码登录
    @Override
    public LoginResult loginByVerifyCode(LoginByVerifyCodeDTO loginByVerifyCodeDTO, String verifyIp) {
        String target = loginByVerifyCodeDTO.getTarget();

        try {
            //先验证验证码
            verificationCodeService.consumeCode(target,"login", loginByVerifyCodeDTO.getCode(),verifyIp);
        } catch (IllegalArgumentException e) {
            return LoginResult.fail(LoginStatusEnum.PASSWORD_ERROR,e.getMessage());
        }

        //再判断账号是否存在，避免泄露账号信息
        User user = findContactLoginUser(target);
        if (user == null) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_NOT_FOUND,"账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())){
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_DISABLED,"账号被禁用");
        }

        return buildLoginResult(user);
    }



    @Override
    @Transactional
    @Audited(action = "auth.reset_password")
    public void resetPassword(ResetPasswordDTO resetPasswordDTO, String verifyIp) {

        verificationCodeService.checkCode(resetPasswordDTO.getTarget(),"reset_password",resetPasswordDTO.getCode());

        User user = findContactLoginUser(resetPasswordDTO.getTarget());

        if (user == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())){
            throw new IllegalArgumentException("账号已被禁用");
        }

        user.setPassword(PasswordUtils.encode(resetPasswordDTO.getNewPassword()));
        userMapper.updateById(user);

        verificationCodeService.consumeCode(
                resetPasswordDTO.getTarget(),
                "reset_password",
                resetPasswordDTO.getCode(),
                verifyIp
        );


    }

    private boolean isPhone(String value) {
        return value != null && PHONE_PATTERN.matcher(value).matches();
    }

    private boolean isEmail(String value) {
        return value != null && EMAIL_PATTERN.matcher(value).matches();
    }
    //处理账号类型的方法。
//    private User findLoginUser(String account) {
//        if (isPhone(account)) {
//            User user = findByField("phone", account);
//            return user != null ? user : findByField("username", account);
//        }
//        if (isEmail(account)) {
//            User user = findByField("email", account);
//            return user != null ? user : findByField("username", account);
//        }
//        return findByField("username", account);
//    }

    private User findByField(String field, String value) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("del_flag", 0);
        queryWrapper.eq(field, value);
        return userMapper.selectOne(queryWrapper);
    }

    private User findContactLoginUser(String account) {
        if (isPhone(account)) {
            return findByField("phone",account);
        }
        if (isEmail(account)) {
            return findByField("email",account);
        }
        return null;
    }

    private LoginResult buildLoginResult(User user) {
        //获取token，判断用的参数
        String token = jwtUtils.generateToken(user.getUserId(),user.getUsername(),user.getRole());

        //将该用户的信息取出并返回到controller
        AuthResultDTO authResultDTO = new AuthResultDTO();
        authResultDTO.setToken(token);
        authResultDTO.setUsername(user.getUsername());
        authResultDTO.setRole(user.getRole());
        authResultDTO.setNickname(user.getNickname());
        authResultDTO.setAvatar(user.getAvatar());

        return LoginResult.success(authResultDTO);
    }

    //判断是否填入邮箱和手机号，并进行验证
    private RegisterResult checkRegisterContact(RegisterDTO registerDTO) {
        if(StringUtils.hasText(registerDTO.getEmail())){
            if (!StringUtils.hasText(registerDTO.getEmailCode())) return RegisterResult.fail("请输入邮箱验证码");
            try {
                verificationCodeService.checkCode(
                        registerDTO.getEmail(),
                        "register",
                        registerDTO.getEmailCode()
                );
            } catch (IllegalArgumentException e) {
                return RegisterResult.fail(e.getMessage());
            }
        }
        if (StringUtils.hasText(registerDTO.getPhone())){
            if (!StringUtils.hasText(registerDTO.getPhoneCode())) return RegisterResult.fail("请输入手机验证码");

            try {
                verificationCodeService.checkCode(
                        registerDTO.getPhone(),
                        "register",
                        registerDTO.getPhoneCode()
                );
            } catch (IllegalArgumentException e) {
                return RegisterResult.fail(e.getMessage());
            }
        }
        return null;
    }

    private void consumeRegisterContact(RegisterDTO registerDTO, String verifyIp) {
        if(StringUtils.hasText(registerDTO.getEmail())){
            verificationCodeService.consumeCode(
                    registerDTO.getEmail(),
                    "register",
                    registerDTO.getEmailCode(),
                    verifyIp
            );
        }
        if (StringUtils.hasText(registerDTO.getPhone())){
            verificationCodeService.consumeCode(
                        registerDTO.getPhone(),
                        "register",
                        registerDTO.getPhoneCode(),
                        verifyIp
            );
        }
    }

}
