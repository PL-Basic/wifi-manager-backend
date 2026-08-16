set @v2_4_1_default_tenant_count = (
    select count(*)
    from t_tenant
    where tenant_code = 'default-tenant'
      and del_flag = 0
);

set @v2_4_1_default_tenant_id = (
    select min(tenant_id)
    from t_tenant
    where tenant_code = 'default-tenant'
      and del_flag = 0
);

set @v2_4_1_guard_sql = if(
    @v2_4_1_default_tenant_count = 1,
    'select 1',
    'select json_extract(''V2_4_1_DEFAULT_TENANT'', ''$'')'
);

prepare v2_4_1_guard_stmt from @v2_4_1_guard_sql;
execute v2_4_1_guard_stmt;
deallocate prepare v2_4_1_guard_stmt;

set @v2_4_1_expand_object_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and (
          (table_name = 't_access_rule' and column_name in ('tenant_id', 'version'))
          or (table_name = 't_client_location' and column_name = 'tenant_id')
          or (table_name = 't_location_authorization' and column_name in ('tenant_id', 'version'))
          or (table_name = 't_audit_log' and column_name in ('tenant_id', 'scope_type'))
          or (table_name = 't_alert_event' and column_name in ('tenant_id', 'version'))
          or (table_name = 't_rule_hit' and column_name = 'tenant_id')
          or (table_name = 't_geofence' and column_name in ('tenant_id', 'version'))
          or (table_name = 't_geofence_state' and column_name = 'tenant_id')
          or (table_name = 't_geofence_event' and column_name = 'tenant_id')
      )
) + (
    select count(distinct concat(table_name, ':', index_name))
    from information_schema.statistics
    where table_schema = database()
      and (
          (table_name = 't_access_rule' and index_name in (
              'idx_rule_tenant_code',
              'idx_rule_tenant_enabled'
          ))
          or (table_name = 't_client_location' and index_name in (
              'idx_location_tenant_report',
              'idx_location_tenant_session_report'
          ))
          or (table_name = 't_location_authorization' and index_name in (
              'idx_location_auth_tenant_user',
              'idx_location_auth_tenant_enabled'
          ))
          or (table_name = 't_audit_log' and index_name = 'idx_audit_scope_tenant_time')
          or (table_name = 't_alert_event' and index_name in (
              'idx_alert_tenant_status',
              'idx_alert_tenant_rule'
          ))
          or (table_name = 't_rule_hit' and index_name in (
              'idx_rule_hit_tenant_event',
              'idx_rule_hit_tenant_time'
          ))
          or (table_name = 't_geofence' and index_name = 'idx_geofence_tenant_enabled')
          or (table_name = 't_geofence_state' and index_name = 'idx_geofence_state_tenant_session')
          or (table_name = 't_geofence_event' and index_name in (
              'idx_geofence_event_tenant_location',
              'idx_geofence_event_tenant_time'
          ))
      )
);

set @v2_4_1_guard_sql = if(
    @v2_4_1_expand_object_count in (0, 29),
    'select 1',
    'select json_extract(''V2_4_1_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_4_1_guard_stmt from @v2_4_1_guard_sql;
execute v2_4_1_guard_stmt;
deallocate prepare v2_4_1_guard_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_access_rule
        add column tenant_id bigint null after id,
        add column version int not null default 0 after enabled,
        add key idx_rule_tenant_code (tenant_id, rule_code),
        add key idx_rule_tenant_enabled (tenant_id, enabled, del_flag, rule_type, id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_client_location
        add column tenant_id bigint null after id,
        add key idx_location_tenant_report (tenant_id, report_time, id),
        add key idx_location_tenant_session_report (tenant_id, session_id, report_time, id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_location_authorization
        add column tenant_id bigint null first,
        add column version int not null default 0 after enabled,
        add key idx_location_auth_tenant_user (tenant_id, user_id),
        add key idx_location_auth_tenant_enabled (tenant_id, enabled, update_time)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_audit_log
        add column tenant_id bigint null after id,
        add column scope_type varchar(16) not null default ''UNCLASSIFIED'' after tenant_id,
        add key idx_audit_scope_tenant_time (scope_type, tenant_id, create_time, id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_alert_event
        add column tenant_id bigint null after id,
        add column version int not null default 0 after status,
        add key idx_alert_tenant_status (tenant_id, status, level, create_time, id),
        add key idx_alert_tenant_rule (tenant_id, rule_code, create_time, id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_rule_hit
        add column tenant_id bigint null after id,
        add key idx_rule_hit_tenant_event (tenant_id, device_code, event_id, rule_code),
        add key idx_rule_hit_tenant_time (tenant_id, hit_time, id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_geofence
        add column tenant_id bigint null after fence_id,
        add column version int not null default 0 after enabled,
        add key idx_geofence_tenant_enabled (tenant_id, enabled, del_flag, fence_id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_geofence_state
        add column tenant_id bigint null after state_id,
        add key idx_geofence_state_tenant_session (tenant_id, fence_id, session_id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

set @v2_4_1_ddl_sql = if(
    @v2_4_1_expand_object_count = 0,
    'alter table t_geofence_event
        add column tenant_id bigint null after event_id,
        add key idx_geofence_event_tenant_location (tenant_id, fence_id, location_id, event_type),
        add key idx_geofence_event_tenant_time (tenant_id, event_time, event_id)',
    'select 1'
);
prepare v2_4_1_ddl_stmt from @v2_4_1_ddl_sql;
execute v2_4_1_ddl_stmt;
deallocate prepare v2_4_1_ddl_stmt;

update t_access_rule
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_location_authorization
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_geofence
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_client_location location_record
join t_session session_record on session_record.session_id = location_record.session_id
set location_record.tenant_id = session_record.tenant_id
where location_record.tenant_id is null;

update t_client_location location_record
join t_esp32_node node_record on node_record.node_id = location_record.node_id
set location_record.tenant_id = node_record.tenant_id
where location_record.tenant_id is null;

update t_client_location location_record
join t_esp32_node node_record on node_record.device_code = location_record.device_code
set location_record.tenant_id = node_record.tenant_id
where location_record.tenant_id is null;

update t_client_location
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_alert_event alert_record
join t_access_rule rule_record on rule_record.rule_code = alert_record.rule_code
set alert_record.tenant_id = rule_record.tenant_id
where alert_record.tenant_id is null;

update t_alert_event
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_rule_hit hit_record
join t_access_rule rule_record on rule_record.id = hit_record.rule_id
set hit_record.tenant_id = rule_record.tenant_id
where hit_record.tenant_id is null;

update t_rule_hit hit_record
join t_session session_record on session_record.session_id = hit_record.session_id
set hit_record.tenant_id = session_record.tenant_id
where hit_record.tenant_id is null;

update t_rule_hit hit_record
join t_esp32_node node_record on node_record.node_id = hit_record.node_id
set hit_record.tenant_id = node_record.tenant_id
where hit_record.tenant_id is null;

update t_rule_hit hit_record
join t_esp32_node node_record on node_record.device_code = hit_record.device_code
set hit_record.tenant_id = node_record.tenant_id
where hit_record.tenant_id is null;

update t_rule_hit
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_geofence_state state_record
join t_geofence fence_record on fence_record.fence_id = state_record.fence_id
set state_record.tenant_id = fence_record.tenant_id
where state_record.tenant_id is null;

update t_geofence_state state_record
join t_session session_record on session_record.session_id = state_record.session_id
set state_record.tenant_id = session_record.tenant_id
where state_record.tenant_id is null;

update t_geofence_state
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_geofence_event event_record
join t_geofence fence_record on fence_record.fence_id = event_record.fence_id
set event_record.tenant_id = fence_record.tenant_id
where event_record.tenant_id is null;

update t_geofence_event event_record
join t_session session_record on session_record.session_id = event_record.session_id
set event_record.tenant_id = session_record.tenant_id
where event_record.tenant_id is null;

update t_geofence_event
set tenant_id = @v2_4_1_default_tenant_id
where tenant_id is null;

update t_audit_log
set scope_type = 'PLATFORM',
    tenant_id = null
where action in (
    'auth.platform_context',
    'auth.platform_tenant_context',
    'auth.register',
    'auth.reset_password',
    'tenant.create',
    'tenant.status',
    'tenant.update',
    'user.delete',
    'user.purge',
    'user.status',
    'user.update'
);

update t_audit_log
set scope_type = 'TENANT',
    tenant_id = @v2_4_1_default_tenant_id
where action in (
    'alert.handle',
    'blacklist.add',
    'blacklist.remove',
    'device.allow',
    'device.allow-client',
    'device.create',
    'device.delete',
    'device.kick',
    'device.manual-block-traffic',
    'device.manual-disconnect-mac',
    'device.restore',
    'device.update',
    'device.wifi.stage',
    'entitlement.adjust',
    'entitlement.reward-order.create',
    'entitlement.unlimited.adjust',
    'geofence.create',
    'geofence.delete',
    'geofence.toggle',
    'geofence.update',
    'location.consent.grant',
    'location.consent.revoke',
    'location.history.clear',
    'location.report',
    'monitor.auto.block-traffic',
    'monitor.auto.disconnect-mac',
    'refund.apply',
    'refund.channel.result',
    'refund.review',
    'rule.create',
    'rule.delete',
    'rule.toggle',
    'rule.update',
    'session.admin-revoke',
    'session.logout',
    'session.portal-authorize'
);

create temporary table v2_4_1_data_guard (
    check_name varchar(64) not null primary key
);

insert into v2_4_1_data_guard(check_name) values ('TENANT_NULL');
insert into v2_4_1_data_guard(check_name)
select case when (
    (select count(*) from t_access_rule where tenant_id is null)
    + (select count(*) from t_client_location where tenant_id is null)
    + (select count(*) from t_location_authorization where tenant_id is null)
    + (select count(*) from t_alert_event where tenant_id is null)
    + (select count(*) from t_rule_hit where tenant_id is null)
    + (select count(*) from t_geofence where tenant_id is null)
    + (select count(*) from t_geofence_state where tenant_id is null)
    + (select count(*) from t_geofence_event where tenant_id is null)
) = 0 then 'TENANT_NULL_OK' else 'TENANT_NULL' end;

insert into v2_4_1_data_guard(check_name) values ('TENANT_REFERENCE');
insert into v2_4_1_data_guard(check_name)
select case when (
    select count(*)
    from (
        select tenant_id from t_access_rule
        union all select tenant_id from t_client_location
        union all select tenant_id from t_location_authorization
        union all select tenant_id from t_audit_log where tenant_id is not null
        union all select tenant_id from t_alert_event
        union all select tenant_id from t_rule_hit
        union all select tenant_id from t_geofence
        union all select tenant_id from t_geofence_state
        union all select tenant_id from t_geofence_event
    ) scoped_record
    left join t_tenant tenant_record on tenant_record.tenant_id = scoped_record.tenant_id
    where tenant_record.tenant_id is null
) = 0 then 'TENANT_REFERENCE_OK' else 'TENANT_REFERENCE' end;

insert into v2_4_1_data_guard(check_name) values ('LOCATION_RELATION_TENANT');
insert into v2_4_1_data_guard(check_name)
select case when (
    select count(*)
    from t_client_location location_record
    left join t_session session_record on session_record.session_id = location_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = location_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = location_record.device_code
    where (location_record.session_id is not null and (
              session_record.session_id is null
              or session_record.tenant_id <> location_record.tenant_id
          ))
       or (location_record.node_id is not null and (
              node_by_id.node_id is null
              or node_by_id.tenant_id <> location_record.tenant_id
          ))
       or (location_record.device_code is not null and (
              node_by_code.node_id is null
              or node_by_code.tenant_id <> location_record.tenant_id
          ))
) = 0 then 'LOCATION_RELATION_TENANT_OK' else 'LOCATION_RELATION_TENANT' end;

insert into v2_4_1_data_guard(check_name) values ('ALERT_RULE_TENANT');
insert into v2_4_1_data_guard(check_name)
select case when (
    select count(*)
    from t_alert_event alert_record
    left join t_access_rule rule_record
      on rule_record.tenant_id = alert_record.tenant_id
     and rule_record.rule_code = alert_record.rule_code
    where rule_record.id is null
) = 0 then 'ALERT_RULE_TENANT_OK' else 'ALERT_RULE_TENANT' end;

insert into v2_4_1_data_guard(check_name) values ('RULE_HIT_RELATION_TENANT');
insert into v2_4_1_data_guard(check_name)
select case when (
    select count(*)
    from t_rule_hit hit_record
    left join t_access_rule rule_record on rule_record.id = hit_record.rule_id
    left join t_alert_event alert_record on alert_record.id = hit_record.alert_id
    left join t_session session_record on session_record.session_id = hit_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = hit_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = hit_record.device_code
    where rule_record.id is null
       or rule_record.tenant_id <> hit_record.tenant_id
       or rule_record.rule_code <> hit_record.rule_code
       or session_record.session_id is null
       or session_record.tenant_id <> hit_record.tenant_id
       or node_by_id.node_id is null
       or node_by_id.tenant_id <> hit_record.tenant_id
       or node_by_code.node_id is null
       or node_by_code.tenant_id <> hit_record.tenant_id
       or (hit_record.alert_id is not null and (
              alert_record.id is null
              or alert_record.tenant_id <> hit_record.tenant_id
          ))
) = 0 then 'RULE_HIT_RELATION_TENANT_OK' else 'RULE_HIT_RELATION_TENANT' end;

insert into v2_4_1_data_guard(check_name) values ('GEOFENCE_STATE_RELATION_TENANT');
insert into v2_4_1_data_guard(check_name)
select case when (
    select count(*)
    from t_geofence_state state_record
    left join t_geofence fence_record on fence_record.fence_id = state_record.fence_id
    left join t_client_location location_record on location_record.id = state_record.last_location_id
    left join t_session session_record on session_record.session_id = state_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = state_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = state_record.device_code
    where fence_record.fence_id is null
       or fence_record.tenant_id <> state_record.tenant_id
       or location_record.id is null
       or location_record.tenant_id <> state_record.tenant_id
       or session_record.session_id is null
       or session_record.tenant_id <> state_record.tenant_id
       or node_by_id.node_id is null
       or node_by_id.tenant_id <> state_record.tenant_id
       or node_by_code.node_id is null
       or node_by_code.tenant_id <> state_record.tenant_id
) = 0 then 'GEOFENCE_STATE_RELATION_TENANT_OK' else 'GEOFENCE_STATE_RELATION_TENANT' end;

insert into v2_4_1_data_guard(check_name) values ('GEOFENCE_EVENT_RELATION_TENANT');
insert into v2_4_1_data_guard(check_name)
select case when (
    select count(*)
    from t_geofence_event event_record
    left join t_geofence fence_record on fence_record.fence_id = event_record.fence_id
    left join t_client_location location_record on location_record.id = event_record.location_id
    left join t_session session_record on session_record.session_id = event_record.session_id
    left join t_esp32_node node_by_id on node_by_id.node_id = event_record.node_id
    left join t_esp32_node node_by_code on node_by_code.device_code = event_record.device_code
    where fence_record.fence_id is null
       or fence_record.tenant_id <> event_record.tenant_id
       or location_record.id is null
       or location_record.tenant_id <> event_record.tenant_id
       or session_record.session_id is null
       or session_record.tenant_id <> event_record.tenant_id
       or node_by_id.node_id is null
       or node_by_id.tenant_id <> event_record.tenant_id
       or node_by_code.node_id is null
       or node_by_code.tenant_id <> event_record.tenant_id
) = 0 then 'GEOFENCE_EVENT_RELATION_TENANT_OK' else 'GEOFENCE_EVENT_RELATION_TENANT' end;

insert into v2_4_1_data_guard(check_name) values ('DUPLICATE_TENANT_KEY');
insert into v2_4_1_data_guard(check_name)
select case when (
    (select count(*) from (
        select tenant_id, rule_code
        from t_access_rule
        group by tenant_id, rule_code
        having count(*) > 1
    ) duplicate_rule)
    + (select count(*) from (
        select tenant_id, user_id
        from t_location_authorization
        group by tenant_id, user_id
        having count(*) > 1
    ) duplicate_location_authorization)
    + (select count(*) from (
        select tenant_id, device_code, event_id, rule_code
        from t_rule_hit
        group by tenant_id, device_code, event_id, rule_code
        having count(*) > 1
    ) duplicate_rule_hit)
    + (select count(*) from (
        select tenant_id, fence_id, session_id
        from t_geofence_state
        group by tenant_id, fence_id, session_id
        having count(*) > 1
    ) duplicate_geofence_state)
    + (select count(*) from (
        select tenant_id, fence_id, location_id, event_type
        from t_geofence_event
        group by tenant_id, fence_id, location_id, event_type
        having count(*) > 1
    ) duplicate_geofence_event)
) = 0 then 'DUPLICATE_TENANT_KEY_OK' else 'DUPLICATE_TENANT_KEY' end;

select count(*) as unknown_audit_action_count
from t_audit_log
where scope_type = 'UNCLASSIFIED';

drop temporary table v2_4_1_data_guard;
