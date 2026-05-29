use wifi;

drop table if exists t_esp32_node;
create table t_esp32_node(
    node_id bigint AUTO_INCREMENT,
    device_code varchar(64) NOT NULL,
    name varchar(64) NOT NULL,
    location varchar(128),
    ip varchar(45),
    firmware_version varchar(32),
    status tinyint NOT NULL DEFAULT 0,
    max_clients int NOT NULL DEFAULT 4,
    current_clients int NOT NULL DEFAULT 0,
    last_heartbeat datetime DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag tinyint NOT NULL DEFAULT 0,

    PRIMARY KEY (node_id),
    UNIQUE KEY idx_device_code(device_code),
    KEY idx_status(status)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

drop table if exists t_session;
create table t_session(
    session_id bigint AUTO_INCREMENT,
    user_id bigint NOT NULL,
    node_id bigint NOT NULL,
    mac varchar(17) NOT NULL,
    ip varchar(45),
    device_info varchar(255),
    login_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time datetime NOT NULL,
    logout_time datetime DEFAULT NULL,
    status tinyint NOT NULL DEFAULT 1,
    bytes_up bigint NOT NULL DEFAULT 0,
    bytes_down bigint NOT NULL DEFAULT 0,

    PRIMARY KEY (session_id),
    KEY idx_user(user_id),
    KEY idx_mac_status(mac, status),
    KEY idx_node(node_id),
    KEY idx_login_time(login_time)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

drop table if exists t_mac_blacklist;
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

drop table if exists t_traffic_log;
create table t_traffic_log(
    id bigint AUTO_INCREMENT,
    session_id bigint NOT NULL,
    mac varchar(17) NOT NULL,
    dst_ip varchar(45) NOT NULL,
    dst_port int,
    sni varchar(255),
    protocol varchar(16),
    bytes_up bigint NOT NULL DEFAULT 0,
    bytes_down bigint NOT NULL DEFAULT 0,
    log_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    KEY idx_mac_time(mac, log_time),
    KEY idx_session(session_id),
    KEY idx_dst_ip(dst_ip)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

insert into t_esp32_node(device_code, name, location, ip, firmware_version, status)
values ('esp32-main', '客厅ESP32网关', '客厅', '192.168.4.1', null, 0);

