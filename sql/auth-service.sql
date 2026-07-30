use wifi;

drop table if exists t_verify_code;
create table t_verify_code(
    id bigint auto_increment,
    target varchar(128) not null comment '接收方：手机号或邮箱',
    target_type varchar(16) not null comment '接收方类型：phone/email',
    scene varchar(32) not null comment '使用场景：register/login/reset_password/bind_contact',
    code varchar(16) not null comment '验证码',
    status tinyint not null default 0 comment '状态：0未使用，1已使用，2已过期',
    expire_time datetime not null comment '过期时间',
    verify_time datetime default null comment '验证通过时间',
    send_ip varchar(45) default null comment '发送请求IP',
    verify_ip varchar(45) default null comment '验证请求IP',
    send_status tinyint not null default 0 comment '发送状态：0待发送，1发送成功，2发送失败',
    send_time datetime default null comment '发送完成时间',
    send_error varchar(512) default null comment '发送失败原因',
    create_time datetime not null default current_timestamp comment '创建时间',

    primary key (id),
    key idx_target_scene_status (target, scene, status),
    key idx_target_scene_create_time (target, scene, create_time),
    key idx_send_ip_scene_create_time (send_ip, scene, create_time),
    key idx_expire_time (expire_time),
    key idx_create_time (create_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='验证码记录表';


drop table if exists t_login_fail_record;
create table t_login_fail_record (
    id bigint auto_increment primary key,

    account varchar(128) not null comment '登录账号：用户名/手机号/邮箱',
    login_type varchar(16) not null comment '登录类型：username/contact',
    request_ip varchar(45) not null comment '请求IP',

    fail_count int not null default 0 comment '连续失败次数',
    lock_until datetime default null comment '锁定截止时间',
    last_fail_time datetime default null comment '最近失败时间',

    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',

    unique key uk_account_type_ip (account, login_type, request_ip),
    key idx_lock_until (lock_until),
    key idx_update_time (update_time)
) default charset = utf8mb4 collate = utf8mb4_unicode_ci comment = '登录失败记录表';

drop table if exists t_oauth_state;
create table t_oauth_state (
    state_id bigint auto_increment,
    state_hash char(64) collate utf8mb4_bin not null comment 'OAuth state 的 SHA-256，不保存原始 state',
    provider varchar(16) not null comment 'github/qq/wechat',
    purpose varchar(16) not null comment 'login/bind',
    bind_user_id bigint default null comment '绑定目的对应的可信本地用户',
    return_uri varchar(512) default null comment 'Provider 回调完成后的安全回跳地址',

    authorization_code_hash char(64) collate utf8mb4_bin default null comment '授权码 SHA-256，用于回调重放保护',
    status tinyint not null default 0 comment '0待回调 1处理中 2已完成 3失败',
    result_status varchar(32) default null,
    result_user_id bigint default null,
    result_message varchar(255) default null,

    expire_time datetime not null,
    consume_time datetime default null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,

    primary key (state_id),
    unique key uk_oauth_state_hash (state_hash),
    unique key uk_oauth_provider_code (provider,authorization_code_hash),
    key idx_oauth_state_expire (status, expire_time),
    key idx_oauth_state_user (bind_user_id,create_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='OAuth 授权状态与回调幂等记录';