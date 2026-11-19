package net.hasor.meteor.performance;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RpcLoadTest {
    @Test
    void warmupCallsAreExcludedFromMeasurement() throws Exception {
        AtomicLong calls = new AtomicLong();
        byte[] payload = { 1, 2, 3 };
        Map<String, Object> report = RpcLoad.run(options(), payload, () -> {
            calls.incrementAndGet();
            return payload;
        });
        Map<?, ?> warmup = (Map<?, ?>) report.get("warmup");
        Map<?, ?> measured = (Map<?, ?>) report.get("measurement");
        assertEquals(Boolean.TRUE, report.get("passed"));
        long warmupCalls = ((Number) warmup.get("attempts")).longValue();
        long measuredCalls = ((Number) measured.get("attempts")).longValue();
        assertTrue(warmupCalls > 0);
        assertTrue(measuredCalls > 0);
        assertEquals(calls.get(), warmupCalls + measuredCalls);
        assertEquals(measuredCalls, measured.get("successes"));
        assertEquals(measuredCalls, ((Map<?, ?>) measured.get("successLatencyMicros")).get("samples"));
    }

    @Test
    void invalidEchoResponsesCountAsFailures() throws Exception {
        Map<String, Object> report = RpcLoad.run(options(), new byte[] { 1 }, () -> new byte[] { 2 });
        Map<?, ?> measured = (Map<?, ?>) report.get("measurement");
        assertEquals(Boolean.FALSE, report.get("passed"));
        assertEquals(0L, measured.get("successes"));
        assertEquals(measured.get("attempts"), measured.get("failures"));
        assertTrue(((Number) measured.get("attempts")).longValue() > 0);
        assertNull(((Map<?, ?>) measured.get("successLatencyMicros")).get("p99"));
    }

    @Test
    void wrappedTimeoutsRemainVisibleInReport() throws Exception {
        Map<String, Object> report = RpcLoad.run(options(), new byte[0], () -> {
            throw new ExecutionException(new TimeoutException("synthetic timeout"));
        });
        Map<?, ?> measured = (Map<?, ?>) report.get("measurement");
        assertEquals(Boolean.FALSE, report.get("passed"));
        assertTrue(((Number) measured.get("failures")).longValue() > 0);
        assertEquals(measured.get("failures"), measured.get("timeouts"));
        assertEquals(measured.get("attempts"), ((Map<?, ?>) measured.get("allLatencyMicros")).get("samples"));
        assertEquals(measured.get("failures"), ((Map<?, ?>) measured.get("errors")).get(TimeoutException.class.getName()));
    }

    private static PerformanceOptions options() {
        return PerformanceOptions.parse(new String[] { "client", "--address", "rsf://127.0.0.1:19000", "--concurrency", "2", "--warmup", "0.05", "--duration", "0.1" });
    }
}
