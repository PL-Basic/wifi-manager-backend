set @v2_7_required_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_announcement',
          't_announcement_content_version',
          't_announcement_comment',
          't_user_capability_restriction',
          't_support_content_review_outbox'
      )
);

set @v2_7_guard_sql = if(
    @v2_7_required_table_count = 5,
    'select 1',
    'select json_extract(''V2_7_REQUIRED_SUPPORT_TABLES'', ''$'')'
);

prepare v2_7_guard_stmt from @v2_7_guard_sql;
execute v2_7_guard_stmt;
deallocate prepare v2_7_guard_stmt;

set @v2_7_target_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_ai_provider',
          't_ai_policy',
          't_ai_policy_version',
          't_ai_review_task',
          't_ai_manual_review'
      )
);

set @v2_7_guard_sql = if(
    @v2_7_target_table_count in (0, 5),
    'select 1',
    'select json_extract(''V2_7_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_7_guard_stmt from @v2_7_guard_sql;
execute v2_7_guard_stmt;
deallocate prepare v2_7_guard_stmt;

set @v2_7_ddl_sql = if(
    @v2_7_target_table_count = 0,
    'create table t_ai_provider (
        provider_id bigint not null auto_increment,
        provider_code varchar(64) not null,
        display_name varchar(96) not null,
        adapter_type varchar(48) not null,
        model_identifier varchar(96) not null,
        status varchar(16) not null default ''DISABLED'',
        config_reference varchar(96) not null,
        no_training_supported tinyint not null default 0,
        retention_mode varchar(24) not null default ''UNKNOWN'',
        timeout_ms int not null,
        last_verified_time datetime default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (provider_id),
        unique key uk_ai_provider_code (provider_code),
        check (status in (''ENABLED'', ''DISABLED'')),
        check (no_training_supported in (0, 1)),
        check (retention_mode in (
            ''NO_RETENTION'',
            ''CONFIGURABLE'',
            ''UNKNOWN''
        )),
        check (timeout_ms between 100 and 120000),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Non-secret AI provider metadata''',
    'select 1'
);

prepare v2_7_ddl_stmt from @v2_7_ddl_sql;
execute v2_7_ddl_stmt;
deallocate prepare v2_7_ddl_stmt;

set @v2_7_ddl_sql = if(
    @v2_7_target_table_count = 0,
    'create table t_ai_policy (
        policy_id bigint not null auto_increment,
        policy_code varchar(64) not null,
        scene varchar(40) not null,
        display_name varchar(96) not null,
        status varchar(16) not null default ''ENABLED'',
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (policy_id),
        unique key uk_ai_policy_code (policy_code),
        unique key uk_ai_policy_scene (scene),
        check (scene in (
            ''ANNOUNCEMENT_REVIEW'',
            ''SUPPORT_SUBMISSION_REVIEW''
        )),
        check (status in (''ENABLED'', ''DISABLED'')),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Stable AI review policy identity''',
    'select 1'
);

prepare v2_7_ddl_stmt from @v2_7_ddl_sql;
execute v2_7_ddl_stmt;
deallocate prepare v2_7_ddl_stmt;

set @v2_7_ddl_sql = if(
    @v2_7_target_table_count = 0,
    'create table t_ai_policy_version (
        policy_version_id bigint not null auto_increment,
        policy_id bigint not null,
        version_no int not null,
        instruction_reference varchar(128) not null,
        instruction_hash char(64) not null,
        response_schema_reference varchar(128) not null,
        response_schema_hash char(64) not null,
        approve_threshold_bps int not null,
        manual_threshold_bps int not null,
        status varchar(16) not null default ''DRAFT'',
        active_policy_key bigint
            generated always as (
                case when status = ''ACTIVE'' then policy_id else null end
            ) stored,
        publish_time datetime default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (policy_version_id),
        unique key uk_ai_policy_version_no (policy_id, version_no),
        unique key uk_ai_policy_one_active (active_policy_key),
        check (version_no > 0),
        check (approve_threshold_bps between 0 and 10000),
        check (manual_threshold_bps between 0 and 10000),
        check (manual_threshold_bps <= approve_threshold_bps),
        check (status in (''DRAFT'', ''ACTIVE'', ''RETIRED'')),
        check (status <> ''ACTIVE'' or publish_time is not null),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Immutable AI policy versions by external references''',
    'select 1'
);

prepare v2_7_ddl_stmt from @v2_7_ddl_sql;
execute v2_7_ddl_stmt;
deallocate prepare v2_7_ddl_stmt;

set @v2_7_ddl_sql = if(
    @v2_7_target_table_count = 0,
    'create table t_ai_review_task (
        review_task_id bigint not null auto_increment,
        review_request_id varchar(96) not null,
        scene varchar(40) not null,
        business_type varchar(32) not null,
        business_id bigint not null,
        tenant_id bigint default null,
        content_version int not null,
        content_hash char(64) not null,
        policy_version_id bigint not null,
        provider_id bigint default null,
        provider_code varchar(64) default null,
        model_identifier varchar(96) default null,
        task_status varchar(24) not null default ''QUEUED'',
        decision_code varchar(16) default null,
        confidence_bps int default null,
        risk_labels_json json default null,
        reason_code varchar(64) default null,
        provider_request_id varchar(128) default null,
        provider_event_id varchar(128) default null,
        duration_ms bigint default null,
        attempt_count int not null default 0,
        started_time datetime default null,
        completed_time datetime default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (review_task_id),
        unique key uk_ai_review_request (review_request_id),
        unique key uk_ai_review_business_version (
            scene,
            business_type,
            business_id,
            content_version
        ),
        unique key uk_ai_review_provider_event (provider_event_id),
        key idx_ai_review_manual_queue (
            task_status,
            tenant_id,
            review_task_id
        ),
        check (
            (scene = ''ANNOUNCEMENT_REVIEW''
                and business_type = ''ANNOUNCEMENT'')
            or (scene = ''SUPPORT_SUBMISSION_REVIEW''
                and business_type = ''SUPPORT_SUBMISSION''
                and tenant_id is not null)
        ),
        check (content_version > 0),
        check (task_status in (
            ''QUEUED'',
            ''RUNNING'',
            ''SUCCEEDED'',
            ''FAILED'',
            ''MANUAL_REQUIRED'',
            ''STALE''
        )),
        check (
            decision_code is null
            or decision_code in (''APPROVE'', ''REJECT'', ''MANUAL'')
        ),
        check (
            confidence_bps is null
            or confidence_bps between 0 and 10000
        ),
        check (
            task_status <> ''SUCCEEDED''
            or decision_code in (''APPROVE'', ''REJECT'')
        ),
        check (
            task_status <> ''MANUAL_REQUIRED''
            or decision_code = ''MANUAL''
        ),
        check (duration_ms is null or duration_ms >= 0),
        check (attempt_count >= 0),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''AI review tasks with references and minimal result metadata''',
    'select 1'
);

prepare v2_7_ddl_stmt from @v2_7_ddl_sql;
execute v2_7_ddl_stmt;
deallocate prepare v2_7_ddl_stmt;

set @v2_7_ddl_sql = if(
    @v2_7_target_table_count = 0,
    'create table t_ai_manual_review (
        manual_review_id bigint not null auto_increment,
        review_task_id bigint not null,
        event_key varchar(96) not null,
        decision_code varchar(16) not null,
        reviewer_user_id bigint not null,
        reason_code varchar(64) not null,
        remark_summary varchar(255) default null,
        create_time datetime not null default current_timestamp,
        primary key (manual_review_id),
        unique key uk_ai_manual_review_event (event_key),
        key idx_ai_manual_review_task (
            review_task_id,
            manual_review_id
        ),
        check (decision_code in (''APPROVE'', ''REJECT''))
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Idempotent manual review actions''',
    'select 1'
);

prepare v2_7_ddl_stmt from @v2_7_ddl_sql;
execute v2_7_ddl_stmt;
deallocate prepare v2_7_ddl_stmt;
