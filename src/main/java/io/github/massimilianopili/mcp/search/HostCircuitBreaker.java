package io.github.massimilianopili.mcp.search;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight per-host circuit breaker for 429 (rate-limit) responses.
 * After {@code threshold} consecutive 429s from the same host, the circuit
 * opens for {@code openDuration}. While open, {@link #isOpen(String)} returns
 * true and callers should skip the host and go straight to fallback.
 * Thread-safe via ConcurrentHashMap + AtomicReference with immutable state.
 */
final class HostCircuitBreaker {

    private final int threshold;
    private final Duration openDuration;
    private final ConcurrentHashMap<String, AtomicReference<HostState>> states = new ConcurrentHashMap<>();

    HostCircuitBreaker(int threshold, Duration openDuration) {
        this.threshold = threshold;
        this.openDuration = openDuration;
    }

    boolean isOpen(String host) {
        AtomicReference<HostState> ref = states.get(host);
        if (ref == null) return false;
        HostState s = ref.get();
        if (s.consecutiveFails >= threshold) {
            if (Instant.now().isBefore(s.openedAt.plus(openDuration))) {
                return true;
            }
            // TTL expired, reset
            ref.compareAndSet(s, HostState.CLOSED);
            return false;
        }
        return false;
    }

    void record429(String host) {
        states.computeIfAbsent(host, k -> new AtomicReference<>(HostState.CLOSED))
              .updateAndGet(HostState::increment);
    }

    void recordSuccess(String host) {
        AtomicReference<HostState> ref = states.get(host);
        if (ref != null) {
            ref.set(HostState.CLOSED);
        }
    }

    private record HostState(int consecutiveFails, Instant openedAt) {
        static final HostState CLOSED = new HostState(0, Instant.MAX);

        HostState increment() {
            return new HostState(consecutiveFails + 1, Instant.now());
        }
    }
}
