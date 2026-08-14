set @v2_9_required_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_tenant',
          't_tenant_plan_assignment',
          't_tenant_quota_reservation',
          't_default_tenant_membership_outbox',
          't_user_operation_request',
          't_announcement',
          't_ai_review_task',
          't_esp32_node',
          't_session',
          't_device_command'
      )
);

set @v2_9_guard_sql = if(
    @v2_9_required_table_count = 10,
    'select 1',
    'select json_extract(''V2_9_REQUIRED_TABLES'', ''$'')'
);

prepare v2_9_guard_stmt from @v2_9_guard_sql;
execute v2_9_guard_stmt;
deallocate prepare v2_9_guard_stmt;

set @v2_9_existing_object_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_tenant_creation_receipt',
          't_tenant_domain_outbox',
          't_announcement_action_request'
      )
) + (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and (
          (table_name = 't_user_operation_request'
              and column_name in (
                  'client_request_id',
                  'request_fingerprint',
                  'version'
              ))
          or (table_name = 't_default_tenant_membership_outbox'
              and column_name in (
                  'worker_id',
                  'lease_until',
                  'claimed_time'
              ))
          or (table_name = 't_tenant_quota_reservation'
              and column_name = 'event_id')
          or (table_name = 't_ai_review_task'
              and column_name in (
                  'next_retry_time',
                  'worker_id',
                  'lease_until',
                  'claimed_time',
                  'last_error_code'
              ))
          or (table_name = 't_esp32_node'
              and column_name in (
                  'creator_user_id',
                  'client_request_id',
                  'request_fingerprint',
                  'quota_reservation_reference',
                  'version'
              ))
          or (table_name = 't_session'
              and column_name in (
                  'client_request_id',
                  'request_fingerprint',
                  'quota_reservation_reference',
                  'version'
              ))
          or (table_name = 't_device_command'
              and column_name in (
                  'actor_user_id',
                  'client_request_id',
                  'request_fingerprint',
                  'dispatch_worker_id',
                  'dispatch_lease_until',
                  'dispatch_claimed_time'
              ))
      )
) + (
    select count(distinct concat(table_name, ':', index_name))
    from information_schema.statistics
    where table_schema = database()
      and (
          (table_name = 't_user_operation_request'
              and index_name = 'uk_user_operation_request')
          or (table_name = 't_default_tenant_membership_outbox'
              and index_name = 'idx_default_membership_claim')
          or (table_name = 't_tenant_quota_reservation'
              and index_name = 'uk_quota_reservation_event')
          or (table_name = 't_ai_review_task'
              and index_name = 'idx_ai_review_due')
          or (table_name = 't_esp32_node'
              and index_name = 'uk_node_create_request')
          or (table_name = 't_session'
              and index_name = 'uk_session_authorize_request')
          or (table_name = 't_device_command'
              and index_name in (
                  'uk_device_command_client_request',
                  'idx_command_dispatch_lease'
              ))
      )
);

set @v2_9_guard_sql = if(
    @v2_9_existing_object_count = 0,
    'select 1',
    'select json_extract(''V2_9_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_9_guard_stmt from @v2_9_guard_sql;
execute v2_9_guard_stmt;
deallocate prepare v2_9_guard_stmt;

create table t_tenant_creation_receipt (
    creation_receipt_id bigint not null auto_increment,
    platform_actor_id bigint not null,
    client_request_id varchar(64) not null,
    request_fingerprint char(64) not null,
    target_tenant_id bigint default null,
    receipt_status varchar(16) not null default 'PENDING',
    result_code varchar(64) default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (creation_receipt_id),
    unique key uk_tenant_creation_request (
        platform_actor_id,
        client_request_id
    ),
    key idx_tenant_creation_target (
        target_tenant_id,
        creation_receipt_id
    ),
    check (receipt_status in ('PENDING', 'APPLIED', 'REJECTED')),
    check (version >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Tenant creation idempotency receipt';

create table t_tenant_domain_outbox (
    outbox_id bigint not null auto_increment,
    event_id varchar(64) not null,
    tenant_id bigint not null,
    aggregate_type varchar(32) not null,
    aggregate_id bigint not null,
    context_version bigint default null,
    event_type varchar(64) not null,
    outbox_status varchar(16) not null default 'PENDING',
    attempt_count int not null default 0,
    next_attempt_time datetime not null default current_timestamp,
    worker_id varchar(64) default null,
    lease_until datetime default null,
    claimed_time datetime default null,
    published_time datetime default null,
    last_error_code varchar(64) default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (outbox_id),
    unique key uk_tenant_domain_event (event_id),
    key idx_tenant_domain_due (
        outbox_status,
        next_attempt_time,
        lease_until,
        outbox_id
    ),
    key idx_tenant_domain_aggregate (
        tenant_id,
        aggregate_type,
        aggregate_id,
        outbox_id
    ),
    check (aggregate_type in ('TENANT', 'PLAN_ASSIGNMENT')),
    check (outbox_status in (
        'PENDING',
        'PROCESSING',
        'RETRY',
        'SENT',
        'DEAD'
    )),
    check (attempt_count >= 0),
    check (version >= 0),
    check (
        (outbox_status = 'PROCESSING'
            and worker_id is not null
            and lease_until is not null
            and claimed_time is not null)
        or (outbox_status <> 'PROCESSING'
            and worker_id is null
            and lease_until is null)
    )
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Tenant lifecycle and assignment result Outbox';

create table t_announcement_action_request (
    action_request_id bigint not null auto_increment,
    scope_type varchar(16) not null,
    tenant_id bigint default null,
    scope_key varchar(96)
        generated always as (
            case
                when scope_type = 'PLATFORM' then 'PLATFORM'
                when scope_type = 'TENANT'
                    then concat('TENANT:', cast(tenant_id as char))
                else null
            end
        ) stored,
    announcement_id bigint not null,
    actor_user_id bigint not null,
    action_type varchar(16) not null,
    client_request_id varchar(64) not null,
    request_fingerprint char(64) not null,
    expected_version int not null,
    request_status varchar(16) not null default 'PENDING',
    result_announcement_status varchar(24) default null,
    result_version int default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (action_request_id),
    unique key uk_announcement_action_request (
        scope_type,
        scope_key,
        actor_user_id,
        action_type,
        client_request_id
    ),
    key idx_announcement_action_target (
        scope_type,
        scope_key,
        announcement_id,
        action_request_id
    ),
    check (
        (scope_type = 'PLATFORM' and tenant_id is null)
        or (scope_type = 'TENANT' and tenant_id is not null)
    ),
    check (action_type in ('UPDATE', 'PUBLISH', 'WITHDRAW')),
    check (request_status in ('PENDING', 'APPLIED', 'REJECTED')),
    check (expected_version >= 0),
    check (result_version is null or result_version >= 0),
    check (version >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Announcement update and lifecycle idempotency receipt';

alter table t_user_operation_request
    add column client_request_id varchar(64) default null
        after requester_name,
    add column request_fingerprint char(64) default null
        after client_request_id,
    add column version int not null default 0
        after reject_reason,
    add unique key uk_user_operation_request (
        requester_id,
        request_type,
        client_request_id
    ),
    add constraint chk_user_operation_version
        check (version >= 0);

alter table t_default_tenant_membership_outbox
    add column worker_id varchar(64) default null
        after next_retry_time,
    add column lease_until datetime default null
        after worker_id,
    add column claimed_time datetime default null
        after lease_until,
    add key idx_default_membership_claim (
        status,
        next_retry_time,
        lease_until,
        outbox_id
    );

alter table t_tenant_quota_reservation
    add column event_id varchar(64) default null
        after business_key,
    add unique key uk_quota_reservation_event (
        tenant_id,
        event_id
    );

alter table t_ai_review_task
    add column next_retry_time datetime default null
        after attempt_count,
    add column worker_id varchar(64) default null
        after next_retry_time,
    add column lease_until datetime default null
        after worker_id,
    add column claimed_time datetime default null
        after lease_until,
    add column last_error_code varchar(64) default null
        after claimed_time,
    add key idx_ai_review_due (
        task_status,
        next_retry_time,
        lease_until,
        review_task_id
    );

alter table t_esp32_node
    add column creator_user_id bigint default null
        after tenant_id,
    add column client_request_id varchar(64) default null
        after creator_user_id,
    add column request_fingerprint char(64) default null
        after client_request_id,
    add column quota_reservation_reference varchar(128) default null
        after request_fingerprint,
    add column version int not null default 0
        after current_clients,
    add unique key uk_node_create_request (
        tenant_id,
        creator_user_id,
        client_request_id
    ),
    add constraint chk_esp32_node_version
        check (version >= 0);

alter table t_session
    add column client_request_id varchar(64) default null
        after user_id,
    add column request_fingerprint char(64) default null
        after client_request_id,
    add column quota_reservation_reference varchar(128) default null
        after request_fingerprint,
    add column version int not null default 0
        after status,
    add unique key uk_session_authorize_request (
        tenant_id,
        user_id,
        client_request_id
    ),
    add constraint chk_session_version
        check (version >= 0);

alter table t_device_command
    add column actor_user_id bigint default null
        after tenant_id,
    add column client_request_id varchar(64) default null
        after actor_user_id,
    add column request_fingerprint char(64) default null
        after client_request_id,
    add column dispatch_worker_id varchar(64) default null
        after next_retry_time,
    add column dispatch_lease_until datetime default null
        after dispatch_worker_id,
    add column dispatch_claimed_time datetime default null
        after dispatch_lease_until,
    add unique key uk_device_command_client_request (
        tenant_id,
        actor_user_id,
        purpose,
        client_request_id
    ),
    add key idx_command_dispatch_lease (
        status,
        next_retry_time,
        dispatch_lease_until,
        command_id
    );
