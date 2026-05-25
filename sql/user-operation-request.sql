use wifi;

create table if not exists t_user_operation_request(
    id bigint AUTO_INCREMENT,
    request_type varchar(64) NOT NULL,
    target_user_id bigint NOT NULL,
    target_username varchar(64),
    requester_id bigint,
    requester_name varchar(64),
    status tinyint NOT NULL DEFAULT 0,
    approver_id bigint,
    approver_name varchar(64),
    reason varchar(255),
    reject_reason varchar(255),
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handle_time datetime DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_status_create_time(status, create_time),
    KEY idx_target_user(target_user_id),
    KEY idx_requester(requester_id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
