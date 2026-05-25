use wifi;

drop table if exists t_access_rule;
create table t_access_rule(
    id           bigint AUTO_INCREMENT,
    rule_code    varchar(64)  NOT NULL,                   # 业务编码，唯一
    rule_type    tinyint      NOT NULL,                   # 1=域名精确 2=域名包含 3=IP精确 4=SNI包含
    pattern      varchar(255) NOT NULL,                   # 匹配字符串
    action_type  tinyint      NOT NULL,                   # 1=kick 2=block_traffic 3=alert_only
    level        tinyint      NOT NULL DEFAULT 2,         # 1=critical 2=warning 3=info
    enabled      tinyint      NOT NULL DEFAULT 1,         # 0=禁用 1=启用
    description  varchar(255),
    create_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag     tinyint      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_code (rule_code),
    KEY idx_enabled_type (enabled, rule_type)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

drop table if exists t_client_location;
create table t_client_location(
    id            bigint AUTO_INCREMENT,
    mac           varchar(32)    NOT NULL,                  # 客户端 MAC，由 Portal/客户端上报
    user_id       bigint,                                   # 授权上报位置的登录用户
    latitude      decimal(10, 7) NOT NULL,                  # 纬度
    longitude     decimal(10, 7) NOT NULL,                  # 经度
    accuracy      decimal(10, 2),                           # 浏览器/客户端给出的精度，单位米
    consent_time  datetime      NOT NULL,                   # 客户端授权定位的时间
    report_time   datetime      NOT NULL,                   # 客户端采集/上报位置的时间
    source        varchar(32)   NOT NULL DEFAULT 'portal',  # portal / mobile / other
    remark        varchar(255),
    create_time   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_mac_report_time (mac, report_time),
    KEY idx_user_report_time (user_id, report_time)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
