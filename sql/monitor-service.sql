use wifi;

drop table if exists t_access_rule;
create table t_access_rule(
    id bigint auto_increment,
    rule_code varchar(64) not null comment '业务编码，唯一',
    rule_type tinyint not null comment '规则类型：1=域名精确 2=域名包含 3=IP精确 4=SNI包含',
    pattern varchar(255) not null comment '匹配字符串',
    action_type tinyint not null comment '动作类型：1=kick 2=block_traffic 3=alert_only',
    level tinyint not null default 2 comment '告警级别：1=critical 2=warning 3=info',
    enabled tinyint not null default 1 comment '是否启用：0=禁用 1=启用',
    description varchar(255) comment '规则说明',
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    del_flag tinyint not null default 0,

    primary key (id),
    unique key uk_rule_code (rule_code),
    key idx_enabled_type (enabled, rule_type)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='访问控制规则表';

drop table if exists t_client_location;
create table t_client_location(
    id bigint auto_increment,
    mac varchar(32) not null comment '客户端MAC，由Portal/客户端上报',
    user_id bigint comment '授权上报位置的登录用户',
    latitude decimal(10, 7) not null comment '纬度',
    longitude decimal(10, 7) not null comment '经度',
    accuracy decimal(10, 2) comment '定位精度，单位米',
    consent_time datetime not null comment '客户端授权定位时间',
    report_time datetime not null comment '客户端采集/上报位置时间',
    source varchar(32) not null default 'portal' comment '来源：portal/mobile/other',
    remark varchar(255),
    create_time datetime not null default current_timestamp,

    primary key (id),
    key idx_mac_report_time (mac, report_time),
    key idx_user_report_time (user_id, report_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客户端授权定位记录表';

drop table if exists t_audit_log;
create table t_audit_log(
    id bigint auto_increment,
    operator_id bigint comment '操作人ID，NULL表示系统',
    operator_name varchar(64),
    action varchar(64) not null comment '操作动作编码',
    target varchar(255) comment '目标对象标识',
    detail json comment '详细参数与结果',
    ip varchar(45),
    create_time datetime not null default current_timestamp,

    primary key (id),
    key idx_operator (operator_id),
    key idx_action (action),
    key idx_create_time (create_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='审计日志表';

drop table if exists t_alert_event;
create table t_alert_event(
    id bigint auto_increment,
    level tinyint not null comment '告警级别：1=critical 2=warning 3=info',
    rule_code varchar(64) not null comment '触发规则编码',
    title varchar(255) not null,
    mac varchar(17),
    user_id bigint,
    detail json,
    status tinyint not null default 0 comment '状态：0=未处理 1=已处理',
    handle_user_id bigint comment '处理人ID',
    handle_time datetime,
    create_time datetime not null default current_timestamp,

    primary key (id),
    key idx_level_status (level, status),
    key idx_create_time (create_time),
    key idx_mac (mac)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='告警事件表';
