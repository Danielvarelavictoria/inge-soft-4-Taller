# ApexStore - ICE implementation of the corrected diagram (Point 4)

The 4 nodes of the diagram run as 4 independent Java processes.
All payment gateways and the database are **simulated** (no connection to real services).

| Node | Main class | Port | Components |
|------|------------|------|------------|
| 4 - Transactional DB | `persistence.Node4PersistenceServer` | 10004 | `PostgresTransactionsDbI` |
| 2 - Backend Core | `backend.Node2BackendServer` | 10005 | `CheckoutServiceI`, `PaymentProcessorContextI` |
| 3 - Gateways | `gateways.Node3GatewaysServer` | 10006 | `StripeStrategyI`, `PseStrategyI`, `CryptoStrategyI` |
| 1 - Clients | `client.Node1ClientApp` | (client) | simulated WebApp / MobileApp |

## Running (4 terminals, in this order)

```
./gradlew runNode4
./gradlew runNode3
./gradlew runNode2
./gradlew runNode1                              # scenario demo
./gradlew runNode1 -PappArgs="breaker"          # fault isolation (circuit breaker)
./gradlew runNode1 -PappArgs="load 3000"        # concurrent load + P95
```
(On Windows: `gradlew.bat runNode4`, etc.)
Ports/hosts are changed in `src/main/resources/node*.cfg` or with `--Key=value`.

## Available simulations (field `data["simulation"]`)
`REJECTION`, `OUTAGE`, `NO_RESPONSE`, `CONGESTION`, `DUPLICATE_CALLBACK`

## Diagram name -> code name
| Diagram | Code |
|---|---|
| gestionarComprasHttp | `IManageHttpPurchases.manageHttpPurchases` |
| iniciarPagoOrden | `IStartOrderPayment.startOrderPayment` |
| notificarResultadoPago | `INotifyPaymentResult.notifyPaymentResult` |
| EstrategiaPago [procesarTransaccionPago] | `PaymentStrategy.processPaymentTransaction` |
| persistirTransaccionPostgres | `IPersistPostgresTransaction.persistPostgresTransaction` |
| ProcesadorPagosContexto | `PaymentProcessorContextI` |
| ServicioCheckout | `CheckoutServiceI` |
| DB_PostgreSQL_Transacciones | `PostgresTransactionsDbI` |
