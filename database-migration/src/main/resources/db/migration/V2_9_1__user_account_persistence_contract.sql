set @v2_9_1_required_table_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_type = 'BASE TABLE'
      and table_name in (
          't_default_tenant_membership_outbox',
          't_verify_code'
      )
);

set @v2_9_1_required_anchor_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and (
          (
              table_name = 't_default_tenant_membership_outbox'
              and column_name = 'event_id'
              and data_type = 'varchar'
               and character_maximum_length = 64
               and is_nullable = 'NO'
               and extra = ''
               and generation_expression = ''
           )
           or (
               table_name = 't_verify_code'
               and column_name = 'consume_time'
               and data_type = 'datetime'
               and datetime_precision = 0
               and is_nullable = 'YES'
               and extra = ''
               and generation_expression = ''
           )
      )
);

set @v2_9_1_guard_sql = if(
    @v2_9_1_required_table_count = 2
        and @v2_9_1_required_anchor_count = 2,
    'select 1',
    'select json_extract(''V2_9_1_REQUIRED_TABLES'', ''$'')'
);

prepare v2_9_1_guard_stmt from @v2_9_1_guard_sql;
execute v2_9_1_guard_stmt;
deallocate prepare v2_9_1_guard_stmt;

set @v2_9_1_outbox_object_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and column_name in ('idempotency_key', 'request_fingerprint')
) + (
    select count(distinct index_name)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and index_name = 'uk_default_membership_request'
);

set @v2_9_1_outbox_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and (
          (
              column_name = 'idempotency_key'
              and data_type = 'varchar'
              and character_maximum_length = 64
               and is_nullable = 'YES'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
               and ordinal_position = (
                  select ordinal_position + 1
                  from information_schema.columns event_column
                  where event_column.table_schema = database()
                    and event_column.table_name =
                        't_default_tenant_membership_outbox'
                    and event_column.column_name = 'event_id'
              )
          )
          or (
              column_name = 'request_fingerprint'
              and data_type = 'char'
              and character_maximum_length = 64
               and is_nullable = 'YES'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
               and ordinal_position = (
                  select ordinal_position + 1
                  from information_schema.columns idempotency_column
                  where idempotency_column.table_schema = database()
                    and idempotency_column.table_name =
                        't_default_tenant_membership_outbox'
                    and idempotency_column.column_name = 'idempotency_key'
              )
          )
      )
) + (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and index_name = 'uk_default_membership_request'
      and non_unique = 0
      and seq_in_index = 1
      and column_name = 'idempotency_key'
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          select count(*)
          from information_schema.statistics all_columns
          where all_columns.table_schema = database()
            and all_columns.table_name =
                't_default_tenant_membership_outbox'
            and all_columns.index_name =
                'uk_default_membership_request'
      ) = 1
);

set @v2_9_1_verify_code_object_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_verify_code'
      and column_name = 'consume_request_key'
) + (
    select count(distinct index_name)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_verify_code'
      and index_name = 'uk_verify_code_consume_request'
);

set @v2_9_1_verify_code_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_verify_code'
      and column_name = 'consume_request_key'
      and data_type = 'char'
      and character_maximum_length = 64
       and is_nullable = 'YES'
       and column_default is null
       and collation_name = 'utf8mb4_unicode_ci'
       and extra = ''
       and generation_expression = ''
       and ordinal_position = (
          select ordinal_position + 1
          from information_schema.columns consume_column
          where consume_column.table_schema = database()
            and consume_column.table_name = 't_verify_code'
            and consume_column.column_name = 'consume_time'
      )
) + (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_verify_code'
      and index_name = 'uk_verify_code_consume_request'
      and non_unique = 0
      and seq_in_index = 1
      and column_name = 'consume_request_key'
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          select count(*)
          from information_schema.statistics all_columns
          where all_columns.table_schema = database()
            and all_columns.table_name = 't_verify_code'
            and all_columns.index_name =
                'uk_verify_code_consume_request'
      ) = 1
);

set @v2_9_1_receipt_object_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_receipt_table_signature_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and table_type = 'BASE TABLE'
      and engine = 'InnoDB'
      and table_collation = 'utf8mb4_unicode_ci'
      and table_comment =
          'User account persistence command idempotency receipt'
);

set @v2_9_1_receipt_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_receipt_column_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and (
          (
              column_name = 'receipt_id'
              and ordinal_position = 1
              and column_type = 'bigint'
               and is_nullable = 'NO'
               and column_default is null
               and extra = 'auto_increment'
               and generation_expression = ''
           )
          or (
              column_name = 'command_type'
              and ordinal_position = 2
              and data_type = 'varchar'
              and character_maximum_length = 32
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'idempotency_key'
              and ordinal_position = 3
              and data_type = 'varchar'
              and character_maximum_length = 64
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'request_fingerprint'
              and ordinal_position = 4
              and data_type = 'char'
              and character_maximum_length = 64
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'user_id'
              and ordinal_position = 5
               and column_type = 'bigint'
               and is_nullable = 'YES'
               and column_default is null
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'result_status'
              and ordinal_position = 6
              and data_type = 'varchar'
              and character_maximum_length = 32
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'create_time'
              and ordinal_position = 7
              and data_type = 'datetime'
              and datetime_precision = 0
               and is_nullable = 'NO'
               and column_default = 'CURRENT_TIMESTAMP'
               and lower(extra) = 'default_generated'
               and generation_expression = ''
           )
          or (
              column_name = 'update_time'
              and ordinal_position = 8
              and data_type = 'datetime'
              and datetime_precision = 0
              and is_nullable = 'NO'
               and column_default = 'CURRENT_TIMESTAMP'
               and lower(extra) =
                   'default_generated on update current_timestamp'
               and generation_expression = ''
           )
      )
);

set @v2_9_1_receipt_index_row_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_receipt_index_signature_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          (
              index_name = 'PRIMARY'
              and non_unique = 0
              and seq_in_index = 1
              and column_name = 'receipt_id'
          )
          or (
              index_name = 'uk_user_account_command'
              and non_unique = 0
              and (
                  (seq_in_index = 1 and column_name = 'command_type')
                  or (
                      seq_in_index = 2
                      and column_name = 'idempotency_key'
                  )
              )
          )
          or (
              index_name = 'idx_user_account_command_user'
              and non_unique = 1
              and (
                  (seq_in_index = 1 and column_name = 'user_id')
                  or (
                      seq_in_index = 2
                      and column_name = 'command_type'
                  )
              )
          )
      )
);

set @v2_9_1_receipt_check_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and constraint_type = 'CHECK'
);

set @v2_9_1_receipt_constraint_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_receipt_constraint_signature_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and (
          (
              constraint_name = 'PRIMARY'
              and constraint_type = 'PRIMARY KEY'
          )
          or (
              constraint_name = 'uk_user_account_command'
              and constraint_type = 'UNIQUE'
          )
          or (
              constraint_name = 'chk_user_account_command_type'
              and constraint_type = 'CHECK'
          )
          or (
              constraint_name = 'chk_user_account_result_status'
              and constraint_type = 'CHECK'
          )
      )
);

set @v2_9_1_receipt_check_signature_count = (
    select count(*)
    from information_schema.table_constraints constraints
    join information_schema.check_constraints checks
      on checks.constraint_schema = constraints.constraint_schema
     and checks.constraint_name = constraints.constraint_name
    where constraints.table_schema = database()
      and constraints.table_name = 't_user_account_command_receipt'
      and constraints.constraint_type = 'CHECK'
      and constraints.enforced = 'YES'
      and (
          (
              constraints.constraint_name =
                  'chk_user_account_command_type'
              and replace(
                  replace(
                      replace(
                      replace(
                          replace(
                              replace(
                                  lower(checks.check_clause),
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
              char(92),
              ''
              ) in (
                  'command_typein(''register'',''password_replace'')',
                  '(command_typein(''register'',''password_replace''))'
              )
          )
          or (
              constraints.constraint_name =
                  'chk_user_account_result_status'
              and replace(
                  replace(
                      replace(
                      replace(
                          replace(
                              replace(
                                  lower(checks.check_clause),
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
              char(92),
              ''
              ) in (
                  'result_statusin(''succeeded'')',
                  '(result_statusin(''succeeded''))',
                  'result_status=''succeeded''',
                  '(result_status=''succeeded'')'
              )
          )
      )
);

set @v2_9_1_guard_sql = case
    when not (
        @v2_9_1_outbox_object_count = 0
        or (
            @v2_9_1_outbox_object_count = 3
            and @v2_9_1_outbox_signature_count = 3
        )
    ) then
        'select json_extract(''V2_9_1_OUTBOX_PARTIAL_DDL_STATE'', ''$'')'
    when not (
        @v2_9_1_verify_code_object_count = 0
        or (
            @v2_9_1_verify_code_object_count = 2
            and @v2_9_1_verify_code_signature_count = 2
        )
    ) then
        'select json_extract(''V2_9_1_VERIFY_CODE_PARTIAL_DDL_STATE'', ''$'')'
    when not (
        @v2_9_1_receipt_object_count = 0
        or (
            @v2_9_1_receipt_object_count = 1
            and @v2_9_1_receipt_table_signature_count = 1
            and @v2_9_1_receipt_column_count = 8
            and @v2_9_1_receipt_column_signature_count = 8
            and @v2_9_1_receipt_index_row_count = 5
            and @v2_9_1_receipt_index_signature_count = 5
            and @v2_9_1_receipt_check_count = 2
            and @v2_9_1_receipt_constraint_count = 4
            and @v2_9_1_receipt_constraint_signature_count = 4
            and @v2_9_1_receipt_check_signature_count = 2
        )
    ) then
        'select json_extract(''V2_9_1_RECEIPT_PARTIAL_DDL_STATE'', ''$'')'
    else 'select 1'
end;

prepare v2_9_1_guard_stmt from @v2_9_1_guard_sql;
execute v2_9_1_guard_stmt;
deallocate prepare v2_9_1_guard_stmt;

set @v2_9_1_ddl_sql = if(
    @v2_9_1_outbox_object_count = 0,
    'alter table t_default_tenant_membership_outbox
        add column idempotency_key varchar(64) default null
            after event_id,
        add column request_fingerprint char(64) default null
            after idempotency_key,
        add unique key uk_default_membership_request (idempotency_key)',
    'select 1'
);

prepare v2_9_1_ddl_stmt from @v2_9_1_ddl_sql;
execute v2_9_1_ddl_stmt;
deallocate prepare v2_9_1_ddl_stmt;

set @v2_9_1_ddl_sql = if(
    @v2_9_1_verify_code_object_count = 0,
    'alter table t_verify_code
        add column consume_request_key char(64) default null
            after consume_time,
        add unique key uk_verify_code_consume_request
            (consume_request_key)',
    'select 1'
);

prepare v2_9_1_ddl_stmt from @v2_9_1_ddl_sql;
execute v2_9_1_ddl_stmt;
deallocate prepare v2_9_1_ddl_stmt;

set @v2_9_1_ddl_sql = if(
    @v2_9_1_receipt_object_count = 0,
    'create table t_user_account_command_receipt (
        receipt_id bigint not null auto_increment,
        command_type varchar(32) not null,
        idempotency_key varchar(64) not null,
        request_fingerprint char(64) not null,
        user_id bigint default null,
        result_status varchar(32) not null,
        create_time datetime not null default current_timestamp,
        update_time datetime not null default current_timestamp
            on update current_timestamp,
        primary key (receipt_id),
        unique key uk_user_account_command
            (command_type, idempotency_key),
        key idx_user_account_command_user (user_id, command_type),
        constraint chk_user_account_command_type
            check (command_type in (''REGISTER'', ''PASSWORD_REPLACE'')),
        constraint chk_user_account_result_status
            check (result_status in (''SUCCEEDED''))
    ) engine=innodb default charset=utf8mb4
      collate=utf8mb4_unicode_ci
      comment=''User account persistence command idempotency receipt''',
    'select 1'
);

prepare v2_9_1_ddl_stmt from @v2_9_1_ddl_sql;
execute v2_9_1_ddl_stmt;
deallocate prepare v2_9_1_ddl_stmt;

set @v2_9_1_post_outbox_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and (
          (
              column_name = 'idempotency_key'
              and data_type = 'varchar'
              and character_maximum_length = 64
               and is_nullable = 'YES'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
               and ordinal_position = (
                  select ordinal_position + 1
                  from information_schema.columns event_column
                  where event_column.table_schema = database()
                    and event_column.table_name =
                        't_default_tenant_membership_outbox'
                    and event_column.column_name = 'event_id'
              )
          )
          or (
              column_name = 'request_fingerprint'
              and data_type = 'char'
              and character_maximum_length = 64
               and is_nullable = 'YES'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
               and ordinal_position = (
                  select ordinal_position + 1
                  from information_schema.columns idempotency_column
                  where idempotency_column.table_schema = database()
                    and idempotency_column.table_name =
                        't_default_tenant_membership_outbox'
                    and idempotency_column.column_name = 'idempotency_key'
              )
          )
      )
) + (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_default_tenant_membership_outbox'
      and index_name = 'uk_default_membership_request'
      and non_unique = 0
      and seq_in_index = 1
      and column_name = 'idempotency_key'
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          select count(*)
          from information_schema.statistics all_columns
          where all_columns.table_schema = database()
            and all_columns.table_name =
                't_default_tenant_membership_outbox'
            and all_columns.index_name =
                'uk_default_membership_request'
      ) = 1
);

set @v2_9_1_post_verify_code_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_verify_code'
      and column_name = 'consume_request_key'
      and data_type = 'char'
      and character_maximum_length = 64
       and is_nullable = 'YES'
       and column_default is null
       and collation_name = 'utf8mb4_unicode_ci'
       and extra = ''
       and generation_expression = ''
       and ordinal_position = (
          select ordinal_position + 1
          from information_schema.columns consume_column
          where consume_column.table_schema = database()
            and consume_column.table_name = 't_verify_code'
            and consume_column.column_name = 'consume_time'
      )
) + (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_verify_code'
      and index_name = 'uk_verify_code_consume_request'
      and non_unique = 0
      and seq_in_index = 1
      and column_name = 'consume_request_key'
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          select count(*)
          from information_schema.statistics all_columns
          where all_columns.table_schema = database()
            and all_columns.table_name = 't_verify_code'
            and all_columns.index_name =
                'uk_verify_code_consume_request'
      ) = 1
);

set @v2_9_1_post_receipt_table_signature_count = (
    select count(*)
    from information_schema.tables
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and table_type = 'BASE TABLE'
      and engine = 'InnoDB'
      and table_collation = 'utf8mb4_unicode_ci'
      and table_comment =
          'User account persistence command idempotency receipt'
);

set @v2_9_1_post_receipt_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_post_receipt_column_signature_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and (
          (
              column_name = 'receipt_id'
              and ordinal_position = 1
              and column_type = 'bigint'
               and is_nullable = 'NO'
               and column_default is null
               and extra = 'auto_increment'
               and generation_expression = ''
           )
          or (
              column_name = 'command_type'
              and ordinal_position = 2
              and data_type = 'varchar'
              and character_maximum_length = 32
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'idempotency_key'
              and ordinal_position = 3
              and data_type = 'varchar'
              and character_maximum_length = 64
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'request_fingerprint'
              and ordinal_position = 4
              and data_type = 'char'
              and character_maximum_length = 64
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'user_id'
              and ordinal_position = 5
               and column_type = 'bigint'
               and is_nullable = 'YES'
               and column_default is null
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'result_status'
              and ordinal_position = 6
              and data_type = 'varchar'
              and character_maximum_length = 32
               and is_nullable = 'NO'
               and column_default is null
               and collation_name = 'utf8mb4_unicode_ci'
               and extra = ''
               and generation_expression = ''
           )
          or (
              column_name = 'create_time'
              and ordinal_position = 7
              and data_type = 'datetime'
              and datetime_precision = 0
               and is_nullable = 'NO'
               and column_default = 'CURRENT_TIMESTAMP'
               and lower(extra) = 'default_generated'
               and generation_expression = ''
           )
          or (
              column_name = 'update_time'
              and ordinal_position = 8
              and data_type = 'datetime'
              and datetime_precision = 0
              and is_nullable = 'NO'
               and column_default = 'CURRENT_TIMESTAMP'
               and lower(extra) =
                   'default_generated on update current_timestamp'
               and generation_expression = ''
           )
      )
);

set @v2_9_1_post_receipt_index_row_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_post_receipt_index_signature_count = (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and sub_part is null
      and index_type = 'BTREE'
      and is_visible = 'YES'
      and collation = 'A'
      and (
          (
              index_name = 'PRIMARY'
              and non_unique = 0
              and seq_in_index = 1
              and column_name = 'receipt_id'
          )
          or (
              index_name = 'uk_user_account_command'
              and non_unique = 0
              and (
                  (seq_in_index = 1 and column_name = 'command_type')
                  or (
                      seq_in_index = 2
                      and column_name = 'idempotency_key'
                  )
              )
          )
          or (
              index_name = 'idx_user_account_command_user'
              and non_unique = 1
              and (
                  (seq_in_index = 1 and column_name = 'user_id')
                  or (
                      seq_in_index = 2
                      and column_name = 'command_type'
                  )
              )
          )
      )
);

set @v2_9_1_post_receipt_check_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and constraint_type = 'CHECK'
);

set @v2_9_1_post_receipt_constraint_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
);

set @v2_9_1_post_receipt_constraint_signature_count = (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 't_user_account_command_receipt'
      and (
          (
              constraint_name = 'PRIMARY'
              and constraint_type = 'PRIMARY KEY'
          )
          or (
              constraint_name = 'uk_user_account_command'
              and constraint_type = 'UNIQUE'
          )
          or (
              constraint_name = 'chk_user_account_command_type'
              and constraint_type = 'CHECK'
          )
          or (
              constraint_name = 'chk_user_account_result_status'
              and constraint_type = 'CHECK'
          )
      )
);

set @v2_9_1_post_receipt_check_signature_count = (
    select count(*)
    from information_schema.table_constraints constraints
    join information_schema.check_constraints checks
      on checks.constraint_schema = constraints.constraint_schema
     and checks.constraint_name = constraints.constraint_name
    where constraints.table_schema = database()
      and constraints.table_name = 't_user_account_command_receipt'
      and constraints.constraint_type = 'CHECK'
      and constraints.enforced = 'YES'
      and (
          (
              constraints.constraint_name =
                  'chk_user_account_command_type'
              and replace(
                  replace(
                      replace(
                      replace(
                          replace(
                              replace(
                                  lower(checks.check_clause),
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
              char(92),
              ''
              ) in (
                  'command_typein(''register'',''password_replace'')',
                  '(command_typein(''register'',''password_replace''))'
              )
          )
          or (
              constraints.constraint_name =
                  'chk_user_account_result_status'
              and replace(
                  replace(
                      replace(
                      replace(
                          replace(
                              replace(
                                  lower(checks.check_clause),
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
              char(92),
              ''
              ) in (
                  'result_statusin(''succeeded'')',
                  '(result_statusin(''succeeded''))',
                  'result_status=''succeeded''',
                  '(result_status=''succeeded'')'
              )
          )
      )
);

set @v2_9_1_guard_sql = if(
    @v2_9_1_post_outbox_signature_count = 3
        and @v2_9_1_post_verify_code_signature_count = 2
        and @v2_9_1_post_receipt_table_signature_count = 1
        and @v2_9_1_post_receipt_column_count = 8
        and @v2_9_1_post_receipt_column_signature_count = 8
        and @v2_9_1_post_receipt_index_row_count = 5
        and @v2_9_1_post_receipt_index_signature_count = 5
        and @v2_9_1_post_receipt_check_count = 2
        and @v2_9_1_post_receipt_constraint_count = 4
        and @v2_9_1_post_receipt_constraint_signature_count = 4
        and @v2_9_1_post_receipt_check_signature_count = 2,
    'select 1',
    'select json_extract(''V2_9_1_POST_DDL_STATE'', ''$'')'
);

prepare v2_9_1_guard_stmt from @v2_9_1_guard_sql;
execute v2_9_1_guard_stmt;
deallocate prepare v2_9_1_guard_stmt;
