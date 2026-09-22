/*
 * ============================================================================
 * GOOD: one set of answers, applied everywhere
 * ============================================================================
 *
 * The same three services. Read any one of them and you can predict the other
 * two, which is the entire deliverable of conceptual integrity.
 *
 * Note that none of the individual choices below is obviously superior to the
 * alternative it replaced. Optional is not universally better than throwing;
 * cursor paging is not universally better than offset paging. What matters is
 * that there is ONE answer and it is written down. A consistent mediocre
 * convention beats three excellent ones.
 */

// ---- Service 1 --------------------------------------------------------------
interface Good_ProductService {
    java.util.Optional<Product> findById(ProductId id);
    Page<Product> search(String query, PageRequest page);
}

// ---- Service 2 --------------------------------------------------------------
interface Good_OrderService {
    java.util.Optional<Order> findById(OrderId id);
    Page<Order> listForBuyer(BuyerId buyer, PageRequest page);
}

// ---- Service 3 --------------------------------------------------------------
interface Good_PaymentService {
    java.util.Optional<Payment> findById(PaymentId id);
    Page<Payment> listForOrder(OrderId order, PageRequest page);
}

/*
 * Three interfaces, one shape. A developer who learns any one of them has
 * learned all three, and the compiler enforces it rather than a reviewer having
 * to notice.
 *
 * The important structural point: the consistency is not achieved by asking
 * people to be careful. It is achieved by SHARED TYPES - Page, PageRequest,
 * Money, and the typed identifiers. Using the convention is now easier than
 * deviating from it, which is the only form of enforcement that survives a
 * deadline.
 */

// ---- The shared vocabulary that makes the convention self-enforcing ---------

record PageRequest(String cursor, int limit) {
    PageRequest {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
    }
    static PageRequest first(int limit) { return new PageRequest(null, limit); }
}

record Page<T>(java.util.List<T> items, String nextCursor) {
    boolean hasMore() { return nextCursor != null; }
}

record Product(ProductId id, String name, Money price, java.time.Instant updatedAt) { }
record Payment(PaymentId id, OrderId order, Money amount, java.time.Instant processedAt) { }

/*
 * ============================================================================
 * The Liskov fix: declare the limitation instead of hiding it
 * ============================================================================
 */
interface Good_PaymentGateway {
    /**
     * Refund `amount` to the buyer.
     *
     * Contract: on a successful return, the refund has been accepted by the
     * provider. An implementation that cannot honour this must throw rather than
     * return, so that no caller is misled.
     */
    PaymentOutcome refund(PaymentId payment, Money amount);

    /**
     * Capability query, so the routing layer can choose a provider that can
     * actually do the job rather than discovering the limitation at runtime.
     */
    boolean supportsPartialRefunds();
}

class Good_PayTabsGateway implements Good_PaymentGateway {

    @Override
    public PaymentOutcome refund(PaymentId payment, Money amount) {
        // Genuinely implemented. PayTabs supports this.
        return new PaymentOutcome(true, null);
    }

    @Override
    public boolean supportsPartialRefunds() { return true; }
}

class Good_CheckoutComGateway implements Good_PaymentGateway {

    /*
     * This provider cannot do partial refunds, and the code says so in both of
     * the ways that matter:
     *
     * - supportsPartialRefunds() returns false, so routing can avoid it.
     * - refund() throws, so a caller that reaches it anyway finds out
     *     immediately rather than telling a buyer their money is on the way.
     *
     * Substitutability is preserved in the sense that matters: no caller is ever
     * given a wrong answer. The limitation is visible rather than silent, which
     * is the difference between a constraint and a defect.
     */
    @Override
    public PaymentOutcome refund(PaymentId payment, Money amount) {
        throw new UnsupportedOperationException(
                "Checkout.com integration does not support partial refunds; route to PayTabs");
    }

    @Override
    public boolean supportsPartialRefunds() { return false; }
}

record PaymentOutcome(boolean successful, String failureReason) { }

/*
 * ============================================================================
 * ShopStream API and service conventions, version 1
 * ============================================================================
 *
 * This block is the actual deliverable of conceptual integrity. The Java above
 * is downstream of it.
 *
 * On a real project it is one page beside the architecture documentation, used
 * in review and handed to every new service team on day one. That is the answer
 * to discussion question 3.
 *
 * ---------------------------------------------------------------------------
 * 1. IDENTIFIERS
 *    Every entity has a typed identifier record wrapping a String
 *    (ProductId, OrderId, PaymentId, BuyerId, SellerId, StreamId).
 *    Never a bare String, never a long, never a raw UUID in a signature.
 *    Rationale: charge(orderId, sellerId) and charge(sellerId, orderId) must not
 *    both compile.
 *
 * 2. MONEY
 *    Always the Money type: minor units in a long, plus an ISO-4217 currency.
 *    Never double. Never float. Never a bare long without a currency.
 *    Rationale: 14,000 transactions per peak hour across six currencies.
 *
 * 3. TIME
 *    java.time.Instant in code. ISO-8601 with an explicit zone on the wire.
 *    Never epoch millis in an API. Never a timestamp without a zone.
 *    Rationale: six markets across four time zones.
 *
 * 4. NOT FOUND
 *    Optional<T> for a lookup that may legitimately find nothing.
 *    An exception only when absence indicates a broken invariant.
 *    Never null.
 *
 * 5. PAGINATION
 *    Cursor-based, via PageRequest and Page<T>. Default limit 50, maximum 200.
 *    Rationale: offset paging degrades on large tables and gives inconsistent
 *    results while rows are being inserted, which is every minute during a
 *    live stream.
 *
 * 6. ERRORS ON THE WIRE
 *    RFC 7807 problem+json, with a correlation id in every response.
 *    Rationale: pain point P4. One error shape means one parser, one dashboard,
 *    one runbook.
 *
 * 7. NAMING
 *    findX returns Optional. getX throws if absent. listX returns a Page.
 *    A reader can tell the failure mode from the method name alone.
 *
 * 8. SUBSTITUTABILITY
 *    An implementation that cannot honour an interface's contract must throw,
 *    never return a plausible-looking success. Capability differences between
 *    providers are declared through explicit capability queries.
 *
 * ---------------------------------------------------------------------------
 * HOW IT IS ENFORCED, because a document alone decays with every hire:
 *
 * - Shared types (Money, Page, typed ids) in a common module, so the
 *     convention is the path of least resistance.
 * - An API linter in CI that rejects a public signature taking a bare String
 *     id, a double amount, or returning null.
 * - A service template that new services are generated from, already wired
 *     with the conventions.
 * - One named architect reviews every cross-service interface change.
 *
 * The first mechanism does most of the work. The others catch what it cannot.
 * Relying on the document alone is how ShopStream arrived at six conventions in
 * the first place, and that is discussion question 1: the problem was never any
 * individual decision, it was the absence of a place where the decision was
 * made once.
 */
