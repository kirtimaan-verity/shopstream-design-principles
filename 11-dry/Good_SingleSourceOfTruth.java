/*
 * ============================================================================
 * GOOD: one rule, one authority, everyone asks
 * ============================================================================
 *
 * The test for whether DRY has actually been applied is not "is the code shorter".
 * It is:
 *
 *      "If the Saudi VAT rate changes tomorrow, how many files do I edit,
 *       and how do I know I found them all?"
 *
 * Bad_ version:  four files, in three modules, and you find them by grepping for
 *                a number - so you do not know that you found them all.
 * Good_ version: one file, and the compiler shows you every caller.
 */

/**
 * The single authority for value-added tax across ShopStream.
 *
 * Cart, invoice, payout and reporting all call this. There is no second
 * representation of the rate anywhere in the system, which is a property you can
 * state and check rather than hope for.
 */
final class VatPolicy {

    /*
     * Basis points, so integer arithmetic throughout and no rounding
     * disagreement between callers. In the Bad_ version the cart used an int
     * percentage and the invoice used a double multiplier, so the two produced
     * different fils on the same order. Here there is one calculation, so that
     * class of discrepancy cannot exist.
     */
    private static final java.util.Map<String, Integer> RATES = java.util.Map.of(
            "AE", 500, "SA", 1500, "EG", 1400,
            "JO", 1600, "PK", 1700, "NG", 750);

    private VatPolicy() { }

    /** VAT payable on a net amount, in the same currency. */
    static Money on(Money net, String market) {
        return new Money(net.minorUnits() * rateFor(market) / 10_000, net.currency());
    }

    /**
     * Net amount contained within a gross amount.
     *
     * This is the operation the seller payout needed. In the Bad_ version the
     * payout code derived it independently, from its own constant, and that is
     * where the drift happened.
     *
     * The general point: putting the RATE in one place is not enough if every
     * caller still writes its own arithmetic against it. The single source of
     * truth has to cover the OPERATIONS on the knowledge, not just the value.
     * That is the difference between a shared constant and a policy object, and
     * it is the step teams usually stop short of.
     */
    static Money netFromGross(Money gross, String market) {
        int bp = rateFor(market);
        return new Money(gross.minorUnits() * 10_000 / (10_000 + bp), gross.currency());
    }

    static int rateBasisPoints(String market) {
        return rateFor(market);
    }

    private static int rateFor(String market) {
        Integer bp = RATES.get(market);
        if (bp == null) {
            /*
             * Fail loudly on an unknown market.
             *
             * The Bad_ version's ternary silently applied 5% to anything that was
             * not "SA", so the Egypt launch would have under-collected VAT in
             * production with no signal at all. A missing entry here stops the
             * request instead.
             */
            throw new IllegalArgumentException("No VAT rate configured for market: " + market);
        }
        return bp;
    }
}

// ---- All four former copies now ask the same authority ----------------------

class Good_CartCalculator {
    Money lineTotal(Money unitPrice, int qty, String market) {
        Money net = unitPrice.times(qty);
        return net.plus(VatPolicy.on(net, market));
    }
}

class Good_InvoiceRenderer {
    String renderTotals(Money net, String market) {
        return "Net: " + net.minorUnits() + " VAT: " + VatPolicy.on(net, market).minorUnits();
    }
}

class Good_SellerPayout {
    private static final int COMMISSION_BASIS_POINTS = 500;   // 5%, ShopStream's cut

    Money payoutFor(Money grossSale, String market) {
        Money net = VatPolicy.netFromGross(grossSale, market);
        long commission = net.minorUnits() * COMMISSION_BASIS_POINTS / 10_000;
        return new Money(net.minorUnits() - commission, net.currency());
    }
}

class Good_FinanceReport {
    /*
     * The reporting query no longer embeds the rate. It asks the policy and
     * parameterises the SQL.
     *
     * Reports are where duplicated business rules hide most successfully,
     * because they are written under deadline by whoever is available and
     * reviewed by nobody who owns the rule. Worth calling out explicitly when
     * teaching this: "check the reports" is a concrete, actionable instruction
     * that finds real drift in real systems.
     */
    String monthlyVatReturn(String market) {
        int bp = VatPolicy.rateBasisPoints(market);
        return "SELECT SUM(total_fils * " + bp + " / (10000 + " + bp + ")) "
             + "FROM orders WHERE market = ?";
    }
}

/*
 * ============================================================================
 * The retry policy, likewise
 * ============================================================================
 *
 * Three services had three different retry behaviours and ShopStream could not
 * state what the system did under gateway degradation. One policy object, three
 * named configurations, and now the answer is readable.
 */
record RetryPolicy(int maxAttempts, java.time.Duration initialDelay,
                   double multiplier, java.time.Duration maxDelay) {

    /*
     * Named configurations rather than one policy for everything.
     *
     * This is DRY applied correctly: the retry MECHANISM is written once, while
     * the PARAMETERS legitimately differ, because payments and notifications have
     * genuinely different requirements. Forcing them to share parameters would be
     * the over-application the counter-case file warns about.
     */
    static RetryPolicy forPayments() {
        // Aggressive cap: a payment retry storm was part of the National Day
        // incident (folder 05). Three attempts, and the circuit breaker takes
        // over from there.
        return new RetryPolicy(3, java.time.Duration.ofMillis(200), 2.0,
                java.time.Duration.ofSeconds(2));
    }

    static RetryPolicy forNotifications() {
        // Off the critical path, so it can afford patience - but the delay is
        // CAPPED, which the Bad_ version's unbounded doubling was not.
        return new RetryPolicy(8, java.time.Duration.ofMillis(500), 2.0,
                java.time.Duration.ofSeconds(30));
    }
}
