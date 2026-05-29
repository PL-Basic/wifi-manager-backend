use wifi;

drop table if exists sys_user;
create table sys_user(
    user_id bigint auto_increment,
    username varchar(64) not null comment '用户名',
    password varchar(128) not null comment '加密密码',
    nickname varchar(64) not null comment '用户昵称',
    email varchar(64) unique comment '邮箱',
    phone varchar(32) unique comment '手机号',
    avatar varchar(255) comment '头像',
    role tinyint not null default 2 comment '角色：0-超级管理员 1-管理员 2-普通用户',
    status tinyint not null default 1 comment '状态：0-禁用 1-启用',
    max_connections int comment '最大连接数',
    daily_quota_minutes int default 480 comment '每日默认使用分钟数',
    expire_time datetime comment '用户有效期',
    last_login_time datetime default null comment '最后登录时间',
    last_login_ip varchar(45) default null comment '最后登录IP',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    del_flag tinyint not null default 0 comment '逻辑删除：0-未删除 1-已删除',

    primary key (user_id),
    unique key idx_username (username),
    key idx_status (status),
    key idx_last_login_time (last_login_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='系统用户表';
