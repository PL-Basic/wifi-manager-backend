package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.audit.Audited;
import com.plagod.dto.UserPageResult;
import com.plagod.dto.UserStatsVO;
import com.plagod.dto.UserStatusDTO;
import com.plagod.dto.UserUpdateDTO;
import com.plagod.dto.UserVO;
import com.plagod.entity.User;
import com.plagod.mapper.UserMapper;
import com.plagod.service.UserManageService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserManageServiceImpl implements UserManageService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserPageResult pageUsers(long current, long size, String keyword) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(wrapper -> wrapper
                    .like("username", keyword)
                    .or().like("nickname", keyword)
                    .or().like("email", keyword)
                    .or().like("phone", keyword));
        }
        queryWrapper.orderByDesc("create_time");

        Page<User> page = userMapper.selectPage(new Page<>(pageCurrent, pageSize), queryWrapper);
        List<UserVO> records = new ArrayList<>();
        for (User user : page.getRecords()) {
            records.add(toVO(user));
        }

        UserPageResult result = new UserPageResult();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(records);
        return result;
    }

    @Override
    public UserVO getUser(Long userId) {
        User user = getExistingUser(userId);
        return toVO(user);
    }

    @Override
    @Audited(action = "user.update")
    public UserVO updateUser(Long userId, UserUpdateDTO updateDTO, Integer operatorRole) {
        User user = getExistingUser(userId);
        if (!Integer.valueOf(0).equals(operatorRole)) {
            if (user.getRole() != null && user.getRole() <= 1) {
                throw new IllegalArgumentException("管理员之间不能互相修改");
            }
            updateDTO.setRole(null);
        }
        BeanUtils.copyProperties(updateDTO, user);
        userMapper.updateById(user);
        return toVO(userMapper.selectById(userId));
    }

    @Override
    @Audited(action = "user.status")
    public void updateStatus(Long userId, UserStatusDTO statusDTO) {
        User user = getExistingUser(userId);
        user.setStatus(statusDTO.getStatus());
        userMapper.updateById(user);
    }

    @Override
    @Audited(action = "user.delete")
    public void deleteUser(Long userId) {
        getExistingUser(userId);
        userMapper.deleteById(userId);
    }

    @Override
    @Audited(action = "user.purge")
    public void purgeUser(Long userId) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", userId);
    }

    @Override
    public UserStatsVO getUserStats() {
        long totalUsers = userMapper.selectCount(new QueryWrapper<User>());

        QueryWrapper<User> enabledWrapper = new QueryWrapper<>();
        enabledWrapper.eq("status", 1);
        long enabledUsers = userMapper.selectCount(enabledWrapper);

        QueryWrapper<User> disabledWrapper = new QueryWrapper<>();
        disabledWrapper.eq("status", 0);
        long disabledUsers = userMapper.selectCount(disabledWrapper);

        QueryWrapper<User> adminWrapper = new QueryWrapper<>();
        adminWrapper.eq("role", 1);
        long adminUsers = userMapper.selectCount(adminWrapper);

        UserStatsVO statsVO = new UserStatsVO();
        statsVO.setTotalUsers(totalUsers);
        statsVO.setEnabledUsers(enabledUsers);
        statsVO.setDisabledUsers(disabledUsers);
        statsVO.setAdminUsers(adminUsers);
        return statsVO;
    }

    private User getExistingUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private UserVO toVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
}
