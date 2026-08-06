package com.plagod.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.plagod.entity.auth.AuthRefreshSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AuthRefreshSessionMapper extends BaseMapper<AuthRefreshSession> {

    AuthRefreshSession selectForUpdate(@Param("sessionId") String sessionId);

    int rotate(@Param("sessionId") String sessionId,
               @Param("expectedVersion") Integer expectedVersion,
               @Param("currentTokenId") String currentTokenId,
               @Param("contextType") String contextType,
               @Param("tenantId") Long tenantId,
               @Param("tenantCode") String tenantCode,
               @Param("tenantRole") String tenantRole,
               @Param("tenantContextVersion") Long tenantContextVersion,
               @Param("memberContextVersion") Long memberContextVersion,
               @Param("securityVersion") Long securityVersion,
               @Param("authoritiesJson") String authoritiesJson,
               @Param("clientInstanceId") String clientInstanceId,
               @Param("userAgentHash") String userAgentHash,
               @Param("ipNetworkHash") String ipNetworkHash,
               @Param("ipChanged") boolean ipChanged,
               @Param("userAgentChanged") boolean userAgentChanged);

    int updateContext(@Param("sessionId") String sessionId,
                      @Param("userId") Long userId,
                      @Param("contextType") String contextType,
                      @Param("tenantId") Long tenantId,
                      @Param("tenantCode") String tenantCode,
                      @Param("tenantRole") String tenantRole,
                      @Param("tenantContextVersion") Long tenantContextVersion,
                      @Param("memberContextVersion") Long memberContextVersion,
                      @Param("authoritiesJson") String authoritiesJson);

    int revokeFamily(@Param("sessionId") String sessionId,
                     @Param("reason") String reason,
                     @Param("now") LocalDateTime now);

    int revokeAllForUser(@Param("userId") Long userId,
                         @Param("reason") String reason,
                         @Param("now") LocalDateTime now);

    int markStepUpRequired(@Param("sessionId") String sessionId,
                           @Param("expectedVersion") Integer expectedVersion);
}
