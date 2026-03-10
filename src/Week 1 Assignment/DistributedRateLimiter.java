import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class DistributedRateLimiter {
    private static final long WINDOW_SECONDS = 3600;
    private static final int MAX_RETRIES = 8;

    private final int maxTokens;
    private final double refillRatePerSecond;
    private final BucketStore store;

    public DistributedRateLimiter(int maxTokensPerHour, BucketStore store) {
        this.maxTokens = maxTokensPerHour;
        this.refillRatePerSecond = maxTokensPerHour / (double) WINDOW_SECONDS;
        this.store = store;
    }

    public RateLimitResult checkRateLimit(String clientId) {
        long now = Instant.now().getEpochSecond();

        for (int i = 0; i < MAX_RETRIES; i++) {
            Bucket current = store.getOrCreate(clientId, () -> new Bucket(maxTokens, now));
            Bucket refilled = refill(current, now);

            if (refilled.tokens < 1.0) {
                long retryAfter = Math.max(1, (long) Math.ceil((1.0 - refilled.tokens) / refillRatePerSecond));
                int remaining = (int) Math.max(0, Math.floor(refilled.tokens));
                long reset = now + Math.max(0, (long) Math.ceil((maxTokens - refilled.tokens) / refillRatePerSecond));
                return new RateLimitResult(false, remaining, retryAfter,
                        "Denied (" + remaining + " requests remaining, retry after " + retryAfter + "s)",
                        new RateLimitStatus(maxTokens - remaining, maxTokens, reset));
            }

            Bucket updated = new Bucket(refilled.tokens - 1.0, refilled.lastRefillEpochSec);
            if (store.compareAndSet(clientId, current, updated)) {
                int remaining = (int) Math.max(0, Math.floor(updated.tokens));
                long reset = now + Math.max(0, (long) Math.ceil((maxTokens - updated.tokens) / refillRatePerSecond));
                return new RateLimitResult(true, remaining, 0,
                        "Allowed (" + remaining + " requests remaining)",
                        new RateLimitStatus(maxTokens - remaining, maxTokens, reset));
            }
        }

        return new RateLimitResult(false, 0, 1,
                "Denied (system busy, retry after 1s)",
                new RateLimitStatus(maxTokens, maxTokens, now + WINDOW_SECONDS));
    }

    public RateLimitStatus getRateLimitStatus(String clientId) {
        long now = Instant.now().getEpochSecond();
        Bucket current = store.getOrCreate(clientId, () -> new Bucket(maxTokens, now));
        Bucket refilled = refill(current, now);
        int remaining = (int) Math.max(0, Math.floor(refilled.tokens));
        long reset = now + Math.max(0, (long) Math.ceil((maxTokens - refilled.tokens) / refillRatePerSecond));
        return new RateLimitStatus(maxTokens - remaining, maxTokens, reset);
    }

    private Bucket refill(Bucket b, long nowEpochSec) {
        if (nowEpochSec <= b.lastRefillEpochSec) {
            return b;
        }
        long elapsed = nowEpochSec - b.lastRefillEpochSec;
        double tokens = Math.min(maxTokens, b.tokens + elapsed * refillRatePerSecond);
        return new Bucket(tokens, nowEpochSec);
    }

    public record RateLimitResult(
            boolean allowed,
            int remaining,
            long retryAfterSeconds,
            String message,
            RateLimitStatus status
    ) {}

    public record RateLimitStatus(int used, int limit, long resetEpochSeconds) {}

    public record Bucket(double tokens, long lastRefillEpochSec) {}

    public interface BucketStore {
        Bucket getOrCreate(String clientId, Supplier<Bucket> init);
        boolean compareAndSet(String clientId, Bucket expected, Bucket updated);
    }

    public static class InMemoryBucketStore implements BucketStore {
        private final ConcurrentHashMap<String, AtomicReference<Bucket>> map = new ConcurrentHashMap<>();

        @Override
        public Bucket getOrCreate(String clientId, Supplier<Bucket> init) {
            AtomicReference<Bucket> ref = map.computeIfAbsent(clientId, k -> new AtomicReference<>(init.get()));
            return ref.get();
        }

        @Override
        public boolean compareAndSet(String clientId, Bucket expected, Bucket updated) {
            AtomicReference<Bucket> ref = map.get(clientId);
            return ref != null && ref.compareAndSet(expected, updated);
        }
    }

    public static void main(String[] args) {
        DistributedRateLimiter limiter = new DistributedRateLimiter(1000, new InMemoryBucketStore());
        System.out.println(limiter.checkRateLimit("abc123").message());
        System.out.println(limiter.checkRateLimit("abc123").message());
        System.out.println(limiter.getRateLimitStatus("abc123"));
    }
}