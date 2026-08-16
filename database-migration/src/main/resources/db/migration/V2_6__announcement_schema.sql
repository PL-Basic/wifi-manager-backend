set @v2_6_required_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_market_product',
          't_market_sku',
          't_market_order',
          't_market_order_item',
          't_market_fulfillment'
      )
);

set @v2_6_guard_sql = if(
    @v2_6_required_table_count = 5,
    'select 1',
    'select json_extract(''V2_6_REQUIRED_MARKETPLACE_TABLES'', ''$'')'
);

prepare v2_6_guard_stmt from @v2_6_guard_sql;
execute v2_6_guard_stmt;
deallocate prepare v2_6_guard_stmt;

set @v2_6_target_table_count = (
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

set @v2_6_guard_sql = if(
    @v2_6_target_table_count in (0, 5),
    'select 1',
    'select json_extract(''V2_6_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_6_guard_stmt from @v2_6_guard_sql;
execute v2_6_guard_stmt;
deallocate prepare v2_6_guard_stmt;

set @v2_6_ddl_sql = if(
    @v2_6_target_table_count = 0,
    'create table t_announcement (
        announcement_id bigint not null auto_increment,
        scope_type varchar(16) not null,
        tenant_id bigint default null,
        scope_key varchar(96)
            generated always as (
                case
                    when scope_type = ''PLATFORM'' then ''PLATFORM''
                    when scope_type = ''TENANT''
                        then concat(''TENANT:'', cast(tenant_id as char))
                    else null
                end
            ) stored,
        author_user_id bigint not null,
        author_display_name varchar(64) not null,
        client_request_id varchar(64) not null,
        request_fingerprint char(64) not null,
        current_content_version int not null default 1,
        published_content_version int default null,
        status varchar(24) not null default ''DRAFT'',
        pinned tinyint not null default 0,
        allow_comments tinyint not null default 1,
        version int not null default 0,
        del_flag tinyint not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (announcement_id),
        unique key uk_announcement_create_request (
            scope_type,
            scope_key,
            author_user_id,
            client_request_id
        ),
        key idx_announcement_scope_status (
            scope_type,
            scope_key,
            status,
            pinned,
            announcement_id
        ),
        check (
            (scope_type = ''PLATFORM'' and tenant_id is null)
            or (scope_type = ''TENANT'' and tenant_id is not null)
        ),
        check (current_content_version > 0),
        check (
            published_content_version is null
            or (
                published_content_version > 0
                and published_content_version <= current_content_version
            )
        ),
        check (status in (
            ''DRAFT'',
            ''REVIEW_PENDING'',
            ''REVIEW_APPROVED'',
            ''REVIEW_REJECTED'',
            ''MANUAL_REVIEW'',
            ''PUBLISHED'',
            ''WITHDRAWN''
        )),
        check (status <> ''PUBLISHED'' or published_content_version is not null),
        check (pinned in (0, 1)),
        check (allow_comments in (0, 1)),
        check (version >= 0),
        check (del_flag in (0, 1))
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Support announcement aggregate''',
    'select 1'
);

prepare v2_6_ddl_stmt from @v2_6_ddl_sql;
execute v2_6_ddl_stmt;
deallocate prepare v2_6_ddl_stmt;

set @v2_6_ddl_sql = if(
    @v2_6_target_table_count = 0,
    'create table t_announcement_content_version (
        announcement_content_id bigint not null auto_increment,
        announcement_id bigint not null,
        content_version int not null,
        title varchar(160) not null,
        summary varchar(320) default null,
        body_text text not null,
        content_hash char(64) not null,
        review_request_id varchar(96) default null,
        ai_review_task_id bigint default null,
        review_decision varchar(16) default null,
        review_reason_code varchar(64) default null,
        review_confidence_bps int default null,
        status varchar(24) not null default ''DRAFT'',
        published_time datetime default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (announcement_content_id),
        unique key uk_announcement_content_version (
            announcement_id,
            content_version
        ),
        unique key uk_announcement_review_request (review_request_id),
        key idx_announcement_content_status (
            announcement_id,
            status,
            content_version
        ),
        check (content_version > 0),
        check (status in (
            ''DRAFT'',
            ''REVIEW_PENDING'',
            ''REVIEW_APPROVED'',
            ''REVIEW_REJECTED'',
            ''MANUAL_REVIEW'',
            ''PUBLISHED'',
            ''WITHDRAWN''
        )),
        check (
            review_decision is null
            or review_decision in (''APPROVE'', ''REJECT'', ''MANUAL'')
        ),
        check (
            review_confidence_bps is null
            or review_confidence_bps between 0 and 10000
        ),
        check (
            status not in (''REVIEW_APPROVED'', ''PUBLISHED'')
            or review_decision = ''APPROVE''
        ),
        check (
            status <> ''REVIEW_REJECTED''
            or review_decision = ''REJECT''
        ),
        check (
            status <> ''MANUAL_REVIEW''
            or review_decision = ''MANUAL''
        ),
        check (status <> ''PUBLISHED'' or published_time is not null),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Immutable announcement content versions''',
    'select 1'
);

prepare v2_6_ddl_stmt from @v2_6_ddl_sql;
execute v2_6_ddl_stmt;
deallocate prepare v2_6_ddl_stmt;

set @v2_6_ddl_sql = if(
    @v2_6_target_table_count = 0,
    'create table t_announcement_comment (
        comment_id bigint not null auto_increment,
        tenant_id bigint not null,
        announcement_id bigint not null,
        parent_comment_id bigint default null,
        author_user_id bigint not null,
        author_display_name varchar(64) not null,
        client_request_id varchar(64) not null,
        request_fingerprint char(64) not null,
        content_text text not null,
        status varchar(20) not null default ''VISIBLE'',
        hidden_by_user_id bigint default null,
        hidden_reason_code varchar(64) default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (comment_id),
        unique key uk_announcement_comment_request (
            tenant_id,
            author_user_id,
            client_request_id
        ),
        key idx_announcement_comment_page (
            tenant_id,
            announcement_id,
            parent_comment_id,
            comment_id
        ),
        check (status in (
            ''VISIBLE'',
            ''USER_DELETED'',
            ''ADMIN_HIDDEN''
        )),
        check (
            status <> ''ADMIN_HIDDEN''
            or (
                hidden_by_user_id is not null
                and hidden_reason_code is not null
            )
        ),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Tenant-partitioned announcement comments''',
    'select 1'
);

prepare v2_6_ddl_stmt from @v2_6_ddl_sql;
execute v2_6_ddl_stmt;
deallocate prepare v2_6_ddl_stmt;

set @v2_6_ddl_sql = if(
    @v2_6_target_table_count = 0,
    'create table t_user_capability_restriction (
        restriction_id bigint not null auto_increment,
        scope_type varchar(16) not null,
        tenant_id bigint default null,
        scope_key varchar(96)
            generated always as (
                case
                    when scope_type = ''GLOBAL'' then ''GLOBAL''
                    when scope_type = ''TENANT''
                        then concat(''TENANT:'', cast(tenant_id as char))
                    else null
                end
            ) stored,
        user_id bigint not null,
        capability varchar(32) not null,
        status varchar(16) not null default ''ACTIVE'',
        reason_code varchar(64) not null,
        operator_user_id bigint not null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (restriction_id),
        unique key uk_capability_restriction_scope (
            scope_type,
            scope_key,
            user_id,
            capability
        ),
        key idx_capability_restriction_lookup (
            user_id,
            status,
            capability,
            restriction_id
        ),
        check (
            (scope_type = ''GLOBAL'' and tenant_id is null)
            or (scope_type = ''TENANT'' and tenant_id is not null)
        ),
        check (capability in (
            ''ANNOUNCEMENT_PUBLISH'',
            ''ANNOUNCEMENT_COMMENT'',
            ''SUPPORT_SUBMISSION''
        )),
        check (status in (''ACTIVE'', ''REVOKED'')),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Independent user capability restrictions''',
    'select 1'
);

prepare v2_6_ddl_stmt from @v2_6_ddl_sql;
execute v2_6_ddl_stmt;
deallocate prepare v2_6_ddl_stmt;

set @v2_6_ddl_sql = if(
    @v2_6_target_table_count = 0,
    'create table t_support_content_review_outbox (
        event_id bigint not null auto_increment,
        review_request_id varchar(96) not null,
        scene varchar(40) not null,
        business_type varchar(32) not null,
        business_id bigint not null,
        tenant_id bigint default null,
        content_version int not null,
        content_hash char(64) not null,
        status varchar(20) not null default ''PENDING'',
        retry_count int not null default 0,
        next_retry_time datetime not null default current_timestamp,
        lease_until datetime default null,
        worker_id varchar(64) default null,
        last_error_code varchar(64) default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (event_id),
        unique key uk_support_review_request (review_request_id),
        unique key uk_support_review_business_version (
            scene,
            business_type,
            business_id,
            content_version
        ),
        key idx_support_review_due (
            status,
            next_retry_time,
            lease_until,
            event_id
        ),
        check (
            (scene = ''ANNOUNCEMENT_REVIEW''
                and business_type = ''ANNOUNCEMENT'')
            or (scene = ''SUPPORT_SUBMISSION_REVIEW''
                and business_type = ''SUPPORT_SUBMISSION''
                and tenant_id is not null)
        ),
        check (content_version > 0),
        check (status in (
            ''PENDING'',
            ''PROCESSING'',
            ''SENT'',
            ''FAILED'',
            ''CANCELLED''
        )),
        check (retry_count >= 0),
        check (
            (
                status = ''PROCESSING''
                and lease_until is not null
                and worker_id is not null
            )
            or (
                status <> ''PROCESSING''
                and lease_until is null
                and worker_id is null
            )
        ),
        check (version >= 0)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Support-owned content review outbox without source text''',
    'select 1'
);

prepare v2_6_ddl_stmt from @v2_6_ddl_sql;
execute v2_6_ddl_stmt;
deallocate prepare v2_6_ddl_stmt;
