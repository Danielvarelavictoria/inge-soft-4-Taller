package com.apexstore.persistence;

import ApexStore.IPersistPostgresTransaction;
import ApexStore.Transaction;
import ApexStore.TransactionNotFound;
import ApexStore.TransactionState;
import com.zeroc.Ice.Current;

import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class PostgresTransactionsDbI implements IPersistPostgresTransaction {

    private final ConcurrentHashMap<String, Transaction> table =
            new ConcurrentHashMap<>();

    @Override
    public boolean persistPostgresTransaction(Transaction transaction, Current current) {

        // Atomic INSERT; if the id already exists it is NOT overwritten.
        boolean inserted = table.putIfAbsent(
                transaction.transactionId, copy(transaction)) == null;

        audit(inserted
                ? "INSERT " + transaction.transactionId + " [" + transaction.state
                  + "] " + transaction.paymentMethod + " " + transaction.amount
                  + " " + transaction.currency
                : "INSERT REJECTED (duplicate) " + transaction.transactionId);

        return inserted;
    }

    @Override
    public boolean updateTransactionState(
            String transactionId,
            TransactionState newState,
            String detail,
            Current current) throws TransactionNotFound {

        AtomicBoolean applied = new AtomicBoolean(false);

        Transaction result = table.computeIfPresent(transactionId, (id, currentRow) -> {

            if (!validTransition(currentRow.state, newState)) {
                return currentRow;
            }

            applied.set(true);
            return new Transaction(
                    currentRow.transactionId, currentRow.amount, currentRow.currency,
                    currentRow.paymentMethod, newState, detail);
        });

        if (result == null) {
            throw new TransactionNotFound(transactionId);
        }

        audit(applied.get()
                ? "UPDATE " + transactionId + " -> " + newState + " (" + detail + ")"
                : "UPDATE IGNORED " + transactionId + ": " + result.state
                  + " -> " + newState + " is not a valid transition");

        return applied.get();
    }

    @Override
    public Transaction queryTransaction(String transactionId, Current current)
            throws TransactionNotFound {

        Transaction t = table.get(transactionId);

        if (t == null) {
            throw new TransactionNotFound(transactionId);
        }
        return copy(t);
    }


    static boolean validTransition(TransactionState current, TransactionState next) {

        if (isFinal(current)) {
            return false;
        }
        if (next == TransactionState.PENDING) {
            return false;
        }
        if (next == TransactionState.PROCESSING) {
            return current == TransactionState.PENDING;
        }
        return true;
    }

    static boolean isFinal(TransactionState s) {
        return s == TransactionState.APPROVED
                || s == TransactionState.REJECTED
                || s == TransactionState.FAILED;
    }

    private static Transaction copy(Transaction t) {
        return new Transaction(t.transactionId, t.amount, t.currency,
                t.paymentMethod, t.state, t.detail);
    }

    private static void audit(String message) {
        System.out.println("[AUDIT " + LocalTime.now().withNano(0) + "] " + message);
    }
}
