create temporary table v2_2_2_contract_guard (
    check_name varchar(64) not null primary key
);

insert into v2_2_2_contract_guard(check_name) values ('TENANT_NULL');
insert into v2_2_2_contract_guard(check_name)
select case when (
        (select count(*) from t_duration_purchase where tenant_id is null)
        + (select count(*) from t_network_entitlement where tenant_id is null)
        + (select count(*) from t_entitlement_usage_log where tenant_id is null)
        + (select count(*) from t_entitlement_order where tenant_id is null)
        + (select count(*) from t_payment_record where tenant_id is null)
        + (select count(*) from t_refund_record where tenant_id is null)
        + (select count(*) from t_trade_status_log where tenant_id is null)
    ) = 0 then 'TENANT_NULL_OK' else 'TENANT_NULL' end;

insert into v2_2_2_contract_guard(check_name) values ('PRICING_VERSION_NULL');
insert into v2_2_2_contract_guard(check_name)
select case when (
        select count(*)
        from t_entitlement_order
        where pricing_version is null
    ) = 0 then 'PRICING_VERSION_NULL_OK' else 'PRICING_VERSION_NULL' end;

insert into v2_2_2_contract_guard(check_name) values ('ORPHAN_USER');
insert into v2_2_2_contract_guard(check_name)
select case when (
        (select count(*) from t_duration_purchase p
            left join sys_user u on u.user_id = p.user_id where u.user_id is null)
        + (select count(*) from t_network_entitlement e
            left join sys_user u on u.user_id = e.user_id where u.user_id is null)
        + (select count(*) from t_entitlement_usage_log l
            left join sys_user u on u.user_id = l.user_id where u.user_id is null)
        + (select count(*) from t_entitlement_order o
            left join sys_user u on u.user_id = o.user_id where u.user_id is null)
        + (select count(*) from t_payment_record p
            left join sys_user u on u.user_id = p.user_id where u.user_id is null)
        + (select count(*) from t_refund_record r
            left join sys_user u on u.user_id = r.user_id where u.user_id is null)
    ) = 0 then 'ORPHAN_USER_OK' else 'ORPHAN_USER' end;

insert into v2_2_2_contract_guard(check_name) values ('ORPHAN_RELATION');
insert into v2_2_2_contract_guard(check_name)
select case when (
        (select count(*)
         from t_entitlement_usage_log l
         left join t_network_entitlement e
           on e.tenant_id = l.tenant_id
          and e.entitlement_id = l.entitlement_id
          and e.user_id = l.user_id
         where e.entitlement_id is null)
        + (select count(*)
           from t_payment_record p
           left join t_entitlement_order o
             on o.tenant_id = p.tenant_id
            and o.order_no = p.order_no
            and o.user_id = p.user_id
           where o.order_id is null)
        + (select count(*)
           from t_refund_record r
           left join t_entitlement_order o
             on o.tenant_id = r.tenant_id
            and o.order_no = r.order_no
            and o.user_id = r.user_id
           left join t_payment_record p
             on p.tenant_id = r.tenant_id
            and p.payment_no = r.payment_no
            and p.order_no = r.order_no
            and p.user_id = r.user_id
           left join t_duration_purchase d
             on d.tenant_id = r.tenant_id
            and d.purchase_id = r.purchase_id
            and d.user_id = r.user_id
           where o.order_id is null
              or p.payment_id is null
              or d.purchase_id is null)
        + (select count(*)
           from t_trade_status_log l
           left join t_entitlement_order o
             on l.business_type = 'ORDER'
            and o.tenant_id = l.tenant_id
            and o.order_no = l.business_no
           left join t_payment_record p
             on l.business_type = 'PAYMENT'
            and p.tenant_id = l.tenant_id
            and p.payment_no = l.business_no
           left join t_refund_record r
             on l.business_type = 'REFUND'
            and r.tenant_id = l.tenant_id
            and r.refund_no = l.business_no
           where (l.business_type = 'ORDER' and o.order_id is null)
              or (l.business_type = 'PAYMENT' and p.payment_id is null)
              or (l.business_type = 'REFUND' and r.refund_id is null))
    ) = 0 then 'ORPHAN_RELATION_OK' else 'ORPHAN_RELATION' end;

insert into v2_2_2_contract_guard(check_name) values ('DUPLICATE_TENANT_KEY');
insert into v2_2_2_contract_guard(check_name)
select case when (
        (select count(*) from (
            select tenant_id, user_id
            from t_network_entitlement
            group by tenant_id, user_id
            having count(*) > 1
        ) duplicate_entitlement)
        + (select count(*) from (
            select tenant_id, request_id, line_no
            from t_entitlement_usage_log
            group by tenant_id, request_id, line_no
            having count(*) > 1
        ) duplicate_usage)
        + (select count(*) from (
            select tenant_id, user_id, client_request_id
            from t_entitlement_order
            group by tenant_id, user_id, client_request_id
            having count(*) > 1
        ) duplicate_order)
        + (select count(*) from (
            select tenant_id, user_id, request_id
            from t_payment_record
            group by tenant_id, user_id, request_id
            having count(*) > 1
        ) duplicate_payment)
        + (select count(*) from (
            select tenant_id, user_id, request_id
            from t_refund_record
            group by tenant_id, user_id, request_id
            having count(*) > 1
        ) duplicate_refund)
        + (select count(*) from (
            select tenant_id, business_type, business_no, event_key, to_status
            from t_trade_status_log
            group by tenant_id, business_type, business_no, event_key, to_status
            having count(*) > 1
        ) duplicate_trade_status)
    ) = 0 then 'DUPLICATE_TENANT_KEY_OK' else 'DUPLICATE_TENANT_KEY' end;

drop temporary table v2_2_2_contract_guard;

alter table t_duration_purchase
    modify column tenant_id bigint not null,
    drop key idx_duration_user_status,
    drop key idx_duration_fifo;

alter table t_network_entitlement
    modify column tenant_id bigint not null,
    modify column mode varchar(16) not null comment 'SUBSCRIPTION、DURATION或UNLIMITED',
    drop key uk_entitlement_user,
    drop key idx_entitlement_tenant_user,
    drop key idx_entitlement_mode_status,
    add unique key uk_entitlement_tenant_user (tenant_id, user_id);

alter table t_entitlement_usage_log
    modify column tenant_id bigint not null,
    drop key uk_usage_request_line,
    drop key idx_usage_tenant_request,
    drop key idx_usage_user_time,
    add unique key uk_usage_tenant_request_line (tenant_id, request_id, line_no);

alter table t_entitlement_order
    modify column tenant_id bigint not null,
    modify column pricing_version int not null,
    drop key uk_entitlement_order_request,
    drop key idx_entitlement_order_tenant_request,
    drop key idx_entitlement_order_user_time,
    drop key idx_entitlement_order_user_status,
    add unique key uk_entitlement_order_tenant_request (tenant_id, user_id, client_request_id);

alter table t_payment_record
    modify column tenant_id bigint not null,
    drop key uk_payment_user_request,
    drop key idx_payment_tenant_request,
    drop key idx_payment_user_time,
    add unique key uk_payment_tenant_user_request (tenant_id, user_id, request_id);

alter table t_refund_record
    modify column tenant_id bigint not null,
    drop key uk_refund_user_request,
    drop key idx_refund_user_time,
    drop key idx_refund_status_time,
    add unique key uk_refund_tenant_user_request (tenant_id, user_id, request_id);

alter table t_trade_status_log
    modify column tenant_id bigint not null,
    drop key uk_trade_status_event,
    drop key idx_trade_status_business,
    add unique key uk_trade_status_tenant_event (
        tenant_id,
        business_type,
        business_no,
        event_key,
        to_status
    );
