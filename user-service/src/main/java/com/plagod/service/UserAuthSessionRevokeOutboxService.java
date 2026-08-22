package com.plagod.service;

public interface UserAuthSessionRevokeOutboxService {

    void dispatchPending(int batchSize);
}
