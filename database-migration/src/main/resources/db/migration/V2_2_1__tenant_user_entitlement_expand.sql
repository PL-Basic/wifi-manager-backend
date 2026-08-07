set @default_tenant_id = (
    select tenant_id
    from t_tenant
    where tenant_code = 'default-tenant'
      and del_flag = 0
    limit 1
);

set @v2_2_1_guard_sql = if(
    @default_tenant_id is null,
    'select json_extract(''V2_2_1_DEFAULT_TENANT'', ''$'')',
    'select 1'
);

prepare v2_2_1_guard_stmt from @v2_2_1_guard_sql;
execute v2_2_1_guard_stmt;
deallocate prepare v2_2_1_guard_stmt;

set @v2_2_1_orphan_user_count = (
    (select count(*) from t_duration_purchase p
        left join sys_user u on u.user_id = p.user_id where u.user_id is null)
    + (select count(*) from t_network_entitlement e
        left join sys_user u on u.user_id = e.user_id where u.user_id is null)
    + (select count(*) from t_entitlement_usage_log l
        left join sys_user u on u.user_id = l.user_id where u.user_id is null)
    + (select count(*) from t_entitlement_order o
        left join sys_user u on u.user_id = o.user_id where u.user_id is null)
    + (select count(*) from t_payment_record p
        left join sys_user u on u.user_id = p.user_id where u.user_id is null)
    + (select count(*) from t_refund_record r
        left join sys_user u on u.user_id = r.user_id where u.user_id is null)
);

set @v2_2_1_guard_sql = if(
    @v2_2_1_orphan_user_count = 0,
    'select 1',
    'select json_extract(''V2_2_1_ORPHAN_USER'', ''$'')'
);

prepare v2_2_1_guard_stmt from @v2_2_1_guard_sql;
execute v2_2_1_guard_stmt;
deallocate prepare v2_2_1_guard_stmt;

set @v2_2_1_expand_object_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and (
          (table_name = 't_duration_purchase' and column_name = 'tenant_id')
          or (table_name = 't_network_entitlement'
              and column_name in ('tenant_id', 'unlimited_previous_mode', 'unlimited_previous_status'))
          or (table_name = 't_entitlement_usage_log' and column_name = 'tenant_id')
          or (table_name = 't_entitlement_order'
              and column_name in (
                  'tenant_id',
                  'pricing_version',
                  'grant_months',
                  'reference_amount_cents',
                  'subscription_ratio_bps',
                  'period_discount_bps'
              ))
          or (table_name = 't_payment_record' and column_name = 'tenant_id')
          or (table_name = 't_refund_record' and column_name = 'tenant_id')
          or (table_name = 't_trade_status_log' and column_name = 'tenant_id')
      )
) + (
    select count(distinct concat(table_name, ':', index_name))
    from information_schema.statistics
    where table_schema = database()
      and index_name in (
          'idx_duration_tenant_user_status',
          'idx_duration_tenant_fifo',
          'idx_entitlement_tenant_user',
          'idx_entitlement_tenant_mode_status',
          'idx_usage_tenant_request',
          'idx_usage_tenant_user_time',
          'idx_entitlement_order_tenant_user_time',
          'idx_entitlement_order_tenant_user_status',
          'idx_entitlement_order_tenant_request',
          'idx_payment_tenant_user_time',
          'idx_payment_tenant_request',
          'idx_refund_tenant_user_time',
          'idx_refund_tenant_status_time',
          'idx_trade_status_tenant_business'
      )
);

set @v2_2_1_guard_sql = if(
    @v2_2_1_expand_object_count in (0, 28),
    'select 1',
    'select json_extract(''V2_2_1_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_2_1_guard_stmt from @v2_2_1_guard_sql;
execute v2_2_1_guard_stmt;
deallocate prepare v2_2_1_guard_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_duration_purchase
    add column tenant_id bigint null after purchase_id,
    add key idx_duration_tenant_user_status (tenant_id, user_id, status),
    add key idx_duration_tenant_fifo (tenant_id, user_id, status, create_time, purchase_id)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_network_entitlement
    add column tenant_id bigint null after entitlement_id,
    add column unlimited_previous_mode varchar(16) null after mode,
    add column unlimited_previous_status tinyint null after status,
    add key idx_entitlement_tenant_user (tenant_id, user_id),
    add key idx_entitlement_tenant_mode_status (tenant_id, mode, status)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_entitlement_usage_log
    add column tenant_id bigint null after id,
    add key idx_usage_tenant_request (tenant_id, request_id),
    add key idx_usage_tenant_user_time (tenant_id, user_id, create_time)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_entitlement_order
    add column tenant_id bigint null after order_id,
    add column pricing_version int null after entitlement_mode,
    modify column grant_seconds bigint null comment ''旧版秒数或时长权益快照'',
    add column grant_months int null after grant_seconds,
    add column reference_amount_cents bigint null after amount_cents,
    add column subscription_ratio_bps int null after reference_amount_cents,
    add column period_discount_bps int null after subscription_ratio_bps,
    add key idx_entitlement_order_tenant_user_time (tenant_id, user_id, create_time),
    add key idx_entitlement_order_tenant_user_status (tenant_id, user_id, status),
    add key idx_entitlement_order_tenant_request (tenant_id, user_id, client_request_id)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_payment_record
    add column tenant_id bigint null after payment_id,
    add key idx_payment_tenant_user_time (tenant_id, user_id, create_time),
    add key idx_payment_tenant_request (tenant_id, user_id, request_id)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_refund_record
    add column tenant_id bigint null after refund_id,
    add key idx_refund_tenant_user_time (tenant_id, user_id, create_time),
    add key idx_refund_tenant_status_time (tenant_id, status, create_time)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

set @v2_2_1_ddl_sql = if(
    @v2_2_1_expand_object_count = 0,
    'alter table t_trade_status_log
    add column tenant_id bigint null after id,
    add key idx_trade_status_tenant_business (tenant_id, business_type, business_no, create_time)',
    'select 1'
);

prepare v2_2_1_ddl_stmt from @v2_2_1_ddl_sql;
execute v2_2_1_ddl_stmt;
deallocate prepare v2_2_1_ddl_stmt;

update t_duration_purchase set tenant_id = @default_tenant_id where tenant_id is null;
update t_network_entitlement set tenant_id = @default_tenant_id where tenant_id is null;
update t_entitlement_usage_log set tenant_id = @default_tenant_id where tenant_id is null;
update t_entitlement_order set tenant_id = @default_tenant_id where tenant_id is null;
update t_payment_record set tenant_id = @default_tenant_id where tenant_id is null;
update t_refund_record set tenant_id = @default_tenant_id where tenant_id is null;
update t_trade_status_log set tenant_id = @default_tenant_id where tenant_id is null;

update t_entitlement_order
set pricing_version = 1
where pricing_version is null;

create temporary table v2_2_1_data_guard (
    check_name varchar(64) not null primary key
);

insert into v2_2_1_data_guard(check_name) values ('TENANT_NULL');
insert into v2_2_1_data_guard(check_name)
select case when (
        (select count(*) from t_duration_purchase where tenant_id is null)
        + (select count(*) from t_network_entitlement where tenant_id is null)
        + (select count(*) from t_entitlement_usage_log where tenant_id is null)
        + (select count(*) from t_entitlement_order where tenant_id is null)
        + (select count(*) from t_payment_record where tenant_id is null)
        + (select count(*) from t_refund_record where tenant_id is null)
        + (select count(*) from t_trade_status_log where tenant_id is null)
    ) = 0 then 'TENANT_NULL_OK' else 'TENANT_NULL' end;

insert into v2_2_1_data_guard(check_name) values ('ORPHAN_USER');
insert into v2_2_1_data_guard(check_name)
select case when (
        (select count(*) from t_duration_purchase p
            left join sys_user u on u.user_id = p.user_id where u.user_id is null)
        + (select count(*) from t_network_entitlement e
            left join sys_user u on u.user_id = e.user_id where u.user_id is null)
        + (select count(*) from t_entitlement_usage_log l
            left join sys_user u on u.user_id = l.user_id where u.user_id is null)
        + (select count(*) from t_entitlement_order o
            left join sys_user u on u.user_id = o.user_id where u.user_id is null)
        + (select count(*) from t_payment_record p
            left join sys_user u on u.user_id = p.user_id where u.user_id is null)
        + (select count(*) from t_refund_record r
            left join sys_user u on u.user_id = r.user_id where u.user_id is null)
    ) = 0 then 'ORPHAN_USER_OK' else 'ORPHAN_USER' end;

insert into v2_2_1_data_guard(check_name) values ('ORPHAN_USAGE');
insert into v2_2_1_data_guard(check_name)
select case when (
        select count(*)
        from t_entitlement_usage_log l
        left join t_network_entitlement e
          on e.tenant_id = l.tenant_id
         and e.entitlement_id = l.entitlement_id
         and e.user_id = l.user_id
        where e.entitlement_id is null
    ) = 0 then 'ORPHAN_USAGE_OK' else 'ORPHAN_USAGE' end;

insert into v2_2_1_data_guard(check_name) values ('ORPHAN_PAYMENT');
insert into v2_2_1_data_guard(check_name)
select case when (
        select count(*)
        from t_payment_record p
        left join t_entitlement_order o
          on o.tenant_id = p.tenant_id
         and o.order_no = p.order_no
         and o.user_id = p.user_id
        where o.order_id is null
    ) = 0 then 'ORPHAN_PAYMENT_OK' else 'ORPHAN_PAYMENT' end;

insert into v2_2_1_data_guard(check_name) values ('ORPHAN_REFUND');
insert into v2_2_1_data_guard(check_name)
select case when (
        select count(*)
        from t_refund_record r
        left join t_entitlement_order o
          on o.tenant_id = r.tenant_id
         and o.order_no = r.order_no
         and o.user_id = r.user_id
        left join t_payment_record p
          on p.tenant_id = r.tenant_id
         and p.payment_no = r.payment_no
         and p.order_no = r.order_no
         and p.user_id = r.user_id
        left join t_duration_purchase d
          on d.tenant_id = r.tenant_id
         and d.purchase_id = r.purchase_id
         and d.user_id = r.user_id
        where o.order_id is null
           or p.payment_id is null
           or d.purchase_id is null
    ) = 0 then 'ORPHAN_REFUND_OK' else 'ORPHAN_REFUND' end;

insert into v2_2_1_data_guard(check_name) values ('ORPHAN_TRADE_STATUS');
insert into v2_2_1_data_guard(check_name)
select case when (
        select count(*)
        from t_trade_status_log l
        left join t_entitlement_order o
          on l.business_type = 'ORDER'
         and o.tenant_id = l.tenant_id
         and o.order_no = l.business_no
        left join t_payment_record p
          on l.business_type = 'PAYMENT'
         and p.tenant_id = l.tenant_id
         and p.payment_no = l.business_no
        left join t_refund_record r
          on l.business_type = 'REFUND'
         and r.tenant_id = l.tenant_id
         and r.refund_no = l.business_no
        where (l.business_type = 'ORDER' and o.order_id is null)
           or (l.business_type = 'PAYMENT' and p.payment_id is null)
           or (l.business_type = 'REFUND' and r.refund_id is null)
    ) = 0 then 'ORPHAN_TRADE_STATUS_OK' else 'ORPHAN_TRADE_STATUS' end;

drop temporary table v2_2_1_data_guard;
