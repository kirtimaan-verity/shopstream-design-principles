/*
 * ============================================================================
 * GOOD: scenarios with numbers, and checks that can fail
 * ============================================================================
 *
 * Same system, same constants. The difference is that every number is traceable
 * to a written scenario, and every scenario has a stated method of analysis.
 *
 * The property to aim for: a new engineer can change any constant here and find
 * out, from the repository alone, what they are risking.
 */
class Good_QualityScenarios {

    /*
     * ------------------------------------------------------------------------
     * SCENARIO Q1 - checkout under peak load
     * ------------------------------------------------------------------------
     *   Context   National Day peak, approximately 14,000 transactions per hour
     *   Stimulus  a buyer submits checkout
     *   Artifact  the checkout path
     *   Response  a definite outcome: accepted, declined, or pending
     *   Measure   99.9% of requests get an outcome; p99 latency under 800ms
     *   Analysis  gateway access logs over the peak window; load test before
     *             each National Day release
     *
     * DERIVED DECISIONS. Each constant below cites the scenario it serves, so a
     * later change is an argument with a written requirement rather than with
     * whoever last touched the file.
     */
    static final int  CHECKOUT_THREAD_POOL   = 200;
    static final int  GATEWAY_BUDGET_MS      = 3_000;   // Q1: see arithmetic below
    static final int  GATEWAY_MAX_ATTEMPTS   = 3;       // Q1, bounded by the breaker

    /*
     * THE ARITHMETIC, WRITTEN DOWN.
     *
     *   14,000 tx/hour        = approximately 4 arrivals per second at peak
     *   200 threads / 4 per s = the pool absorbs roughly 50 seconds of arrivals
     *   Budget must be well under that, or a degraded gateway exhausts the pool
     *   before any request gives up. 3s leaves headroom for a retry inside the
     *   same budget and keeps worst-case occupancy near 12 concurrent requests.
     *
     * This is four lines of division. It is also the difference between the 8%
     * incident and not having it. The value of a scenario is that it makes the
     * calculation obvious enough to actually do.
     */

    /*
     * ------------------------------------------------------------------------
     * SCENARIO Q2 - catalogue search under peak load
     * ------------------------------------------------------------------------
     *   Context   peak browsing, 80,000 concurrent viewers on a single stream
     *   Stimulus  a buyer searches the live catalogue
     *   Artifact  the catalogue read path
     *   Response  results returned
     *   Measure   p95 under 500ms
     *   Analysis  runtime measurement in production; load test on the read path
     *
     * TRADE-OFF, NAMED AND OWNED. Meeting Q2 means caching, and caching means
     * stale prices. The trade was made explicitly:
     *
     *   Decision  cache catalogue reads for 60 seconds, not 3600
     *   Wins      performance (Q2)
     *   Loses     consistency: a price change can be up to 60s stale
     *   Rejected  3600s, which is better for Q2 and was rejected because a
     *             seller changing a price live must see it take effect within
     *             the same stream segment
     *   Owner     product, not engineering, because the losing side is visible
     *             to sellers and buyers
     *
     * The rejected option and its owner are the parts that stop this being
     * re-litigated every six months. Compare with CACHE_TTL_SECONDS = 3600 in
     * the Bad_ file, where the same decision was made by whoever typed it.
     */
    static final int  CATALOGUE_CACHE_TTL_SECONDS = 60;

    /*
     * ------------------------------------------------------------------------
     * SCENARIO Q3 - adding a market
     * ------------------------------------------------------------------------
     *   Context   a new market is signed, with a payment provider neither of the
     *             current two supports
     *   Stimulus  the team adds support for it
     *   Artifact  the payment subsystem
     *   Response  the provider is available in production
     *   Measure   no change to any building block outside the payment module;
     *             no redeployment of the Order Service
     *   Analysis  SCENARIO-BASED: walk the change through the design and count
     *             the building blocks it touches. Needs no running system, and
     *             can be done before any code exists.
     *
     * This is the scenario that justifies the extension point in folder 15 and
     * the SPI in folder 01. Without it, both are speculative generality and
     * folder 10 would rule against them. With it, they are the cheapest way to
     * meet a written requirement.
     *
     * Worth stating plainly in review: THE SCENARIO IS WHAT MAKES THE
     * ABSTRACTION DEFENSIBLE. Not taste, not experience, not "best practice".
     */
}

/*
 * ============================================================================
 * Scenarios as executable checks
 * ============================================================================
 *
 * A scenario names its method of analysis. Where that method can be automated,
 * automate it: the requirement then fails the build instead of failing in
 * production, and it stays true as the system changes.
 *
 * These are sometimes called fitness functions. They are ordinary tests whose
 * subject is a quality rather than a behaviour.
 */
class QualityChecks {

    /*
     * Q3, checked mechanically. This is the modifiability requirement expressed
     * as something that can fail.
     *
     * An architecture test like this one is worth more than a diagram, because
     * a diagram describes what was intended and this describes what is true.
     */
    void orderModuleDoesNotDependOnAnyPaymentProvider() {
        // assertThat(classesIn("shopstream.orders"))
        //     .doNotDependOn("shopstream.payments.internal..")
        //     .andDoNotDependOn("com.checkout..", "com.paytabs..");
    }

    /*
     * Q1, checked in the load test rather than asserted in a unit test, because
     * latency under contention is not observable in isolation.
     */
    void checkoutMeetsLatencyBudgetUnderPeakLoad() {
        // given 14_000 transactions/hour for 15 minutes
        // assert p99 < 800ms and outcome rate > 99.9%
    }

    /*
     * A coupling metric, run in the build, so maintainability has a number
     * rather than an opinion. Folder 02 explains what efferent coupling is and
     * why the count matters.
     */
    void noBuildingBlockExceedsEfferentCouplingOf(int limit) {
        // assertThat(efferentCouplingOf(everyModule())).isLessThan(limit);
    }

    /*
     * GOODHART GUARD.
     *
     * Every target above can be gamed. p99 latency improves if slow requests
     * start returning empty results quickly. So each performance target is
     * paired with an OUTCOME measure that moves in the opposite direction when
     * the proxy is gamed:
     *
     *      p99 checkout latency   paired with   checkout completion rate
     *      p95 search latency     paired with   search-to-purchase rate
     *      deploy frequency       paired with   change failure rate
     *
     * A single number as a target will be met. Whether it was met by improving
     * the system is a separate question, and it needs a second number to answer.
     */
}
