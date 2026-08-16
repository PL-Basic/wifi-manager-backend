set @v2_5_1_required_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_entitlement_order',
          't_saas_plan_version',
          't_tenant_plan_assignment',
          't_tenant_quota_reservation'
      )
);

set @v2_5_1_guard_sql = if(
    @v2_5_1_required_table_count = 4,
    'select 1',
    'select json_extract(''V2_5_1_REQUIRED_PREDECESSOR_TABLES'', ''$'')'
);

prepare v2_5_1_guard_stmt from @v2_5_1_guard_sql;
execute v2_5_1_guard_stmt;
deallocate prepare v2_5_1_guard_stmt;

set @v2_5_1_market_table_count = (
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

set @v2_5_1_entitlement_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and column_name in ('source_type', 'source_reference')
);

set @v2_5_1_entitlement_index_count = (
    select count(distinct index_name)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and index_name = 'uk_entitlement_order_source'
);

set @v2_5_1_entitlement_check_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and constraint_type = 'CHECK'
);

set @v2_5_1_entitlement_column_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and (
          (
              column_name = 'source_type'
              and data_type = 'varchar'
              and character_maximum_length = 24
              and is_nullable = 'YES'
              and column_default is null
              and collation_name = 'utf8mb4_unicode_ci'
              and upper(extra) = 'STORED GENERATED'
              and replace(
                  replace(
                      replace(
                          replace(
                              replace(
                                  replace(
                                      replace(
                                          replace(
                                              lower(generation_expression),
                                              char(92),
                                              ''
                                          ),
                                          '_utf8mb4',
                                          ''
                                      ),
                                      '`',
                                      ''
                                  ),
                                  ' ',
                                  ''
                              ),
                              char(9),
                              ''
                          ),
                          char(10),
                          ''
                      ),
                      '(',
                      ''
                  ),
                  ')',
                  ''
              ) =
                  'casewhenorder_type=''marketplace''then''marketplace'''
                  'whenorder_type=''reward''then''admin_reward'''
                  'whenorder_type=''purchase''then''direct_purchase'''
                  'elsenullend'
          )
          or (
              column_name = 'source_reference'
              and data_type = 'varchar'
              and character_maximum_length = 128
              and is_nullable = 'YES'
              and column_default is null
              and collation_name = 'utf8mb4_unicode_ci'
              and extra = ''
              and generation_expression = ''
          )
      )
);

set @v2_5_1_entitlement_index_signature_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and index_name = 'uk_entitlement_order_source'
      and non_unique = 0
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          (seq_in_index = 1 and column_name = 'tenant_id')
          or (seq_in_index = 2 and column_name = 'source_type')
          or (seq_in_index = 3 and column_name = 'source_reference')
      )
);

set @v2_5_1_entitlement_index_row_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and index_name = 'uk_entitlement_order_source'
);

set @v2_5_1_entitlement_check_signature_count = (
    select count(*)
    from information_schema.table_constraints constraints
    join information_schema.check_constraints checks
      on checks.constraint_schema = constraints.constraint_schema
     and checks.constraint_name = constraints.constraint_name
    where constraints.table_schema = database()
      and constraints.table_name = 't_entitlement_order'
      and constraints.constraint_type = 'CHECK'
      and constraints.enforced = 'YES'
      and (
          (
              constraints.constraint_name = 'chk_entitlement_order_type'
              and replace(
                  replace(
                      replace(
                          replace(
                              replace(
                                  replace(
                                      replace(
                                          replace(
                                              lower(checks.check_clause),
                                              char(92),
                                              ''
                                          ),
                                          '_utf8mb4',
                                          ''
                                      ),
                                      '`',
                                      ''
                                  ),
                                  ' ',
                                  ''
                              ),
                              char(9),
                              ''
                          ),
                          char(10),
                          ''
                      ),
                      '(',
                      ''
                  ),
                  ')',
                  ''
              ) =
                  'order_typein''purchase'',''reward'',''marketplace'''
          )
          or (
              constraints.constraint_name = 'chk_entitlement_market_source'
              and replace(
                  replace(
                      replace(
                          replace(
                              replace(
                                  replace(
                                      replace(
                                          replace(
                                              lower(checks.check_clause),
                                              char(92),
                                              ''
                                          ),
                                          '_utf8mb4',
                                          ''
                                      ),
                                      '`',
                                      ''
                                  ),
                                  ' ',
                                  ''
                              ),
                              char(9),
                              ''
                          ),
                          char(10),
                          ''
                      ),
                      '(',
                      ''
                  ),
                  ')',
                  ''
              ) =
                  'order_type<>''marketplace'''
                  'orsource_referenceisnotnull'
          )
      )
);

set @v2_5_1_object_count = (
    @v2_5_1_market_table_count
    + @v2_5_1_entitlement_column_count
    + @v2_5_1_entitlement_index_count
    + @v2_5_1_entitlement_check_count
);

set @v2_5_1_guard_sql = if(
    @v2_5_1_object_count = 0
        or (
            @v2_5_1_object_count = 10
            and @v2_5_1_entitlement_column_signature_count = 2
            and @v2_5_1_entitlement_index_row_count = 3
            and @v2_5_1_entitlement_index_signature_count = 3
            and @v2_5_1_entitlement_check_signature_count = 2
        ),
    'select 1',
    'select json_extract(''V2_5_1_PARTIAL_DDL_STATE'', ''$'')'
);

prepare v2_5_1_guard_stmt from @v2_5_1_guard_sql;
execute v2_5_1_guard_stmt;
deallocate prepare v2_5_1_guard_stmt;

create temporary table v2_5_1_data_guard (
    check_name varchar(64) not null primary key
);

insert into v2_5_1_data_guard(check_name) values ('ENTITLEMENT_ORDER_TYPE');

set @v2_5_1_guard_sql = if(
    @v2_5_1_object_count = 0,
    'insert into v2_5_1_data_guard(check_name)
     select case when (
         select count(*)
         from t_entitlement_order
         where order_type not in (''PURCHASE'', ''REWARD'')
            or order_no is null
            or tenant_id is null
            or user_id is null
     ) = 0
     then ''ENTITLEMENT_ORDER_TYPE_OK''
     else ''ENTITLEMENT_ORDER_TYPE'' end',
    'select 1'
);

prepare v2_5_1_guard_stmt from @v2_5_1_guard_sql;
execute v2_5_1_guard_stmt;
deallocate prepare v2_5_1_guard_stmt;

drop temporary table v2_5_1_data_guard;

set @v2_5_1_ddl_sql = if(
    @v2_5_1_object_count = 0,
    'alter table t_entitlement_order
        add column source_type varchar(24)
            generated always as (
                case
                    when order_type = ''MARKETPLACE'' then ''MARKETPLACE''
                    when order_type = ''REWARD'' then ''ADMIN_REWARD''
                    when order_type = ''PURCHASE'' then ''DIRECT_PURCHASE''
                    else null
                end
            ) stored after order_type,
        add column source_reference varchar(128) null after source_type,
        add unique key uk_entitlement_order_source (
            tenant_id,
            source_type,
            source_reference
        ),
        add constraint chk_entitlement_order_type check (
            order_type in (''PURCHASE'', ''REWARD'', ''MARKETPLACE'')
        ),
        add constraint chk_entitlement_market_source check (
            order_type <> ''MARKETPLACE''
            or source_reference is not null
        )',
    'select 1'
);

prepare v2_5_1_ddl_stmt from @v2_5_1_ddl_sql;
execute v2_5_1_ddl_stmt;
deallocate prepare v2_5_1_ddl_stmt;

set @v2_5_1_post_entitlement_column_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and (
          (
              column_name = 'source_type'
              and data_type = 'varchar'
              and character_maximum_length = 24
              and is_nullable = 'YES'
              and column_default is null
              and collation_name = 'utf8mb4_unicode_ci'
              and upper(extra) = 'STORED GENERATED'
              and replace(
                  replace(
                      replace(
                          replace(
                              replace(
                                  replace(
                                      replace(
                                          replace(
                                              lower(generation_expression),
                                              char(92),
                                              ''
                                          ),
                                          '_utf8mb4',
                                          ''
                                      ),
                                      '`',
                                      ''
                                  ),
                                  ' ',
                                  ''
                              ),
                              char(9),
                              ''
                          ),
                          char(10),
                          ''
                      ),
                      '(',
                      ''
                  ),
                  ')',
                  ''
              ) =
                  'casewhenorder_type=''marketplace''then''marketplace'''
                  'whenorder_type=''reward''then''admin_reward'''
                  'whenorder_type=''purchase''then''direct_purchase'''
                  'elsenullend'
          )
          or (
              column_name = 'source_reference'
              and data_type = 'varchar'
              and character_maximum_length = 128
              and is_nullable = 'YES'
              and column_default is null
              and collation_name = 'utf8mb4_unicode_ci'
              and extra = ''
              and generation_expression = ''
          )
      )
);

set @v2_5_1_post_entitlement_check_signature_count = (
    select count(*)
    from information_schema.table_constraints constraints
    join information_schema.check_constraints checks
      on checks.constraint_schema = constraints.constraint_schema
     and checks.constraint_name = constraints.constraint_name
    where constraints.table_schema = database()
      and constraints.table_name = 't_entitlement_order'
      and constraints.constraint_type = 'CHECK'
      and constraints.enforced = 'YES'
      and (
          (
              constraints.constraint_name = 'chk_entitlement_order_type'
              and replace(
                  replace(
                      replace(
                          replace(
                              replace(
                                  replace(
                                      replace(
                                          replace(
                                              lower(checks.check_clause),
                                              char(92),
                                              ''
                                          ),
                                          '_utf8mb4',
                                          ''
                                      ),
                                      '`',
                                      ''
                                  ),
                                  ' ',
                                  ''
                              ),
                              char(9),
                              ''
                          ),
                          char(10),
                          ''
                      ),
                      '(',
                      ''
                  ),
                  ')',
                  ''
              ) =
                  'order_typein''purchase'',''reward'',''marketplace'''
          )
          or (
              constraints.constraint_name = 'chk_entitlement_market_source'
              and replace(
                  replace(
                      replace(
                          replace(
                              replace(
                                  replace(
                                      replace(
                                          replace(
                                              lower(checks.check_clause),
                                              char(92),
                                              ''
                                          ),
                                          '_utf8mb4',
                                          ''
                                      ),
                                      '`',
                                      ''
                                  ),
                                  ' ',
                                  ''
                              ),
                              char(9),
                              ''
                          ),
                          char(10),
                          ''
                      ),
                      '(',
                      ''
                  ),
                  ')',
                  ''
              ) =
                  'order_type<>''marketplace'''
                  'orsource_referenceisnotnull'
          )
      )
);

set @v2_5_1_post_entitlement_check_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and constraint_type = 'CHECK'
);

set @v2_5_1_post_entitlement_index_signature_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and index_name = 'uk_entitlement_order_source'
      and non_unique = 0
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          (seq_in_index = 1 and column_name = 'tenant_id')
          or (seq_in_index = 2 and column_name = 'source_type')
          or (seq_in_index = 3 and column_name = 'source_reference')
      )
);

set @v2_5_1_post_entitlement_index_row_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_entitlement_order'
      and index_name = 'uk_entitlement_order_source'
);

set @v2_5_1_guard_sql = if(
    @v2_5_1_post_entitlement_column_signature_count = 2
        and @v2_5_1_post_entitlement_index_row_count = 3
        and @v2_5_1_post_entitlement_index_signature_count = 3
        and @v2_5_1_post_entitlement_check_count = 2
        and @v2_5_1_post_entitlement_check_signature_count = 2,
    'select 1',
    'select json_extract(''V2_5_1_POST_DDL_STATE'', ''$'')'
);

prepare v2_5_1_guard_stmt from @v2_5_1_guard_sql;
execute v2_5_1_guard_stmt;
deallocate prepare v2_5_1_guard_stmt;

set @v2_5_1_ddl_sql = if(
    @v2_5_1_object_count = 0,
    'create table t_market_product (
        product_id bigint not null auto_increment,
        product_code varchar(64) not null,
        product_type varchar(32) not null,
        name varchar(128) not null,
        summary varchar(255) default null,
        description_text text not null,
        status varchar(16) not null default ''DRAFT'',
        display_order int not null default 0,
        publish_time datetime default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (product_id),
        unique key uk_market_product_code (product_code),
        key idx_market_product_catalog (
            status,
            display_order,
            product_id
        ),
        check (product_type in (
            ''PERSONAL_ENTITLEMENT'',
            ''SAAS_SUBSCRIPTION'',
            ''HARDWARE_DEMO''
        )),
        check (status in (''DRAFT'', ''PUBLISHED'', ''UNPUBLISHED'')),
        check (display_order >= 0),
        check (version >= 0),
        check (status <> ''PUBLISHED'' or publish_time is not null)
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Demo商城商品目录''',
    'select 1'
);

prepare v2_5_1_ddl_stmt from @v2_5_1_ddl_sql;
execute v2_5_1_ddl_stmt;
deallocate prepare v2_5_1_ddl_stmt;

set @v2_5_1_ddl_sql = if(
    @v2_5_1_object_count = 0,
    'create table t_market_sku (
        sku_id bigint not null auto_increment,
        sku_code varchar(64) not null,
        product_id bigint not null,
        target_type varchar(32) not null,
        specification_json json not null,
        price_cents bigint not null,
        entitlement_product_code varchar(32) default null,
        saas_plan_version_id bigint default null,
        hardware_model_code varchar(64) default null,
        status varchar(16) not null default ''ACTIVE'',
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (sku_id),
        unique key uk_market_sku_code (sku_code),
        key idx_market_sku_product_status (
            product_id,
            status,
            sku_id
        ),
        key idx_market_sku_entitlement (entitlement_product_code),
        key idx_market_sku_plan_version (saas_plan_version_id),
        check (price_cents >= 0),
        check (status in (''ACTIVE'', ''INACTIVE'')),
        check (version >= 0),
        check (
            (
                target_type = ''PERSONAL_ENTITLEMENT''
                and entitlement_product_code is not null
                and saas_plan_version_id is null
                and hardware_model_code is null
            )
            or (
                target_type = ''SAAS_SUBSCRIPTION''
                and entitlement_product_code is null
                and saas_plan_version_id is not null
                and hardware_model_code is null
            )
            or (
                target_type = ''HARDWARE_DEMO''
                and entitlement_product_code is null
                and saas_plan_version_id is null
                and hardware_model_code is not null
            )
        )
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Demo商城可售SKU''',
    'select 1'
);

prepare v2_5_1_ddl_stmt from @v2_5_1_ddl_sql;
execute v2_5_1_ddl_stmt;
deallocate prepare v2_5_1_ddl_stmt;

set @v2_5_1_ddl_sql = if(
    @v2_5_1_object_count = 0,
    'create table t_market_order (
        order_id bigint not null auto_increment,
        order_no varchar(64) not null,
        tenant_id bigint not null,
        subject_type varchar(16) not null,
        user_id bigint default null,
        subject_key varchar(96)
            generated always as (
                case
                    when subject_type = ''USER''
                        then concat(''USER:'', cast(user_id as char))
                    when subject_type = ''TENANT'' then ''TENANT''
                    else null
                end
            ) stored,
        actor_user_id bigint not null,
        client_request_id varchar(64) not null,
        request_fingerprint char(64) not null,
        total_amount_cents bigint not null,
        payment_mode varchar(16) not null,
        payment_status varchar(16) not null default ''PENDING'',
        order_status varchar(32) not null default ''PENDING_DEMO_PAYMENT'',
        paid_time datetime default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (order_id),
        unique key uk_market_order_no (order_no),
        unique key uk_market_order_request (
            tenant_id,
            subject_type,
            subject_key,
            client_request_id
        ),
        key idx_market_order_subject_time (
            tenant_id,
            subject_type,
            subject_key,
            create_time,
            order_id
        ),
        key idx_market_order_status (
            tenant_id,
            order_status,
            create_time,
            order_id
        ),
        check (
            (subject_type = ''USER'' and user_id is not null)
            or (subject_type = ''TENANT'' and user_id is null)
        ),
        check (total_amount_cents >= 0),
        check (payment_mode in (''LOCAL_DEMO'', ''MANUAL_DEMO'')),
        check (payment_status in (
            ''PENDING'',
            ''SUCCEEDED'',
            ''FAILED'',
            ''CLOSED''
        )),
        check (order_status in (
            ''PENDING_DEMO_PAYMENT'',
            ''PAID'',
            ''FULFILLING'',
            ''FULFILLED'',
            ''CANCELLED'',
            ''CLOSED'',
            ''FULFILLMENT_FAILED''
        )),
        check (version >= 0),
        check (
            order_status not in (
                ''PAID'',
                ''FULFILLING'',
                ''FULFILLED'',
                ''FULFILLMENT_FAILED''
            )
            or payment_status = ''SUCCEEDED''
        ),
        check (
            payment_status <> ''SUCCEEDED''
            or paid_time is not null
        )
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Demo商城订单与付款真相''',
    'select 1'
);

prepare v2_5_1_ddl_stmt from @v2_5_1_ddl_sql;
execute v2_5_1_ddl_stmt;
deallocate prepare v2_5_1_ddl_stmt;

set @v2_5_1_ddl_sql = if(
    @v2_5_1_object_count = 0,
    'create table t_market_order_item (
        order_item_id bigint not null auto_increment,
        order_id bigint not null,
        line_no int not null,
        sku_id bigint not null,
        sku_code varchar(64) not null,
        product_code varchar(64) not null,
        product_name varchar(128) not null,
        product_type varchar(32) not null,
        specification_snapshot_json json not null,
        unit_price_cents bigint not null,
        quantity int not null,
        subtotal_cents bigint not null,
        entitlement_product_code varchar(32) default null,
        saas_plan_version_id bigint default null,
        hardware_model_code varchar(64) default null,
        create_time datetime not null default current_timestamp,
        primary key (order_item_id),
        unique key uk_market_order_item_line (order_id, line_no),
        key idx_market_order_item_sku (sku_id, order_item_id),
        key idx_market_order_item_plan (saas_plan_version_id),
        check (line_no > 0),
        check (unit_price_cents >= 0),
        check (quantity > 0),
        check (subtotal_cents = unit_price_cents * quantity),
        check (
            (
                product_type = ''PERSONAL_ENTITLEMENT''
                and entitlement_product_code is not null
                and saas_plan_version_id is null
                and hardware_model_code is null
            )
            or (
                product_type = ''SAAS_SUBSCRIPTION''
                and entitlement_product_code is null
                and saas_plan_version_id is not null
                and hardware_model_code is null
            )
            or (
                product_type = ''HARDWARE_DEMO''
                and entitlement_product_code is null
                and saas_plan_version_id is null
                and hardware_model_code is not null
            )
        )
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Demo商城不可变订单项快照''',
    'select 1'
);

prepare v2_5_1_ddl_stmt from @v2_5_1_ddl_sql;
execute v2_5_1_ddl_stmt;
deallocate prepare v2_5_1_ddl_stmt;

set @v2_5_1_ddl_sql = if(
    @v2_5_1_object_count = 0,
    'create table t_market_fulfillment (
        fulfillment_id bigint not null auto_increment,
        tenant_id bigint not null,
        order_id bigint not null,
        order_item_id bigint not null,
        event_key varchar(128) not null,
        fulfillment_type varchar(32) not null,
        fulfillment_mode varchar(16) not null,
        target_business_key varchar(128) not null,
        status varchar(32) not null default ''PENDING'',
        result_reference varchar(128) default null,
        attempt_count int not null default 0,
        next_attempt_time datetime not null default current_timestamp,
        lease_until datetime default null,
        worker_id varchar(64) default null,
        last_error_key varchar(64) default null,
        version int not null default 0,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp on update current_timestamp,
        primary key (fulfillment_id),
        unique key uk_market_fulfillment_event (event_key),
        unique key uk_market_fulfillment_item_type (
            order_item_id,
            fulfillment_type
        ),
        key idx_market_fulfillment_due (
            status,
            next_attempt_time,
            lease_until,
            fulfillment_id
        ),
        key idx_market_fulfillment_order (
            tenant_id,
            order_id,
            order_item_id
        ),
        check (attempt_count >= 0),
        check (version >= 0),
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
        check (
            fulfillment_mode <> ''TARGET_DOMAIN''
            or status <> ''SUCCEEDED''
            or result_reference is not null
        ),
        check (
            (
                fulfillment_mode = ''TARGET_DOMAIN''
                and fulfillment_type in (
                    ''PERSONAL_ENTITLEMENT'',
                    ''SAAS_SUBSCRIPTION''
                )
                and status in (
                    ''PENDING'',
                    ''PROCESSING'',
                    ''SUCCEEDED'',
                    ''FAILED''
                )
            )
            or (
                fulfillment_mode = ''TRACK_ONLY''
                and fulfillment_type = ''HARDWARE_DEMO''
                and status in (
                    ''PENDING'',
                    ''DEMO_DELIVERY_PENDING'',
                    ''DEMO_DELIVERED'',
                    ''CANCELLED''
                )
            )
        )
    ) default charset=utf8mb4 collate=utf8mb4_unicode_ci
      comment=''Demo商城幂等履约Outbox''',
    'select 1'
);

prepare v2_5_1_ddl_stmt from @v2_5_1_ddl_sql;
execute v2_5_1_ddl_stmt;
deallocate prepare v2_5_1_ddl_stmt;
