set @v2_10_code_ready = lower('${p3cCodeReady}');

set @v2_10_guard_sql = if(
    @v2_10_code_ready = 'true',
    'select 1',
    'select json_extract(''V2_10_TENANT_CODE_NOT_READY'', ''$'')'
);

prepare v2_10_guard_stmt from @v2_10_guard_sql;
execute v2_10_guard_stmt;
deallocate prepare v2_10_guard_stmt;

create temporary table v2_10_contract_guard (
    check_name varchar(64) not null primary key
);

insert into v2_10_contract_guard(check_name) values ('TENANT_NULL');
insert into v2_10_contract_guard(check_name)
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

insert into v2_10_contract_guard(check_name) values ('TENANT_REFERENCE');
insert into v2_10_contract_guard(check_name)
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

insert into v2_10_contract_guard(check_name) values ('LOCATION_RELATION_TENANT');
insert into v2_10_contract_guard(check_name)
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

insert into v2_10_contract_guard(check_name) values ('ALERT_RULE_TENANT');
insert into v2_10_contract_guard(check_name)
select case when (
    select count(*)
    from t_alert_event alert_record
    left join t_access_rule rule_record
      on rule_record.tenant_id = alert_record.tenant_id
     and rule_record.rule_code = alert_record.rule_code
    where rule_record.id is null
) = 0 then 'ALERT_RULE_TENANT_OK' else 'ALERT_RULE_TENANT' end;

insert into v2_10_contract_guard(check_name) values ('RULE_HIT_RELATION_TENANT');
insert into v2_10_contract_guard(check_name)
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

insert into v2_10_contract_guard(check_name) values ('GEOFENCE_STATE_RELATION_TENANT');
insert into v2_10_contract_guard(check_name)
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

insert into v2_10_contract_guard(check_name) values ('GEOFENCE_EVENT_RELATION_TENANT');
insert into v2_10_contract_guard(check_name)
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

insert into v2_10_contract_guard(check_name) values ('AUDIT_SCOPE_TENANT');
insert into v2_10_contract_guard(check_name)
select case when (
    select count(*)
    from t_audit_log
    where scope_type not in ('PLATFORM', 'TENANT')
       or (scope_type = 'PLATFORM' and tenant_id is not null)
       or (scope_type = 'TENANT' and tenant_id is null)
) = 0 then 'AUDIT_SCOPE_TENANT_OK' else 'AUDIT_SCOPE_TENANT' end;

insert into v2_10_contract_guard(check_name) values ('UNKNOWN_AUDIT_ACTION');
insert into v2_10_contract_guard(check_name)
select case when (
    select count(*)
    from t_audit_log
    where action not in (
        'alert.handle',
        'auth.platform_context',
        'auth.platform_tenant_context',
        'auth.register',
        'auth.reset_password',
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
        'session.portal-authorize',
        'tenant.create',
        'tenant.status',
        'tenant.update',
        'user.delete',
        'user.purge',
        'user.status',
        'user.update'
    )
) = 0 then 'UNKNOWN_AUDIT_ACTION_OK' else 'UNKNOWN_AUDIT_ACTION' end;

insert into v2_10_contract_guard(check_name) values ('DUPLICATE_TENANT_KEY');
insert into v2_10_contract_guard(check_name)
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

drop temporary table v2_10_contract_guard;

alter table t_access_rule
    modify column tenant_id bigint not null,
    drop key uk_rule_code,
    drop key idx_rule_tenant_code,
    add unique key uk_rule_tenant_code (tenant_id, rule_code);

alter table t_client_location
    modify column tenant_id bigint not null;

alter table t_location_authorization
    modify column tenant_id bigint not null,
    drop primary key,
    drop key idx_location_auth_tenant_user,
    add primary key (tenant_id, user_id);

alter table t_audit_log
    add constraint chk_audit_scope_tenant check (
        (scope_type = 'PLATFORM' and tenant_id is null)
        or (scope_type = 'TENANT' and tenant_id is not null)
    );

alter table t_alert_event
    modify column tenant_id bigint not null;

alter table t_rule_hit
    modify column tenant_id bigint not null,
    drop key uk_rule_hit_event,
    drop key idx_rule_hit_tenant_event,
    add unique key uk_rule_hit_tenant_event (
        tenant_id,
        device_code,
        event_id,
        rule_code
    );

alter table t_geofence
    modify column tenant_id bigint not null;

alter table t_geofence_state
    modify column tenant_id bigint not null,
    drop key uk_geofence_session,
    drop key idx_geofence_state_tenant_session,
    add unique key uk_geofence_state_tenant_session (
        tenant_id,
        fence_id,
        session_id
    );

alter table t_geofence_event
    modify column tenant_id bigint not null,
    drop key uk_geofence_location_event,
    drop key idx_geofence_event_tenant_location,
    add unique key uk_geofence_event_tenant_location (
        tenant_id,
        fence_id,
        location_id,
        event_type
    );
