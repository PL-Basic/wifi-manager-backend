package com.plagod.service;

import com.plagod.dto.CommandResultEvent;

public interface CommandResultEventService {

    void handleCommandResult(CommandResultEvent event);
}