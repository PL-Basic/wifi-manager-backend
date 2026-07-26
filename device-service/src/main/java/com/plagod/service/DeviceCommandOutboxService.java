package com.plagod.service;

import com.plagod.entity.DeviceCommandRecord;

public interface DeviceCommandOutboxService {

    // 命令必须与调用方的业务数据处于同一个数据库事务。
    Long enqueue(DeviceCommandRecord command);
}
