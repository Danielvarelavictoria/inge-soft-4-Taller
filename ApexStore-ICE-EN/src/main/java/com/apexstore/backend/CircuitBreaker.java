package com.apexstore.backend;


public class CircuitBreaker {

    private final int threshold;
    private final long cooldownMs;

    private int consecutiveFailures = 0;
    private long openUntil = 0;

    public CircuitBreaker(int threshold, long cooldownMs) {
        this.threshold = threshold;
        this.cooldownMs = cooldownMs;
    }

    public synchronized boolean allow() {
        return System.currentTimeMillis() >= openUntil;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntil = 0;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= threshold) {
            openUntil = System.currentTimeMillis() + cooldownMs;
        }
    }

    public synchronized boolean isOpen() {
        return System.currentTimeMillis() < openUntil;
    }
}
