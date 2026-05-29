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
    create_time datetime not null default current_timestamp comment '创建时间',

    primary key (id),
    key idx_target_scene_status (target, scene, status),
    key idx_expire_time (expire_time),
    key idx_create_time (create_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='验证码记录表';
