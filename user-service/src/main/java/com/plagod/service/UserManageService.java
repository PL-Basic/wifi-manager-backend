package com.plagod.service;

import com.plagod.vo.user.UserPageResult;
import com.plagod.vo.user.UserStatsVO;
import com.plagod.dto.user.UserStatusDTO;
import com.plagod.dto.user.UserUpdateDTO;
import com.plagod.vo.user.UserVO;

public interface UserManageService {
    UserPageResult pageUsers(long current, long size, String keyword);

    UserVO getUser(Long userId);

    UserVO updateUser(Long userId, UserUpdateDTO updateDTO, Integer operatorRole);

    void updateStatus(Long userId, UserStatusDTO statusDTO);

    void deleteUser(Long userId);

    void purgeUser(Long userId);

    UserStatsVO getUserStats();
}
