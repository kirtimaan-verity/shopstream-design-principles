/*
 * ============================================================================
 * BAD: one rule, four representations, three of them still correct
 * ============================================================================
 *
 * The VAT rate for Saudi Arabia moved from 5% to 15%. Find the four places it is
 * encoded below, and find the one that was missed.
 *
 * Then note how each one is written differently: an int percentage, a double
 * multiplier, a hard-coded constant and a string inside SQL. No duplicate-code
 * detector will flag these as related, because textually they have nothing in
 * common. The duplication is in the KNOWLEDGE, which is exactly the kind that
 * tooling cannot find and that drifts fastest.
 */

// ---- Copy 1: the cart -------------------------------------------------------
class Bad_CartCalculator {
    Money lineTotal(Money unitPrice, int qty, String market) {
        Money net = unitPrice.times(qty);
        int vatPercent = market.equals("SA") ? 15 : 5;      // updated correctly
        return net.plus(new Money(net.minorUnits() * vatPercent / 100, net.currency()));
    }
}

// ---- Copy 2: the invoice ----------------------------------------------------
class Bad_InvoiceRenderer {
    String renderTotals(Money net, String market) {
        double vatRate = market.equals("SA") ? 0.15 : 0.05;  // updated correctly,
                                                            // and now a double,
                                                            // so it rounds
                                                            // differently from
                                                            // copy 1
        long vat = Math.round(net.minorUnits() * vatRate);
        /*
         * Two representations of the same rule that disagree on rounding. On a
         * single order the difference is one fils. Across 14,000 transactions per
         * peak hour it is a reconciliation gap that finance has to explain, and
         * nobody can find the cause because both numbers look right.
         *
         * This is a second, quieter class of DRY failure: the copies agree on the
         * RULE and disagree on the ARITHMETIC.
         */
        return "Net: " + net.minorUnits() + " VAT: " + vat;
    }
}

// ---- Copy 3: the seller payout ----------------------------------------------
class Bad_SellerPayout {

    /*
     * THE ONE THAT WAS MISSED.
     *
     * When the Saudi rate changed, this file was not touched. It is in a
     * different module, owned by a different team, and it does not contain the
     * word "VAT" anywhere near the constant.
     *
     * Consequence: buyers were charged 15%, sellers were paid out as if the rate
     * were 5%. Every Saudi transaction produced a small discrepancy. No exception
     * was thrown. No test failed - the payout tests asserted against the old
     * constant, so they all passed, in green, for months.
     *
     * That last detail is worth pausing on. The test suite did not merely fail to
     * catch the drift; it actively certified the wrong answer, because the test
     * and the code duplicated the same stale knowledge.
     */
    private static final int VAT_PERCENT = 5;

    Money payoutFor(Money grossSale, String market) {
        long net = grossSale.minorUnits() * 100 / (100 + VAT_PERCENT);
        return new Money(net * 95 / 100, grossSale.currency());  // minus 5% commission
    }
}

// ---- Copy 4: the finance report ---------------------------------------------
class Bad_FinanceReport {
    String monthlyVatReturn(String market) {
        /*
         * Copy four lives inside a SQL string, which means it is invisible to the
         * compiler, to the IDE's rename refactoring, and to any static analysis.
         *
         * Reporting queries are the most common hiding place for duplicated
         * business rules, because they are usually written by a different person
         * at a different time under a deadline, and they are rarely reviewed by
         * the team that owns the rule.
         */
        return "SELECT SUM(total_fils * 0.15 / 1.15) FROM orders WHERE market = 'SA'";
    }
}

/*
 * ============================================================================
 * A second duplication: retry policy, three services, three behaviours
 * ============================================================================
 *
 * Each of these was written by a different engineer solving the same problem
 * under time pressure, and each is individually reasonable.
 *
 * Together they mean ShopStream has no retry policy. It has three, and nobody
 * can state what the system does under gateway degradation - which is exactly
 * the question ops needed answered during the 90-minute National Day incident.
 */
class Bad_OrderServiceRetry {
    void call() {
        for (int i = 0; i < 3; i++) {           // 3 attempts, no backoff
            try { doIt(); return; } catch (RuntimeException e) { }
        }
    }
    void doIt() { }
}

class Bad_PaymentServiceRetry {
    void call() throws InterruptedException {
        for (int i = 0; i < 5; i++) {           // 5 attempts, fixed 1s delay
            try { doIt(); return; } catch (RuntimeException e) { Thread.sleep(1000); }
        }
    }
    void doIt() { }
}

class Bad_NotificationRetry {
    void call() throws InterruptedException {
        long delay = 100;
        for (int i = 0; i < 10; i++) {          // 10 attempts, exponential, no cap
            try { doIt(); return; } catch (RuntimeException e) { Thread.sleep(delay); delay *= 2; }
        }
        // 10 doublings from 100ms means the last wait is over 50 seconds, on a
        // thread that is holding a connection. Nobody intended that; it is what
        // the arithmetic does.
    }
    void doIt() { }
}
