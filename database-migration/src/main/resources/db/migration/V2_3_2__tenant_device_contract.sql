create temporary table v2_3_2_contract_guard (
    check_name varchar(64) not null primary key
);

insert into v2_3_2_contract_guard(check_name) values ('TENANT_NULL');
insert into v2_3_2_contract_guard(check_name)
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

insert into v2_3_2_contract_guard(check_name) values ('TENANT_REFERENCE');
insert into v2_3_2_contract_guard(check_name)
select case when (
        (select count(*) from t_esp32_node r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_session r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_mac_blacklist r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_traffic_log r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_client_signal r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_session_user_guard r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_client_access_guard r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_device_command r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
        + (select count(*) from t_device_wifi_config r
            left join t_tenant t on t.tenant_id = r.tenant_id where t.tenant_id is null)
    ) = 0 then 'TENANT_REFERENCE_OK' else 'TENANT_REFERENCE' end;

insert into v2_3_2_contract_guard(check_name) values ('SESSION_NODE_TENANT');
insert into v2_3_2_contract_guard(check_name)
select case when (
        select count(*)
        from t_session s
        left join t_esp32_node n on n.node_id = s.node_id
        where n.node_id is null
           or n.tenant_id <> s.tenant_id
    ) = 0 then 'SESSION_NODE_TENANT_OK' else 'SESSION_NODE_TENANT' end;

insert into v2_3_2_contract_guard(check_name) values ('TRAFFIC_RELATION_TENANT');
insert into v2_3_2_contract_guard(check_name)
select case when (
        select count(*)
        from t_traffic_log traffic
        left join t_esp32_node by_code on by_code.device_code = traffic.device_code
        left join t_esp32_node by_id on by_id.node_id = traffic.node_id
        left join t_session session_record on session_record.session_id = traffic.session_id
        where by_code.node_id is null
           or by_id.node_id is null
           or session_record.session_id is null
           or by_code.tenant_id <> traffic.tenant_id
           or by_id.tenant_id <> traffic.tenant_id
           or session_record.tenant_id <> traffic.tenant_id
    ) = 0 then 'TRAFFIC_RELATION_TENANT_OK' else 'TRAFFIC_RELATION_TENANT' end;

insert into v2_3_2_contract_guard(check_name) values ('SIGNAL_RELATION_TENANT');
insert into v2_3_2_contract_guard(check_name)
select case when (
        select count(*)
        from t_client_signal signal_record
        left join t_esp32_node by_code on by_code.device_code = signal_record.device_code
        left join t_esp32_node by_id on by_id.node_id = signal_record.node_id
        left join t_session session_record on session_record.session_id = signal_record.session_id
        where by_code.node_id is null
           or by_id.node_id is null
           or by_code.tenant_id <> signal_record.tenant_id
           or by_id.tenant_id <> signal_record.tenant_id
           or (signal_record.session_id > 0 and (
               session_record.session_id is null
               or session_record.tenant_id <> signal_record.tenant_id
           ))
    ) = 0 then 'SIGNAL_RELATION_TENANT_OK' else 'SIGNAL_RELATION_TENANT' end;

insert into v2_3_2_contract_guard(check_name) values ('WIFI_RELATION_TENANT');
insert into v2_3_2_contract_guard(check_name)
select case when (
        select count(*)
        from t_device_wifi_config wifi_config
        left join t_esp32_node by_code on by_code.device_code = wifi_config.device_code
        left join t_esp32_node by_id on by_id.node_id = wifi_config.node_id
        where by_code.node_id is null
           or by_id.node_id is null
           or by_code.tenant_id <> wifi_config.tenant_id
           or by_id.tenant_id <> wifi_config.tenant_id
    ) = 0 then 'WIFI_RELATION_TENANT_OK' else 'WIFI_RELATION_TENANT' end;

insert into v2_3_2_contract_guard(check_name) values ('COMMAND_RELATION_TENANT');
insert into v2_3_2_contract_guard(check_name)
select case when (
        select count(*)
        from t_device_command command_record
        left join t_esp32_node by_code on by_code.device_code = command_record.device_code
        left join t_esp32_node by_id on by_id.node_id = command_record.node_id
        left join t_session session_record on session_record.session_id = command_record.session_id
        where not (
            command_record.purpose = 'RUNTIME_TEST'
            and command_record.status in (2, 3, 4, 5)
            and by_code.node_id is null
            and by_id.node_id is null
            and session_record.session_id is null
        )
          and (
              (by_code.node_id is null
               and by_id.node_id is null
               and session_record.session_id is null)
              or (by_code.node_id is not null
                  and by_code.tenant_id <> command_record.tenant_id)
              or (by_id.node_id is not null
                  and by_id.tenant_id <> command_record.tenant_id)
              or (session_record.session_id is not null
                  and session_record.tenant_id <> command_record.tenant_id)
          )
    ) = 0 then 'COMMAND_RELATION_TENANT_OK' else 'COMMAND_RELATION_TENANT' end;

insert into v2_3_2_contract_guard(check_name) values ('DUPLICATE_TENANT_KEY');
insert into v2_3_2_contract_guard(check_name)
select case when (
        (select count(*) from (
            select tenant_id, mac
            from t_mac_blacklist
            group by tenant_id, mac
            having count(*) > 1
        ) duplicate_blacklist)
        + (select count(*) from (
            select tenant_id, device_code, event_id
            from t_traffic_log
            group by tenant_id, device_code, event_id
            having count(*) > 1
        ) duplicate_traffic)
        + (select count(*) from (
            select tenant_id, user_id
            from t_session_user_guard
            group by tenant_id, user_id
            having count(*) > 1
        ) duplicate_session_guard)
        + (select count(*) from (
            select tenant_id, mac
            from t_client_access_guard
            group by tenant_id, mac
            having count(*) > 1
        ) duplicate_client_guard)
    ) = 0 then 'DUPLICATE_TENANT_KEY_OK' else 'DUPLICATE_TENANT_KEY' end;

drop temporary table v2_3_2_contract_guard;

alter table t_esp32_node
    modify column tenant_id bigint not null;

alter table t_session
    modify column tenant_id bigint not null;

alter table t_mac_blacklist
    modify column tenant_id bigint not null,
    drop key idx_mac,
    drop key idx_blacklist_tenant_mac,
    add unique key uk_blacklist_tenant_mac (tenant_id, mac);

alter table t_traffic_log
    modify column tenant_id bigint not null,
    drop key uk_traffic_device_event,
    drop key idx_traffic_tenant_device_event,
    add unique key uk_traffic_tenant_device_event (tenant_id, device_code, event_id);

alter table t_client_signal
    modify column tenant_id bigint not null;

alter table t_session_user_guard
    modify column tenant_id bigint not null,
    drop primary key,
    drop key idx_session_guard_tenant_user,
    add primary key (tenant_id, user_id);

alter table t_client_access_guard
    modify column tenant_id bigint not null,
    drop primary key,
    drop key idx_client_guard_tenant_mac,
    add primary key (tenant_id, mac);

alter table t_device_command
    modify column tenant_id bigint not null;

alter table t_device_wifi_config
    modify column tenant_id bigint not null;
