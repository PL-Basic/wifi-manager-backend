alter table t_entitlement_order
    add column order_type varchar(24) not null default 'PURCHASE' comment 'PURCHASE或REWARD' after product_code,
    add column remark varchar(255) default null after close_reason;
