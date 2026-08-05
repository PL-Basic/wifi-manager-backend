package com.plagod.service;

import com.plagod.vo.user.UserConnectionPolicyVO;
import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.vo.user.UserVO;

import com.plagod.vo.user.UserRoleSnapshotVO;
import java.util.List;

public interface UserManageService {
    UserPageResult pageUsers(long current, long size, String keyword);

    UserVO getUser(Long userId);

    List<UserRoleSnapshotVO> getRoleSnapshots(List<Long> userIds);

    UserVO updateUser(Long userId, UserUpdateDTO updateDTO, Integer operatorRole);

    void updateStatus(Long userId, UserStatusDTO statusDTO);

    void deleteUser(Long userId);

    void purgeUser(Long userId);

    UserStatsVO getUserStats();

    // 查询 Portal 授权使用的用户连接策略
    UserConnectionPolicyVO getConnectionPolicy(Long userId);
}
