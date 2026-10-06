package com.apexstore.gateways;

import ApexStore.INotifyPaymentResultPrx;
import com.apexstore.common.IceConfig;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Node3GatewaysServer {

    public static void main(String[] args) {

        Communicator communicator = IceConfig.init(args, "node3.cfg");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            communicator.destroy();
        }));

        ObjectAdapter adapter = communicator.createObjectAdapter("Node3Adapter");

        ObjectPrx base = communicator.propertyToProxy("ApexStore.Callback.Proxy");
        if (base == null) {
            throw new IllegalStateException("Missing ApexStore.Callback.Proxy");
        }
        INotifyPaymentResultPrx callback =
                INotifyPaymentResultPrx.uncheckedCast(base.ice_invocationTimeout(3000));

        adapter.add(new StripeStrategyI(callback, scheduler), Util.stringToIdentity("Stripe"));
        adapter.add(new PseStrategyI(callback, scheduler), Util.stringToIdentity("PSE"));
        adapter.add(new CryptoStrategyI(callback, scheduler), Util.stringToIdentity("Crypto"));

        adapter.activate();

        System.out.println("[Node 3] Strategies published: Stripe, PSE, Crypto (simulated).");
        System.out.println("[Node 3] Provides: EstrategiaPago.procesarTransaccionPago");
        System.out.println("[Node 3] Requires: notificarResultadoPago -> " + callback);

        communicator.waitForShutdown();
    }
}
