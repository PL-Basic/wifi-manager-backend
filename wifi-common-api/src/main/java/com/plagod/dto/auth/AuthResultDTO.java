package com.plagod.dto.auth;


import com.plagod.vo.tenant.TenantContextVO;
import lombok.Data;

@Data
public class AuthResultDTO {
    private String userId;
    private String token;
    private String username;
    private Integer role;
    private String nickname;
    private String avatar;
    private String accountState;
    private TenantContextVO context;
}
