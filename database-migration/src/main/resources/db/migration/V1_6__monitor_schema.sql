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
create table t_client_location(
    id bigint auto_increment,
    mac varchar(32) not null comment '由可信ACTIVE Session推导的客户端MAC',
    user_id bigint comment '位置所属用户',
    session_id bigint comment '可信ACTIVE Session，历史数据为空',
    node_id bigint comment 'Session所属ESP32节点，历史数据为空',
    device_code varchar(64) comment 'Session所属设备编码，历史数据为空',
    trusted_binding tinyint not null default 0 comment '0=历史数据 1=可信Session绑定',
    latitude decimal(10, 7) not null comment '纬度',
    longitude decimal(10, 7) not null comment '经度',
    accuracy decimal(10, 2) comment '定位精度，单位米',
    consent_time datetime not null comment '服务端记录的用户授权时间',
    report_time datetime not null comment '服务端接收时间',
    source varchar(32) not null default 'portal' comment '位置传感器来源',
    remark varchar(255),
    create_time datetime not null default current_timestamp,

    primary key (id),
    key idx_mac_report_time (mac, report_time),
    key idx_user_report_time (user_id, report_time),
    key idx_session_report_time (session_id, report_time),
    key idx_node_report_time (node_id, report_time),
    key idx_device_report_time (device_code, report_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客户端授权定位记录表';
create table t_location_authorization(
    user_id bigint not null comment '授权用户ID',
    enabled tinyint not null default 0 comment '0=未授权或已撤销 1=允许上报',
    consent_time datetime comment '最近一次授权时间',
    revoked_time datetime comment '最近一次撤销时间',
    last_report_time datetime comment '最近一次成功位置上报时间',
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,

    primary key (user_id),
    key idx_location_authorization_enabled (enabled, update_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户位置共享授权状态';
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


create table t_rule_hit(
    id bigint auto_increment,
    event_id varchar(64) not null,
    device_code varchar(64) not null,
    node_id bigint not null,
    session_id bigint not null,
    user_id bigint,
    mac varchar(17) not null,
    rule_id bigint not null,
    rule_code varchar(64) not null,
    rule_type tinyint not null,
    action_type tinyint not null,
    level tinyint not null,
    suppressed tinyint not null default 0
    comment '是否被冷却窗口抑制：0=否 1=是',
    alert_id bigint,
    hit_time datetime not null,
    create_time datetime not null default current_timestamp,

    primary key (id),
    unique key uk_rule_hit_event(device_code, event_id, rule_code),
    key idx_rule_hit_time(rule_code, hit_time),
    key idx_rule_hit_user_time(user_id, hit_time),
    key idx_rule_hit_mac_time(mac, hit_time),
    key idx_rule_hit_alert(alert_id)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='流量规则命中记录';

create table t_geofence (
    fence_id bigint auto_increment,
    name varchar(64) not null,
    center_latitude decimal(10, 7) not null,
    center_longitude decimal(10, 7) not null,
    radius_meters decimal(10, 2) not null,
    enabled tinyint not null default 1,
    description varchar(255),
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    del_flag tinyint not null default 0,

    primary key (fence_id),
    key idx_geofence_enabled (enabled, del_flag)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='圆形地理围栏';

create table t_geofence_state (
    state_id bigint auto_increment,
    fence_id bigint not null,
    session_id bigint not null,
    user_id bigint not null,
    node_id bigint not null,
    device_code varchar(64) not null,
    mac varchar(17) not null,
    inside_state tinyint not null
    comment '0=围栏外 1=围栏内',
    last_location_id bigint not null,
    last_report_time datetime not null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,

    primary key (state_id),
    unique key uk_geofence_session (fence_id, session_id),
    key idx_geofence_state_user (user_id),
    key idx_geofence_state_session (session_id),
    key idx_geofence_state_location (last_location_id)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='围栏与可信Session当前状态';

create table t_geofence_event (
    event_id bigint auto_increment,
    fence_id bigint not null,
    location_id bigint not null,
    user_id bigint not null,
    session_id bigint not null,
    node_id bigint not null,
    device_code varchar(64) not null,
    mac varchar(17) not null,
    event_type varchar(8) not null comment 'ENTER或EXIT',
    event_time datetime not null,
    create_time datetime not null default current_timestamp,

    primary key (event_id),
    unique key uk_geofence_location_event(fence_id, location_id, event_type),
    key idx_geofence_event_fence_time(fence_id, event_time),
    key idx_geofence_event_user_time(user_id, event_time),
    key idx_geofence_event_session_time(session_id, event_time),
    key idx_geofence_event_mac_time(mac, event_time)

) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='地理围栏进入和离开事件';
