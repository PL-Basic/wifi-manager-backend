set @v2_9_3_verify_base_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_verify_code'
      and column_name in (
          'target',
          'scene',
          'send_status',
          'verify_status',
          'status',
          'expire_time'
      )
);

set @v2_9_3_support_legacy_check_count = (
    select count(*)
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 't_support_submission'
      and constraint_type = 'CHECK'
      and constraint_name = 't_support_submission_chk_12'
);

set @v2_9_3_ai_claim_column_count = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 't_ai_review_task'
      and column_name in (
          'task_status',
          'worker_id',
          'lease_until',
          'claimed_time'
      )
);

set @v2_9_3_new_object_count = (
    select
        (
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 't_verify_code'
              and column_name in (
                  'verify_claim_owner',
                  'verify_lease_until',
                  'verify_claimed_time'
              )
        )
        + (
            select count(*)
            from information_schema.statistics
            where table_schema = database()
              and table_name = 't_verify_code'
              and index_name = 'idx_verify_code_claim'
        )
        + (
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = database()
              and constraint_name in (
                  'chk_verify_code_claim_owner',
                  'chk_support_submission_review_task',
                  'chk_support_manual_without_ai_task',
                  'chk_ai_review_task_claim_owner'
              )
        )
);

set @v2_9_3_guard_sql = if(
    @v2_9_3_verify_base_column_count = 6
        and @v2_9_3_support_legacy_check_count = 1
        and @v2_9_3_ai_claim_column_count = 4
        and @v2_9_3_new_object_count = 0,
    'select 1',
    'select json_extract(''V2_9_3_PARTIAL_OR_INCOMPATIBLE_STATE'', ''$'')'
);

prepare v2_9_3_guard_stmt from @v2_9_3_guard_sql;
execute v2_9_3_guard_stmt;
deallocate prepare v2_9_3_guard_stmt;

alter table t_verify_code
    add column verify_claim_owner varchar(64) default null
        after verify_error,
    add column verify_lease_until datetime default null
        after verify_claim_owner,
    add column verify_claimed_time datetime default null
        after verify_lease_until,
    add key idx_verify_code_claim (
        target,
        scene,
        send_status,
        status,
        verify_status,
        verify_lease_until,
        id
    ),
    add constraint chk_verify_code_claim_owner
        check (
            (
                verify_claim_owner is null
                and verify_lease_until is null
                and verify_claimed_time is null
            )
            or (
                verify_claim_owner is not null
                and char_length(trim(verify_claim_owner)) > 0
                and verify_lease_until is not null
                and verify_claimed_time is not null
                and verify_lease_until > verify_claimed_time
                and send_status = 1
                and verify_status = 0
                and status = 0
            )
        );

alter table t_support_submission
    drop check t_support_submission_chk_12,
    add constraint chk_support_submission_review_task
        check (
            status not in (
                'ACCEPTED',
                'SPAM_REJECTED',
                'CONVERTED_TO_TICKET'
            )
            or ai_review_task_id is not null
        ),
    add constraint chk_support_manual_without_ai_task
        check (
            status <> 'MANUAL_REVIEW'
            or ai_review_task_id is not null
            or (
                ai_review_task_id is null
                and outcome_reason_code is not null
                and outcome_reason_code = 'AI_REVIEW_MANUAL_REQUIRED'
            )
        );

update t_ai_review_task
set task_status = 'QUEUED',
    worker_id = null,
    lease_until = null,
    claimed_time = null,
    next_retry_time = current_timestamp,
    last_error_code = 'LEGACY_RUNNING_CLAIM_RECOVERED',
    completed_time = null,
    version = version + 1,
    update_time = current_timestamp
where task_status = 'RUNNING'
  and (
      worker_id is null
      or lease_until is null
      or claimed_time is null
      or lease_until <= claimed_time
  );

alter table t_ai_review_task
    add constraint chk_ai_review_task_claim_owner
        check (
            (
                task_status = 'RUNNING'
                and worker_id is not null
                and char_length(trim(worker_id)) > 0
                and lease_until is not null
                and claimed_time is not null
                and lease_until > claimed_time
            )
            or (
                task_status <> 'RUNNING'
                and worker_id is null
                and lease_until is null
                and claimed_time is null
            )
        );

set @v2_9_3_post_object_count = (
    select
        (
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 't_verify_code'
              and column_name in (
                  'verify_claim_owner',
                  'verify_lease_until',
                  'verify_claimed_time'
              )
        )
        + (
            select count(distinct index_name)
            from information_schema.statistics
            where table_schema = database()
              and table_name = 't_verify_code'
              and index_name = 'idx_verify_code_claim'
        )
        + (
            select count(*)
            from information_schema.table_constraints
            where constraint_schema = database()
              and constraint_name in (
                  'chk_verify_code_claim_owner',
                  'chk_support_submission_review_task',
                  'chk_support_manual_without_ai_task',
                  'chk_ai_review_task_claim_owner'
              )
        )
);

set @v2_9_3_post_guard_sql = if(
    @v2_9_3_post_object_count = 8,
    'select 1',
    'select json_extract(''V2_9_3_POST_CONDITION_FAILED'', ''$'')'
);

prepare v2_9_3_post_guard_stmt from @v2_9_3_post_guard_sql;
execute v2_9_3_post_guard_stmt;
deallocate prepare v2_9_3_post_guard_stmt;
