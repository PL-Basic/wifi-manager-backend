package com.plagod.mapper;

import com.plagod.entity.device.DeviceCommandRecord;
import com.plagod.entity.device.Esp32Node;
import com.plagod.entity.device.SessionRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DevicePersistenceCarrierMappingContractTest {

    @Test
    void exposesCarrierFieldsWithoutOwningLaterBusinessOperations()
            throws Exception {
        assertNotNull(Esp32Node.class.getDeclaredField("clientRequestId"));
        assertNotNull(Esp32Node.class.getDeclaredField(
                "quotaReservationReference"));
        assertNotNull(Esp32Node.class.getDeclaredField("version"));
        assertNotNull(SessionRecord.class.getDeclaredField("clientRequestId"));
        assertNotNull(SessionRecord.class.getDeclaredField(
                "quotaReservationReference"));
        assertNotNull(SessionRecord.class.getDeclaredField("version"));
        assertNotNull(DeviceCommandRecord.class.getDeclaredField(
                "dispatchWorkerId"));
        assertNotNull(DeviceCommandRecord.class.getDeclaredField(
                "dispatchLeaseUntil"));
    }
}
