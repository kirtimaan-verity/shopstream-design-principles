/*
 * ============================================================================
 * GOOD: runtime temporal coupling reduced to what the business actually needs
 * ============================================================================
 *
 * The fix is not "make everything asynchronous". The fix is:
 *
 *   1. Decide which calls genuinely belong on the buyer's critical path.
 *      Here: exactly one, payment.
 *   2. Move the rest off it as events (folder 04).
 *   3. Make the one that stays resilient: a time budget, a circuit breaker,
 *      an idempotent retry, and a defined fallback.
 *
 * Step 3 is the one teams skip. An unprotected synchronous call is still an
 * unprotected synchronous call even after you have removed three of its siblings.
 */
class Good_ResilientCheckout {

    private final Payments payments;
    private final EventPublisher events;
    private final CircuitBreaker paymentBreaker;

    Good_ResilientCheckout(Payments payments, EventPublisher events, CircuitBreaker paymentBreaker) {
        this.payments = payments;
        this.events = events;
        this.paymentBreaker = paymentBreaker;
    }

    Order checkout(Order order, String correlationId) {

        /*
         * THE ONE CALL THAT STAYS SYNCHRONOUS
         *
         * Justification, in business terms rather than technical ones: the buyer
         * is standing in a live stream with a card in their hand and needs an
         * answer in seconds. "Your order is being processed, we will email you"
         * is a worse product, and on a live-commerce platform where the seller
         * is talking to the buyer in real time it is close to unusable.
         *
         * So the temporal coupling to payment is DELIBERATE and JUSTIFIED. What
         * changes is that it is now bounded and survivable.
         */
        PaymentOutcome outcome = paymentBreaker.call(
                /* budget  */ java.time.Duration.ofSeconds(3),
                /* attempt */ () -> payments.authorise(order.id(), order.total()),
                /* if the breaker is open or the budget is blown */
                () -> PaymentOutcome.UNAVAILABLE);

        /*
         * THE TIME BUDGET: 3 seconds, not 30.
         *
         * A 30 second timeout is not a safety net, it is a queue. At 14,000
         * transactions per peak hour a 200 thread pool holds roughly 20 seconds
         * of arrivals, so a 30 second timeout guarantees pool exhaustion before
         * the first request even gives up. The budget has to be smaller than the
         * time it takes for the queue to fill, and that is an arithmetic
         * question, not a matter of taste.
         *
         * THE CIRCUIT BREAKER: after N consecutive failures the breaker opens and
         * calls return immediately without touching the gateway. Two effects:
         *
         * - ShopStream stops burning threads on a call that is going to fail.
         * - ShopStream stops hammering a gateway that is already struggling.
         *     During the National Day incident the retry storm was part of the
         *     cause, not part of the recovery.
         *
         * IDEMPOTENCY: `authorise` is idempotent on OrderId (see folder 01), so a
         * retry after a timeout cannot double-charge. Without that guarantee a
         * retry is a defect, because a timeout tells you nothing about whether
         * the far side completed.
         */

        Order settled = switch (outcome) {
            case AUTHORISED  -> order.withStatus(OrderStatus.AUTHORISED);
            case DECLINED    -> order.withStatus(OrderStatus.CANCELLED);

            /*
             * THE FALLBACK, which is an architecture decision with a business
             * owner and not a technical detail.
             *
             * The gateway is unreachable. Options were:
             *
             *   (a) fail the checkout - buyer loses the purchase, ShopStream
             *                                 loses the revenue, and this is what
             *                                 the old code did to 8% of buyers
             *   (b) accept as PENDING - order is captured, payment is retried
             *                                 out of band, buyer told "confirming"
             *   (c) accept optimistically - ship first, charge later, absorb the
             *                                 fraud risk
             *
             * ShopStream chose (b): during a 90 minute gateway degradation the
             * platform keeps taking orders and settles them when the gateway
             * recovers. This trades a clean state machine for revenue, and it
             * needs a reconciliation job, a buyer-facing message, and a rule for
             * how long PENDING may last before it becomes CANCELLED.
             *
             * That is a decision to record in an architecture decision record,
             * with the options above as the alternatives considered. If it only
             * lives in this switch expression, the next person along will
             * "clean it up".
             */
            case UNAVAILABLE -> order.withStatus(OrderStatus.PLACED);
        };

        /*
         * EVERYTHING ELSE BECOMES A FACT ON A TOPIC
         *
         * Notification, analytics and the seller dashboard were three blocking
         * calls. They are now zero. This method does not know those consumers
         * exist, cannot be slowed down by them, and cannot be broken by them.
         *
         * Availability arithmetic, redone:
         *
         *      before   payment 99.9 x email 99.9 x analytics 99.5 x dash 99.9
         *               = 99.2%   (approx 5.8 hours per month)
         *
         *      after    payment 99.9, and even that degrades to PENDING rather
         *               than failing
         *               = 99.9%+  (approx 43 minutes per month)
         *
         * Nothing was rewritten to get there. Three lines moved.
         */
        events.publish(OrderPlaced.TOPIC, OrderPlaced.from(settled, correlationId));

        return settled;
    }

    /*
     * WHAT THIS COST, stated plainly so the room does not think async is free:
     *
     * - EVENTUAL CONSISTENCY THE BUYER CAN SEE. The confirmation email now
     *     arrives 200ms to 2s after the confirmation screen. Usually invisible,
     *     occasionally not, and support needs to know the answer to "I ordered
     *     but got no email yet".
     *
     * - A NEW FAILURE MODE. If the publish succeeds and the consumer dies, no
     *     email is ever sent and nothing throws. That needs consumer lag
     *     monitoring, which is infrastructure ShopStream did not previously run.
     *
     * - HARDER DEBUGGING. No stack trace crosses a broker. This is pain point
     *     P4 (4 hours to root cause) and it gets worse before it gets better.
     *     The correlation id on the event is the mitigation, and it only works if
     *     it is mandatory on every event and propagated by every consumer.
     *
     * - A RECONCILIATION JOB THAT DID NOT EXIST. Fallback (b) creates PENDING
     *     orders that something must resolve. That is a new component, with its
     *     own failure modes, that only exists because of this design choice.
     *
     * Removing temporal coupling moves work from the request path to the
     * operations team. That is usually the right trade at ShopStream's scale, and
     * it is still a trade.
     */
}

enum PaymentOutcome { AUTHORISED, DECLINED, UNAVAILABLE }

interface CircuitBreaker {
    PaymentOutcome call(java.time.Duration budget,
                        java.util.function.Supplier<PaymentResult> attempt,
                        java.util.function.Supplier<PaymentOutcome> fallback);
}

interface EventPublisher { void publish(String topic, Object event); }

record OrderPlaced(OrderId orderId, Money total, String correlationId) {
    static final String TOPIC = "shopstream.orders.placed.v1";
    static OrderPlaced from(Order o, String correlationId) {
        return new OrderPlaced(o.id(), o.total(), correlationId);
    }
}
