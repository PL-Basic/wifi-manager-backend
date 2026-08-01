create table t_esp32_node(
    node_id bigint AUTO_INCREMENT,
    device_code varchar(64) NOT NULL,
    name varchar(64) NOT NULL,
    location varchar(128),
    latitude decimal(10, 7) default null comment '节点安装纬度，WGS84',
    longitude decimal(10, 7) default null comment '节点安装经度，WGS84',
    ip varchar(45),
    firmware_version varchar(32),
    wifi_status varchar(32) DEFAULT NULL,
    status tinyint NOT NULL DEFAULT 0,
    max_clients int NOT NULL DEFAULT 4,
    current_clients int NOT NULL DEFAULT 0,
    last_heartbeat datetime DEFAULT NULL,
    rssi_at_one_meter smallint not null default -59 comment '节点一米参考RSSI，单位dBm',
    path_loss_exponent decimal(4, 2) not null default 2.00 comment '节点环境路径损耗指数',
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag tinyint NOT NULL DEFAULT 0,

    PRIMARY KEY (node_id),
    UNIQUE KEY idx_device_code(device_code),
    KEY idx_status_heartbeat(status, last_heartbeat)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
create table t_session(
    session_id bigint AUTO_INCREMENT,
    user_id bigint NOT NULL,
    entitlement_id bigint DEFAULT NULL,
    authorization_mode varchar(16) DEFAULT NULL,
    node_id bigint NOT NULL,
    replaced_session_id bigint DEFAULT NULL,
    mac varchar(17) NOT NULL,
    ip varchar(45),
    device_info varchar(255),
    login_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time datetime NOT NULL,
    last_seen_time datetime DEFAULT NULL,
    last_renew_time datetime DEFAULT NULL,
    last_billed_time datetime DEFAULT NULL,
    consumed_seconds bigint NOT NULL DEFAULT 0,
    end_reason varchar(32) DEFAULT NULL,
    logout_time datetime DEFAULT NULL,
    status tinyint NOT NULL DEFAULT 1,
    bytes_up bigint NOT NULL DEFAULT 0,
    bytes_down bigint NOT NULL DEFAULT 0,

    PRIMARY KEY (session_id),
    KEY idx_user(user_id),
    KEY idx_mac_status(mac, status),
    KEY idx_node(node_id),
    KEY idx_login_time(login_time),
    KEY idx_session_entitlement_status (entitlement_id, status),
    KEY idx_session_replacement (replaced_session_id, status),
    KEY idx_session_status_seen (status, last_seen_time)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
create table t_mac_blacklist(
    id bigint AUTO_INCREMENT,
    mac varchar(17) NOT NULL,
    reason varchar(255),
    operator_id bigint,
    expire_time datetime DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY idx_mac(mac)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
create table t_traffic_log(
    id bigint auto_increment,
    event_id varchar(64) not null,
    node_id bigint not null,
    device_code varchar(64) not null,
    session_id bigint not null,
    mac varchar(17) not null,
    dst_ip varchar(45) not null,
    dst_port int,
    sni varchar(255),
    protocol varchar(16),
    bytes_up bigint not null default 0,
    bytes_down bigint not null default 0,
    log_time datetime not null default current_timestamp,

    primary key (id),
    unique key uk_traffic_device_event(device_code, event_id),
    key idx_mac_time(mac, log_time),
    key idx_session(session_id),
    key idx_log_time(log_time),
    key idx_node_time(node_id, log_time),
    key idx_device_time(device_code, log_time),
    key idx_dst_ip(dst_ip)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table t_client_signal(
    id bigint auto_increment,
    node_id bigint not null,
    device_code varchar(64) not null,
    mac varchar(17) not null,
    session_id bigint not null default 0,
    rssi tinyint not null,
    state varchar(32) not null,
    report_time datetime not null default current_timestamp,

    primary key (id),
    key idx_device_mac_time(device_code, mac, report_time),
    key idx_node_time(node_id, report_time),
    key idx_session_time(session_id, report_time),
    key idx_report_time(report_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table t_session_user_guard (
    user_id bigint not null comment '需要串行分配 Session 名额的用户ID',
    create_time datetime not null default current_timestamp,

    primary key (user_id)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户Session并发控制锁行';

create table t_client_access_guard (
    mac varchar(17) not null comment '需要串行处理授权和黑名单操作的客户端MAC',
    create_time datetime not null default current_timestamp,

    primary key (mac)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客户端访问状态并发控制锁行';

create table t_device_command (
    command_id bigint auto_increment,
    request_id varchar(64) not null,
    node_id bigint default null,
    device_code varchar(64) not null,
    command_type varchar(32) not null,
    purpose varchar(32) not null,
    session_id bigint default null,
    mac varchar(17) default null,
    alert_id bigint default null,
    ttl_seconds int default null,
    topic varchar(191) not null,
    payload text not null,
    encrypted_payload text default null,

    status tinyint not null default 0 comment '0-待发布，1-已发布待结果，2-执行成功，3-执行失败，4-发布失败，5-结果超时',
    retry_count int not null default 0,
    next_retry_time datetime default null,
    publish_time datetime default null,
    deadline_time datetime default null,
    result_time datetime default null,
    result_message varchar(255) default null,

    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,

    primary key (command_id),
    unique key uk_device_command_request (request_id),
    key idx_command_dispatch (status, next_retry_time),
    key idx_command_timeout (status, deadline_time),
    key idx_command_session_status (session_id, status),
    key idx_command_device_time (device_code, create_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='ESP32 MQTT 命令记录与 Outbox';

create table t_device_wifi_config (
    wifi_config_id bigint auto_increment,
    node_id bigint not null,
    device_code varchar(64) not null,
    request_id varchar(64) not null,
    config_version bigint not null,
    ssid varchar(32) not null,
    password_configured tinyint(1) not null default 0,
    status tinyint not null default 0
    comment '0-下发中,1-已预置,2-已激活,3-失败,4-结果未知,5-已被替换',

    staged_time datetime default null,
    activated_time datetime default null,
    failure_message varchar(255) default null,

    create_time datetime not null default current_timestamp,
    update_time datetime not null
    default current_timestamp
    on update current_timestamp,

    primary key (wifi_config_id),
    unique key uk_wifi_config_request (request_id),
    unique key uk_wifi_config_node_version (node_id,config_version),
    key idx_wifi_config_node_status (node_id,status)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='ESP32 上游 WiFi 候选配置任务';
