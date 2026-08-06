package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.AuthRefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AuthRefreshTokenMapper extends BaseMapper<AuthRefreshToken> {

    AuthRefreshToken selectByHashForUpdate(@Param("tokenHash") String tokenHash);

    int markRotated(@Param("tokenId") String tokenId,
                    @Param("replacementTokenId") String replacementTokenId,
                    @Param("now") LocalDateTime now);

    int markReplayed(@Param("tokenId") String tokenId,
                     @Param("now") LocalDateTime now);

    int revokeActiveForSession(@Param("sessionId") String sessionId,
                               @Param("now") LocalDateTime now);

    int revokeActiveForUser(@Param("userId") Long userId,
                            @Param("now") LocalDateTime now);
}
