/*
 * ============================================================================
 * THE SPI - what ShopStream REQUIRES
 * ============================================================================
 *
 * Called by:      ShopStream (PaymentServiceImpl)
 * Implemented by: gateway adapters - Checkout.com, PayTabs, and whoever we sign
 *                 for Nigeria in 2027
 *
 * The call direction is INVERTED compared to the API. ShopStream is the caller
 * here; the third party is the implementer. That single fact drives every design
 * decision below.
 *
 * DESIGN RULES FOR AN SPI (worth putting on a slide):
 *
 * 1. Keep it SMALL. Every method is work you are imposing on every future
 *    provider. Four methods here; the Bad_ version had that many by accident.
 *
 * 2. NEVER add a method without a default. Adding an abstract method to an SPI
 *    breaks every existing implementation at once. `default` methods on a Java
 *    interface are an SPI evolution tool, not a convenience.
 *
 * 3. Speak the PROVIDER's language on the way in, the DOMAIN's language on the
 *    way out. The adapter's job is exactly that translation - see folder 12
 *    (Abstraction) for what happens when the abstraction leaks instead.
 *
 * 4. This is the PCI-DSS boundary. Raw cardholder data exists on the far side
 *    of this interface and nowhere else in ShopStream. The audit scope is one
 *    package.
 */
interface Good_PaymentProviderSpi {

    /** Stable machine name used for routing and registration, e.g. "checkout-com". */
    String providerName();

    /**
     * Can this provider settle in this currency, in this market?
     *
     * ShopStream calls this to route; the provider answers. Compare with the
     * Bad_ version, where the *caller* had to know which provider handled AED.
     */
    boolean supports(String currency, String marketCode);

    /**
     * Do the actual authorisation against the gateway.
     *
     * Returns the DOMAIN type (PaymentResult), not a Checkout.com response object.
     * Throws the DOMAIN exception, not `CheckoutComHttpException`. If either of
     * those leaked, every caller of the API above would learn which provider ran.
     */
    PaymentResult authorise(OrderId order, Money amount, String buyerPaymentToken);

    /**
     * Refund. Note the `default` - PayTabs supports partial refunds, Checkout.com
     * (in this fiction) does not. Rather than forcing every provider to implement
     * a capability it lacks, the SPI declares a safe fallback and the routing layer
     * decides what to do about it.
     *
     * This is also a Liskov Substitution question in disguise: a provider that
     * "implements" refund by silently doing nothing would be a substitutability
     * violation. Declaring the limitation is honest; hiding it is a defect.
     * See folder 13.
     */
    default PaymentResult refund(PaymentId payment, Money amount) {
        throw new UnsupportedOperationException(providerName() + " does not support refunds");
    }
}
