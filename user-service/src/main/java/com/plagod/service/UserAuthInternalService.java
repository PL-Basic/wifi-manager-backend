package com.plagod.service;

import com.plagod.dto.AuthUserDTO;
import com.plagod.dto.UserRegisterCommandDTO;
import com.plagod.dto.UserRegisterResultDTO;

public interface UserAuthInternalService {

    AuthUserDTO findByAccount(String account);

    UserRegisterResultDTO register(UserRegisterCommandDTO command);
}
