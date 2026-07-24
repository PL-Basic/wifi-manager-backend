package com.plagod.service;

import com.plagod.dto.ClientSignalEvent;

public interface ClientSignalEventService {

    void handleClientSignalEvent(ClientSignalEvent event);
}
