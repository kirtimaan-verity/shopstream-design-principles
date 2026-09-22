/*
 * ============================================================================
 * GOOD: one concern per layer, and cross-cutting concerns at a boundary
 * ============================================================================
 *
 *      HTTP / serialisation          Good_CheckoutEndpoint
 *              |
 *              v
 *      use case orchestration        Good_PlaceOrderUseCase
 *              |
 *              v
 *      business rules                Good_PricingPolicy   (no infrastructure)
 *              |
 *              v
 *      ports                         Orders, Catalogue, Payments
 *              ^
 *              |
 *      adapters                      Postgres, Checkout.com, SMTP
 *
 * Arrows point downward except the last one. Adapters depend on the ports, not
 * the other way round - that inversion is folder 16, and it is what stops the
 * layering from being merely cosmetic.
 */

/**
 * Layer 1: HTTP. Knows about requests, headers, status codes, JSON.
 * Knows nothing about VAT, orders or databases.
 *
 * Changes when: the API contract changes. Nothing else.
 */
class Good_CheckoutEndpoint {

    private final Good_PlaceOrderUseCase useCase;

    Good_CheckoutEndpoint(Good_PlaceOrderUseCase useCase) {
        this.useCase = useCase;
    }

    HttpResponse handle(HttpRequest request) {
        /*
         * Translate the transport into domain types, then get out of the way.
         *
         * Authentication is not here. It is a filter in front of this endpoint,
         * applied to every route, written once. The security team can audit one
         * filter instead of forty handlers, which is the difference between an
         * audit that happens and one that does not.
         */
        var command = new PlaceOrder(
                new BuyerId(request.param("buyer_id")),
                new ProductId(request.param("product_id")),
                Integer.parseInt(request.param("qty")),
                request.header("X-Market"));

        try {
            Order order = useCase.execute(command);
            return HttpResponse.ok(order.id().value());
        } catch (ValidationFailed e) {
            // Domain failures translated into transport failures here, at the
            // boundary. The use case does not know what an HTTP 400 is, and it
            // should not - the same use case is called by an admin CLI tool.
            return HttpResponse.badRequest(e.getMessage());
        }
    }
}

record PlaceOrder(BuyerId buyer, ProductId product, int qty, String market) { }

/**
 * Layer 2: the use case. Orchestrates the steps. Owns the transaction boundary.
 * Contains no business RULES and no infrastructure DETAIL.
 *
 * Read the body: it is a description of the workflow in domain language, at a
 * consistent level of abstraction. There is no SQL, no HTTP, no SMTP, and no
 * arithmetic. Everything specific has been delegated one level down.
 *
 * Changes when: the steps of the workflow change. Not when a VAT rate changes,
 * not when the schema changes, not when the API contract changes.
 */
class Good_PlaceOrderUseCase {

    private final Catalogue catalogue;
    private final Orders orders;
    private final Payments payments;
    private final Good_PricingPolicy pricing;
    private final DomainEvents events;

    Good_PlaceOrderUseCase(Catalogue catalogue, Orders orders, Payments payments,
                           Good_PricingPolicy pricing, DomainEvents events) {
        this.catalogue = catalogue;
        this.orders = orders;
        this.payments = payments;
        this.pricing = pricing;
        this.events = events;
    }

    Order execute(PlaceOrder command) {

        if (command.qty() <= 0 || command.qty() > 50) {
            throw new ValidationFailed("quantity must be between 1 and 50");
        }

        Money unitPrice = catalogue.priceOf(command.product());
        Money total = pricing.totalFor(unitPrice, command.qty(), command.market());

        Order order = new Order(OrderId.next(), command.buyer(), catalogue.sellerOf(command.product()),
                null, java.util.List.of(new OrderLine(command.product(), command.qty(), unitPrice)),
                OrderStatus.PLACED);
        orders.save(order);

        PaymentResult payment = payments.authorise(order.id(), total);
        Order settled = order.withStatus(
                payment.authorised() ? OrderStatus.AUTHORISED : OrderStatus.CANCELLED);
        orders.save(settled);

        // Invoice rendering and email have left the request path entirely.
        // They are consumers of this event now (folders 04 and 05).
        events.publish(new OrderPlaced(settled.id(), total));

        return settled;
    }
}

/**
 * Layer 3: business rules. Pure. No database, no clock, no network, no framework.
 *
 * This is the class finance cares about, and it is now 20 readable lines instead
 * of three lines buried at line 40 of an HTTP handler.
 *
 * What that buys:
 * - unit tests with no infrastructure at all, running in microseconds
 * - reuse by the quote endpoint and the seller payout report, which stops the
 *     copy-paste that folder 11 is about
 * - the Egypt launch is a new entry in one map, in one file, reviewed by
 *     someone who understands tax
 */
class Good_PricingPolicy {

    /*
     * Market VAT rates in basis points, so no floating point and no rounding
     * drift. See folder 00 for why money is never a double at 14,000
     * transactions per peak hour.
     *
     * This map is the entire Egypt launch, as far as pricing is concerned.
     */
    private static final java.util.Map<String, Integer> VAT_BASIS_POINTS = java.util.Map.of(
            "AE", 500,    //  5%
            "SA", 1500,   // 15%
            "EG", 1400,   // 14%
            "JO", 1600,   // 16%
            "PK", 1700,   // 17%
            "NG",  750);  // 7.5%

    Money totalFor(Money unitPrice, int qty, String market) {
        Money net = unitPrice.times(qty);
        Integer bp = VAT_BASIS_POINTS.get(market);
        if (bp == null) {
            throw new ValidationFailed("No VAT rate configured for market " + market);
        }
        long vat = net.minorUnits() * bp / 10_000;
        return net.plus(new Money(vat, net.currency()));
    }
}

/*
 * ============================================================================
 * Cross-cutting concerns, handled at a boundary instead of in every method
 * ============================================================================
 *
 * Tracing, audit logging and metrics apply to everything. Folder 03 showed what
 * happens when you solve that with a base class: a fragile base class and a
 * performance regression in a service that did not want the behaviour.
 *
 * The alternative is a decorator on the port. It implements the same interface,
 * adds the concern, and delegates. Nothing in the business logic knows it exists.
 *
 * This is also the answer to discussion question 3: PCI-DSS audit logging on every
 * payment operation is one class, applied at wiring time, deletable in one line.
 */
class AuditedPayments implements Payments {

    private final Payments delegate;
    private final AuditTrail audit;

    AuditedPayments(Payments delegate, AuditTrail audit) {
        this.delegate = delegate;
        this.audit = audit;
    }

    @Override
    public PaymentResult authorise(OrderId order, Money amount) {
        /*
         * The audit record required by PCI-DSS: who, what, when, outcome, and a
         * correlation id. Written once, applied to every payment call, and the
         * business logic is not aware of it.
         *
         * Wiring:
         *     Payments payments = new AuditedPayments(
         *                             new CheckoutComPayments(...), auditTrail);
         *
         * Turning it off for a load test is deleting that one wrapper. Compare
         * with removing an audit line from forty methods and hoping you got them
         * all.
         */
        audit.record("payment.authorise.attempt", order.value(), amount);
        PaymentResult result = delegate.authorise(order, amount);
        audit.record(result.authorised() ? "payment.authorise.ok" : "payment.authorise.declined",
                order.value(), amount);
        return result;
    }
}

/*
 * ============================================================================
 * "Loose, but functionally sufficient" - what we deliberately did NOT do
 * ============================================================================
 *
 * The phrase is a limit in both directions. Here is where that limit
 * was applied in this file, so the room sees that restraint is also a decision:
 *
 *   NOT DONE: a factory for Good_PricingPolicy.
 *      There is one implementation and no requirement suggesting a second.
 *      A factory would add a file and a level of indirection to buy a change
 *      nobody has asked for.
 *
 *   NOT DONE: an interface for Good_PlaceOrderUseCase.
 *      Nothing needs to substitute it. An interface with exactly one
 *      implementation, forever, is a rename with extra steps.
 *
 *   NOT DONE: events between the layers of this file.
 *      The endpoint calling the use case directly is correct. It is synchronous
 *      by nature, in the same process, and the buyer is waiting. Putting a
 *      broker in there would add operational cost and remove the stack trace,
 *      buying nothing.
 *
 *   DONE: a port for Payments.
 *      Justified by evidence in the brief, not by taste. Two providers exist
 *      today and five new markets are on the roadmap, so the second
 *      implementation is not hypothetical - it is scheduled.
 *
 *   DONE: a decorator for auditing.
 *      Justified by PCI-DSS, which is a written, non-negotiable constraint.
 *
 * The pattern in those five decisions: every abstraction is traceable to a named
 * requirement or constraint in the ShopStream brief. When a participant proposes
 * an abstraction, ask which line of the brief it serves. If they cannot point at
 * one, folder 10 applies.
 */

// ---- Supporting cast, elided ------------------------------------------------
interface HttpRequest   { String param(String n); String header(String n); }
interface HttpResponse  { static HttpResponse ok(String body) { return null; }
                          static HttpResponse badRequest(String msg) { return null; } }
interface Catalogue     { Money priceOf(ProductId p); SellerId sellerOf(ProductId p); }
interface Orders        { void save(Order o); }
interface Payments      { PaymentResult authorise(OrderId order, Money amount); }
interface DomainEvents  { void publish(Object event); }
interface AuditTrail    { void record(String event, String subject, Money amount); }
record   OrderPlaced    (OrderId id, Money total) { }
class    ValidationFailed extends RuntimeException { ValidationFailed(String m) { super(m); } }
