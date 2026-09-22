/*
 * ============================================================================
 * GOOD: one class per actor
 * ============================================================================
 *
 * The split was not made by counting lines or by looking for methods that felt
 * unrelated. It was made by asking, for each block, WHO FILES THE TICKET - and
 * then giving each of those people their own file.
 *
 *      Finance     -> VatCalculator
 *      Ops         -> PaymentRetryPolicy
 *      Compliance  -> PaymentAuditTrail
 *      Marketing   -> (left the service entirely; see the note at the bottom)
 *      Commerce    -> Good_PlaceOrder, the orchestration itself
 */

/**
 * ACTOR: the commerce team.
 * Reason to change: the steps of placing an order.
 *
 * Read the body. It is a sequence of named business steps at one level of
 * abstraction, with no tax arithmetic, no retry loop, no audit format and no
 * email copy. Everything specific has moved to the class whose owner cares about
 * it.
 */
class Good_PlaceOrder {

    private final Catalogue catalogue;
    private final VatCalculator vat;
    private final PaymentRetryPolicy paymentPolicy;
    private final Orders orders;
    private final DomainEvents events;

    Good_PlaceOrder(Catalogue catalogue, VatCalculator vat, PaymentRetryPolicy paymentPolicy,
                    Orders orders, DomainEvents events) {
        this.catalogue = catalogue;
        this.vat = vat;
        this.paymentPolicy = paymentPolicy;
        this.orders = orders;
        this.events = events;
    }

    Order execute(BuyerId buyer, ProductId product, int qty, String market) {

        Money net = catalogue.priceOf(product).times(qty);
        Money gross = net.plus(vat.on(net, market));

        Order order = new Order(OrderId.next(), buyer, catalogue.sellerOf(product), null,
                java.util.List.of(new OrderLine(product, qty, catalogue.priceOf(product))),
                OrderStatus.PLACED);
        orders.save(order);

        PaymentResult payment = paymentPolicy.authorise(order.id(), gross);

        Order settled = order.withStatus(
                payment.authorised() ? OrderStatus.AUTHORISED : OrderStatus.CANCELLED);
        orders.save(settled);

        events.publish(new OrderPlaced(settled.id(), gross, market));
        return settled;
    }
}

/**
 * ACTOR: finance.
 * Reason to change: tax law, or a new market.
 *
 * The Egypt launch is now one line in this file, reviewed by someone who
 * understands tax, in a class small enough that they can read all of it.
 *
 * Also a pure function: no database, no network, no clock. Finance can be shown
 * a test that demonstrates the rule, which is a materially different
 * conversation from being shown a 90-line checkout method.
 */
class VatCalculator {

    private static final java.util.Map<String, Integer> BASIS_POINTS = java.util.Map.of(
            "AE", 500, "SA", 1500, "EG", 1400,
            "JO", 1600, "PK", 1700, "NG", 750);

    Money on(Money net, String market) {
        Integer bp = BASIS_POINTS.get(market);
        if (bp == null) {
            // Fails loudly rather than defaulting to 5% for an unconfigured
            // market, which is what the Bad_ version did silently.
            throw new IllegalArgumentException("No VAT rate for market: " + market);
        }
        return new Money(net.minorUnits() * bp / 10_000, net.currency());
    }
}

/**
 * ACTOR: ops.
 * Reason to change: an incident, a change in gateway behaviour, a capacity review.
 *
 * This is the file Ops edits at 3am. It contains retry counts, time budgets and
 * the circuit breaker, and it contains nothing else. A hurried change here
 * cannot break a tax rule or a compliance record, and the review needs one
 * person who understands one thing.
 *
 * The parameters also encode what was learned from the National Day incident:
 * three attempts rather than an unbounded loop, a three second budget rather
 * than thirty, and a breaker so the retries stop when the gateway is already
 * struggling. See folder 05.
 */
class PaymentRetryPolicy {

    private static final int MAX_ATTEMPTS = 3;
    private static final java.time.Duration BUDGET = java.time.Duration.ofSeconds(3);

    private final PaymentGateway gateway;
    private final CircuitBreaker breaker;

    PaymentRetryPolicy(PaymentGateway gateway, CircuitBreaker breaker) {
        this.gateway = gateway;
        this.breaker = breaker;
    }

    PaymentResult authorise(OrderId order, Money amount) {
        return breaker.call(BUDGET, MAX_ATTEMPTS, () -> gateway.charge(order, amount));
    }
}

/**
 * ACTOR: compliance.
 * Reason to change: a PCI-DSS assessment, a regulator, a new market's rules.
 *
 * An assessor can now read one twenty-line class to verify that ShopStream's
 * payment audit trail is adequate and that no cardholder data is written. In
 * the Bad_ version that verification meant reading a method that also sent
 * email.
 *
 * Note this is also the decorator pattern from folder 08: it implements the same
 * interface it wraps, so it can be applied at wiring time and removed in one
 * line.
 */
class PaymentAuditTrail implements PaymentGateway {

    private final PaymentGateway delegate;
    private final AuditSink sink;

    PaymentAuditTrail(PaymentGateway delegate, AuditSink sink) {
        this.delegate = delegate;
        this.sink = sink;
    }

    @Override
    public PaymentResult charge(OrderId order, Money amount) {
        sink.write(new AuditRecord("payment.attempt", order.value(),
                amount.minorUnits(), amount.currency(), java.time.Instant.now(), null));

        PaymentResult result = delegate.charge(order, amount);

        sink.write(new AuditRecord("payment.result", order.value(),
                amount.minorUnits(), amount.currency(), java.time.Instant.now(),
                result.authorised() ? "AUTHORISED" : "DECLINED"));
        return result;
    }
}

record AuditRecord(String event, String subject, long amountMinorUnits,
                   String currency, java.time.Instant at, String outcome) { }

/*
 * ============================================================================
 * Where did marketing's email go?
 * ============================================================================
 *
 * Out of the Order Service entirely. It subscribes to OrderPlaced (folder 04)
 * and lives in the Notification Service.
 *
 * That answers the sentence that made the Bad_ version indefensible: marketing
 * can now change an email subject line without redeploying the code that takes
 * money. Weekly copy changes no longer touch the checkout path, no longer need a
 * commerce review, and no longer carry the release risk of the payment logic.
 *
 * Worth naming the general move: applying SRP properly sometimes shows that a
 * responsibility does not belong in this SYSTEM, not merely in this class. That
 * is the same discovery as at the end of folder 07, arrived at from a different
 * direction.
 *
 * ============================================================================
 * The limitation, which matters as much as the principle
 * ============================================================================
 *
 * Five classes replaced one, and there is a version of this that goes too far.
 *
 * If the next step were an OrderIdGenerator, an OrderLineFactory, an
 * OrderStatusTransitioner and an OrderPersistenceCoordinator, following a single
 * checkout would mean opening nine files. That is worse than the god class,
 * because at least the god class could be read top to bottom.
 *
 * The stopping condition is the actor test, not the aesthetic one. There is no
 * actor for "order id generation" - nobody at ShopStream will ever file a ticket
 * about it independently. So it stays where it is used.
 *
 * A practical heuristic that keeps this honest: split when a class has actually
 * been changed several times and the change requests came from different people.
 * Splitting before that evidence exists is guessing about the future, which is
 * folder 10's territory. SRP is at its best as a diagnosis of pain that has
 * already occurred, and at its worst as a rule applied to code nobody has
 * complained about.
 */

// ---- Supporting cast, elided ------------------------------------------------
interface Catalogue      { Money priceOf(ProductId p); SellerId sellerOf(ProductId p); }
interface Orders         { void save(Order o); }
interface PaymentGateway { PaymentResult charge(OrderId order, Money amount); }
interface CircuitBreaker { PaymentResult call(java.time.Duration budget, int maxAttempts,
                                              java.util.function.Supplier<PaymentResult> attempt); }
interface AuditSink      { void write(AuditRecord record); }
interface DomainEvents   { void publish(Object event); }
record   OrderPlaced     (OrderId id, Money total, String market) { }
