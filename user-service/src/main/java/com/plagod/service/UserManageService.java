package com.plagod.service;

import com.plagod.dto.UserPageResult;
import com.plagod.dto.UserStatsVO;
import com.plagod.dto.UserStatusDTO;
import com.plagod.dto.UserUpdateDTO;
import com.plagod.dto.UserVO;

public interface UserManageService {
    UserPageResult pageUsers(long current, long size, String keyword);

    UserVO getUser(Long userId);

    UserVO updateUser(Long userId, UserUpdateDTO updateDTO);

    void updateStatus(Long userId, UserStatusDTO statusDTO);

    void deleteUser(Long userId);

    void purgeUser(Long userId);

    UserStatsVO getUserStats();
}
