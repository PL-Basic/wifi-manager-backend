package com.plagod.event;

public class DefaultTenantMembershipOutboxCreatedEvent {
    private final String eventId;

    public DefaultTenantMembershipOutboxCreatedEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}
