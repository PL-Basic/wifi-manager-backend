create database wifi;

use wifi;



drop table if exists sys_user;
create table sys_user
(
    user_id             bigint AUTO_INCREMENT,                                                       #用户ID
    username            varchar(64)  NOT NULL,                                                       #用户名
    password            varchar(128) NOT NULL,                                                       #加密密码
    nickname            varchar(64)  NOT NULL,                                                       #用户昵称
    email               varchar(64) UNIQUE,                                                          #邮箱
    phone               varchar(32) UNIQUE,                                                          #手机号
    avatar              varchar(255),                                                                #头像
    role                tinyint      NOT NULL DEFAULT 2,                                             #角色：1-管理员 2-普通用户
    status              tinyint      NOT NULL DEFAULT 1,                                             # 状态：0-禁用 1-启用
    max_connections     int,                                                                         #最大连接数
    daily_quota_minutes int                   DEFAULT 480,                                           #每天默认使用时间
    expire_time         datetime,                                                                    #用户有效期
    last_login_time     datetime              DEFAULT NULL,                                          #最后登录时间
    last_login_ip       varchar(45)           DEFAULT NULL,                                          #最后登录的IP地址
    create_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,                             #创建时间
    update_time         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, #更新时间
    del_flag            tinyint      NOT NULL DEFAULT 0,                                             #逻辑删除:0-未删除 1-已删除

#添加索引
    PRIMARY KEY (user_id),
    UNIQUE KEY idx_username (username),
    KEY idx_status (status),
    KEY idx_last_login_time (last_login_time)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


-- ============ ESP32 节点表 ============
drop table if exists t_esp32_node;
create table t_esp32_node
(
    node_id          bigint AUTO_INCREMENT,
    device_code      varchar(64) NOT NULL,              #设备唯一编码（固件烧录时写入）
    name             varchar(64) NOT NULL,              #节点名称（客厅/卧室）
    location         varchar(128),                      #位置说明
    ip               varchar(45),                       #节点当前IP
    firmware_version varchar(32),                       #固件版本
    status           tinyint     NOT NULL DEFAULT 0,    #状态：0-离线 1-在线
    max_clients      int         NOT NULL DEFAULT 4,    #最大可承载客户端数
    current_clients  int         NOT NULL DEFAULT 0,    #当前已连客户端数
    last_heartbeat   datetime             DEFAULT NULL, #最后心跳时间
    create_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         tinyint     NOT NULL DEFAULT 0,

    PRIMARY KEY (node_id),
    UNIQUE KEY idx_device_code (device_code),
    KEY idx_status (status)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============ 连接会话表（在线 + 历史） ============
drop table if exists t_session;
create table t_session
(
    session_id  bigint AUTO_INCREMENT,
    user_id     bigint      NOT NULL,                           #用户ID
    node_id     bigint      NOT NULL,                           #接入的ESP32节点
    mac         varchar(17) NOT NULL,                           #客户端MAC
    ip          varchar(45),                                    #客户端IP（DHCP分配）
    device_info varchar(255),                                   #User-Agent或设备型号
    login_time  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP, #登录时间
    expire_time datetime    NOT NULL,                           #配额到期时间
    logout_time datetime             DEFAULT NULL,              #实际下线时间
    status      tinyint     NOT NULL DEFAULT 1,                 #状态：0-离线 1-在线 2-被踢 3-过期
    bytes_up    bigint      NOT NULL DEFAULT 0,                 #上行字节
    bytes_down  bigint      NOT NULL DEFAULT 0,                 #下行字节

    PRIMARY KEY (session_id),
    KEY idx_user (user_id),
    KEY idx_mac_status (mac, status),
    KEY idx_node (node_id),
    KEY idx_login_time (login_time)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============ MAC 黑名单 ============
drop table if exists t_mac_blacklist;
create table t_mac_blacklist
(
    id          bigint AUTO_INCREMENT,
    mac         varchar(17) NOT NULL,
    reason      varchar(255),                      #拉黑原因
    operator_id bigint,                            #操作管理员ID
    expire_time datetime             DEFAULT NULL, #解禁时间（NULL=永久）
    create_time datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY idx_mac (mac)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============ 流量明细日志 ============
drop table if exists t_traffic_log;
create table t_traffic_log
(
    id         bigint AUTO_INCREMENT,
    session_id bigint      NOT NULL,
    mac        varchar(17) NOT NULL,
    dst_ip     varchar(45) NOT NULL, #目标IP
    dst_port   int,                  #目标端口
    sni        varchar(255),         #HTTPS SNI（可选）
    protocol   varchar(16),          #TCP/UDP
    bytes_up   bigint      NOT NULL DEFAULT 0,
    bytes_down bigint      NOT NULL DEFAULT 0,
    log_time   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_mac_time (mac, log_time),
    KEY idx_session (session_id),
    KEY idx_dst_ip (dst_ip)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============ 审计日志 ============
drop table if exists t_audit_log;
create table t_audit_log
(
    id            bigint AUTO_INCREMENT,
    operator_id   bigint,               #操作人ID（NULL=系统）
    operator_name varchar(64),
    action        varchar(64) NOT NULL, #USER_CREATE/DEVICE_KICK/...
    target        varchar(255),         #目标对象标识
    detail        json,                 #详细参数与结果
    ip            varchar(45),
    create_time   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_operator (operator_id),
    KEY idx_action (action),
    KEY idx_create_time (create_time)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============ 告警事件 ============
drop table if exists t_alert_event;
create table t_alert_event
(
    id             bigint AUTO_INCREMENT,
    level          tinyint      NOT NULL,           #等级：1-info 2-warn 3-danger 4-critical
    rule_code      varchar(64)  NOT NULL,           #触发规则编码
    title          varchar(255) NOT NULL,
    mac            varchar(17),
    user_id        bigint,
    detail         json,
    status         tinyint      NOT NULL DEFAULT 0, #0-未处理 1-已确认 2-已处理
    handle_user_id bigint,                          #处理人
    handle_time    datetime,
    create_time    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_level_status (level, status),
    KEY idx_create_time (create_time),
    KEY idx_mac (mac)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

drop table if exists t_access_rule;
create table t_access_rule
(
    id          bigint AUTO_INCREMENT,
    rule_code   varchar(64)  NOT NULL,           # 业务编码，唯一
    rule_type   tinyint      NOT NULL,           # 1=域名精确 2=域名包含 3=IP精确 4=SNI包含
    pattern     varchar(255) NOT NULL,           # 匹配字符串
    action_type tinyint      NOT NULL,           # 1=kick 2=block_traffic 3=alert_only
    level       tinyint      NOT NULL DEFAULT 2, # 1=critical 2=warning 3=info
    enabled     tinyint      NOT NULL DEFAULT 1, # 0=禁用 1=启用
    description varchar(255),
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    tinyint      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_code (rule_code),
    KEY idx_enabled_type (enabled, rule_type)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

drop table if exists t_client_location;
create table t_client_location
(
    id           bigint AUTO_INCREMENT,
    mac          varchar(32)    NOT NULL,                  # 客户端 MAC，由 Portal/客户端上报
    user_id      bigint,                                   # 授权上报位置的登录用户
    latitude     decimal(10, 7) NOT NULL,                  # 纬度
    longitude    decimal(10, 7) NOT NULL,                  # 经度
    accuracy     decimal(10, 2),                           # 浏览器/客户端给出的精度，单位米
    consent_time datetime       NOT NULL,                  # 客户端授权定位的时间
    report_time  datetime       NOT NULL,                  # 客户端采集/上报位置的时间
    source       varchar(32)    NOT NULL DEFAULT 'portal', # portal / mobile / other
    remark       varchar(255),
    create_time  datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_mac_report_time (mac, report_time),
    KEY idx_user_report_time (user_id, report_time)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


create table if not exists t_user_operation_request
(
    id              bigint AUTO_INCREMENT,
    request_type    varchar(64) NOT NULL,
    target_user_id  bigint      NOT NULL,
    target_username varchar(64),
    requester_id    bigint,
    requester_name  varchar(64),
    status          tinyint     NOT NULL DEFAULT 0,
    approver_id     bigint,
    approver_name   varchar(64),
    reason          varchar(255),
    reject_reason   varchar(255),
    create_time     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handle_time     datetime             DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_status_create_time (status, create_time),
    KEY idx_target_user (target_user_id),
    KEY idx_requester (requester_id)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

drop table if exists t_verify_code;
create table t_verify_code
(
    id          bigint auto_increment,

    target      varchar(128) not null comment '接收方：手机或者邮箱',
    target_type varchar(16)  not null comment '接收方类型：phone/email',
    scene       varchar(32)  not null comment '使用场景：register/login/reset_password/bind_contact',

    code        varchar(16)  not null comment '验证码',
    status      tinyint      not null default 0 comment '状态：0未使用，1已使用，2已过期',

    expire_time datetime     not null comment '过期时间',
    verify_time datetime              default null comment '验证通过时间',

    send_ip     varchar(45)           default null comment '发送请求IP',
    verify_ip   varchar(45)           default null comment '验证请求IP',

    create_time datetime     not null default current_timestamp comment '创建时间',

    primary key (id),
    key idx_target_scene_status (target, scene, status),
    key idx_expire_time (expire_time),
    key idx_create_time (create_time)
) default charset = utf8mb4 comment '验证码记录表';