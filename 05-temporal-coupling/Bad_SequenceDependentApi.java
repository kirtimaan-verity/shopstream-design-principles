/*
 * ============================================================================
 * FLAVOUR (b): ORDER-OF-CALLS TEMPORAL COUPLING
 * ============================================================================
 *
 * No network. No threads. No distributed system. Still temporal coupling -
 * and this is the flavour that gets overlooked, because people associate
 * "temporal" exclusively with microservices.
 *
 * The rule this class enforces exists nowhere in the type system:
 *
 *     init() -> setProvider() -> setAmount() -> setOrder() -> execute()
 *
 * Call them in any other order and you get a NullPointerException, a wrong
 * charge, or - worst of the three - a silently successful call that did the
 * wrong thing.
 */
class Bad_SequenceDependentApi {

    private Object gatewayConnection;   // null until init()
    private String provider;            // null until setProvider()
    private Money amount;               // null until setAmount()
    private OrderId order;              // null until setOrder()
    private boolean executed;

    /** MUST be called first. Says so in the wiki. The wiki moved in 2024. */
    void init() {
        this.gatewayConnection = new Object();
    }

    /** MUST be called after init(), before execute(). */
    void setProvider(String provider) {
        // If init() has not run, this silently "works" and fails four calls later,
        // at which point the stack trace points at execute() and blames the
        // innocent party. Debugging temporal coupling means debugging the past.
        this.provider = provider;
    }

    void setAmount(Money amount) { this.amount = amount; }

    void setOrder(OrderId order) { this.order = order; }

    /**
     * MUST be called last. MUST NOT be called twice - the second call
     * double-charges, because nothing here checks `executed`.
     */
    PaymentResult execute() {
        // NPE if any setter was skipped. Which one? Read the stack trace,
        // find the null, work backwards through the caller's control flow to
        // discover which call was missing. Ten minutes, on a good day.
        gatewayConnection.toString();
        return new PaymentResult(new PaymentId("p1"), true, "**** 4242", null);
    }

    /*
     * WHY THIS IS ARCHITECTURALLY INTERESTING, NOT JUST UGLY CODE:
     *
     * 1. THE CONTRACT IS UNWRITABLE. You cannot express "call these in this
     *    order" in a Java interface. So the contract lives in documentation,
     *    and documentation is not verified by anything.
     *
     * 2. IT IS CONTAGIOUS. Every caller must know the sequence. Fifteen call
     *    sites means fifteen copies of the sequence, and eventually one of them
     *    is subtly wrong. That copy is a duplication problem (folder 11) caused
     *    by a temporal coupling problem.
     *
     * 3. IT DESTROYS THREAD SAFETY. Mutable state between calls means one
     *    instance cannot be shared. At 14,000 transactions per peak hour, that
     *    is either an object pool nobody asked for, or a race condition nobody
     *    found yet.
     *
     * 4. IT FAILS THE BASIC INTERFACE TEST. An interface should be easy to use
     *    and HARD TO MISUSE. This one is easy to misuse in five distinct ways,
     *    and four of them compile.
     *
     * Where you will meet this in real systems: legacy SDKs, JDBC-era APIs,
     * builders that forgot to be immutable, and - most often - any class whose
     * setters exist because a framework demanded a no-arg constructor.
     */
}
