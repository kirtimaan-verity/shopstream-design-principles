/*
 * ============================================================================
 * AN SPI IMPLEMENTATION - the Checkout.com adapter
 * ============================================================================
 *
 * This class is allowed to be ugly. It is the only place in ShopStream that knows
 * Checkout.com exists, so all the vendor-specific mess is CONTAINED here:
 * their JSON shape, their error codes, their HTTP quirks, their idea of what a
 * currency code looks like.
 *
 * The test for a good adapter: could you delete this file, write a different
 * provider, and have nothing else in the codebase change? If yes, the SPI is
 * doing its job.
 */
class Good_CheckoutComProvider implements Good_PaymentProviderSpi {

    private final Object httpClient;      // vendor SDK - never escapes this class
    private final String secretKey;       // injected from config, never hard-coded (folder 06)

    Good_CheckoutComProvider(Object httpClient, String secretKey) {
        this.httpClient = httpClient;
        this.secretKey = secretKey;
    }

    @Override
    public String providerName() {
        return "checkout-com";
    }

    @Override
    public boolean supports(String currency, String marketCode) {
        // Vendor knowledge, stated once, in the one class entitled to have it.
        return java.util.Set.of("AED", "SAR", "USD").contains(currency)
                && java.util.Set.of("AE", "SA", "KW", "BH").contains(marketCode);
    }

    @Override
    public PaymentResult authorise(OrderId order, Money amount, String buyerPaymentToken) {
        /*
         * TRANSLATION IN - domain types to vendor types.
         * Checkout.com wants minor units and a lowercase currency; our Money is
         * already minor units, so this is cheap. If it were expensive, that cost
         * would still belong here rather than leaking upward.
         */
        // var request = new CheckoutComChargeRequest(
        //         amount.minorUnits(), amount.currency().toLowerCase(),
        //         buyerPaymentToken, order.value() /* used as their idempotency key */);

        /*
         * TRANSLATION OUT - vendor types to domain types, INCLUDING FAILURES.
         *
         * A CheckoutComHttpException must never escape this method. If it did,
         * PaymentServiceImpl would need a `catch (CheckoutComHttpException)`,
         * and at that moment the abstraction has leaked and the SPI is worthless.
         * See folder 12 for a worked example of exactly that leak.
         */
        try {
            // var response = httpClient.post("/payments", request, secretKey);
            // return new PaymentResult(new PaymentId(response.id()), response.approved(),
            //                          response.source().last4(), response.responseSummary());
            return new PaymentResult(new PaymentId("pay_ck_123"), true, "**** 4242", null);

        } catch (RuntimeException vendorFailure) {
            // Domain-level failure. The caller learns "declined", not "HTTP 502 from cko".
            return new PaymentResult(null, false, null, "gateway_unavailable");
        }
    }

    /*
     * Note: no `refund` override. In this fiction Checkout.com has no refund API,
     *       so the SPI's `default` applies and the routing layer gets an honest
     *       UnsupportedOperationException rather than a silent no-op.
     */
}
