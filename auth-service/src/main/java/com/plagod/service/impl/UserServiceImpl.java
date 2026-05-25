package com.plagod.service.impl;

import com.plagod.client.UserServiceClient;
import com.plagod.dto.ApiResponse;
import com.plagod.dto.AuthResultDTO;
import com.plagod.dto.AuthUserDTO;
import com.plagod.dto.LoginDTO;
import com.plagod.dto.LoginResult;
import com.plagod.dto.RegisterDTO;
import com.plagod.dto.RegisterResult;
import com.plagod.dto.UserRegisterCommandDTO;
import com.plagod.dto.UserRegisterResultDTO;
import com.plagod.enums.ConflictFieldEnum;
import com.plagod.enums.LoginStatusEnum;
import com.plagod.service.UserService;
import com.plagod.utils.JwtUtils;
import com.plagod.utils.PasswordUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public RegisterResult register(RegisterDTO registerDTO) {
        UserRegisterCommandDTO command = new UserRegisterCommandDTO();
        BeanUtils.copyProperties(registerDTO, command);

        ApiResponse<UserRegisterResultDTO> response = userServiceClient.register(command);
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            return RegisterResult.conflict(EnumSet.noneOf(ConflictFieldEnum.class), "注册服务暂不可用");
        }

        UserRegisterResultDTO result = response.getData();
        if (result.isSuccess()) {
            return RegisterResult.success();
        }
        return RegisterResult.conflict(toConflictFields(result.getConflictFields()), result.getMessage());
    }

    @Override
    public LoginResult login(LoginDTO loginDTO) {
        ApiResponse<AuthUserDTO> response = userServiceClient.findByAccount(loginDTO.getAccount());
        AuthUserDTO user = response == null || response.getCode() != 200 ? null : response.getData();
        if (user == null) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_NOT_FOUND, "账号不存在");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            return LoginResult.fail(LoginStatusEnum.ACCOUNT_DISABLED, "账号被禁用");
        }
        if (!PasswordUtils.matches(loginDTO.getPassword(), user.getPassword())) {
            return LoginResult.fail(LoginStatusEnum.PASSWORD_ERROR, "密码错误");
        }

        String token = jwtUtils.generateToken(user.getUserId(), user.getUsername(), user.getRole());

        AuthResultDTO authResultDTO = new AuthResultDTO();
        authResultDTO.setToken(token);
        authResultDTO.setUsername(user.getUsername());
        authResultDTO.setNickname(user.getNickname());
        authResultDTO.setAvatar(user.getAvatar());

        return LoginResult.success(authResultDTO);
    }

    private Set<ConflictFieldEnum> toConflictFields(Iterable<String> fields) {
        Set<ConflictFieldEnum> result = EnumSet.noneOf(ConflictFieldEnum.class);
        if (fields == null) {
            return result;
        }
        for (String field : fields) {
            try {
                result.add(ConflictFieldEnum.valueOf(field));
            } catch (IllegalArgumentException ignored) {
                // Keep auth compatible if user-service adds new conflict fields.
            }
        }
        return result;
    }
}
