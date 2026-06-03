package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.configuration.VerificationCodeProperties;
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

    @Autowired
    private VerificationCodeProperties verificationCodeProperties;


    //手机和邮箱的正则格式
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");


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

        //获取当地现在时间
        LocalDateTime now = LocalDateTime.now();
        //检测是否发送验证码功能被限制
        checkSendLimit(cleanTarget,scene,sendIp,now);

        //生成验证码
        String code = generateCode();

        VerifyCode verifyCode = new VerifyCode();
        verifyCode.setTarget(cleanTarget);
        verifyCode.setTargetType(targetType);
        verifyCode.setScene(scene);
        verifyCode.setCode(code);
        verifyCode.setStatus(0);
        verifyCode.setExpireTime(now.plusMinutes(verificationCodeProperties.getExpiryMinutes()));
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

    //检测限流验证码发送
    private void checkSendLimit(String target, String scene, String sendIp,LocalDateTime now) {

        //限流窗口开始时间
        LocalDateTime targetIntervalStart = now.minusSeconds(verificationCodeProperties.getTargetIntervalSeconds());
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime ipMinuteStart = now.minusMinutes(1);

        //获取近期请求的总数
        Long recentTargetCount = verifyCodeMapper.selectCount(
                new QueryWrapper<VerifyCode>()
                        .eq("target",target)
                        .eq("scene",scene)
                        .ge("create_time",targetIntervalStart)
        );

        if (recentTargetCount != null && recentTargetCount > 0){
            throw new IllegalArgumentException("验证码发送太频繁，请稍后再试");
        }

        //获取今天请求的总数
        Long targetCount = verifyCodeMapper.selectCount(
                new QueryWrapper<VerifyCode>()
                        .eq("target",target)
                        .eq("scene",scene)
                        .ge("create_time",todayStart)
        );

        if (targetCount != null && targetCount >= verificationCodeProperties.getTargetDailyLimit()){
            throw new IllegalArgumentException("今日验证码发送次数已达上限");
        }



        if (StringUtils.hasText(sendIp)){
            //同一个IP、同一个scene的请求，在一分钟内不能超过IP_MINUTER_LIMIT
            Long ipMinuteCount = verifyCodeMapper.selectCount(
                    new QueryWrapper<VerifyCode>()
                            .eq("send_ip",sendIp)
                            .eq("scene",scene)
                            .ge("create_time",ipMinuteStart)
            );

            if (ipMinuteCount != null && ipMinuteCount >= verificationCodeProperties.getIpMinuteLimit()){
                throw new IllegalArgumentException("验证码发送太频繁，请稍后再试");
            }
            //同一个IP、同一个scene的请求，在一天之内不能超过IP_DAILY_LIMIT
            Long ipTodayCount = verifyCodeMapper.selectCount(
                    new QueryWrapper<VerifyCode>()
                            .eq("send_ip",sendIp)
                            .eq("scene",scene)
                            .ge("create_time",todayStart)
            );

            if (ipTodayCount != null && ipTodayCount >= verificationCodeProperties.getIpDailyLimit()){
                throw new IllegalArgumentException("当前网络验证码请求次数已经达到上限");
            }
        }
    }


    //生成随机验证码
    private String generateCode(){
        StringBuilder builder = new StringBuilder(verificationCodeProperties.getCodeLength());
        for (int i = 0; i < verificationCodeProperties.getCodeLength(); i++) {
            int index = random.nextInt(verificationCodeProperties.getCodeChars().length());
            builder.append(verificationCodeProperties.getCodeChars().charAt(index));
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
