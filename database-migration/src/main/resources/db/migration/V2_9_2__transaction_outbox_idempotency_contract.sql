set @v2_9_2_required_default_outbox_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and column_name in (
          'outbox_id',
          'event_id',
          'idempotency_key',
          'request_fingerprint',
          'user_id',
          'role',
          'status',
          'retry_count',
          'next_retry_time',
          'worker_id',
          'lease_until',
          'claimed_time',
          'last_error',
          'create_time',
          'update_time'
      )
);

set @v2_9_2_default_claim_index_column_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and index_name = 'idx_default_membership_claim'
      and (
          (seq_in_index = 1 and column_name = 'status')
          or (seq_in_index = 2 and column_name = 'next_retry_time')
          or (seq_in_index = 3 and column_name = 'lease_until')
          or (seq_in_index = 4 and column_name = 'outbox_id')
      )
);

set @v2_9_2_default_status_constraint = (
    select min(constraints.constraint_name)
    from information_schema.table_constraints constraints
    join information_schema.check_constraints checks
      on checks.constraint_schema = constraints.constraint_schema
     and checks.constraint_name = constraints.constraint_name
    where constraints.table_schema = database()
      and constraints.table_name =
          't_default_tenant_membership_outbox'
      and constraints.constraint_type = 'CHECK'
      and lower(checks.check_clause) like '%status%'
      and lower(checks.check_clause) like '%pending%'
      and lower(checks.check_clause) like '%retry%'
      and lower(checks.check_clause) like '%succeeded%'
      and lower(checks.check_clause) not like '%processing%'
      and lower(checks.check_clause) not like '%dead%'
);

set @v2_9_2_default_status_constraint_count = (
    select count(*)
    from information_schema.table_constraints constraints
    join information_schema.check_constraints checks
      on checks.constraint_schema = constraints.constraint_schema
     and checks.constraint_name = constraints.constraint_name
    where constraints.table_schema = database()
      and constraints.table_name =
          't_default_tenant_membership_outbox'
      and constraints.constraint_type = 'CHECK'
      and lower(checks.check_clause) like '%status%'
      and lower(checks.check_clause) like '%pending%'
      and lower(checks.check_clause) like '%retry%'
      and lower(checks.check_clause) like '%succeeded%'
      and lower(checks.check_clause) not like '%processing%'
      and lower(checks.check_clause) not like '%dead%'
);

set @v2_9_2_target_object_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_name in (
          't_user_auth_session_revoke_outbox',
          't_entitlement_lease_receipt'
      )
) + (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and constraint_name in (
          'chk_default_membership_outbox_status',
          'chk_default_membership_claim_owner'
      )
);

set @v2_9_2_invalid_existing_default_outbox_count = (
    select count(*)
    from t_default_tenant_membership_outbox
    where status not in ('PENDING', 'RETRY', 'SUCCEEDED')
       or worker_id is not null
       or lease_until is not null
);

set @v2_9_2_guard_sql = if(
    @v2_9_2_required_default_outbox_column_count = 15
        and @v2_9_2_default_claim_index_column_count = 4
        and @v2_9_2_default_status_constraint_count = 1
        and @v2_9_2_default_status_constraint is not null
        and @v2_9_2_target_object_count = 0
        and @v2_9_2_invalid_existing_default_outbox_count = 0,
    'select 1',
    'select json_extract(''V2_9_2_REQUIRED_CONTRACT_STATE'', ''$'')'
);

prepare v2_9_2_guard_stmt from @v2_9_2_guard_sql;
execute v2_9_2_guard_stmt;
deallocate prepare v2_9_2_guard_stmt;

set @v2_9_2_drop_status_check_sql = concat(
    'alter table t_default_tenant_membership_outbox drop check `',
    replace(@v2_9_2_default_status_constraint, '`', '``'),
    '`'
);

prepare v2_9_2_ddl_stmt from @v2_9_2_drop_status_check_sql;
execute v2_9_2_ddl_stmt;
deallocate prepare v2_9_2_ddl_stmt;

alter table t_default_tenant_membership_outbox
    comment='User-owned default tenant membership Outbox',
    add constraint chk_default_membership_outbox_status
        check (status in (
            'PENDING',
            'PROCESSING',
            'RETRY',
            'SUCCEEDED',
            'DEAD'
        )),
    add constraint chk_default_membership_claim_owner
        check (
            (status = 'PROCESSING'
                and worker_id is not null
                and char_length(trim(worker_id)) > 0
                and lease_until is not null
                and claimed_time is not null
                and lease_until > claimed_time)
            or (status <> 'PROCESSING'
                and worker_id is null
                and lease_until is null)
        );

create table t_user_auth_session_revoke_outbox (
    outbox_id bigint not null auto_increment,
    event_id varchar(64) not null,
    user_id bigint not null,
    revoke_reason varchar(64) not null,
    status varchar(16) not null default 'PENDING',
    retry_count int not null default 0,
    next_retry_time datetime default current_timestamp,
    worker_id varchar(64) default null,
    lease_until datetime default null,
    claimed_time datetime default null,
    completed_time datetime default null,
    last_error_code varchar(64) default null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (outbox_id),
    unique key uk_user_auth_revoke_event (event_id),
    key idx_user_auth_revoke_claim (
        status,
        next_retry_time,
        lease_until,
        outbox_id
    ),
    key idx_user_auth_revoke_user (
        user_id,
        status,
        outbox_id
    ),
    constraint chk_user_auth_revoke_status
        check (status in (
            'PENDING',
            'PROCESSING',
            'RETRY',
            'SUCCEEDED',
            'DEAD'
        )),
    constraint chk_user_auth_revoke_retry
        check (retry_count >= 0),
    constraint chk_user_auth_revoke_claim_owner
        check (
            (status = 'PROCESSING'
                and worker_id is not null
                and char_length(trim(worker_id)) > 0
                and lease_until is not null
                and claimed_time is not null
                and lease_until > claimed_time)
            or (status <> 'PROCESSING'
                and worker_id is null
                and lease_until is null)
        ),
    constraint chk_user_auth_revoke_terminal
        check (
            (status in ('SUCCEEDED', 'DEAD')
                and completed_time is not null
                and next_retry_time is null)
            or (status not in ('SUCCEEDED', 'DEAD')
                and completed_time is null
                and next_retry_time is not null)
        )
) engine=innodb default charset=utf8mb4
  collate=utf8mb4_unicode_ci
  comment='User-owned Auth session revoke Outbox';

create table t_entitlement_lease_receipt (
    receipt_id bigint not null auto_increment,
    tenant_id bigint not null,
    request_id varchar(64) not null,
    request_fingerprint char(64) not null,
    entitlement_id bigint default null,
    user_id bigint not null,
    session_id bigint not null,
    usage_seconds bigint not null,
    requested_ttl_seconds int not null,
    receipt_status varchar(16) not null default 'PENDING',
    result_allowed tinyint(1) default null,
    result_entitlement_id bigint default null,
    result_mode varchar(16) default null,
    result_ttl_seconds int default null,
    result_charged_seconds bigint default null,
    result_remaining_seconds bigint default null,
    result_subscription_end_time datetime default null,
    result_reason varchar(64) default null,
    completed_time datetime default null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (receipt_id),
    unique key uk_entitlement_lease_request (
        tenant_id,
        request_id
    ),
    key idx_entitlement_lease_subject (
        tenant_id,
        user_id,
        session_id,
        receipt_id
    ),
    constraint chk_entitlement_lease_request_values
        check (
            tenant_id > 0
            and user_id > 0
            and session_id > 0
            and usage_seconds >= 0
            and requested_ttl_seconds > 0
        ),
    constraint chk_entitlement_lease_receipt_status
        check (receipt_status in ('PENDING', 'COMPLETED')),
    constraint chk_entitlement_lease_result_values
        check (
            (result_allowed is null or result_allowed in (0, 1))
            and (
                result_mode is null
                or result_mode in (
                    'DURATION',
                    'SUBSCRIPTION',
                    'UNLIMITED'
                )
            )
            and (
                result_ttl_seconds is null
                or result_ttl_seconds > 0
            )
            and (
                result_charged_seconds is null
                or result_charged_seconds >= 0
            )
            and (
                result_remaining_seconds is null
                or result_remaining_seconds >= 0
            )
        ),
    constraint chk_entitlement_lease_completion
        check (
            (receipt_status = 'PENDING'
                and result_allowed is null
                and result_entitlement_id is null
                and result_mode is null
                and result_ttl_seconds is null
                and result_charged_seconds is null
                and result_remaining_seconds is null
                and result_subscription_end_time is null
                and result_reason is null
                and completed_time is null)
            or (receipt_status = 'COMPLETED'
                and result_allowed is not null
                and result_charged_seconds is not null
                and result_reason is not null
                and completed_time is not null)
        ),
    constraint chk_entitlement_lease_allowed_result
        check (
            result_allowed is null
            or (
                result_allowed = 0
                and result_ttl_seconds is null
            )
            or (
                result_allowed = 1
                and result_entitlement_id is not null
                and result_mode is not null
                and result_ttl_seconds is not null
                and result_ttl_seconds > 0
                and (
                    (result_mode = 'DURATION'
                        and result_remaining_seconds > 0)
                    or (result_mode = 'SUBSCRIPTION'
                        and result_subscription_end_time is not null)
                    or result_mode = 'UNLIMITED'
                )
            )
        )
) engine=innodb default charset=utf8mb4
  collate=utf8mb4_unicode_ci
  comment='User-owned entitlement lease idempotency receipt';

set @v2_9_2_post_object_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_user_auth_session_revoke_outbox',
          't_entitlement_lease_receipt'
      )
) + (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and constraint_name in (
          'chk_default_membership_outbox_status',
          'chk_default_membership_claim_owner'
      )
);

set @v2_9_2_guard_sql = if(
    @v2_9_2_post_object_count = 4,
    'select 1',
    'select json_extract(''V2_9_2_POST_DDL_STATE'', ''$'')'
);

prepare v2_9_2_guard_stmt from @v2_9_2_guard_sql;
execute v2_9_2_guard_stmt;
deallocate prepare v2_9_2_guard_stmt;
