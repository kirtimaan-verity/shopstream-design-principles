/*
 * ============================================================================
 * GOOD: order-of-calls temporal coupling removed by making it inexpressible
 * ============================================================================
 *
 * The Bad_ version had a five step sequence that only documentation enforced.
 * The fix is not to document it better. The fix is to make the wrong sequences
 * impossible to write.
 *
 * Two techniques, in order of preference:
 *
 *   1. NO SEQUENCE AT ALL. One immutable request object, one method. There is
 *      no "before" and no "after", so there is no order to get wrong.
 *
 *   2. WHERE A SEQUENCE IS UNAVOIDABLE, encode it in the TYPE SYSTEM so that
 *      calling out of order does not compile.
 */

/**
 * Technique 1: the request carries everything, the method does one thing.
 *
 * The compiler now enforces what the wiki used to ask for. You cannot construct
 * this record without a provider, an amount and an order, so `execute()` cannot
 * be reached in a half-initialised state. There is nothing to call twice by
 * accident, because there is no instance state to corrupt.
 */
record ChargeRequest(OrderId order, Money amount, String providerName, String idempotencyKey) {

    ChargeRequest {
        // Validate once, at construction. An invalid ChargeRequest cannot exist,
        // so no downstream code needs a null check or a "did you call init()" guard.
        java.util.Objects.requireNonNull(order, "order");
        java.util.Objects.requireNonNull(amount, "amount");
        java.util.Objects.requireNonNull(providerName, "providerName");
        if (amount.minorUnits() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}

class Good_SelfContainedApi {

    private final Good_PaymentProviderRegistry providers;

    Good_SelfContainedApi(Good_PaymentProviderRegistry providers) {
        this.providers = providers;
    }

    /**
     * One call. No init, no setters, no ordering rule, no mutable state, and
     * therefore thread safe by construction, which matters at 14,000
     * transactions per peak hour.
     *
     * Measured against the interface characteristics from folder 01: easy to
     * learn (one method), easy to use (one argument), hard to misuse (the
     * invalid states do not compile). The Bad_ version failed all three.
     */
    PaymentResult charge(ChargeRequest request) {
        return providers.forName(request.providerName())
                .authorise(request.order(), request.amount(), request.idempotencyKey());
    }
}

/*
 * ============================================================================
 * Technique 2: when the sequence is real, make the compiler own it
 * ============================================================================
 *
 * Some sequences are genuine domain rules rather than accidents of
 * implementation. A ShopStream live stream really must be created before it can
 * start, started before it can accept orders, and ended before it can be
 * archived. You cannot design that away, because it is the business.
 *
 * What you CAN do is stop representing it as one mutable object with a status
 * field and a pile of guard clauses. Give each state its own type, and let each
 * type expose only the operations that are legal in that state.
 */

/** Created, not yet broadcasting. The only thing you can do is start it. */
record ScheduledStream(StreamId id, SellerId seller, java.time.Instant plannedStart) {
    LiveStream start() {
        return new LiveStream(id, seller, java.time.Instant.now());
    }
    // No acceptOrder(). No end(). Those methods do not exist here, so a caller
    // cannot invoke them, so there is no runtime check to write and no
    // IllegalStateException to throw.
}

/** Broadcasting. Orders are accepted. It can be ended. It cannot be started again. */
record LiveStream(StreamId id, SellerId seller, java.time.Instant startedAt) {
    void acceptOrder(Order order) { /* ... */ }
    EndedStream end() {
        return new EndedStream(id, seller, startedAt, java.time.Instant.now());
    }
}

/** Over. Read only. The VOD asset can be published; orders cannot be taken. */
record EndedStream(StreamId id, SellerId seller,
                   java.time.Instant startedAt, java.time.Instant endedAt) {
    void publishReplay() { /* ... */ }
}

/*
 * WHAT CHANGED, and why it is worth the extra types:
 *
 *   Bad:   stream.setStatus("LIVE"); stream.acceptOrder(o);
 * - acceptOrder must check the status at runtime
 * - a caller can set any status at any time
 * - "can I take an order on an ended stream?" is answered by reading
 *            the body of acceptOrder, if the guard is even there
 *
 *   Good:  liveStream.acceptOrder(o);
 * - acceptOrder exists only on the type where it is legal
 * - illegal transitions are compile errors, not production incidents
 * - the state machine is readable from the type signatures alone
 *
 * COST, honestly: three types instead of one, and the persistence layer has to
 * map a status column back to the right type on load. For a state machine with
 * two states this is over-engineering (folder 10). For one that guards revenue
 * and has four or more states, it pays for itself the first time somebody tries
 * to take an order against a stream that ended twenty minutes ago.
 */

interface Good_PaymentProviderRegistry {
    Provider forName(String name);
    interface Provider {
        PaymentResult authorise(OrderId order, Money amount, String idempotencyKey);
    }
}
