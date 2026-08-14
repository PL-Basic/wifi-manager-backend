set @default_tenant_id = (
    select tenant_id
    from t_tenant
    where tenant_code = 'default-tenant'
      and del_flag = 0
    limit 1
);

set @v2_3_1_guard_sql = if(
    @default_tenant_id is null,
    'select json_extract(''V2_3_1_DEFAULT_TENANT'', ''$'')',
    'select 1'
);

prepare v2_3_1_guard_stmt from @v2_3_1_guard_sql;
execute v2_3_1_guard_stmt;
deallocate prepare v2_3_1_guard_stmt;

set @v2_3_1_expand_object_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and column_name = 'tenant_id'
      and table_name in (
          't_esp32_node',
          't_session',
          't_mac_blacklist',
          't_traffic_log',
          't_client_signal',
          't_session_user_guard',
          't_client_access_guard',
          't_device_command',
          't_device_wifi_config'
      )
) + (
    select count(distinct concat(table_name, ':', index_name))
    from information_schema.statistics
    where table_schema = database()
      and (
          (table_name = 't_esp32_node' and index_name = 'idx_node_tenant_list')
          or (table_name = 't_session' and index_name in (
              'idx_session_tenant_login',
              'idx_session_tenant_user_status',
              'idx_session_tenant_mac_status',
              'idx_session_tenant_node_status'
          ))
          or (table_name = 't_mac_blacklist' and index_name in (
              'idx_blacklist_tenant_mac',
              'idx_blacklist_tenant_expire'
          ))
          or (table_name = 't_traffic_log' and index_name in (
              'idx_traffic_tenant_device_event',
              'idx_traffic_tenant_log',
              'idx_traffic_tenant_mac_time',
              'idx_traffic_tenant_session_time',
              'idx_traffic_tenant_node_time'
          ))
          or (table_name = 't_client_signal' and index_name in (
              'idx_signal_tenant_report',
              'idx_signal_tenant_device_mac_time',
              'idx_signal_tenant_node_mac_time',
              'idx_signal_tenant_session_time'
          ))
          or (table_name = 't_session_user_guard' and index_name = 'idx_session_guard_tenant_user')
          or (table_name = 't_client_access_guard' and index_name = 'idx_client_guard_tenant_mac')
          or (table_name = 't_device_command' and index_name in (
              'idx_command_tenant_id',
              'idx_command_tenant_device_time',
              'idx_command_tenant_session_status',
              'idx_command_tenant_mac_time'
          ))
          or (table_name = 't_device_wifi_config' and index_name in (
              'idx_wifi_config_tenant_node_version',
              'idx_wifi_config_tenant_node_status',
              'idx_wifi_config_tenant_device_request'
          ))
      )
);

set @v2_3_1_guard_sql = if(
    @v2_3_1_expand_object_count in (0, 34),
    'select 1',
    'select json_extract(''V2_3_1_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_3_1_guard_stmt from @v2_3_1_guard_sql;
execute v2_3_1_guard_stmt;
deallocate prepare v2_3_1_guard_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_esp32_node
    add column tenant_id bigint null after node_id,
    add key idx_node_tenant_list (tenant_id, del_flag, status, node_id)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_session
    add column tenant_id bigint null after session_id,
    add key idx_session_tenant_login (tenant_id, login_time, session_id),
    add key idx_session_tenant_user_status (tenant_id, user_id, status, login_time, session_id),
    add key idx_session_tenant_mac_status (tenant_id, mac, status, session_id),
    add key idx_session_tenant_node_status (tenant_id, node_id, status, login_time)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_mac_blacklist
    add column tenant_id bigint null after id,
    add key idx_blacklist_tenant_mac (tenant_id, mac),
    add key idx_blacklist_tenant_expire (tenant_id, expire_time)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_traffic_log
    add column tenant_id bigint null after id,
    add key idx_traffic_tenant_device_event (tenant_id, device_code, event_id),
    add key idx_traffic_tenant_log (tenant_id, log_time, id),
    add key idx_traffic_tenant_mac_time (tenant_id, mac, log_time),
    add key idx_traffic_tenant_session_time (tenant_id, session_id, log_time),
    add key idx_traffic_tenant_node_time (tenant_id, node_id, log_time)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_client_signal
    add column tenant_id bigint null after id,
    add key idx_signal_tenant_report (tenant_id, report_time, id),
    add key idx_signal_tenant_device_mac_time (tenant_id, device_code, mac, report_time),
    add key idx_signal_tenant_node_mac_time (tenant_id, node_id, mac, report_time),
    add key idx_signal_tenant_session_time (tenant_id, session_id, report_time)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_session_user_guard
    add column tenant_id bigint null after user_id,
    add key idx_session_guard_tenant_user (tenant_id, user_id)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_client_access_guard
    add column tenant_id bigint null after mac,
    add key idx_client_guard_tenant_mac (tenant_id, mac)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_device_command
    add column tenant_id bigint null after command_id,
    add key idx_command_tenant_id (tenant_id, command_id),
    add key idx_command_tenant_device_time (tenant_id, device_code, create_time),
    add key idx_command_tenant_session_status (tenant_id, session_id, status, command_id),
    add key idx_command_tenant_mac_time (tenant_id, mac, create_time)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_ddl_sql = if(
    @v2_3_1_expand_object_count = 0,
    'alter table t_device_wifi_config
    add column tenant_id bigint null after wifi_config_id,
    add key idx_wifi_config_tenant_node_version (tenant_id, node_id, config_version),
    add key idx_wifi_config_tenant_node_status (tenant_id, node_id, status),
    add key idx_wifi_config_tenant_device_request (tenant_id, device_code, request_id)',
    'select 1'
);

prepare v2_3_1_ddl_stmt from @v2_3_1_ddl_sql;
execute v2_3_1_ddl_stmt;
deallocate prepare v2_3_1_ddl_stmt;

set @v2_3_1_signal_device_node_mismatch_count = (
    select count(*)
    from t_client_signal s
    join t_esp32_node by_code on by_code.device_code = s.device_code
    join t_esp32_node by_id on by_id.node_id = s.node_id
    where by_code.node_id <> by_id.node_id
);

set @v2_3_1_command_session_mac_mismatch_count = (
    select count(*)
    from t_device_command c
    join t_session s on s.session_id = c.session_id
    where c.mac is not null
      and c.mac <> s.mac
);

set @v2_3_1_unresolved_command_count = (
    select count(*)
    from t_device_command c
    left join t_esp32_node by_code on by_code.device_code = c.device_code
    left join t_esp32_node by_id on by_id.node_id = c.node_id
    left join t_session s on s.session_id = c.session_id
    where by_code.node_id is null
      and by_id.node_id is null
      and s.session_id is null
);

update t_esp32_node
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_session s
join t_esp32_node n on n.node_id = s.node_id
set s.tenant_id = n.tenant_id
where s.tenant_id is null
  and n.tenant_id is not null;

update t_session
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_mac_blacklist
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_traffic_log t
join t_esp32_node n on n.device_code = t.device_code
set t.tenant_id = n.tenant_id
where t.tenant_id is null
  and n.tenant_id is not null;

update t_traffic_log t
join t_esp32_node n on n.node_id = t.node_id
set t.tenant_id = n.tenant_id
where t.tenant_id is null
  and n.tenant_id is not null;

update t_traffic_log t
join t_session s on s.session_id = t.session_id
set t.tenant_id = s.tenant_id
where t.tenant_id is null
  and s.tenant_id is not null;

update t_traffic_log
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_client_signal s
join t_esp32_node n on n.device_code = s.device_code
set s.tenant_id = n.tenant_id
where s.tenant_id is null
  and n.tenant_id is not null;

update t_client_signal s
join t_esp32_node n on n.node_id = s.node_id
set s.tenant_id = n.tenant_id
where s.tenant_id is null
  and n.tenant_id is not null;

update t_client_signal s
join t_session session_record on session_record.session_id = s.session_id
set s.tenant_id = session_record.tenant_id
where s.tenant_id is null
  and s.session_id > 0
  and session_record.tenant_id is not null;

update t_client_signal
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_session_user_guard g
join (
    select user_id, min(tenant_id) as tenant_id
    from t_session
    where tenant_id is not null
    group by user_id
    having count(distinct tenant_id) = 1
) session_owner on session_owner.user_id = g.user_id
set g.tenant_id = session_owner.tenant_id
where g.tenant_id is null;

update t_session_user_guard
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_client_access_guard g
join (
    select mac, min(tenant_id) as tenant_id
    from t_session
    where tenant_id is not null
    group by mac
    having count(distinct tenant_id) = 1
) session_owner on session_owner.mac = g.mac
set g.tenant_id = session_owner.tenant_id
where g.tenant_id is null;

update t_client_access_guard
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_device_command c
join t_esp32_node n on n.device_code = c.device_code
set c.tenant_id = n.tenant_id
where c.tenant_id is null
  and n.tenant_id is not null;

update t_device_command c
join t_esp32_node n on n.node_id = c.node_id
set c.tenant_id = n.tenant_id
where c.tenant_id is null
  and n.tenant_id is not null;

update t_device_command c
join t_session s on s.session_id = c.session_id
set c.tenant_id = s.tenant_id
where c.tenant_id is null
  and s.tenant_id is not null;

update t_device_command
set tenant_id = @default_tenant_id
where tenant_id is null;

update t_device_wifi_config w
join t_esp32_node n on n.node_id = w.node_id
set w.tenant_id = n.tenant_id
where w.tenant_id is null
  and n.tenant_id is not null;

update t_device_wifi_config w
join t_esp32_node n on n.device_code = w.device_code
set w.tenant_id = n.tenant_id
where w.tenant_id is null
  and n.tenant_id is not null;

update t_device_wifi_config
set tenant_id = @default_tenant_id
where tenant_id is null;

create temporary table v2_3_1_data_guard (
    check_name varchar(64) not null primary key
);

insert into v2_3_1_data_guard(check_name) values ('TENANT_NULL');
insert into v2_3_1_data_guard(check_name)
select case when (
        (select count(*) from t_esp32_node where tenant_id is null)
        + (select count(*) from t_session where tenant_id is null)
        + (select count(*) from t_mac_blacklist where tenant_id is null)
        + (select count(*) from t_traffic_log where tenant_id is null)
        + (select count(*) from t_client_signal where tenant_id is null)
        + (select count(*) from t_session_user_guard where tenant_id is null)
        + (select count(*) from t_client_access_guard where tenant_id is null)
        + (select count(*) from t_device_command where tenant_id is null)
        + (select count(*) from t_device_wifi_config where tenant_id is null)
    ) = 0 then 'TENANT_NULL_OK' else 'TENANT_NULL' end;

insert into v2_3_1_data_guard(check_name) values ('TENANT_REFERENCE');
insert into v2_3_1_data_guard(check_name)
select case when (
        (select count(*) from t_esp32_node r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_session r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_mac_blacklist r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_traffic_log r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_client_signal r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_session_user_guard r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_client_access_guard r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_device_command r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_device_wifi_config r left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
    ) = 0 then 'TENANT_REFERENCE_OK' else 'TENANT_REFERENCE' end;

insert into v2_3_1_data_guard(check_name) values ('SESSION_NODE_TENANT');
insert into v2_3_1_data_guard(check_name)
select case when (
        select count(*)
        from t_session s
        left join t_esp32_node n on n.node_id = s.node_id
        where n.node_id is null
           or n.tenant_id <> s.tenant_id
    ) = 0 then 'SESSION_NODE_TENANT_OK' else 'SESSION_NODE_TENANT' end;

insert into v2_3_1_data_guard(check_name) values ('TRAFFIC_RELATION_TENANT');
insert into v2_3_1_data_guard(check_name)
select case when (
        select count(*)
        from t_traffic_log t
        left join t_esp32_node by_code on by_code.device_code = t.device_code
        left join t_esp32_node by_id on by_id.node_id = t.node_id
        left join t_session s on s.session_id = t.session_id
        where by_code.node_id is null
           or by_id.node_id is null
           or s.session_id is null
           or by_code.tenant_id <> t.tenant_id
           or by_id.tenant_id <> t.tenant_id
           or s.tenant_id <> t.tenant_id
    ) = 0 then 'TRAFFIC_RELATION_TENANT_OK' else 'TRAFFIC_RELATION_TENANT' end;

insert into v2_3_1_data_guard(check_name) values ('SIGNAL_RELATION_TENANT');
insert into v2_3_1_data_guard(check_name)
select case when (
        select count(*)
        from t_client_signal signal_record
        left join t_esp32_node by_code on by_code.device_code = signal_record.device_code
        left join t_esp32_node by_id on by_id.node_id = signal_record.node_id
        left join t_session s on s.session_id = signal_record.session_id
        where by_code.node_id is null
           or by_id.node_id is null
           or by_code.tenant_id <> signal_record.tenant_id
           or by_id.tenant_id <> signal_record.tenant_id
           or (signal_record.session_id > 0 and (
               s.session_id is null or s.tenant_id <> signal_record.tenant_id
           ))
    ) = 0 then 'SIGNAL_RELATION_TENANT_OK' else 'SIGNAL_RELATION_TENANT' end;

insert into v2_3_1_data_guard(check_name) values ('WIFI_RELATION_TENANT');
insert into v2_3_1_data_guard(check_name)
select case when (
        select count(*)
        from t_device_wifi_config w
        left join t_esp32_node by_code on by_code.device_code = w.device_code
        left join t_esp32_node by_id on by_id.node_id = w.node_id
        where by_code.node_id is null
           or by_id.node_id is null
           or by_code.tenant_id <> w.tenant_id
           or by_id.tenant_id <> w.tenant_id
    ) = 0 then 'WIFI_RELATION_TENANT_OK' else 'WIFI_RELATION_TENANT' end;

insert into v2_3_1_data_guard(check_name) values ('COMMAND_RELATION_TENANT');
insert into v2_3_1_data_guard(check_name)
select case when (
        select count(*)
        from t_device_command c
        left join t_esp32_node by_code on by_code.device_code = c.device_code
        left join t_esp32_node by_id on by_id.node_id = c.node_id
        left join t_session s on s.session_id = c.session_id
        where not (
            c.purpose = 'RUNTIME_TEST'
            and c.status in (2, 3, 4, 5)
            and by_code.node_id is null
            and by_id.node_id is null
            and s.session_id is null
        )
          and (
              (by_code.node_id is null and by_id.node_id is null and s.session_id is null)
              or (by_code.node_id is not null and by_code.tenant_id <> c.tenant_id)
              or (by_id.node_id is not null and by_id.tenant_id <> c.tenant_id)
              or (s.session_id is not null and s.tenant_id <> c.tenant_id)
          )
    ) = 0 then 'COMMAND_RELATION_TENANT_OK' else 'COMMAND_RELATION_TENANT' end;

select
    @v2_3_1_signal_device_node_mismatch_count as signal_device_node_mismatch_count,
    @v2_3_1_command_session_mac_mismatch_count as command_session_mac_mismatch_count,
    @v2_3_1_unresolved_command_count as unresolved_command_count;

drop temporary table v2_3_1_data_guard;
