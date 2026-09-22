/*
 * ============================================================================
 * CALL COUPLING, turned up to maximum
 * ============================================================================
 *
 * This is the Order Service as the A5 audit diagram describes it. Before reading
 * the commentary, do the exercise yourself:
 *
 * Count the arrows leaving this class. That number is its EFFERENT coupling.
 * Then ask: for how many of them could I write a unit test without a network?
 */
class Bad_OrderService_DirectCalls {

    /*
     * ---- PROBLEM 1: CREATION COUPLING -------------------------------------
     *
     * `new` is the strongest form of call coupling available. It binds this class
     * to a CONCRETE type, to that type's CONSTRUCTOR SIGNATURE, and to its
     * LIFECYCLE - all at compile time, all unchangeable without editing this file.
     *
     * Creation is worth counting as a dependency type in its own right for exactly
     * this reason. `new` is not one dependency, it is three.
     */
    private final PaymentService payment = new PaymentService();
    private final InventoryService inventory = new InventoryService();
    private final NotificationService notifier = new NotificationService();
    private final AnalyticsClient analytics = new AnalyticsClient();

    /*
     * ---- PROBLEM 2: SHARED DATABASE (A5 Problem 4) -------------------------
     *
     * Order, Product and Payment all hold one of these, all pointing at one
     * schema. Afferent coupling on PostgresConnection = 3, and none of the three
     * knows what the others read. When Product renamed `stock_count` to
     * `available_units`, Order broke - at runtime, in production, with no compiler
     * warning at any point.
     */
    private final PostgresConnection db = new PostgresConnection("jdbc:postgresql://10.0.3.14/shopstream");

    /**
     * Place an order from a live stream.
     *
     * Read the body and notice: this method is not a workflow, it is a WIRING
     *                           DIAGRAM. Every collaborator in the system is named here.
     */
    OrderId placeOrder(BuyerId buyer, ProductId product, int qty, StreamId stream) {

        // Direct call, concrete class, no interface. Static coupling.
        int available = inventory.checkStock(product);
        if (available < qty) {
            throw new IllegalStateException("Out of stock");
        }

        Money unitPrice = new Money(db.queryPrice(product), "AED");
        Money total = unitPrice.times(qty);

        /*
         * ---- PROBLEM 3: STATIC UTILITY CALL --------------------------------
         *
         * `TaxUtils.calculateVat` looks harmless. It is a dependency you cannot
         * inject, cannot fake, cannot vary per market, and cannot see in a
         * constructor. When ShopStream launches in Egypt (14% VAT, not 5%),
         * somebody has to edit a static method used by four other services.
         *
         * A static call is call coupling that has been made INVISIBLE. That is
         * worse than a visible dependency, not better.
         */
        Money tax = TaxUtils.calculateVat(total);
        Money grandTotal = total.plus(tax);

        OrderId orderId = new OrderId(java.util.UUID.randomUUID().toString());
        db.insertOrder(orderId, buyer, product, qty, grandTotal, "PLACED");

        /*
         * ---- PROBLEM 4: THE CYCLE (A5 Problem 2) ---------------------------
         *
         * Order calls Payment...
         */
        payment.chargeAndConfirm(orderId, grandTotal, this);
        //                                             ^^^^
        // ...and hands Payment a reference to ITSELF, so Payment can call back.
        //
        // This is the bidirectional dependency from the audit diagram, in one
        // argument. Order depends on Payment (efferent). Payment depends on Order
        // (afferent, from Order's point of view). Neither can be built, tested,
        // deployed, versioned or reasoned about alone.
        //
        // Note it is also a TEMPORAL coupling and a CONTROL coupling: Order is
        // telling Payment "and when you're done, drive my state machine for me."

        notifier.sendOrderEmail(buyer, orderId);   // blocking SMTP call, in checkout
        analytics.track("order_placed", orderId);  // blocking HTTP call, in checkout

        return orderId;
    }

    /**
     * ---- THE OTHER HALF OF THE CYCLE --------------------------------------
     *
     * Payment Service calls this to move the order forward. Which means the
     * Payment Service holds a compile-time reference to `Bad_OrderService_DirectCalls`.
     *
     * Consequences to name in class:
     *
     * - Renaming this method is a cross-service breaking change.
     * - A Payment deployment can fail because of an Order class it was compiled
     *   against.
     * - If this method throws, the money has been taken and the order is stuck
     *   in PLACED. Who reconciles? Nobody. This is the 8% failure incident.
     * - You cannot answer "who owns order state?" - both services mutate it.
     */
    void confirmPaid(OrderId orderId, PaymentId paymentId) {
        db.updateOrderStatus(orderId, "CONFIRMED");
        notifier.sendReceipt(orderId, paymentId);
    }
}

/*
 * ============================================================================
 * THE OTHER SIDE OF THE CYCLE, for completeness
 * ============================================================================
 */
class PaymentService {
    /**
     * Note the third parameter. Payment Service - a lower-level, more widely
     * reused building block - has a compile-time dependency on a higher-level,
     * ShopStream-specific orchestrator.
     *
     * The dependency arrow points the WRONG WAY. That is what folder 16
     * (Dependency Inversion) exists to fix. But notice: you do not need DIP to see
     * the problem. You just need to draw the arrows.
     */
    void chargeAndConfirm(OrderId order, Money amount, Bad_OrderService_DirectCalls callback) {
        PaymentId paymentId = new PaymentId("pay_" + order.value());
        // ... talk to Checkout.com ...
        callback.confirmPaid(order, paymentId);   // <- the cycle closes here
    }
}

// ---- Supporting cast, elided ------------------------------------------------
class InventoryService     { int checkStock(ProductId p) { return 42; } }
class NotificationService  { void sendOrderEmail(BuyerId b, OrderId o) { } 
                             void sendReceipt(OrderId o, PaymentId p) { } }
class AnalyticsClient      { void track(String event, OrderId o) { } }
class PostgresConnection   { PostgresConnection(String url) { }
                             long queryPrice(ProductId p) { return 28000L; }
                             void insertOrder(OrderId i, BuyerId b, ProductId p, int q, Money m, String s) { }
                             void updateOrderStatus(OrderId i, String s) { } }
class TaxUtils             { static Money calculateVat(Money m) { return new Money(m.minorUnits() / 20, m.currency()); } }
