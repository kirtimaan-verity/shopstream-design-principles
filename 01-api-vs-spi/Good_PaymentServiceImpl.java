/*
 * ============================================================================
 * THE PIVOT - implements the API, consumes the SPI
 * ============================================================================
 *
 *     Order Service ------> Good_PaymentApi          (what ShopStream OFFERS)
 *                                  ^
 *                                  | implements
 *                                  |
 *                          Good_PaymentServiceImpl     (this class)
 *                                  |
 *                                  | uses
 *                                  v
 *                          Good_PaymentProviderSpi     (what ShopStream REQUIRES)
 *                                  ^
 *                    +-------------+-------------+
 *                    |                           |
 *          Good_CheckoutComProvider    Good_PayTabsProvider
 *
 * Everything ShopStream-specific - routing, retry policy, market rules,
 * idempotency - lives HERE, in the one class that is allowed to know about both
 * sides. Callers above it and providers below it stay ignorant of each other.
 *
 * This class is small on purpose. If it grows past routing + policy, that is a
 * cohesion signal (folder 07), not a reason to widen either interface.
 */
class Good_PaymentServiceImpl implements Good_PaymentApi {

    /**
     * The providers, injected. NOT `new CheckoutComProvider()` - see folder 02 for
     * why construction inside a class is coupling you cannot test around, and
     * folder 16 for why this class must not own the concrete types.
     */
    private final java.util.List<Good_PaymentProviderSpi> providers;
    private final String marketCode;   // "AE", "SA", "EG"... - a runtime value

    Good_PaymentServiceImpl(java.util.List<Good_PaymentProviderSpi> providers, String marketCode) {
        this.providers = java.util.List.copyOf(providers);
        this.marketCode = marketCode;
    }

    @Override
    public PaymentResult authorise(OrderId order, Money amount) {
        // Routing decision: ShopStream policy, not caller knowledge, not provider knowledge.
        Good_PaymentProviderSpi provider = route(amount.currency());

        String token = lookupBuyerPaymentToken(order);   // token, never a card number
        return provider.authorise(order, amount, token);
    }

    @Override
    public PaymentResult capture(PaymentId payment) {
        return providerThatIssued(payment).authorise(null, null, null); // elided
    }

    @Override
    public PaymentResult refund(PaymentId payment, Money amount) {
        return providerThatIssued(payment).refund(payment, amount);
    }

    /**
     * ADDING A PROVIDER FOR THE NIGERIA LAUNCH:
     *
     * 1. Write `NigeriaGatewayProvider implements Good_PaymentProviderSpi`.
     * 2. Add it to the list passed into this constructor at wiring time.
     *
     * That is the whole change. Nothing above this class recompiles. Compare
     * against the Bad_ version, where step 1 alone changed an interface the Order
     * Service depends on.
     *
     * Note also that this loop is OPEN FOR EXTENSION, CLOSED FOR MODIFICATION -
     * it is the Open/Closed Principle applied to routing. See folder 15, where the
     * same problem is solved badly with a switch statement.
     */
    private Good_PaymentProviderSpi route(String currency) {
        return providers.stream()
                .filter(p -> p.supports(currency, marketCode))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No payment provider for " + currency + " in market " + marketCode));
    }

    // Elided - this is a teaching repo, not a payment system.
    private String lookupBuyerPaymentToken(OrderId order) { return "tok_..."; }
    private Good_PaymentProviderSpi providerThatIssued(PaymentId payment) { return providers.get(0); }
}
