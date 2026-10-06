package com.apexstore.client;

import ApexStore.IManageHttpPurchasesPrx;
import ApexStore.PaymentRequest;
import ApexStore.RequestAck;
import ApexStore.Transaction;
import ApexStore.TransactionNotFound;
import ApexStore.TransactionState;
import com.apexstore.common.IceConfig;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectPrx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;


public class Node1ClientApp {

    public static void main(String[] args) throws Exception {

        String mode = (args.length > 0 && !args[0].startsWith("--")) ? args[0] : "demo";
        int n = (args.length > 1 && !args[1].startsWith("--")) ? Integer.parseInt(args[1]) : 1000;

        try (Communicator communicator = IceConfig.init(args, "node1.cfg")) {

            ObjectPrx base = communicator.propertyToProxy("ApexStore.Checkout.Proxy");
            IManageHttpPurchasesPrx checkout = IManageHttpPurchasesPrx.checkedCast(base);

            if (checkout == null) {
                throw new IllegalStateException("Could not reach the Checkout (Node 2).");
            }

            switch (mode) {
                case "breaker":
                    breakerScenario(checkout);
                    break;
                case "load":
                    loadScenario(checkout, n);
                    break;
                default:
                    demoScenario(checkout);
            }
        }
    }


    private static void demoScenario(IManageHttpPurchasesPrx checkout) throws Exception {

        String run = String.valueOf(System.currentTimeMillis() % 100000);
        List<String> ids = new ArrayList<>();

        System.out.println("=== SCENARIOS (run " + run + ") ===\n");

        ids.add(send("WebApp", checkout, "Stripe approved",
                request("WEB-" + run + "-01", 49.90, "USD", "stripe", stripe(), null)));
        ids.add(send("MobileApp", checkout, "PSE approved",
                request("MOB-" + run + "-02", 150000, "COP", "pse", pse(), null)));
        ids.add(send("WebApp", checkout, "Crypto approved",
                request("WEB-" + run + "-03", 0.0025, "BTC", "crypto", crypto(), null)));

        ids.add(send("MobileApp", checkout, "Crypto rejected by the gateway",
                request("MOB-" + run + "-04", 0.01, "BTC", "crypto", crypto(), "REJECTION")));

        send("WebApp", checkout, "DUPLICATE resend of order 01",
                request("WEB-" + run + "-01", 49.90, "USD", "stripe", stripe(), null));

        ids.add(send("MobileApp", checkout, "Stripe without token (strategy validation)",
                request("MOB-" + run + "-06", 20, "USD", "stripe", new HashMap<>(), null)));

        ids.add(send("WebApp", checkout, "PSE does not respond (no callback)",
                request("WEB-" + run + "-07", 80000, "COP", "pse", pse(), "NO_RESPONSE")));
        ids.add(send("MobileApp", checkout, "Crypto congested (late callback)",
                request("MOB-" + run + "-08", 0.02, "BTC", "crypto", crypto(), "CONGESTION")));
        ids.add(send("WebApp", checkout, "Stripe down",
                request("WEB-" + run + "-09", 30, "USD", "stripe", stripe(), "OUTAGE")));

        send("MobileApp", checkout, "Unsupported method (wallet)",
                request("MOB-" + run + "-10", 10, "USD", "wallet", new HashMap<>(), null));

        ids.add(send("WebApp", checkout, "Stripe with DUPLICATE callback",
                request("WEB-" + run + "-11", 15, "USD", "stripe", stripe(), "DUPLICATE_CALLBACK")));

        System.out.println("\n--- Waiting for asynchronous results (up to 14 s) ---");
        waitForFinalStates(checkout, ids, 14000);

        System.out.println("\n=== FINAL STATE IN THE DATABASE (Node 4) ===");
        System.out.printf("%-16s %-8s %-10s %s%n", "ORDER", "METHOD", "STATE", "DETAIL");
        for (String id : ids) {
            Transaction t = checkout.queryOrderStatus(id);
            System.out.printf("%-16s %-8s %-10s %s%n", t.transactionId, t.paymentMethod, t.state, t.detail);
        }
    }


    private static void breakerScenario(IManageHttpPurchasesPrx checkout) throws Exception {

        String run = String.valueOf(System.currentTimeMillis() % 100000);
        System.out.println("=== FAULT ISOLATION: Stripe down, PSE healthy ===\n");

        for (int i = 1; i <= 5; i++) {
            send("WebApp", checkout, "Stripe down #" + i,
                    request("BRK-" + run + "-S" + i, 10, "USD", "stripe", stripe(), "OUTAGE"));
        }

        send("MobileApp", checkout, "Normal PSE while Stripe is down",
                request("BRK-" + run + "-P1", 90000, "COP", "pse", pse(), null));

        Thread.sleep(2500);
        Transaction t = checkout.queryOrderStatus("BRK-" + run + "-P1");
        System.out.println("\nPSE ended in state: " + t.state + "  <- not affected by Stripe");
        System.out.println("(Stripe requests 4 and 5 must say 'circuit open': they fail without calling the gateway.)");
    }


    private static void loadScenario(IManageHttpPurchasesPrx checkout, int n) throws Exception {

        String run = String.valueOf(System.currentTimeMillis() % 100000);
        final int window = 64;   // simultaneous in-flight requests
        Semaphore inFlight = new Semaphore(window);

        System.out.println("=== LOAD: " + n + " purchases, " + window + " simultaneous ===");
        System.out.println("(If you just ran 'breaker', wait 10 s: the Stripe circuit is still open.)");

        long[] latencies = new long[n];
        String[] ids = new String[n];
        List<CompletableFuture<RequestAck>> futures = new ArrayList<>();

        long start = System.nanoTime();

        for (int i = 0; i < n; i++) {
            final int k = i;
            String id = "LOAD-" + run + "-" + i;
            ids[i] = id;

            PaymentRequest r;
            switch (i % 3) {
                case 0:  r = request(id, 25, "USD", "stripe", stripe(), null); break;
                case 1:  r = request(id, 90000, "COP", "pse", pse(), null); break;
                default: r = request(id, 0.001, "BTC", "crypto", crypto(), null);
            }

            inFlight.acquire();
            long t0 = System.nanoTime();
            futures.add(checkout.manageHttpPurchasesAsync(r).whenComplete((a, e) -> {
                latencies[k] = (System.nanoTime() - t0) / 1_000_000;
                inFlight.release();
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        double seconds = (System.nanoTime() - start) / 1e9;

        long accepted = futures.stream().filter(f -> f.join().accepted).count();
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);

        System.out.printf("Acks received in %.2f s  (~%.0f requests/s)%n", seconds, n / seconds);
        System.out.println("Accepted: " + accepted + " / " + n);
        System.out.println("Ack latency  P50=" + sorted[n / 2] + " ms   P95="
                + sorted[(int) (n * 0.95) - 1] + " ms   max=" + sorted[n - 1] + " ms"
                + "   (RAS-02 goal: P95 <= 250 ms)");

        System.out.println("\n--- Waiting for callbacks (6 s) ---");
        Thread.sleep(6000);

        Map<TransactionState, Integer> count = new HashMap<>();
        for (String id : ids) {
            count.merge(checkout.queryOrderStatus(id).state, 1, Integer::sum);
        }
        System.out.println("Final states: " + count);
    }


    private static String send(String app, IManageHttpPurchasesPrx checkout,
                               String description, PaymentRequest r) {

        long t0 = System.nanoTime();
        RequestAck ack = checkout.manageHttpPurchases(r);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("[%s] %-44s -> accepted=%-5s state=%-10s (%d ms) %s%n",
                app, description, ack.accepted, ack.state, ms, ack.message);
        return r.transactionId;
    }

    private static void waitForFinalStates(IManageHttpPurchasesPrx checkout,
                                           List<String> ids, long maxMs) throws Exception {

        long deadline = System.currentTimeMillis() + maxMs;

        while (System.currentTimeMillis() < deadline) {
            boolean all = true;
            for (String id : ids) {
                try {
                    TransactionState s = checkout.queryOrderStatus(id).state;
                    if (s == TransactionState.PENDING || s == TransactionState.PROCESSING) {
                        all = false;
                    }
                } catch (TransactionNotFound ex) {
                    all = false;
                }
            }
            if (all) {
                return;
            }
            Thread.sleep(500);
        }
    }

    private static PaymentRequest request(String id, double amount, String currency,
                                          String method, Map<String, String> data,
                                          String simulation) {
        Map<String, String> d = new HashMap<>(data);
        if (simulation != null) {
            d.put("simulation", simulation);
        }
        return new PaymentRequest(id, amount, currency, method, d);
    }

    private static Map<String, String> stripe() {
        Map<String, String> d = new HashMap<>();
        d.put("cardToken", "tok_simulated_visa");
        d.put("securityCvc", "123");
        return d;
    }

    private static Map<String, String> pse() {
        Map<String, String> d = new HashMap<>();
        d.put("bankCode", "1007");
        d.put("docType", "CC");
        d.put("accountNumber", "1234567890");
        return d;
    }

    private static Map<String, String> crypto() {
        Map<String, String> d = new HashMap<>();
        d.put("walletAddress", "bc1qsimulated0000000000000000000000");
        d.put("blockchainNetwork", "bitcoin-testnet");
        return d;
    }
}
