package com.apexstore.gateways;

import ApexStore.INotifyPaymentResultPrx;
import ApexStore.PaymentRequest;

import java.util.concurrent.ScheduledExecutorService;


public class CryptoStrategyI extends SimulatedGatewayBase {

    public CryptoStrategyI(INotifyPaymentResultPrx callback,
                           ScheduledExecutorService scheduler) {
        super("Crypto", callback, scheduler);
    }

    @Override
    protected String validate(PaymentRequest r) {
        if (value(r, "walletAddress").isEmpty()
                || value(r, "blockchainNetwork").isEmpty()) {
            return "Crypto: missing walletAddress or blockchainNetwork";
        }
        if (!"BTC".equalsIgnoreCase(r.currency)) {
            return "Crypto: only accepts BTC";
        }
        return null;
    }

    @Override
    protected long latencyMs() {
        // Block confirmation: the slowest of the three.
        return random(1500, 3000);
    }
}
