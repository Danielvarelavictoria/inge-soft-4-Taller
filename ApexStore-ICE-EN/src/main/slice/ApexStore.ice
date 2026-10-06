// ============================================================================
//  ApexStore - Slice contract of the CORRECTED DIAGRAM (Point 3)
//
//  Each Slice interface maps to an interface (lollipop/socket) of the diagram:
//
//    gestionarComprasHttp         -> IManageHttpPurchases         (Node 1 -> Node 2)
//    iniciarPagoOrden             -> IStartOrderPayment           (Checkout -> Context)
//    notificarResultadoPago       -> INotifyPaymentResult         (Gateways -> Context)
//    EstrategiaPago               -> PaymentStrategy              (Context -> Gateways)
//      [procesarTransaccionPago]     ONE polymorphic signature (RAS-04 fix)
//    persistirTransaccionPostgres -> IPersistPostgresTransaction  (Context -> Node 4)
//      (Crypto NO LONGER accesses the DB: encapsulation fix, RAS-03)
// ============================================================================
module ApexStore
{
    // Payment-method specific data (cardToken, bankCode, walletAddress...).
    // The interface is agnostic: each strategy knows which keys it needs
    // (information hiding, RAS-04).
    dictionary<string, string> PaymentMethodData;

    enum TransactionState
    {
        PENDING,      // stored in DB, not yet sent to the gateway
        PROCESSING,   // gateway accepted it; waiting for the callback
        APPROVED,     // final state
        REJECTED,     // final state
        FAILED        // final state (gateway down, timeout, etc.)
    };

    // Unified signature for the three payment methods.
    struct PaymentRequest
    {
        string transactionId;   // idempotency key
        double amount;
        string currency;
        string paymentMethod;   // "stripe" | "pse" | "crypto" | ...
        PaymentMethodData data;
    };

    struct RequestAck
    {
        string transactionId;
        bool accepted;
        TransactionState state;
        string message;
    };

    // Asynchronous result that the gateway delivers through a callback.
    struct PaymentResult
    {
        string transactionId;
        bool successful;
        string externalReference;
        string message;
    };

    struct Transaction
    {
        string transactionId;
        double amount;
        string currency;
        string paymentMethod;
        TransactionState state;
        string detail;
    };

    exception TransactionNotFound
    {
        string transactionId;
    };

    exception GatewayUnavailable
    {
        string gateway;
        string reason;
    };

    // ---------------------------- Node 1 -> Node 2 ---------------------------
    interface IManageHttpPurchases
    {
        RequestAck manageHttpPurchases(PaymentRequest request);
        Transaction queryOrderStatus(string transactionId)
            throws TransactionNotFound;
    };

    // ----------------------- CheckoutService -> Context ----------------------
    interface IStartOrderPayment
    {
        RequestAck startOrderPayment(PaymentRequest request);
        Transaction queryOrderStatus(string transactionId)
            throws TransactionNotFound;
    };

    // ------------------------ Gateways -> Context (callback) -----------------
    interface INotifyPaymentResult
    {
        void notifyPaymentResult(PaymentResult result);
    };

    // The Strategy context exposes both provided services of the diagram.
    interface PaymentProcessorContext extends IStartOrderPayment, INotifyPaymentResult
    {
    };

    // ---------------------- Context -> Gateways (Strategy) -------------------
    interface PaymentStrategy
    {
        RequestAck processPaymentTransaction(PaymentRequest request)
            throws GatewayUnavailable;
    };

    // ------------------------- Context -> Node 4 (DB) ------------------------
    interface IPersistPostgresTransaction
    {
        // true = inserted; false = already existed (idempotency, avoids double charge)
        bool persistPostgresTransaction(Transaction transaction);

        // Atomic state transition. true = applied; false = rejected because it is
        // an invalid transition (e.g. duplicate or late callback).
        bool updateTransactionState(string transactionId,
                                    TransactionState newState,
                                    string detail)
            throws TransactionNotFound;

        Transaction queryTransaction(string transactionId)
            throws TransactionNotFound;
    };
};
