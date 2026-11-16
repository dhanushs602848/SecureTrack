package com.example.securetrack;

import com.example.securetrack.models.LocationRecord;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class PermissionUtilsTest {

    @Test
    public void testLocationRecordUuidGeneration() {
        String uuid = UUID.randomUUID().toString();
        LocationRecord record = new LocationRecord(1L, uuid, 37.7749, -122.4194, 5.0f, 10.0, 0.0f, "2025-01-01T00:00:00Z");

        assertNotNull(record);
        assertEquals(1L, (long) record.getDeviceId());
        assertEquals(uuid, record.getClientUuid());
        assertEquals(37.7749, record.getLatitude(), 0.0001);
        assertEquals(-122.4194, record.getLongitude(), 0.0001);
    }
}
