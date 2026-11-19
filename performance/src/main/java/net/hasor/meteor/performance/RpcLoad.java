package net.hasor.meteor.performance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import net.hasor.meteor.domain.MetTimeoutException;
import org.HdrHistogram.Histogram;

/** 固定并发的闭环负载；每个 worker 独占计数器和直方图，结束后合并。 */
final class RpcLoad {
    private RpcLoad() {
    }

    static Map<String, Object> run(PerformanceOptions options, byte[] expected, Callable<byte[]> call) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(options.concurrency, task -> {
            Thread worker = new Thread(task, "Meteor-performance-worker");
            worker.setDaemon(true);
            return worker;
        });
        try {
            Map<String, Object> warmup = phase(workers, options, options.warmupNanos, expected, call);
            System.err.println("Warmup complete; starting measurement.");
            Map<String, Object> measured = phase(workers, options, options.durationNanos, expected, call);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warmup", warmup);
            result.put("measurement", measured);
            result.put("passed", ((Number) warmup.get("failures")).longValue() == 0 && ((Number) measured.get("failures")).longValue() == 0 && ((Number) measured.get("successes")).longValue() > 0);
            return result;
        } finally {
            workers.shutdownNow();
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Load workers did not stop");
            }
        }
    }

    private static Map<String, Object> phase(ExecutorService workers, PerformanceOptions options, long duration, byte[] expected, Callable<byte[]> call) throws Exception {
        WorkerResult total = new WorkerResult();
        if (duration == 0)
            return total.report(0, 0);
        CountDownLatch ready = new CountDownLatch(options.concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong deadline = new AtomicLong();
        List<Future<WorkerResult>> tasks = new ArrayList<>();
        for (int i = 0; i < options.concurrency; i++) {
            tasks.add(workers.submit(() -> {
                WorkerResult result = new WorkerResult();
                ready.countDown();
                start.await();
                while (!Thread.currentThread().isInterrupted() && System.nanoTime() < deadline.get()) {
                    long began = System.nanoTime();
                    result.attempts++;
                    try {
                        byte[] response = call.call();
                        long elapsed = microsSince(began);
                        if (!Arrays.equals(expected, response)) {
                            result.failure(new IllegalStateException("Echo payload differs"));
                        } else {
                            result.successes++;
                            result.successLatency.recordValue(elapsed);
                        }
                        result.allLatency.recordValue(elapsed);
                    } catch (Exception failure) {
                        result.allLatency.recordValue(microsSince(began));
                        result.failure(failure);
                        if (failure instanceof InterruptedException)
                            Thread.currentThread().interrupt();
                    }
                }
                return result;
            }));
        }
        try {
            if (!ready.await(30, TimeUnit.SECONDS))
                throw new IllegalStateException("Load workers did not become ready");
            long began = System.nanoTime();
            deadline.set(began + duration);
            start.countDown();
            List<WorkerResult> completed = new ArrayList<>();
            long finishDeadline = deadline.get() + TimeUnit.MILLISECONDS.toNanos(options.timeoutMillis) + TimeUnit.SECONDS.toNanos(10);
            for (Future<WorkerResult> task : tasks) {
                completed.add(task.get(Math.max(1, finishDeadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            }
            long elapsed = System.nanoTime() - began;
            for (WorkerResult result : completed)
                total.add(result);
            return total.report(duration, elapsed);
        } finally {
            start.countDown();
            for (Future<WorkerResult> task : tasks)
                task.cancel(true);
        }
    }

    private static long microsSince(long began) {
        return Math.max(1, TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - began));
    }

    private static final class WorkerResult {
        private final Histogram         allLatency     = histogram();
        private final Histogram         successLatency = histogram();
        private final Map<String, Long> errors         = new LinkedHashMap<>();
        private final List<String>      samples        = new ArrayList<>();
        private       long              attempts;
        private       long              successes;
        private       long              timeouts;

        private WorkerResult() {
        }

        private static Histogram histogram() {
            Histogram histogram = new Histogram(TimeUnit.MINUTES.toMicros(1), 3);
            histogram.setAutoResize(true);
            return histogram;
        }

        private void failure(Exception failure) {
            Throwable cause = failure;
            boolean timeout = false;
            for (int depth = 0; depth < 64; depth++) {
                timeout |= cause instanceof TimeoutException || cause instanceof MetTimeoutException;
                if (cause.getCause() == null || cause.getCause() == cause)
                    break;
                cause = cause.getCause();
            }
            if (timeout)
                this.timeouts++;
            this.errors.merge(cause.getClass().getName(), 1L, Long::sum);
            if (this.samples.size() < 8)
                this.samples.add(cause.getClass().getName() + ": " + cause.getMessage());
        }

        private void add(WorkerResult other) {
            this.attempts += other.attempts;
            this.successes += other.successes;
            this.timeouts += other.timeouts;
            this.allLatency.add(other.allLatency);
            this.successLatency.add(other.successLatency);
            other.errors.forEach((key, count) -> this.errors.merge(key, count, Long::sum));
            for (String sample : other.samples)
                if (this.samples.size() < 8)
                    this.samples.add(sample);
        }

        private Map<String, Object> report(long configured, long elapsed) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("configuredSeconds", configured / 1_000_000_000.0);
            report.put("elapsedSeconds", elapsed / 1_000_000_000.0);
            report.put("drainSeconds", Math.max(0, elapsed - configured) / 1_000_000_000.0);
            report.put("attempts", this.attempts);
            report.put("successes", this.successes);
            report.put("failures", this.attempts - this.successes);
            report.put("timeouts", this.timeouts);
            report.put("errorRate", this.attempts == 0 ? 0.0 : (this.attempts - this.successes) / (double) this.attempts);
            report.put("successfulRequestsPerSecond", elapsed == 0 ? 0.0 : this.successes * 1_000_000_000.0 / elapsed);
            report.put("allLatencyMicros", latency(this.allLatency));
            report.put("successLatencyMicros", latency(this.successLatency));
            report.put("errors", this.errors);
            report.put("errorSamples", this.samples);
            return report;
        }

        private static Map<String, Object> latency(Histogram histogram) {
            Map<String, Object> result = new LinkedHashMap<>();
            long count = histogram.getTotalCount();
            result.put("samples", count);
            result.put("min", count == 0 ? null : histogram.getMinValue());
            result.put("mean", count == 0 ? null : histogram.getMean());
            result.put("p50", count == 0 ? null : histogram.getValueAtPercentile(50));
            result.put("p90", count == 0 ? null : histogram.getValueAtPercentile(90));
            result.put("p95", count == 0 ? null : histogram.getValueAtPercentile(95));
            result.put("p99", count == 0 ? null : histogram.getValueAtPercentile(99));
            result.put("p999", count == 0 ? null : histogram.getValueAtPercentile(99.9));
            result.put("max", count == 0 ? null : histogram.getMaxValue());
            return result;
        }
    }
}
