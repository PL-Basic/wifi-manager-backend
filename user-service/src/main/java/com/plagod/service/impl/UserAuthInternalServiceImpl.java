package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.plagod.audit.Audited;
import com.plagod.dto.AuthUserDTO;
import com.plagod.dto.UserRegisterCommandDTO;
import com.plagod.dto.UserRegisterResultDTO;
import com.plagod.entity.User;
import com.plagod.mapper.UserMapper;
import com.plagod.service.UserAuthInternalService;
import com.plagod.utils.PasswordUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class UserAuthInternalServiceImpl implements UserAuthInternalService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public AuthUserDTO findByAccount(String account) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("del_flag", 0);
        queryWrapper.and(wrapper -> wrapper.eq("username", account)
                .or().eq("email", account)
                .or().eq("phone", account));
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            return null;
        }
        AuthUserDTO dto = new AuthUserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

    @Override
    @Audited(action = "auth.register", target = "sys_user")
    public UserRegisterResultDTO register(UserRegisterCommandDTO command) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("del_flag", 0);
        queryWrapper.and(wrapper -> {
            wrapper.eq("username", command.getUsername());
            if (StringUtils.hasText(command.getEmail())) {
                wrapper.or().eq("email", command.getEmail());
            }
            if (StringUtils.hasText(command.getPhone())) {
                wrapper.or().eq("phone", command.getPhone());
            }
        });

        List<User> users = userMapper.selectList(queryWrapper);
        List<String> conflictFields = collectConflictFields(users, command);
        if (!conflictFields.isEmpty()) {
            return UserRegisterResultDTO.conflict("registration info already exists", conflictFields);
        }

        User user = new User();
        BeanUtils.copyProperties(command, user, "role", "status", "delFlag");
        user.setRole(2);
        user.setStatus(1);
        user.setDelFlag(0);
        user.setPassword(PasswordUtils.encode(command.getPassword()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("register duplicate conflict, username={}", command.getUsername(), e);
            return UserRegisterResultDTO.conflict("registration info already exists", new ArrayList<>());
        }
        return UserRegisterResultDTO.success("register success");
    }

    private List<String> collectConflictFields(List<User> users, UserRegisterCommandDTO command) {
        List<String> fields = new ArrayList<>();
        for (User user : users) {
            if (Objects.equals(user.getUsername(), command.getUsername()) && !fields.contains("USERNAME")) {
                fields.add("USERNAME");
            }
            if (StringUtils.hasText(command.getEmail())
                    && Objects.equals(user.getEmail(), command.getEmail())
                    && !fields.contains("EMAIL")) {
                fields.add("EMAIL");
            }
            if (StringUtils.hasText(command.getPhone())
                    && Objects.equals(user.getPhone(), command.getPhone())
                    && !fields.contains("PHONE")) {
                fields.add("PHONE");
            }
        }
        return fields;
    }
}
