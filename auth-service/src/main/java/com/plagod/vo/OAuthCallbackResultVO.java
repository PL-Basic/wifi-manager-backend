package com.plagod.vo;

import com.plagod.vo.user.SocialIdentityVO;
import com.plagod.vo.tenant.TenantContextVO;
import lombok.Data;

@Data
public class OAuthCallbackResultVO {

    private String status;
    private String message;
    private Long userId;

    /**
     * 只有首次 LOGIN 回调成功时返回本项目 JWT。
     * 重复回调不重新签发 token。
     */
    private String token;

    private String username;
    private Integer role;
    private String nickname;
    private String avatar;
    private String accountState;
    private TenantContextVO context;
    private SocialIdentityVO identity;

    private String returnUri;
    private Boolean replayed;
}
