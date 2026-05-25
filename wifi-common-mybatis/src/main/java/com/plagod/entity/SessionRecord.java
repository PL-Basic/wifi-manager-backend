package com.plagod.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_session")
public class SessionRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "session_id", type = IdType.AUTO)
    private Long sessionId;

    private Long userId;

    private Long nodeId;

    private String mac;

    private String ip;

    private String deviceInfo;

    private LocalDateTime loginTime;

    private LocalDateTime expireTime;

    private LocalDateTime logoutTime;

    private Integer status;

    private Long bytesUp;

    private Long bytesDown;
}
