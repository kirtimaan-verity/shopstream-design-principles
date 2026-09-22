/*
 * ============================================================================
 * GOOD: open for new providers, closed for modification
 * ============================================================================
 *
 * The three switches are gone. Not replaced with a better switch - gone. Routing
 * is now a lookup over a set of registered providers, and each provider knows
 * its own capabilities.
 *
 * Adding a Nigerian provider:
 *
 *   1. write NigeriaGatewayProvider implements PaymentProvider
 *   2. add it to the list at wiring time
 *
 * Two steps, both additive. Nothing in this file changes. Nothing that currently
 * carries payment traffic is edited, so nothing that currently works can be
 * broken by the change.
 */
class Good_PaymentRouter {

    private final java.util.List<PaymentProvider> providers;

    Good_PaymentRouter(java.util.List<PaymentProvider> providers) {
        this.providers = java.util.List.copyOf(providers);
    }

    PaymentResult authorise(OrderId order, Money amount, String market) {
        return providerFor(market, amount.currency()).authorise(order, amount);
    }

    PaymentResult refund(PaymentId payment, Money amount, String market) {
        return providerFor(market, amount.currency()).refund(payment, amount);
    }

    java.util.List<String> reconcile(java.time.LocalDate date, String market) {
        return providerFor(market, null).settlementsFor(date);
    }

    /*
     * The routing rule, expressed once.
     *
     * In the Bad_ version this same rule was written three times, in three
     * shapes, and the three copies had already drifted apart - switch 2 was
     * missing Jordan and nobody knew.
     *
     * Here there is one path, so the reconcile job cannot be missed on a launch:
     * a provider that can authorise for Nigeria is by construction the same
     * object that reconciles for Nigeria. The class of bug the Bad_ version
     * produced is not fixed, it is eliminated.
     */
    private PaymentProvider providerFor(String market, String currency) {
        return providers.stream()
                .filter(p -> p.serves(market, currency))
                .findFirst()
                .orElseThrow(() -> new NoProviderAvailable(market, currency));
    }
}

/**
 * The extension point.
 *
 * A provider declares which markets it serves and how to talk to it. The router
 * never learns a vendor's name, calling convention, amount format or settlement
 * report shape - all of that is translated inside each implementation, which is
 * folder 12's argument applied here.
 */
interface PaymentProvider {

    String name();

    /** Capability declaration, replacing the switch in the router. */
    boolean serves(String market, String currency);

    PaymentResult authorise(OrderId order, Money amount);

    PaymentResult refund(PaymentId payment, Money amount);

    java.util.List<String> settlementsFor(java.time.LocalDate date);
}

/* -------------------------------------------------------------------------- */

class CheckoutComProvider implements PaymentProvider {

    private final CheckoutComClient client;

    CheckoutComProvider(CheckoutComClient client) { this.client = client; }

    @Override public String name() { return "checkout-com"; }

    /*
     * The vendor's coverage, stated once, in the class that owns the knowledge.
     * In the Bad_ version this fact was spread across three switch statements in
     * a class that belonged to nobody in particular.
     */
    @Override
    public boolean serves(String market, String currency) {
        return java.util.Set.of("AE", "SA", "KW", "BH").contains(market);
    }

    @Override
    public PaymentResult authorise(OrderId order, Money amount) {
        return client.charge(order.value(), amount.minorUnits(),
                amount.currency().toLowerCase());
    }

    @Override
    public PaymentResult refund(PaymentId payment, Money amount) {
        return client.refund(payment.value(), amount.minorUnits());
    }

    @Override
    public java.util.List<String> settlementsFor(java.time.LocalDate date) {
        return client.listSettlements(date.toString());
    }
}

class PayTabsProvider implements PaymentProvider {

    private final PayTabsClient client;

    PayTabsProvider(PayTabsClient client) { this.client = client; }

    @Override public String name() { return "paytabs"; }

    @Override
    public boolean serves(String market, String currency) {
        return java.util.Set.of("AE", "SA", "EG", "JO", "OM").contains(market);
    }

    @Override
    public PaymentResult authorise(OrderId order, Money amount) {
        return client.createPayment(order.value(), toDecimal(amount), amount.currency());
    }

    @Override
    public PaymentResult refund(PaymentId payment, Money amount) {
        return client.refundPayment(payment.value(), toDecimal(amount));
    }

    @Override
    public java.util.List<String> settlementsFor(java.time.LocalDate date) {
        return client.getSettlementReport(date.toString());
    }

    private String toDecimal(Money amount) {
        return new java.math.BigDecimal(amount.minorUnits()).movePointLeft(2).toString();
    }
}

/*
 * ============================================================================
 * The Nigeria launch, in full
 * ============================================================================
 *
 *      class NigeriaGatewayProvider implements PaymentProvider {
 *          public String name() { return "nigeria-gw"; }
 *          public boolean serves(String market, String currency) {
 *              return "NG".equals(market);
 *          }
 *          ... four methods, all vendor-specific, all contained ...
 *      }
 *
 * and, at wiring time:
 *
 *      new Good_PaymentRouter(List.of(
 *              new CheckoutComProvider(checkoutComClient),
 *              new PayTabsProvider(payTabsClient),
 *              new NigeriaGatewayProvider(nigeriaClient)));   // <- one line
 *
 * NOT CHANGED: Good_PaymentRouter, CheckoutComProvider, PayTabsProvider, or any
 * caller of any of them.
 *
 * NOT NEEDED: regression testing of the existing providers, because none of
 * their code was touched. That is the risk reduction OCP actually delivers, and
 * it is worth stating in those terms rather than as "extensibility" - a CTO
 * cares that a market launch cannot break payments in the markets that already
 * work.
 *
 * ============================================================================
 * The limitations
 * ============================================================================
 *
 * OPEN ALONG ONE AXIS ONLY. This design is open for new PROVIDERS. It is not
 * open for, say, a new payment METHOD such as buy-now-pay-later, which needs an
 * instalment schedule that PaymentProvider cannot express. When that arrives,
 * this abstraction will need to change, and that is fine - it was built for the
 * variation that was written down, and it served it.
 *
 * You get one axis per abstraction. Choosing the wrong one is the failure mode
 * in folder 10, where the axis was guessed from a roadmap bullet with no
 * specification.
 *
 * THE EVIDENCE THAT JUSTIFIED IT HERE, restated because it is the whole skill:
 * - two implementations exist today, so the shape is observed, not guessed
 * - five more are named, with markets, in the brief
 * - the cost of the alternative is a triple edit on the payment path, five
 *     times over, with a known silent-failure mode
 *
 * That is discussion question 3, and the one-sentence answer for a CTO:
 * we build extension points where the requirement is written down and the second
 * case already exists, and we do not build them anywhere else.
 */

class NoProviderAvailable extends RuntimeException {
    NoProviderAvailable(String market, String currency) {
        super("No payment provider serves market " + market + " for " + currency);
    }
}

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
