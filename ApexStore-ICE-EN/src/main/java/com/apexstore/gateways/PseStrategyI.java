package com.apexstore.gateways;

import ApexStore.INotifyPaymentResultPrx;
import ApexStore.PaymentRequest;

import java.util.concurrent.ScheduledExecutorService;

public class PseStrategyI extends SimulatedGatewayBase {

    public PseStrategyI(INotifyPaymentResultPrx callback,
                        ScheduledExecutorService scheduler) {
        super("PSE", callback, scheduler);
    }

    @Override
    protected String validate(PaymentRequest r) {
        if (value(r, "bankCode").isEmpty()
                || value(r, "docType").isEmpty()
                || value(r, "accountNumber").isEmpty()) {
            return "PSE: missing bankCode, docType or accountNumber";
        }
        if (!"COP".equalsIgnoreCase(r.currency)) {
            return "PSE: only accepts COP";
        }
        return null;
    }

    @Override
    protected long latencyMs() {
        return random(400, 1200);
    }
}
