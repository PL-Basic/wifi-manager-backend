package com.plagod.vo.user;

import lombok.Data;

/**
 * 提供给内部服务使用的用户连接策略。
 */
@Data
public class UserConnectionPolicyVO {

    private Long userId;

    // 已处理默认值，保证返回值大于等于 1。
    private Integer maxConnections;
}