/*
 * ============================================================================
 * BAD: qualities as adjectives, tuning as folklore
 * ============================================================================
 *
 * Read the constants below and try to answer, for any one of them:
 *
 *      "What breaks if I change this? How would I know?"
 *
 * You cannot, because no target was ever written down. Each value is somebody's
 * recollection of a number that seemed to help during an incident, and none can
 * be validated or safely changed by the next person.
 */
class Bad_UnmeasuredQualityGoals {

    /*
     * The requirements, as they exist in this codebase.
     *
     * Every one of these is unfailable. There is no observation of the running
     * system that could contradict "must be fast", so the requirement is always
     * satisfied and never useful. It cannot drive a design decision, cannot be
     * tested, and cannot settle an argument.
     */
    // Checkout must be fast.
    // The catalogue must be scalable.
    // Payments must be reliable.
    // The system should be secure and maintainable.

    /*
     * TUNING BY FOLKLORE
     *
     * Each of these was set once, by someone, for a reason that is now lost. No
     * comment says what it is protecting or what it costs.
     */
    private static final int    THREAD_POOL_SIZE       = 200;
    private static final int    DB_CONNECTION_POOL     = 50;
    private static final int    GATEWAY_TIMEOUT_MS     = 30_000;
    private static final int    SEARCH_RESULT_LIMIT    = 500;
    private static final int    CACHE_TTL_SECONDS      = 3600;
    private static final int    MAX_RETRIES            = 3;

    /*
     * THE ARITHMETIC NOBODY DID
     *
     * 200 threads, a 30 second gateway timeout, and 14,000 transactions per hour
     * at peak. That is roughly four arrivals per second; a 30 second hold means
     * about 120 requests in flight against a pool of 200 whenever the gateway
     * degrades. Add the retries and the pool is gone.
     *
     * Nobody made a decision that produced this. Three constants were chosen
     * independently, by three people, none of whom had a stated target to check
     * them against. The 8% failure incident is the sum of those three choices.
     *
     * A quality scenario would have caught it on a whiteboard, before any code:
     * write down "the gateway degrades to 20s response for 90 minutes" as a
     * scenario, and the pool arithmetic is a two-minute calculation.
     */

    /*
     * OPTIMISATION BY INTUITION
     *
     * Somebody profiled nothing, decided search was slow because of object
     * allocation, and hand-rolled a string builder loop that is now the most
     * unreadable method in the catalogue.
     *
     * The actual cause of the 12 second search was resource contention with
     * image resizing in the same process (folder 07). This optimisation
     * addressed none of it, and it will never be removed, because removing it
     * feels risky and nobody can show it does nothing.
     *
     * That last point is the real cost of unmeasured work: it is not the effort
     * spent, it is that it becomes permanent. Code nobody can justify is also
     * code nobody can delete.
     */
    String buildSearchQuery(String term, String category) {
        // 40 lines of manual string assembly "for performance"
        return "";
    }

    /*
     * THE TRADE-OFF MADE SILENTLY
     *
     * CACHE_TTL_SECONDS = 3600 is a decision about CONSISTENCY versus
     * PERFORMANCE, and it was made by whoever typed the number.
     *
     * A seller who changes a price during a live stream may see the old price
     * for up to an hour, in front of an audience, while the buyer is being told
     * a number that is no longer true. That is a business decision with revenue
     * and possibly legal consequences, and it lives in an unexplained constant
     * in a class nobody owns.
     *
     * The failure is not the value. One hour might be exactly right. The failure
     * is that the trade was never named, so nobody with the authority to make it
     * ever saw it.
     */
}
