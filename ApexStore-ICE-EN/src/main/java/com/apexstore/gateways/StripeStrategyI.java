package com.apexstore.gateways;

import ApexStore.INotifyPaymentResultPrx;
import ApexStore.PaymentRequest;

import java.util.concurrent.ScheduledExecutorService;

public class StripeStrategyI extends SimulatedGatewayBase {

    public StripeStrategyI(INotifyPaymentResultPrx callback,
                           ScheduledExecutorService scheduler) {
        super("Stripe", callback, scheduler);
    }

    @Override
    protected String validate(PaymentRequest r) {
        if (value(r, "cardToken").isEmpty()) {
            return "Stripe: missing cardToken";
        }
        if (value(r, "securityCvc").isEmpty()) {
            return "Stripe: missing securityCvc";
        }
        if (!"USD".equalsIgnoreCase(r.currency)) {
            return "Stripe: only accepts USD";
        }
        return null;
    }

    @Override
    protected long latencyMs() {
        return random(100, 300);
    }
}
