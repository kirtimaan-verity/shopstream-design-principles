/*
 * ============================================================================
 * FLAVOUR (a): RUNTIME TEMPORAL COUPLING - the synchronous chain
 * ============================================================================
 *
 * Important setup for the room: this class is GOOD by every lesson so far.
 *
 *   - every dependency is an interface         (folder 02)
 *   - nothing is constructed inside            (folder 02)
 *   - no inheritance                           (folder 03)
 *   - dependencies injected via constructor    (folder 16)
 *
 * A static analysis tool would give it a clean bill of health. Your dependency
 * graph is a tidy DAG. And it is the class that took ShopStream down for 90
 * minutes on National Day.
 *
 * That is the whole point of this folder: TEMPORAL COUPLING IS INVISIBLE TO
 * EVERY STRUCTURAL TECHNIQUE YOU HAVE LEARNED SO FAR.
 */
class Bad_SynchronousCheckout {

    private final Payments payments;
    private final Notifications notifications;
    private final Analytics analytics;
    private final SellerDashboard sellerDashboard;

    Bad_SynchronousCheckout(Payments payments, Notifications notifications,
                            Analytics analytics, SellerDashboard sellerDashboard) {
        this.payments = payments;
        this.notifications = notifications;
        this.analytics = analytics;
        this.sellerDashboard = sellerDashboard;
    }

    /**
     * Checkout. Runs on the buyer's HTTP request thread.
     *
     * Trace the thread through this method and count how many other systems must
     * be healthy, RIGHT NOW, for a buyer to complete a purchase.
     */
    Order checkout(Order order) {

        /*
         * ---- CALL 1: PAYMENT - belongs here -------------------------------
         *
         * Genuinely on the critical path. The buyer must learn whether they paid.
         *
         * But look at the timeout: 30 seconds, no circuit breaker, no fallback.
         *                          On National Day the gateway did not fail - it slowed to ~20s. Every
         *                          checkout thread sat here. The pool (200 threads) exhausted in under a
         *                          minute. After that, requests that never touch payment - browsing a
         *                          catalogue, watching a stream - got connection-refused, because there
         *                          was no thread left to serve them.
         *
         * THE FAILURE PROPAGATED FROM A DEPENDENCY THAT WAS NEVER DOWN.
         * That is temporal coupling's signature move, and it is why "is the
         * payment service up?" was the wrong question during the incident.
         */
        PaymentResult payment = payments.authorise(order.id(), order.total());  // blocks up to 30s
        if (!payment.authorised()) {
            return order.withStatus(OrderStatus.CANCELLED);
        }

        /*
         * ---- CALL 2: EMAIL - does NOT belong here --------------------------
         *
         * SMTP. On the buyer's thread. In 2026.
         *
         * The buyer is staring at a spinner while ShopStream negotiates TLS with
         * a mail relay. If the relay is slow, checkout is slow. If the relay is
         * down, THE ORDER FAILS - after the money was already taken in call 1.
         *
         * Ask the room what state the system is in if this line throws:
         * charged, no order confirmation, and an exception that will roll back a
         * transaction that cannot roll back a card authorisation. Nobody
         * reconciles this. This is a real source of the 8%.
         */
        notifications.sendOrderConfirmation(order.buyer(), order.id());          // blocks

        /*
         * ---- CALL 3: ANALYTICS - absolutely does not belong here ------------
         *
         * A third-party HTTP endpoint, on the revenue path. ShopStream's ability
         * to take money now depends on an analytics vendor's uptime, their TLS
         * certificate not expiring, and their DNS resolving.
         *
         * This is the one that makes people wince, and it is in almost every
         * checkout method in the industry.
         */
        analytics.track("order_completed", order.id(), order.total());           // blocks

        /*
         * ---- CALL 4: SELLER DASHBOARD - does not belong here ----------------
         *
         * The seller finding out 200ms later instead of instantly harms nobody.
         * The buyer's purchase failing because a dashboard was redeploying harms
         * everybody.
         */
        sellerDashboard.recordSale(order.seller(), order.total());               // blocks

        return order.withStatus(OrderStatus.CONFIRMED);
    }

    /*
     * DO THE ARITHMETIC ON THE WHITEBOARD - it lands harder than the argument:
     *
     *     Payment      99.9%
     *     Email        99.9%
     *     Analytics    99.5%   (a third party; you do not control it)
     *     Dashboard    99.9%
     *     ---------------------
     *     Checkout     99.2%   which is about 5.8 hours of downtime per month
     *
     * Nobody designed a 99.2% checkout. It was assembled, one reasonable-looking
     * line at a time, by four different people over two years - and each of those
     * four lines passed code review.
     *
     * Now add the retry loop the team added after the incident, without
     * idempotency and without a breaker: every timed-out checkout retried three
     * times, tripling load on a gateway that was already the bottleneck. The
     * remediation made the outage longer. If you take one thing from this folder:
     * A RETRY WITHOUT A CIRCUIT BREAKER IS AN ATTACK YOU LAUNCH ON YOURSELF.
     */
}

// ---- Supporting cast, elided ------------------------------------------------
interface Payments        { PaymentResult authorise(OrderId order, Money amount); }
interface Notifications   { void sendOrderConfirmation(BuyerId buyer, OrderId order); }
interface Analytics       { void track(String event, OrderId order, Money amount); }
interface SellerDashboard { void recordSale(SellerId seller, Money amount); }
