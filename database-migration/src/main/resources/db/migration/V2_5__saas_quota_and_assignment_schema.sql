set @v2_5_existing_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_saas_plan',
          't_saas_plan_version',
          't_tenant_subscription',
          't_tenant_quota',
          't_tenant_usage_daily'
      )
);

set @v2_5_guard_sql = if(
    @v2_5_existing_table_count = 5,
    'select 1',
    'select json_extract(''V2_5_REQUIRED_V2_1_TABLES'', ''$'')'
);

prepare v2_5_guard_stmt from @v2_5_guard_sql;
execute v2_5_guard_stmt;
deallocate prepare v2_5_guard_stmt;

create temporary table v2_5_data_guard (
    check_name varchar(64) not null primary key
);

insert into v2_5_data_guard(check_name) values ('PLAN_VERSION_REFERENCE');
insert into v2_5_data_guard(check_name)
select case when (
    (select count(*)
     from t_saas_plan_version plan_version
     left join t_saas_plan plan_record on plan_record.plan_id = plan_version.plan_id
     where plan_record.plan_id is null
        or plan_version.member_limit < 0
        or plan_version.device_limit < 0
        or plan_version.concurrent_session_limit < 0
        or plan_version.daily_minutes_limit < 0
        or (plan_version.status = 'PUBLISHED' and plan_version.publish_time is null))
    +
    (select count(*)
     from t_saas_plan plan_record
     left join t_saas_plan_version plan_version
       on plan_version.plan_version_id = plan_record.current_published_version_id
      and plan_version.plan_id = plan_record.plan_id
     where plan_record.current_published_version_id is not null
       and (plan_version.plan_version_id is null
            or plan_version.status <> 'PUBLISHED'))
) = 0 then 'PLAN_VERSION_REFERENCE_OK' else 'PLAN_VERSION_REFERENCE' end;

insert into v2_5_data_guard(check_name) values ('SUBSCRIPTION_DATA');
insert into v2_5_data_guard(check_name)
select case when (
    select count(*)
    from t_tenant_subscription subscription_record
    left join t_tenant tenant_record
      on tenant_record.tenant_id = subscription_record.tenant_id
    left join t_saas_plan_version plan_version
      on plan_version.plan_version_id = subscription_record.plan_version_id
    where tenant_record.tenant_id is null
       or plan_version.plan_version_id is null
       or subscription_record.end_time <= subscription_record.start_time
       or (
           subscription_record.status in ('TRIAL', 'ACTIVE')
           and plan_version.status <> 'PUBLISHED'
       )
) = 0 then 'SUBSCRIPTION_DATA_OK' else 'SUBSCRIPTION_DATA' end;

insert into v2_5_data_guard(check_name) values ('QUOTA_DATA');
insert into v2_5_data_guard(check_name)
select case when (
    select count(*)
    from t_tenant_quota quota_record
    left join t_tenant tenant_record
      on tenant_record.tenant_id = quota_record.tenant_id
    where tenant_record.tenant_id is null
       or quota_record.limit_value < 0
       or quota_record.used_value < 0
       or quota_record.used_value > quota_record.limit_value
) = 0 then 'QUOTA_DATA_OK' else 'QUOTA_DATA' end;

insert into v2_5_data_guard(check_name) values ('USAGE_DATA');
insert into v2_5_data_guard(check_name)
select case when (
    select count(*)
    from t_tenant_usage_daily usage_record
    left join t_tenant tenant_record
      on tenant_record.tenant_id = usage_record.tenant_id
    where tenant_record.tenant_id is null
       or usage_record.used_value < 0
) = 0 then 'USAGE_DATA_OK' else 'USAGE_DATA' end;

drop temporary table v2_5_data_guard;

set @v2_5_new_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_tenant_plan_assignment',
          't_tenant_quota_reservation'
      )
);

set @v2_5_guard_sql = if(
    @v2_5_new_table_count in (0, 2),
    'select 1',
    'select json_extract(''V2_5_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_5_guard_stmt from @v2_5_guard_sql;
execute v2_5_guard_stmt;
deallocate prepare v2_5_guard_stmt;

set @v2_5_ddl_sql = if(
    @v2_5_new_table_count = 0,
    'create table t_tenant_plan_assignment (
        assignment_id bigint not null auto_increment,
        tenant_id bigint not null,
        plan_version_id bigint not null,
        client_request_id varchar(64) not null,
        source_type varchar(32) not null,
        source_reference varchar(128) not null,
        request_fingerprint char(64) not null,
        requested_start_time datetime not null,
        requested_end_time datetime not null,
        plan_snapshot_hash char(64) not null,
        status varchar(24) not null default ''PENDING'',
        subscription_id bigint default null,
        actor_user_id bigint not null,
        reason_code varchar(64) default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (assignment_id),
        unique key uk_plan_assignment_request (
            tenant_id,
            source_type,
            client_request_id
        ),
        key idx_plan_assignment_tenant_status (
            tenant_id,
            status,
            create_time,
            assignment_id
        ),
        key idx_plan_assignment_version (plan_version_id, assignment_id),
        key idx_plan_assignment_subscription (subscription_id),
        check (requested_end_time > requested_start_time),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''租户SaaS套餐幂等分配记录''',
    'select 1'
);

prepare v2_5_ddl_stmt from @v2_5_ddl_sql;
execute v2_5_ddl_stmt;
deallocate prepare v2_5_ddl_stmt;

set @v2_5_ddl_sql = if(
    @v2_5_new_table_count = 0,
    'create table t_tenant_quota_reservation (
        reservation_id bigint not null auto_increment,
        tenant_id bigint not null,
        quota_type varchar(32) not null,
        business_type varchar(32) not null,
        business_key varchar(128) not null,
        amount bigint not null,
        status varchar(16) not null default ''RESERVED'',
        expire_time datetime not null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (reservation_id),
        unique key uk_quota_reservation_business (
            tenant_id,
            quota_type,
            business_type,
            business_key
        ),
        key idx_quota_reservation_due (
            status,
            expire_time,
            reservation_id
        ),
        key idx_quota_reservation_tenant (
            tenant_id,
            quota_type,
            status,
            reservation_id
        ),
        check (amount > 0),
        check (status in (
            ''RESERVED'',
            ''CONFIRMED'',
            ''RELEASED'',
            ''EXPIRED''
        )),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''租户配额幂等预留记录''',
    'select 1'
);

prepare v2_5_ddl_stmt from @v2_5_ddl_sql;
execute v2_5_ddl_stmt;
deallocate prepare v2_5_ddl_stmt;
