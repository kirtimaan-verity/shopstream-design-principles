/*
 * ============================================================================
 * A SECOND SPI IMPLEMENTATION - the PayTabs adapter
 * ============================================================================
 *
 * The second implementation is the one that PROVES the abstraction. A single
 * implementation behind an interface tells you nothing: you may simply have
 * described one vendor's API and called it a boundary.
 *
 * Walk this side by side with Good_CheckoutComProvider in class. Different
 * vendor, different capabilities (PayTabs does support refunds), different
 * markets - identical interface, and PaymentServiceImpl cannot tell them apart.
 *
 * Ask the room: "if I deleted Checkout.com tomorrow, what else changes?"
 * Answer: one line of wiring configuration. That is what loose-but-sufficient
 *         coupling actually feels like.
 */
class Good_PayTabsProvider implements Good_PaymentProviderSpi {

    private final Object payTabsClient;
    private final String profileId;

    Good_PayTabsProvider(Object payTabsClient, String profileId) {
        this.payTabsClient = payTabsClient;
        this.profileId = profileId;
    }

    @Override
    public String providerName() {
        return "paytabs";
    }

    @Override
    public boolean supports(String currency, String marketCode) {
        // Broader market coverage - this is why PayTabs gets the Egypt launch.
        return java.util.Set.of("AED", "SAR", "EGP", "JOD").contains(currency)
                && java.util.Set.of("AE", "SA", "EG", "JO", "OM").contains(marketCode);
    }

    @Override
    public PaymentResult authorise(OrderId order, Money amount, String buyerPaymentToken) {
        // PayTabs wants MAJOR units as a decimal string. Their problem, solved here.
        // String theirAmount = new java.math.BigDecimal(amount.minorUnits())
        //         .movePointLeft(2).toPlainString();
        return new PaymentResult(new PaymentId("pay_pt_987"), true, "**** 1111", null);
    }

    /**
     * PayTabs DOES support partial refunds, so this provider overrides the default.
     *
     * The capability difference between providers is now expressed in exactly one
     * place - the type system - rather than as a comment in a runbook or a branch
     * inside the Order Service.
     */
    @Override
    public PaymentResult refund(PaymentId payment, Money amount) {
        return new PaymentResult(payment, true, "**** 1111", null);
    }
}
