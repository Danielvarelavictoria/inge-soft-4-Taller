package com.apexstore.backend;

import ApexStore.GatewayUnavailable;
import ApexStore.IPersistPostgresTransactionPrx;
import ApexStore.PaymentProcessorContext;
import ApexStore.PaymentRequest;
import ApexStore.PaymentResult;
import ApexStore.PaymentStrategyPrx;
import ApexStore.RequestAck;
import ApexStore.Transaction;
import ApexStore.TransactionNotFound;
import ApexStore.TransactionState;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.LocalException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class PaymentProcessorContextI implements PaymentProcessorContext {

    private final IPersistPostgresTransactionPrx persistence;
    private final PaymentStrategyRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final long callbackTimeoutMs;

    private final Map<String, String> inFlight = new ConcurrentHashMap<>();

    public PaymentProcessorContextI(
            IPersistPostgresTransactionPrx persistence,
            PaymentStrategyRegistry registry,
            ScheduledExecutorService scheduler,
            long callbackTimeoutMs) {

        this.persistence = persistence;
        this.registry = registry;
        this.scheduler = scheduler;
        this.callbackTimeoutMs = callbackTimeoutMs;
    }


    @Override
    public RequestAck startOrderPayment(PaymentRequest r, Current current) {

        String id = r.transactionId;
        String method = r.paymentMethod == null ? "" : r.paymentMethod.toLowerCase();

        System.out.println("[Context] Order " + id + " method=" + method);

        if (id == null || id.isBlank() || r.amount <= 0) {
            return ack(id, false, TransactionState.REJECTED,
                    "Invalid request (empty id or amount <= 0)");
        }

        PaymentStrategyPrx strategy = registry.get(method);
        if (strategy == null) {
            return ack(id, false, TransactionState.REJECTED,
                    "Unsupported payment method: " + r.paymentMethod);
        }

        try {
            Transaction created = new Transaction(
                    id, r.amount, r.currency, method,
                    TransactionState.PENDING, "Order registered");

            if (!persistence.persistPostgresTransaction(created)) {
                return duplicateResponse(id);
            }

        } catch (LocalException e) {
            System.out.println("[Context] DB unavailable: " + e.getClass().getSimpleName());
            return ack(id, false, TransactionState.FAILED,
                    "Persistence unavailable; no charge was made");
        }

        CircuitBreaker breaker = registry.breaker(method);
        if (!breaker.allow()) {
            mark(id, TransactionState.FAILED, "Circuit open for " + method);
            return ack(id, false, TransactionState.FAILED,
                    "Gateway " + method + " temporarily disabled (circuit open)");
        }

        inFlight.put(id, method);
        RequestAck gatewayAck;

        try {
            gatewayAck = strategy.processPaymentTransaction(r);

        } catch (GatewayUnavailable | LocalException e) {
            inFlight.remove(id);
            breaker.recordFailure();
            String reason = e instanceof GatewayUnavailable
                    ? ((GatewayUnavailable) e).reason
                    : e.getClass().getSimpleName();

            mark(id, TransactionState.FAILED, "Gateway unavailable: " + reason);
            System.out.println("[Context] Isolated failure in " + method + ": " + reason);

            return ack(id, false, TransactionState.FAILED,
                    "Gateway " + method + " unavailable: " + reason);
        }

        if (!gatewayAck.accepted) {
            inFlight.remove(id);
            breaker.recordSuccess();   // the gateway answered; it only rejected the request
            mark(id, TransactionState.REJECTED, gatewayAck.message);
            return ack(id, false, TransactionState.REJECTED, gatewayAck.message);
        }

        mark(id, TransactionState.PROCESSING, gatewayAck.message);
        scheduler.schedule(() -> expireTimeout(id), callbackTimeoutMs, TimeUnit.MILLISECONDS);

        return ack(id, true, TransactionState.PROCESSING, gatewayAck.message);
    }

    @Override
    public Transaction queryOrderStatus(String transactionId, Current current)
            throws TransactionNotFound {

        return persistence.queryTransaction(transactionId);
    }



    @Override
    public void notifyPaymentResult(PaymentResult r, Current current) {

        System.out.println("[Context] Callback for " + r.transactionId
                + " successful=" + r.successful);

        String method = inFlight.remove(r.transactionId);
        if (method != null) {
            registry.breaker(method).recordSuccess();
        }

        TransactionState state = r.successful
                ? TransactionState.APPROVED
                : TransactionState.REJECTED;

        try {
            boolean applied = persistence.updateTransactionState(
                    r.transactionId, state,
                    r.message + " [ref=" + r.externalReference + "]");

            if (applied) {
                System.out.println("[Context] " + r.transactionId + " -> " + state
                        + " (the client sees it with queryOrderStatus)");
            } else {
                System.out.println("[Context] Callback ignored for " + r.transactionId
                        + " (duplicate or late; check reconciliation if a charge was made)");
            }

        } catch (TransactionNotFound e) {
            System.out.println("[Context] ALERT: callback without a registered order: "
                    + r.transactionId);
        }
    }


    private void expireTimeout(String id) {

        String method = inFlight.remove(id);
        if (method == null) {
            return;
        }

        registry.breaker(method).recordFailure();
        System.out.println("[Context] Callback timeout for " + id + " (" + method + ")");
        mark(id, TransactionState.FAILED,
                "No response from the gateway within " + callbackTimeoutMs + " ms");
    }

    private RequestAck duplicateResponse(String id) {
        try {
            Transaction existing = persistence.queryTransaction(id);
            return ack(id, false, existing.state,
                    "Duplicate transaction: already exists in state " + existing.state
                            + " (not charged again)");
        } catch (TransactionNotFound | LocalException e) {
            return ack(id, false, TransactionState.REJECTED, "Duplicate transaction");
        }
    }

    private void mark(String id, TransactionState state, String detail) {
        try {
            persistence.updateTransactionState(id, state, detail);
        } catch (TransactionNotFound | LocalException e) {
            System.out.println("[Context] Could not update " + id + " to " + state
                    + ": " + e.getClass().getSimpleName());
        }
    }

    private static RequestAck ack(String id, boolean ok, TransactionState state, String msg) {
        return new RequestAck(id == null ? "" : id, ok, state, msg);
    }
}
