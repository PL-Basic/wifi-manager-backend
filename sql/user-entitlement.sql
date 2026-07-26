use wifi;
create table if not exists t_duration_purchase (
    purchase_id bigint AUTO_INCREMENT,
    order_no varchar(64) NOT NULL ,
    user_id bigint NOT NULL ,
    purchased_seconds bigint NOT NULL ,
    remaining_seconds bigint NOT NULL ,
    paid_amount_cents bigint NOT NULL ,
    refundable tinyint NOT NULL DEFAULT 1,
    status tinyint NOT NULL DEFAULT 1 comment '1-可用，2-耗尽，3-已退款',
    refunded_amount_cents bigint DEFAULT NULL,
    refund_time datetime DEFAULT NULL,
    create_time datetime not NULL DEFAULT current_timestamp,
    update_time datetime not NULL DEFAULT current_timestamp ON UPDATE current_timestamp,

    primary key (purchase_id),
    unique key uk_duration_order_no (order_no),
    key idx_duration_user_status (user_id, status),
    key idx_duration_fifo (user_id, status, create_time, purchase_id)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='购买时长订单';

create table if not exists t_network_entitlement (
    entitlement_id bigint auto_increment,
    user_id bigint not null,
    mode varchar(16) not null comment 'SUBSCRIPTION或DURATION',
    subscription_start_time datetime default null,
    subscription_end_time datetime default null,
    remaining_seconds bigint not null default 0,
    status tinyint not null default 1 comment '0-停用，1-有效',
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,

    primary key (entitlement_id),
    unique key uk_entitlement_user (user_id),
    key idx_entitlement_mode_status (mode, status),
    key idx_entitlement_end_time (subscription_end_time)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='网络访问权益';

create table if not exists t_entitlement_usage_log (
    id bigint auto_increment,
    entitlement_id bigint not null,
    user_id bigint not null,
    request_id varchar(64) not null,
    line_no int not null,
    purchase_id bigint default null,
    authorization_mode varchar(16) not null,
    session_id bigint default null,
    change_seconds bigint not null comment '正数增加，负数消费',
    before_seconds bigint not null,
    after_seconds bigint not null,
    reason varchar(32) not null,
    create_time datetime not null default current_timestamp,

    primary key (id),
    unique key uk_usage_request_line(request_id, line_no),
    key idx_usage_purchase(purchase_id),
    key idx_usage_entitlement_time (entitlement_id, create_time),
    key idx_usage_user_time (user_id, create_time),
    key idx_usage_session (session_id)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='权益时长变更流水';