set @v2_8_required_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_announcement',
          't_announcement_content_version',
          't_announcement_comment',
          't_user_capability_restriction',
          't_support_content_review_outbox',
          't_ai_provider',
          't_ai_policy',
          't_ai_policy_version',
          't_ai_review_task',
          't_ai_manual_review'
      )
);

set @v2_8_guard_sql = if(
    @v2_8_required_table_count = 10,
    'select 1',
    'select json_extract(''V2_8_REQUIRED_SUPPORT_AI_TABLES'', ''$'')'
);

prepare v2_8_guard_stmt from @v2_8_guard_sql;
execute v2_8_guard_stmt;
deallocate prepare v2_8_guard_stmt;

set @v2_8_target_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_support_user_guard',
          't_support_daily_guard',
          't_support_submission',
          't_support_ticket',
          't_support_ticket_message',
          't_support_ticket_transition'
      )
);

set @v2_8_guard_sql = if(
    @v2_8_target_table_count in (0, 6),
    'select 1',
    'select json_extract(''V2_8_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_8_guard_stmt from @v2_8_guard_sql;
execute v2_8_guard_stmt;
deallocate prepare v2_8_guard_stmt;

create table if not exists t_support_user_guard (
    user_guard_id bigint not null auto_increment,
    tenant_id bigint not null,
    user_id bigint not null,
    pending_count int not null default 0,
    unresolved_count int not null default 0,
    abuse_warning_count int not null default 0,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (user_guard_id),
    unique key uk_support_user_guard (tenant_id, user_id),
    check (pending_count >= 0),
    check (unresolved_count >= 0),
    check (abuse_warning_count >= 0),
    check (version >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Tenant user support pending and unresolved counters';

create table if not exists t_support_daily_guard (
    daily_guard_id bigint not null auto_increment,
    tenant_id bigint not null,
    user_id bigint not null,
    quota_date date not null,
    limit_used int not null default 0,
    attempt_count int not null default 0,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (daily_guard_id),
    unique key uk_support_daily_guard (
        tenant_id,
        user_id,
        quota_date
    ),
    check (limit_used >= 0),
    check (attempt_count >= limit_used),
    check (version >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Asia Shanghai support submission quota usage';

create table if not exists t_support_submission (
    submission_id bigint not null auto_increment,
    tenant_id bigint not null,
    user_id bigint not null,
    client_request_id varchar(64) not null,
    request_fingerprint char(64) not null,
    title varchar(160) not null,
    content_text text default null,
    content_hash char(64) not null,
    content_version int not null default 1,
    accepted_time datetime not null,
    quota_date date not null,
    status varchar(28) not null default 'REVIEW_PENDING',
    review_request_id varchar(96) not null,
    ai_review_task_id bigint default null,
    review_decision varchar(16) default null,
    outcome_reason_code varchar(64) default null,
    ticket_id bigint default null,
    limit_refunded tinyint not null default 0,
    quota_refund_event_key varchar(96) default null,
    quota_refund_time datetime default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (submission_id),
    unique key uk_support_submission_request (
        tenant_id,
        user_id,
        client_request_id
    ),
    unique key uk_support_submission_review_request (review_request_id),
    unique key uk_support_submission_ticket (ticket_id),
    unique key uk_support_submission_refund_event (
        quota_refund_event_key
    ),
    key idx_support_submission_pending (
        tenant_id,
        user_id,
        status,
        submission_id
    ),
    check (content_version > 0),
    check (status in (
        'REVIEW_PENDING',
        'MANUAL_REVIEW',
        'ACCEPTED',
        'SPAM_REJECTED',
        'CONVERTED_TO_TICKET',
        'SYSTEM_ABANDONED'
    )),
    check (
        review_decision is null
        or review_decision in ('APPROVE', 'REJECT', 'MANUAL')
    ),
    check (
        status <> 'MANUAL_REVIEW'
        or review_decision = 'MANUAL'
    ),
    check (
        status <> 'ACCEPTED'
        or review_decision = 'APPROVE'
    ),
    check (
        status <> 'SPAM_REJECTED'
        or review_decision = 'REJECT'
    ),
    check (
        status <> 'CONVERTED_TO_TICKET'
        or ticket_id is not null
    ),
    check (
        status = 'CONVERTED_TO_TICKET'
        or ticket_id is null
    ),
    check (
        status not in (
            'SPAM_REJECTED',
            'CONVERTED_TO_TICKET',
            'SYSTEM_ABANDONED'
        )
        or content_text is null
    ),
    check (
        (
            limit_refunded = 0
            and quota_refund_event_key is null
            and quota_refund_time is null
        )
        or (
            limit_refunded = 1
            and quota_refund_event_key is not null
            and quota_refund_time is not null
            and status = 'SYSTEM_ABANDONED'
        )
    ),
    check (
        status <> 'SYSTEM_ABANDONED'
        or (
            limit_refunded = 1
            and ai_review_task_id is null
            and ticket_id is null
        )
    ),
    check (
        status not in (
            'MANUAL_REVIEW',
            'ACCEPTED',
            'SPAM_REJECTED',
            'CONVERTED_TO_TICKET'
        )
        or ai_review_task_id is not null
    ),
    check (limit_refunded in (0, 1)),
    check (version >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Tenant user support submissions and review state';

create table if not exists t_support_ticket (
    ticket_id bigint not null auto_increment,
    ticket_no varchar(40) not null,
    tenant_id bigint not null,
    source_submission_id bigint not null,
    requester_user_id bigint not null,
    title_snapshot varchar(160) not null,
    category_code varchar(40) not null default 'GENERAL',
    priority_code varchar(16) not null default 'NORMAL',
    status varchar(20) not null default 'OPEN',
    assignee_user_id bigint default null,
    version int not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp
        on update current_timestamp,
    primary key (ticket_id),
    unique key uk_support_ticket_no (ticket_no),
    unique key uk_support_ticket_submission (source_submission_id),
    key idx_support_ticket_tenant_status (
        tenant_id,
        status,
        ticket_id
    ),
    key idx_support_ticket_requester (
        tenant_id,
        requester_user_id,
        ticket_id
    ),
    check (priority_code in ('LOW', 'NORMAL', 'HIGH')),
    check (status in (
        'OPEN',
        'IN_PROGRESS',
        'WAITING_USER',
        'RESOLVED',
        'CLOSED'
    )),
    check (
        status = 'OPEN'
        or assignee_user_id is not null
    ),
    check (version >= 0)
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Tenant support tickets created from accepted submissions';

create table if not exists t_support_ticket_message (
    message_id bigint not null auto_increment,
    ticket_id bigint not null,
    tenant_id bigint not null,
    sender_type varchar(20) not null,
    sender_user_id bigint default null,
    sender_key varchar(96)
        generated always as (
            case
                when sender_type = 'SYSTEM' then 'SYSTEM'
                else concat(
                    sender_type,
                    ':',
                    cast(sender_user_id as char)
                )
            end
        ) stored,
    client_request_id varchar(64) not null,
    content_text text not null,
    user_visible tinyint not null default 1,
    create_time datetime not null default current_timestamp,
    primary key (message_id),
    unique key uk_support_ticket_message_request (
        ticket_id,
        sender_key,
        client_request_id
    ),
    key idx_support_ticket_message_page (
        tenant_id,
        ticket_id,
        message_id
    ),
    check (sender_type in ('USER', 'TENANT_ADMIN', 'SYSTEM')),
    check (
        (sender_type = 'SYSTEM' and sender_user_id is null)
        or (
            sender_type in ('USER', 'TENANT_ADMIN')
            and sender_user_id is not null
        )
    ),
    check (user_visible in (0, 1))
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Idempotent tenant support ticket messages';

create table if not exists t_support_ticket_transition (
    transition_id bigint not null auto_increment,
    ticket_id bigint not null,
    tenant_id bigint not null,
    event_key varchar(96) not null,
    from_status varchar(20) not null,
    to_status varchar(20) not null,
    operator_type varchar(20) not null,
    operator_user_id bigint default null,
    reason_code varchar(64) not null,
    create_time datetime not null default current_timestamp,
    primary key (transition_id),
    unique key uk_support_ticket_transition_event (
        ticket_id,
        event_key,
        to_status
    ),
    key idx_support_ticket_transition_page (
        tenant_id,
        ticket_id,
        transition_id
    ),
    check (operator_type in (
        'USER',
        'TENANT_ADMIN',
        'SYSTEM'
    )),
    check (
        (operator_type = 'SYSTEM' and operator_user_id is null)
        or (
            operator_type in ('USER', 'TENANT_ADMIN')
            and operator_user_id is not null
        )
    ),
    check (
        (from_status = 'NONE' and to_status = 'OPEN')
        or (from_status = 'OPEN' and to_status = 'IN_PROGRESS')
        or (
            from_status = 'IN_PROGRESS'
            and to_status in ('WAITING_USER', 'RESOLVED')
        )
        or (
            from_status = 'WAITING_USER'
            and to_status in ('IN_PROGRESS', 'RESOLVED')
        )
        or (from_status = 'RESOLVED' and to_status = 'CLOSED')
    )
) default charset=utf8mb4 collate=utf8mb4_unicode_ci
  comment='Idempotent legal support ticket transitions';
