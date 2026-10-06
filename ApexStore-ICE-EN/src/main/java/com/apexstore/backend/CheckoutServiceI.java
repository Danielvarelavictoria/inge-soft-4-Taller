package com.apexstore.backend;

import ApexStore.IManageHttpPurchases;
import ApexStore.IStartOrderPaymentPrx;
import ApexStore.PaymentRequest;
import ApexStore.RequestAck;
import ApexStore.Transaction;
import ApexStore.TransactionNotFound;
import com.zeroc.Ice.Current;


public class CheckoutServiceI implements IManageHttpPurchases {

    private final IStartOrderPaymentPrx startOrderPayment;

    public CheckoutServiceI(IStartOrderPaymentPrx startOrderPayment) {
        this.startOrderPayment = startOrderPayment;
    }

    @Override
    public RequestAck manageHttpPurchases(PaymentRequest request, Current current) {

        System.out.println("[Checkout] Request received " + request.transactionId);
        return startOrderPayment.startOrderPayment(request);
    }

    @Override
    public Transaction queryOrderStatus(String transactionId, Current current)
            throws TransactionNotFound {

        return startOrderPayment.queryOrderStatus(transactionId);
    }
}
