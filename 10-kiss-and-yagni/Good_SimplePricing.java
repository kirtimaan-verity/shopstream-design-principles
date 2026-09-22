/*
 * ============================================================================
 * GOOD: the requirement that exists, and nothing else
 * ============================================================================
 *
 * The same delivered capability as the 200-line version in Bad_, minus the
 * factory, the registry, the plugin loader, the DSL and the nineteen-field
 * context object.
 *
 * A new engineer reads this in under a minute and can change a VAT rate
 * correctly on their first day.
 */
class Good_SimplePricing {

    /*
     * Market VAT rates in basis points. Integer arithmetic, no floating point.
     *
     * DEFEND THIS AGAINST YAGNI, because a sharp participant will challenge it:
     * is a market-keyed map itself speculative generality?
     *
     * No, and the test is the one from the README. The brief states, in writing:
     * "Support for 5 new markets (Egypt, Jordan, Pakistan, India, Nigeria)".
     * The variation is named, listed and dated. Handling it costs one map, which
     * is less code than the single hard-coded rate plus the conditional that
     * would replace it.
     *
     * Contrast with `PricingContext.isArTryOnSession`, whose justification was
     * "AR product try-on (future)" with no specification and no date. Same
     * document, opposite verdicts, because the evidence is different. That
     * distinction is the entire skill.
     */
    private static final java.util.Map<String, Integer> VAT_BASIS_POINTS = java.util.Map.of(
            "AE", 500, "SA", 1500, "EG", 1400,
            "JO", 1600, "PK", 1700, "NG", 750);

    /**
     * Total for a line, including market VAT and any discount.
     *
     * Pure function: no clock, no database, no network, no framework. Testable in
     * microseconds, readable in one screen, and the arithmetic is visible at the
     * call site rather than assembled at runtime from a registry.
     */
    Money totalFor(Money unitPrice, int quantity, String market, Money discount) {

        Money net = unitPrice.times(quantity);

        if (discount.minorUnits() > net.minorUnits()) {
            throw new IllegalArgumentException("Discount exceeds line total");
        }
        Money discounted = new Money(net.minorUnits() - discount.minorUnits(), net.currency());

        Integer basisPoints = VAT_BASIS_POINTS.get(market);
        if (basisPoints == null) {
            throw new IllegalArgumentException("No VAT rate configured for market: " + market);
        }
        long vat = discounted.minorUnits() * basisPoints / 10_000;

        return discounted.plus(new Money(vat, discounted.currency()));
    }
}

/*
 * ============================================================================
 * "But what about when auction mode arrives?" - discussion question 2
 * ============================================================================
 *
 * This is the objection that always comes, and the answer is the lesson.
 *
 * WHAT ACTUALLY HAPPENS IN YEAR TWO:
 *
 * Auction pricing turns out to need bid state, a closing time, reserve-price
 * handling, anti-sniping time extensions, and concurrency control across
 * thousands of simultaneous bidders. It is not "unit price times quantity with a
 * different rule applied". It is a different problem with a different shape.
 *
 * Starting from THIS file, the change is:
 *
 *      interface Pricing {
 *          Money totalFor(PricingRequest request);
 *      }
 *      class FixedPricing   implements Pricing { ... this file, unchanged ... }
 *      class AuctionPricing implements Pricing { ... the real thing ... }
 *
 * One interface, extracted when the second implementation actually exists and
 * its real shape is known. Half a day, and the abstraction fits because it was
 * derived from two real cases rather than guessed from one.
 *
 * Starting from the Bad_ file, the change is: discover that PricingRule cannot
 * express bid state, work out which of the seven types can be salvaged, migrate
 * whatever was built on the DSL, remove the plugin loader nobody used, and then
 * build the real thing anyway.
 *
 * THE PRINCIPLE, in a form that fits on a slide:
 *
 *      Do not build the abstraction. Build the second case.
 *      The abstraction is obvious once two real implementations exist,
 *      and it is a guess before that.
 *
 * This is also why "extract an interface" is a cheap refactoring and "remove a
 * speculative framework" is an expensive one. The asymmetry is what makes YAGNI
 * the correct default rather than merely a preference: the cost of being wrong
 * about the future is much higher than the cost of adding structure later.
 *
 * NOTE the boundary of the argument. YAGNI applies to abstractions inside a
 * building block. It does NOT apply to decisions that are expensive to retrofit:
 * money representation, identifier schemes, security boundaries, data residency,
 * audit trails. Those are cost-of-delay decisions and they get made early, on
 * purpose. Folder 00 makes two of them deliberately, and the reasoning is
 * written down there rather than assumed.
 */
