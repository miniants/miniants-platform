package cn.miniants.platform.queue;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueueJobTest {

    @Test
    void readsReservedFields() {
        QueueJob job = new QueueJob("a1", Map.of(
                QueueJobFields.STATUS, QueueJobStatus.QUEUED,
                QueueJobFields.ATTEMPT, "2",
                QueueJobFields.MAX_ATTEMPTS, "10",
                QueueJobFields.LAST_ERROR, "x",
                "deviceNo", "N01"));
        assertEquals("a1", job.jobId());
        assertEquals(QueueJobStatus.QUEUED, job.status());
        assertEquals(2, job.attempt());
        assertEquals(10, job.maxAttempts());
        assertEquals("x", job.lastError());
        assertEquals("N01", job.get("deviceNo"));
        assertFalse(job.isTerminal());
    }

    @Test
    void terminalAndBadAttempt() {
        assertTrue(new QueueJob("id", Map.of(QueueJobFields.STATUS, QueueJobStatus.FAILED)).isTerminal());
        assertTrue(new QueueJob("id", Map.of(QueueJobFields.STATUS, QueueJobStatus.SUCCEEDED)).isTerminal());
        assertEquals(0, new QueueJob("id", Map.of(QueueJobFields.ATTEMPT, "nope")).attempt());
        assertEquals("", new QueueJob("id", Map.of()).get("missing"));
    }
}
