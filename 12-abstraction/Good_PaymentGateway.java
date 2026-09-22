/*
 * ============================================================================
 * GOOD: an abstraction in the caller's language
 * ============================================================================
 *
 * The test that matters: delete Checkout.com from the build. What still compiles?
 *
 *   Bad_ version:  the interface, the Order Service, the use case, the tests.
 *                  Nothing survives.
 *   Good_ version: everything except one adapter class.
 *
 * That is the difference between an abstraction and a package boundary.
 */
interface Good_PaymentGateway {

    /*
     * Domain types in, domain types out. No vendor request object, no vendor
     * response object, no vendor exception, no protocol in the name.
     *
     * Every word in this signature comes from ShopStream's vocabulary. A new
     * engineer can read it without knowing that Checkout.com exists, which is
     * the practical definition of a successful abstraction.
     */
    PaymentOutcome authorise(OrderId order, Money amount, PaymentToken token);

    PaymentOutcome capture(PaymentId payment);

    /*
     * Refund, with the capability difference handled honestly.
     *
     * PayTabs supports partial refunds; Checkout.com does not. Three options
     * were available, and it is worth walking all three in class because this is
     * where most real abstractions go wrong:
     *
     *   (a) Drop refunds from the interface entirely.
     *       Loses a capability ShopStream needs. Rejected.
     *
     *   (b) Declare it, and let a provider that cannot do it silently do
     *       nothing.
     *       This is the option teams take, and it is the worst one. The caller
     *       believes the refund happened. The buyer does not get their money.
     *       Nothing throws. It is a Liskov violation and a customer-facing
     *       defect at the same time - see folder 13.
     *
     *   (c) Declare it, and make the limitation explicit and visible.
     *       Chosen. The default throws, so a provider that lacks the capability
     *       fails loudly at the point of use, and the routing layer can decide
     *       what to do about it.
     *
     * The general principle: an abstraction may HIDE a difference, but it must
     * never LIE about one. Hiding which vendor ran is the job. Hiding that the
     * money was not returned is a defect wearing an abstraction's clothes.
     */
    default PaymentOutcome refund(PaymentId payment, Money amount) {
        throw new UnsupportedOperationException("This gateway does not support refunds");
    }
}

/*
 * The domain's own result type, with the vendor's status table translated away.
 *
 * A sealed hierarchy rather than a boolean plus a string, so that callers must
 * handle every case and the compiler tells them when a new one appears. This is
 * the legitimate use of inheritance discussed at the end of folder 03.
 */
sealed interface PaymentOutcome permits Authorised, Declined, GatewayUnavailable { }

record Authorised(PaymentId id, String maskedCard) implements PaymentOutcome { }

/**
 * Decline reasons expressed in ShopStream's terms, not the vendor's.
 *
 * Checkout.com says "20051". PayTabs says something else. Both mean the same
 * thing to ShopStream: the buyer has no money. The translation happens in the
 * adapter, once, and every caller downstream reads a word rather than a code.
 *
 * Practical payoff: the retry rule "retry a soft decline, never retry
 * INSUFFICIENT_FUNDS" can now be written once, in the domain, and it stays
 * correct when the provider changes.
 */
record Declined(DeclineReason reason) implements PaymentOutcome { }

enum DeclineReason { INSUFFICIENT_FUNDS, CARD_EXPIRED, SUSPECTED_FRAUD, DO_NOT_HONOUR, OTHER }

/** The gateway could not be reached or did not answer within the time budget. */
record GatewayUnavailable(java.time.Duration waited) implements PaymentOutcome { }

/** An opaque handle. Deliberately not a card number - see folder 09 and PCI-DSS. */
record PaymentToken(String value) { }

/* -------------------------------------------------------------------------- */

/**
 * The adapter. The only class in ShopStream that knows Checkout.com exists.
 *
 * All the translation lives here: their request shape, their status codes, their
 * exceptions, their currency conventions. This class is allowed to be tedious,
 * because tedium contained in one file is cheap and tedium spread across a
 * codebase is not.
 */
class Good_CheckoutComGateway implements Good_PaymentGateway {

    @Override
    public PaymentOutcome authorise(OrderId order, Money amount, PaymentToken token) {
        try {
            // var request = new CheckoutComRequest(amount.minorUnits(),
            //         amount.currency().toLowerCase(), order.value(), token.value());
            // var response = http.post("/payments", request);

            String responseCode = "10000";   // stand-in for the vendor call

            // Translation: vendor status table -> domain outcome. Written once,
            // here, instead of at every call site as in the Bad_ version.
            return switch (responseCode) {
                case "10000" -> new Authorised(new PaymentId("pay_ck_1"), "**** 4242");
                case "20051" -> new Declined(DeclineReason.INSUFFICIENT_FUNDS);
                case "20054" -> new Declined(DeclineReason.CARD_EXPIRED);
                case "20059" -> new Declined(DeclineReason.SUSPECTED_FRAUD);
                case "20005" -> new Declined(DeclineReason.DO_NOT_HONOUR);
                default      -> new Declined(DeclineReason.OTHER);
            };

        } catch (RuntimeException vendorFailure) {
            // The vendor exception stops here. It does not appear in any
            // signature above this line, so it cannot spread.
            return new GatewayUnavailable(java.time.Duration.ofSeconds(3));
        }
    }

    @Override
    public PaymentOutcome capture(PaymentId payment) {
        return new Authorised(payment, "**** 4242");
    }

    // No refund override: Checkout.com cannot do it, so the honest default applies.
}

/* -------------------------------------------------------------------------- */

/**
 * The fake. Fifteen lines, no network, no sandbox account, no vendor credentials.
 *
 * This is discussion question 3, and it is the argument that persuades engineers
 * who are unmoved by architectural purity:
 *
 *   Bad_ version:  testing checkout means a Checkout.com sandbox account. Tests
 *                  are slow, need network access, need shared credentials, fail
 *                  when the sandbox is down, and cannot easily simulate a
 *                  timeout or a fraud decline at all. So the team writes few of
 *                  them, and the ones they write are flaky.
 *
 *   Good_ version: this class. Tests run in milliseconds, offline, and every
 *                  outcome including GatewayUnavailable can be triggered on
 *                  demand - which is exactly the case that caused the 8% failure
 *                  incident and was previously untestable.
 *
 * The existence of this fake is also the proof that the abstraction is real. If
 * an in-memory implementation is awkward to write, the interface is still shaped
 * like the vendor.
 */
class Good_InMemoryPaymentGateway implements Good_PaymentGateway {

    private final java.util.Map<OrderId, PaymentOutcome> scripted = new java.util.HashMap<>();

    /** Tests use this to script the outcome they want to exercise. */
    void willReturn(OrderId order, PaymentOutcome outcome) {
        scripted.put(order, outcome);
    }

    @Override
    public PaymentOutcome authorise(OrderId order, Money amount, PaymentToken token) {
        return scripted.getOrDefault(order, new Authorised(new PaymentId("pay_fake"), "**** 0000"));
    }

    @Override
    public PaymentOutcome capture(PaymentId payment) {
        return new Authorised(payment, "**** 0000");
    }

    @Override
    public PaymentOutcome refund(PaymentId payment, Money amount) {
        return new Authorised(payment, "**** 0000");
    }
}

/* -------------------------------------------------------------------------- */

/**
 * The caller, rewritten. Compare against Bad_OrderServiceUsingLeakyGateway.
 */
class Good_OrderServiceUsingGateway {

    private final Good_PaymentGateway gateway;

    Good_OrderServiceUsingGateway(Good_PaymentGateway gateway) {
        this.gateway = gateway;
    }

    Order pay(Order order, PaymentToken token) {
        PaymentOutcome outcome = gateway.authorise(order.id(), order.total(), token);

        /*
         * Exhaustive over a sealed type, so the compiler enforces that every
         * outcome is handled. When a fourth outcome is added - say
         * RequiresThreeDSecure for the European markets - this switch fails to
         * compile and the developer is shown every place that needs a decision.
         *
         * Compare with the Bad_ version's string comparisons against vendor
         * status codes, where an unhandled code silently fell into the default
         * branch and cancelled the order.
         *
         * Nothing in this method names a vendor, a protocol, an HTTP status or a
         * response code. Checkout.com could be replaced with PayTabs, or with a
         * bank integration over a message queue, and this method would not
         * change.
         */
        return switch (outcome) {
            case Authorised a         -> order.withStatus(OrderStatus.AUTHORISED);
            case Declined d           -> order.withStatus(OrderStatus.CANCELLED);
            case GatewayUnavailable g -> order.withStatus(OrderStatus.PLACED);  // retry out of band
        };
    }
}
