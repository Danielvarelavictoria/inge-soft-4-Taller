package com.apexstore.persistence;

import com.apexstore.common.IceConfig;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class Node4PersistenceServer {

    public static void main(String[] args) {

        Communicator communicator = IceConfig.init(args, "node4.cfg");
        Runtime.getRuntime().addShutdownHook(new Thread(communicator::destroy));

        ObjectAdapter adapter = communicator.createObjectAdapter("Node4Adapter");

        adapter.add(new PostgresTransactionsDbI(), Util.stringToIdentity("Persistence"));
        adapter.activate();

        System.out.println("[Node 4] DB_PostgreSQL_Transacciones (simulated) ready.");
        System.out.println("[Node 4] Provides: persistirTransaccionPostgres");

        communicator.waitForShutdown();
    }
}
