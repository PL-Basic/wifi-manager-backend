create table t_verify_code (
    id bigint auto_increment primary key,
    target varchar(128) not null comment '手机号或邮箱',
    target_type varchar(16) not null comment 'phone/email',
    scene varchar(32) not null comment 'register/login/reset_password/bind_contact',

    code_hash varchar(100) default null comment '本地验证码BCrypt摘要，云端核验成功后保存提交值摘要',
    verification_provider varchar(32) not null comment 'email-smtp/local/aliyun-number-auth',
    provider_out_id varchar(64) default null comment '后端生成的供应商关联ID',
    provider_biz_id varchar(128) default null comment '供应商短信业务ID',
    provider_request_id varchar(128) default null comment '供应商发送请求ID',
    provider_send_code varchar(64) default null comment '供应商发送结果码',

    send_status tinyint not null default 0 comment '0待发送 1成功 2失败',
    send_time datetime default null,
    send_error varchar(512) default null,

    verify_status tinyint not null default 0 comment '0未核验 1已核验',
    verify_attempt_count int not null default 0 comment '有效核验尝试次数',
    provider_verify_code varchar(64) default null comment '供应商核验接口结果码',
    provider_verify_result varchar(32) default null comment 'PASS/UNKNOWN等结果',
    verify_error varchar(512) default null,

    status tinyint not null default 0 comment '0可消费 1已消费 2已过期',
    expire_time datetime not null,
    verify_time datetime default null comment '首次核验通过时间',
    consume_time datetime default null comment '业务消费时间',
    send_ip varchar(45) default null,
    verify_ip varchar(45) default null,
    create_time datetime not null default current_timestamp,

    unique key uk_verify_provider_out_id (verification_provider, provider_out_id),
    key idx_target_scene_status (target,scene,status),
    key idx_target_scene_create_time (target,scene,create_time),
    key idx_send_ip_scene_create_time (send_ip,scene,create_time),
    key idx_expire_time (expire_time),
    key idx_create_time (create_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='验证码发送、核验与消费记录';
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
