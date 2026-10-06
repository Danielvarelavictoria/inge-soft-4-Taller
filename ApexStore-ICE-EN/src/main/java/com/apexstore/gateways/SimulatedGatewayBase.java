package com.apexstore.gateways;

import ApexStore.GatewayUnavailable;
import ApexStore.INotifyPaymentResultPrx;
import ApexStore.PaymentRequest;
import ApexStore.PaymentResult;
import ApexStore.PaymentStrategy;
import ApexStore.RequestAck;
import ApexStore.TransactionState;
import com.zeroc.Ice.Current;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public abstract class SimulatedGatewayBase implements PaymentStrategy {

    private static final int CALLBACK_RETRIES = 3;

    private final String name;
    private final INotifyPaymentResultPrx callback;
    private final ScheduledExecutorService scheduler;

    protected SimulatedGatewayBase(
            String name,
            INotifyPaymentResultPrx callback,
            ScheduledExecutorService scheduler) {

        this.name = name;
        this.callback = callback;
        this.scheduler = scheduler;
    }

    protected abstract String validate(PaymentRequest request);

    protected abstract long latencyMs();

    @Override
    public RequestAck processPaymentTransaction(PaymentRequest r, Current current)
            throws GatewayUnavailable {

        String simulation = value(r, "simulation");

        log("Request " + r.transactionId + " (" + r.amount + " " + r.currency + ")"
                + (simulation.isEmpty() ? "" : " [simulation=" + simulation + "]"));

        if (simulation.equals("OUTAGE")) {
            throw new GatewayUnavailable(name, "Simulated gateway out of service");
        }

        String error = validate(r);
        if (error != null) {
            log("Rejected by validation: " + error);
            return new RequestAck(
                    r.transactionId, false, TransactionState.REJECTED, error);
        }

        if (!simulation.equals("NO_RESPONSE")) {
            long delay = simulation.equals("CONGESTION") ? 8000 : latencyMs();
            scheduler.schedule(() -> completeCharge(r, simulation),
                    delay, TimeUnit.MILLISECONDS);
        }

        return new RequestAck(
                r.transactionId, true, TransactionState.PROCESSING,
                "Charge accepted by " + name + "; the result will arrive via callback");
    }


    private void completeCharge(PaymentRequest r, String simulation) {

        boolean successful = !simulation.equals("REJECTION");

        PaymentResult result = new PaymentResult(
                r.transactionId,
                successful,
                name.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8),
                successful ? "Payment approved by " + name + " (simulated)"
                           : "Payment rejected by " + name + " (simulated)");

        log("Result ready for " + r.transactionId
                + ": " + (successful ? "APPROVED" : "REJECTED"));

        notifyResult(result, 1);

        if (simulation.equals("DUPLICATE_CALLBACK")) {
            log("Resending duplicate callback for " + r.transactionId);
            notifyResult(result, 1);
        }
    }

    private void notifyResult(PaymentResult result, int attempt) {

        callback.notifyPaymentResultAsync(result).whenComplete((v, ex) -> {

            if (ex == null) {
                return;
            }

            if (attempt < CALLBACK_RETRIES) {
                log("Callback for " + result.transactionId + " failed (attempt "
                        + attempt + "): " + ex.getClass().getSimpleName() + ". Retrying...");
                scheduler.schedule(() -> notifyResult(result, attempt + 1),
                        300L * attempt, TimeUnit.MILLISECONDS);
            } else {
                log("Callback for " + result.transactionId
                        + " could NOT be delivered after " + attempt + " attempts.");
            }
        });
    }


    protected static String value(PaymentRequest r, String key) {
        if (r.data == null) {
            return "";
        }
        String v = r.data.get(key);
        return v == null ? "" : v.trim();
    }

    protected static long random(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    protected void log(String message) {
        System.out.println("[" + name + " Simulated] " + message);
    }
}
