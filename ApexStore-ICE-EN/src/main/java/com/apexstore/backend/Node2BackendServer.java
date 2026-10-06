package com.apexstore.backend;

import ApexStore.IPersistPostgresTransactionPrx;
import ApexStore.IStartOrderPaymentPrx;
import ApexStore.PaymentStrategyPrx;
import com.apexstore.common.IceConfig;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class Node2BackendServer {

    public static void main(String[] args) {

        Communicator communicator = IceConfig.init(args, "node2.cfg");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            communicator.destroy();
        }));

        ObjectAdapter adapter = communicator.createObjectAdapter("Node2Adapter");

        int callbackTimeoutMs = IceConfig.intValue(communicator, "ApexStore.CallbackTimeoutMs", 5000);
        int gatewayTimeoutMs = IceConfig.intValue(communicator, "ApexStore.GatewayInvocationTimeoutMs", 2000);
        int threshold = IceConfig.intValue(communicator, "ApexStore.Breaker.Threshold", 3);
        int cooldownMs = IceConfig.intValue(communicator, "ApexStore.Breaker.CooldownMs", 10000);


        ObjectPrx dbProxy = communicator.propertyToProxy("ApexStore.Persistence.Proxy");
        if (dbProxy == null) {
            throw new IllegalStateException("Missing ApexStore.Persistence.Proxy");
        }
        IPersistPostgresTransactionPrx persistence =
                IPersistPostgresTransactionPrx.uncheckedCast(dbProxy.ice_invocationTimeout(3000));


        PaymentStrategyRegistry registry = new PaymentStrategyRegistry();
        String list = communicator.getProperties().getProperty("ApexStore.Strategies");

        for (String method : list.split(",")) {
            method = method.trim();
            if (method.isEmpty()) {
                continue;
            }
            ObjectPrx base = communicator.propertyToProxy("ApexStore.Strategy." + method);
            if (base == null) {
                throw new IllegalStateException("Missing ApexStore.Strategy." + method);
            }
            registry.register(
                    method,
                    PaymentStrategyPrx.uncheckedCast(base.ice_invocationTimeout(gatewayTimeoutMs)),
                    new CircuitBreaker(threshold, cooldownMs));

            System.out.println("[Node 2] Strategy registered: " + method);
        }


        PaymentProcessorContextI context = new PaymentProcessorContextI(
                persistence, registry, scheduler, callbackTimeoutMs);

        ObjectPrx contextBase = adapter.add(context, Util.stringToIdentity("PaymentProcessor"));


        adapter.add(
                new CheckoutServiceI(IStartOrderPaymentPrx.uncheckedCast(contextBase)),
                Util.stringToIdentity("Checkout"));

        adapter.activate();

        System.out.println("[Node 2] Checkout published (gestionarComprasHttp).");
        System.out.println("[Node 2] PaymentProcessor published (iniciarPagoOrden, notificarResultadoPago).");
        System.out.println("[Node 2] Callback timeout=" + callbackTimeoutMs + " ms, breaker="
                + threshold + " failures / " + cooldownMs + " ms.");

        communicator.waitForShutdown();
    }
}
