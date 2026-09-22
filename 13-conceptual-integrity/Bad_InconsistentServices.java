/*
 * ============================================================================
 * BAD: six reasonable decisions, one incoherent system
 * ============================================================================
 *
 * Read the three service interfaces below as if you were a new engineer at
 * ShopStream who has to fix a bug that spans all three, during an incident.
 *
 * The exercise: for each service, write down what happens when the thing you
 * asked for does not exist. You need three different answers, and getting one of
 * them wrong produces a NullPointerException in production.
 */

// ---- Service 1 --------------------------------------------------------------
interface Bad_ProductService {

    /** Returns null when not found. */
    ProductDto findById(String id);

    /** Money as a double. Page and size for paging. Epoch millis for time. */
    java.util.List<ProductDto> search(String query, int page, int size);
}

class ProductDto {
    String id;              // String
    String name;
    double price;           // double. In a system doing 14,000 transactions/hour.
    long updatedAt;         // epoch millis
}

// ---- Service 2 --------------------------------------------------------------
interface Bad_OrderService {

    /** Throws when not found. Different convention, same kind of question. */
    OrderDto findById(long id) throws OrderNotFoundException;

    /** Offset and limit rather than page and size. */
    java.util.List<OrderDto> list(int offset, int limit);
}

class OrderDto {
    long id;                        // long, not String
    long totalFils;                 // minor units as a long
    String createdAt;               // "2026-09-10 14:30:00", no timezone
}

class OrderNotFoundException extends Exception { }

// ---- Service 3 --------------------------------------------------------------
interface Bad_PaymentService {

    /** Optional. A third convention for the same question. */
    java.util.Optional<PaymentDto> findById(java.util.UUID id);

    /** Cursor-based paging. A third paging model. */
    CursorPage<PaymentDto> list(String cursor, int limit);
}

class PaymentDto {
    java.util.UUID id;                      // UUID, not String, not long
    java.math.BigDecimal amount;            // BigDecimal, not double, not long
    java.time.Instant processedAt;          // ISO-8601 with zone
}

record CursorPage<T>(java.util.List<T> items, String nextCursor) { }

/*
 * ============================================================================
 * What this costs, in the order the costs actually arrive
 * ============================================================================
 *
 * 1. THE NULL POINTER THAT KEEPS COMING BACK.
 *
 *    A developer who has been working in the Payment Service moves to a ticket
 *    in the Product Service and writes:
 *
 *        productService.findById(id).getName()
 *
 *    In Payment that is an Optional and it compiles into a mistake the compiler
 *    catches. In Product it is a raw object and a null. The bug ships. It ships
 *    again six months later when a different developer makes the same move,
 *    because the trap is in the design rather than in the person.
 *
 * 2. THE MONEY ONE, WHICH IS NOT MERELY UNTIDY.
 *
 *    ProductDto.price is a double. Order works in long fils. So somewhere there
 *    is a conversion, and it looks like:
 *
 *        long fils = (long) (product.price * 100);
 *
 *    At AED 280 average basket and 14,000 transactions per peak hour, the
 *    rounding drift is a reconciliation gap that finance reports monthly and
 *    nobody can trace, because the loss happens at a type boundary rather than
 *    in any single piece of logic.
 *
 * 3. THE INCIDENT COST, WHICH IS THE ONE THE BRIEF CARES ABOUT.
 *
 *    At 3am during the National Day incident, an engineer paging through three
 *    services has to remember three not-found conventions, three id types, three
 *    money representations, three paging models, three error shapes and three
 *    timestamp formats.
 *
 *    None of that is hard. All of it consumes attention that is not available.
 *    Pain point P4 - four hours to identify root cause - is partly this. The
 *    system is not complex; it is INCOHERENT, and incoherence costs most exactly
 *    when you can least afford it.
 *
 * 4. THE COMPOUNDING ONE.
 *
 *    ShopStream is hiring ahead of Series B. Every new engineer learns six
 *    conventions instead of one, and every new service adds a seventh, because
 *    there is no convention to follow - only precedents to choose between.
 */

/*
 * ============================================================================
 * The Liskov violation: a subtype that lies
 * ============================================================================
 */
interface PaymentGateway {
    /**
     * Refund `amount` to the buyer.
     *
     * The contract, as every caller reasonably reads it: when this returns
     * successfully, the money is on its way back to the buyer.
     */
    PaymentResult refund(PaymentId payment, Money amount);
}

class Bad_PayTabsGateway implements PaymentGateway {

    /*
     * This implementation cannot honour the contract. The integration for
     * partial refunds was never finished.
     *
     * Rather than failing, it returns success.
     *
     * The reasoning at the time was ordinary and sympathetic: throwing broke the
     * checkout flow in staging, the deadline was close, and a TODO was added.
     *
     * The consequence: the Order Service marks the order REFUNDED. The buyer
     * sees "refund processed" in the app. Support closes the ticket. No money
     * moves. Nothing throws, nothing is logged, no monitor fires. The defect is
     * discovered weeks later through chargebacks, which cost ShopStream fees on
     * top of the original amount and count against the merchant account with the
     * card schemes.
     *
     * WHY THIS IS A CONCEPTUAL INTEGRITY FAILURE AND NOT JUST A BUG:
     *
     * Every caller that reasoned correctly about PaymentGateway now reasons
     * incorrectly about this one instance. The type says "substitutable"; the
     * behaviour is not. Once one implementation lies, callers can no longer trust
     * the interface at all, so they start writing:
     *
     *     if (gateway instanceof PayTabsGateway) { ... }
     *
     * and the abstraction from folder 12 is dead. One dishonest subtype does not
     * just break its own callers; it degrades the entire concept.
     *
     * THE HONEST ALTERNATIVES, in order of preference:
     *   1. Implement it properly.
     *   2. Throw UnsupportedOperationException, so callers find out immediately
     *      and the routing layer can pick a different provider.
     *   3. Remove refund from the interface and model the capability explicitly.
     *
     * All three are more work than returning success, and all three are cheaper
     * than chargebacks.
     */
    @Override
    public PaymentResult refund(PaymentId payment, Money amount) {
        // TODO: implement partial refunds
        return new PaymentResult(payment, true, "**** 1111", null);
    }
}
