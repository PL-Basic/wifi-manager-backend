package com.plagod.service;

public interface DeviceCommandDispatchService {

    void dispatchOne(Long commandId);

    void timeoutOne(Long commandId);
}