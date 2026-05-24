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
