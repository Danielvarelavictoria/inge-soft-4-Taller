package com.apexstore.backend;

import ApexStore.PaymentStrategyPrx;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class PaymentStrategyRegistry {

    private final Map<String, PaymentStrategyPrx> strategies = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public void register(String method, PaymentStrategyPrx strategy, CircuitBreaker breaker) {

        String key = method.toLowerCase();
        strategies.put(key, strategy);
        breakers.put(key, breaker);
    }

    public PaymentStrategyPrx get(String method) {
        return method == null ? null : strategies.get(method.toLowerCase());
    }

    public CircuitBreaker breaker(String method) {
        return method == null ? null : breakers.get(method.toLowerCase());
    }

    public Set<String> methods() {
        return strategies.keySet();
    }
}
