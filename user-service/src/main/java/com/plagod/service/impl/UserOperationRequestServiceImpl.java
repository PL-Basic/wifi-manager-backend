package com.plagod.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.plagod.dto.UserOperationRequestPageResult;
import com.plagod.dto.UserOperationReviewDTO;
import com.plagod.entity.User;
import com.plagod.entity.UserOperationRequest;
import com.plagod.mapper.UserMapper;
import com.plagod.mapper.UserOperationRequestMapper;
import com.plagod.service.UserOperationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserOperationRequestServiceImpl implements UserOperationRequestService {

    private static final String TYPE_PURGE_USER = "user.purge";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_APPROVED = 1;
    private static final int STATUS_REJECTED = 2;

    @Autowired
    private UserOperationRequestMapper requestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserOperationRequestPageResult pageRequests(long current, long size, Integer status) {
        long pageCurrent = current <= 0 ? 1 : current;
        long pageSize = size <= 0 ? 10 : Math.min(size, 100);
        QueryWrapper<UserOperationRequest> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");

        Page<UserOperationRequest> page = requestMapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        UserOperationRequestPageResult result = new UserOperationRequestPageResult();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        return result;
    }

    @Override
    public Long requestPurge(Long targetUserId, Long requesterId, String requesterName, String reason) {
        User target = getExistingUser(targetUserId);
        UserOperationRequest request = new UserOperationRequest();
        request.setRequestType(TYPE_PURGE_USER);
        request.setTargetUserId(targetUserId);
        request.setTargetUsername(target.getUsername());
        request.setRequesterId(requesterId);
        request.setRequesterName(requesterName);
        request.setReason(reason);
        request.setStatus(STATUS_PENDING);
        request.setCreateTime(LocalDateTime.now());
        requestMapper.insert(request);
        return request.getId();
    }

    @Override
    public void review(Long id, Long approverId, String approverName, UserOperationReviewDTO dto) {
        UserOperationRequest request = requestMapper.selectById(id);
        if (request == null) {
            throw new IllegalArgumentException("申请不存在");
        }
        if (!Integer.valueOf(STATUS_PENDING).equals(request.getStatus())) {
            throw new IllegalArgumentException("申请已处理");
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        request.setApproverId(approverId);
        request.setApproverName(approverName);
        request.setHandleTime(LocalDateTime.now());
        request.setStatus(approved ? STATUS_APPROVED : STATUS_REJECTED);
        request.setRejectReason(dto.getRejectReason());
        requestMapper.updateById(request);

        if (approved && TYPE_PURGE_USER.equals(request.getRequestType())) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ?", request.getTargetUserId());
        }
    }

    private User getExistingUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }
}
