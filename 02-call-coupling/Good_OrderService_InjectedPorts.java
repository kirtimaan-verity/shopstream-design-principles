/*
 * ============================================================================
 * CALL COUPLING, reduced to loose-but-sufficient
 * ============================================================================
 *
 * Four changes from the Bad_ version. Walk them one at a time in class:
 *
 * 1. NOTHING IS CONSTRUCTED HERE. No `new` on a collaborator. Creation coupling
 *    gone - this class no longer knows any concrete class's constructor.
 *
 * 2. THE CYCLE IS CUT. The payment port RETURNS a result. Order reads it and
 *    decides what the order's state becomes. Payment never calls back, never
 *    sees an Order type, never mutates order state. Order owns order state -
 *    a sentence you could not say about the Bad_ version.
 *
 * 3. SIX COLLABORATORS BECAME THREE. Notification and analytics left the
 *    critical path entirely (folder 04). Tax became a policy object instead of
 *    an invisible static call.
 *
 * 4. EVERY DEPENDENCY IS AN INTERFACE THIS SERVICE OWNS. That last clause is
 *    the one that actually reduces coupling rather than relocating it - see
 *    the honesty note at the bottom, and folder 16.
 */
class Good_OrderService_InjectedPorts {

    /*
     * These are PORTS: narrow interfaces, named in the language of what the Order
     *                  Service needs, declared next to the Order Service, not next to the adapter.
     *
     * `final` matters. It says: these are established at construction and cannot
     * be swapped mid-flight. No `setActiveProvider(...)` surprises at 2am.
     */
    private final Orders orders;
    private final Stock stock;
    private final Payments payments;
    private final TaxPolicy tax;

    Good_OrderService_InjectedPorts(Orders orders, Stock stock, Payments payments, TaxPolicy tax) {
        this.orders = orders;
        this.stock = stock;
        this.payments = payments;
        this.tax = tax;
    }

    /**
     * The same use case as the Bad_ version. Read them side by side: this one
     * describes a BUSINESS WORKFLOW; the other described a deployment topology.
     */
    Order placeOrder(BuyerId buyer, SellerId seller, StreamId stream, ProductId product, int qty) {

        if (!stock.isAvailable(product, qty)) {
            throw new OutOfStock(product);
        }

        Money unitPrice = stock.priceOf(product);
        Money net = unitPrice.times(qty);
        Money gross = net.plus(tax.vatOn(net));    // market-specific rule, injected

        Order order = new Order(OrderId.next(), buyer, seller, stream,
                java.util.List.of(new OrderLine(product, qty, unitPrice)),
                OrderStatus.PLACED);
        orders.save(order);

        /*
         * ---- THE CYCLE, CUT --------------------------------------------------
         *
         * Compare:
         *     Bad:   payment.chargeAndConfirm(orderId, total, this);
         *     Good:  PaymentResult result = payments.authorise(order.id(), gross);
         *
         * One word - `return` instead of `callback` - removes an entire class of
         * problems. Payment is now a LEAF dependency: things call it, it calls
         * nothing of ours. It can be deployed, versioned, load-tested and rolled
         * back on its own.
         *
         * This is the single highest-value refactoring on the A5 board, and it is
         * also the cheapest. Say that out loud; participants expect the valuable
         * fixes to be the expensive ones.
         */
        PaymentResult result = payments.authorise(order.id(), gross);

        Order settled = result.authorised()
                ? order.withStatus(OrderStatus.AUTHORISED)
                : order.withStatus(OrderStatus.CANCELLED);

        orders.save(settled);
        return settled;
    }
}

/*
 * ============================================================================
 * THE PORTS - narrow, domain-named, owned by the Order Service
 * ============================================================================
 *
 * Notice what these interfaces are NOT called: not `IPaymentService`, not
 * `PostgresOrderDao`, not `CheckoutComGateway`. They are named for what the ORDER
 * SERVICE needs, in the Order Service's own words. That naming is not cosmetic -
 * it is what makes the dependency inversion real (folder 16). An interface named
 * after the implementation is just the implementation with extra steps.
 */

/** What Order needs from persistence. Two methods. Not a general-purpose DAO. */
interface Orders {
    void save(Order order);
    java.util.Optional<Order> byId(OrderId id);
}

/** What Order needs from the Product Service. Order does not need `search()`. */
interface Stock {
    boolean isAvailable(ProductId product, int qty);
    Money priceOf(ProductId product);
}

/**
 * What Order needs from payments. ONE method.
 *
 * Compare with folder 01's `Good_PaymentApi`, which has three. That is correct
 * and intentional: the Payment module OFFERS three operations; the Order Service
 * REQUIRES one. Interface Segregation says the consumer's interface is shaped by
 * the consumer, not by the provider's full catalogue.
 */
interface Payments {
    PaymentResult authorise(OrderId order, Money amount);
}

/** Was a static call. Now a collaborator - so Egypt's 14% is a wiring decision. */
interface TaxPolicy {
    Money vatOn(Money net);
}

class OutOfStock extends RuntimeException {
    OutOfStock(ProductId p) { super("Out of stock: " + p.value()); }
}

/*
 * ============================================================================
 * HONESTY NOTE: what we did NOT achieve
 * ============================================================================
 *
 * The blunt version of the rule:
 *
 *   forgoing static dependencies in favour of dynamic ones does not necessarily
 *   reduce the underlying coupling
 *
 * Test the claim honestly against this file:
 *
 *   REALLY REDUCED. Order no longer depends on Checkout.com, on SMTP, on the
 *   analytics vendor, or on a Postgres driver. Those are gone, not moved - the
 *   Order Service could run with all four vendors replaced and never know.
 *
 *   MERELY RELOCATED. Order still cannot place an order without SOMETHING that
 *   authorises payments. The dependency on the CAPABILITY is irreducible; only
 *   the dependency on the IMPLEMENTATION went away. If we had written
 *   `Good_OrderService(CheckoutComClient client)` - injected, but concrete - we
 *   would have gained testability and gained nothing else. That is the trap the
 *   trap being described here, and it is extremely common in codebases that
 *   have "adopted DI".
 *
 *   NOT ADDRESSED AT ALL. `payments.authorise(...)` is still a BLOCKING call on
 *   the checkout path. If the gateway takes 30 seconds, checkout takes 30
 *   seconds. That is TEMPORAL coupling and no amount of interface extraction
 *   touches it. Folder 05.
 *
 * The question a reviewer should ask: "we introduced an interface, did coupling
 * go down?" And the answer that actually settles it: "which KIND of coupling?"
 */
