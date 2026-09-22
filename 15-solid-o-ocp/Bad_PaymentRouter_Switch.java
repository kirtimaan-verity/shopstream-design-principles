/*
 * ============================================================================
 * BAD: the same switch, three times
 * ============================================================================
 *
 * Two providers today, five new markets on the roadmap.
 *
 * Read all three methods before commenting. The exercise: highlight the provider
 * switch in each one, then plan the change for a Nigerian provider and count how
 * many places you have to touch - and how many you could plausibly miss.
 */
class Bad_PaymentRouter_Switch {

    private final CheckoutComClient checkoutCom;
    private final PayTabsClient payTabs;

    Bad_PaymentRouter_Switch(CheckoutComClient checkoutCom, PayTabsClient payTabs) {
        this.checkoutCom = checkoutCom;
        this.payTabs = payTabs;
    }

    /*
     * SWITCH 1 of 3.
     *
     * Note also that the routing RULE (which provider serves which market) is
     * expressed here as a chain of string comparisons, mixed in with the calling
     * convention of each vendor. Two different kinds of knowledge in one
     * expression, which is folder 08's problem arriving inside folder 15's.
     */
    PaymentResult authorise(OrderId order, Money amount, String market) {
        switch (market) {
            case "AE":
            case "SA":
                return checkoutCom.charge(order.value(), amount.minorUnits(),
                        amount.currency().toLowerCase());
            case "EG":
            case "JO":
                return payTabs.createPayment(order.value(),
                        new java.math.BigDecimal(amount.minorUnits()).movePointLeft(2).toString(),
                        amount.currency());
            default:
                // The default is a runtime failure for any market that has not
                // been added here. The Nigeria launch fails in production unless
                // somebody remembers this line.
                throw new IllegalStateException("No provider configured for market " + market);
        }
    }

    /*
     * SWITCH 2 of 3.
     *
     * Same rule, expressed again, with slightly different structure because a
     * different person wrote it a few months later. The two switches already
     * disagree in one respect: this one does not handle "JO" at all.
     *
     * Nobody noticed, because Jordan has not launched yet. It will be noticed on
     * launch day, in production, by a buyer trying to get a refund.
     */
    PaymentResult refund(PaymentId payment, Money amount, String market) {
        if (market.equals("AE") || market.equals("SA")) {
            return checkoutCom.refund(payment.value(), amount.minorUnits());
        } else if (market.equals("EG")) {
            return payTabs.refundPayment(payment.value(),
                    new java.math.BigDecimal(amount.minorUnits()).movePointLeft(2).toString());
        }
        throw new IllegalStateException("No provider configured for market " + market);
    }

    /*
     * SWITCH 3 of 3, and this is the one that causes the incident.
     *
     * Nightly reconciliation. It runs at 2am, it is not in anybody's launch
     * checklist, and no smoke test exercises it.
     *
     * When Nigeria launches and switches 1 and 2 are updated, this one is
     * missed. Nigerian transactions are authorised and refunded correctly, and
     * simply never reconciled. Finance discovers a gap weeks later and cannot
     * explain it, because from every other angle the system is working.
     *
     * This is the real argument for OCP and it is not about elegance. THE DANGER
     * IS NOT THE EDITS YOU MAKE, IT IS THE ONE YOU FORGET. Any design where
     * correctness depends on a human remembering all three sites will eventually
     * be wrong, and it will be wrong in the place that is hardest to observe.
     */
    java.util.List<String> reconcile(java.time.LocalDate date, String market) {
        switch (market) {
            case "AE":
            case "SA":
                return checkoutCom.listSettlements(date.toString());
            case "EG":
            case "JO":
                return payTabs.getSettlementReport(date.toString());
            default:
                return java.util.List.of();
                // Returns empty rather than throwing, so the failure is silent.
                // A missing market produces a clean, successful, empty
                // reconciliation run and a green dashboard.
        }
    }

    /*
     * THE CHANGE, COSTED
     *
     * Adding a Nigerian provider:
     *
     *   1. add a field for the new client
     *   2. add it to the constructor, which changes every caller's wiring
     *   3. edit switch 1
     *   4. edit switch 2
     *   5. edit switch 3          <- the one that gets forgotten
     *   6. retest all three methods for both existing providers, because they
     *      were modified
     *
     * Six steps, three of them edits to code that currently works and carries
     * every payment ShopStream takes. Step 6 is the hidden cost: the risk of the
     * change is not proportional to the new feature, it is proportional to
     * everything in this class.
     *
     * Five markets are on the roadmap, so this is five times.
     */
}

// ---- Vendor clients, with their incompatible conventions --------------------
interface CheckoutComClient {
    PaymentResult charge(String reference, long minorUnits, String lowercaseCurrency);
    PaymentResult refund(String paymentId, long minorUnits);
    java.util.List<String> listSettlements(String isoDate);
}

interface PayTabsClient {
    PaymentResult createPayment(String cartId, String decimalAmount, String currency);
    PaymentResult refundPayment(String tranRef, String decimalAmount);
    java.util.List<String> getSettlementReport(String isoDate);
}
