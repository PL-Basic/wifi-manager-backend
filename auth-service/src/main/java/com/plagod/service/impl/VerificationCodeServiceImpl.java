package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.entity.VerifyCode;
import com.plagod.mapper.VerifyCodeMapper;
import com.plagod.sender.VerifyCodeSender;
import com.plagod.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;


@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {
    //手机和邮箱的正则格式
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int CODE_LENGTH = 6;
    //过期时间5分钟
    private static final int EXPIRE_MINUTES = 5;
    //生成验证码的字符集
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private VerifyCodeMapper verifyCodeMapper;

    @Autowired
    private VerifyCodeSender verifyCodeSender;


    //发送code给请求IP
    @Override
    public void sendCode(String target, String scene, String sendIp) {
        String cleanTarget = cleanTarget(target);
        String targetType = resolveTarget(cleanTarget);

        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();

        VerifyCode verifyCode = new VerifyCode();
        verifyCode.setTarget(cleanTarget);
        verifyCode.setTargetType(targetType);
        verifyCode.setScene(scene);
        verifyCode.setCode(code);
        verifyCode.setStatus(0);
        verifyCode.setExpireTime(now.plusMinutes(EXPIRE_MINUTES));
        verifyCode.setSendIp(sendIp);

        verifyCodeMapper.insert(verifyCode);
        verifyCodeSender.send(cleanTarget,targetType,scene,code);
    }


    //检查验证码
    @Override
    public void checkCode(String target, String scene, String code) {
        findValidCode(target, scene, code);
    }

    //消费验证码
    @Override
    public void consumeCode(String target, String scene, String code, String verifyIp) {
        VerifyCode verifyCode =  findValidCode(target, scene, code);
        LocalDateTime now = LocalDateTime.now();

        verifyCode.setStatus(1);
        verifyCode.setVerifyIp(verifyIp);
        verifyCode.setVerifyTime(now);

        verifyCodeMapper.updateById(verifyCode);
    }

    //清洗target
    private String cleanTarget(String text){
        if(!StringUtils.hasText(text)){
            throw new IllegalArgumentException("手机号或者邮箱不能为空");
        }
        return text.trim();
    }

    //判断请求的是手机号还是邮箱
    private String resolveTarget(String text){
        if (PHONE_PATTERN.matcher(text).matches()){
            return "phone";
        }
        if (EMAIL_PATTERN.matcher(text).matches()){
            return "email";
        }
        throw new IllegalArgumentException("手机号或邮箱格式不正确");
    }


    //生成随机验证码
    private String generateCode(){
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = random.nextInt(CODE_CHARS.length());
            builder.append(CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

    //获取验证码
    private VerifyCode findValidCode(String target, String scene, String code) {
        String cleanTarget = cleanTarget(target);
        //判断输入的形式是否是手机号或者邮箱
        resolveTarget(cleanTarget);

        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        String cleanCode = code.trim().toUpperCase(Locale.ROOT);

        QueryWrapper<VerifyCode> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("target", cleanTarget);
        queryWrapper.eq("scene", scene);
        queryWrapper.eq("status", 0);
        queryWrapper.orderByDesc("id");
        queryWrapper.last("limit 1");

        VerifyCode verifyCode = verifyCodeMapper.selectOne(queryWrapper);
        if (verifyCode == null) {
            throw new IllegalArgumentException("验证码不存在或已过期");
        }

        LocalDateTime now = LocalDateTime.now();
        if (verifyCode.getExpireTime().isBefore(now)) {
            verifyCode.setStatus(2);
            verifyCodeMapper.updateById(verifyCode);
            throw new IllegalArgumentException("验证码已经过期");
        }

        if (!verifyCode.getCode().equalsIgnoreCase(cleanCode)) {
            throw new IllegalArgumentException("验证码错误");
        }

        return verifyCode;
    }

}
