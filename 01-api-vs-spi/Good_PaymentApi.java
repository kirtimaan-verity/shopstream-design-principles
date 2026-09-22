/*
 * ============================================================================
 * THE API - what ShopStream OFFERS
 * ============================================================================
 *
 * Called by:      Order Service, Seller Dashboard (refunds), Ops tooling
 * Implemented by: ShopStream itself (exactly one implementation: PaymentServiceImpl)
 *
 * Read the method list and notice what is absent: no gateway, no provider, no API
 * key, no webhook, no URL. A caller cannot tell from this interface that
 * Checkout.com or PayTabs exist - and that is the entire point. When the Nigeria
 * provider is added in 2027, this file does not change and the Order Service does
 * not redeploy.
 *
 * The characteristics of a good interface, checked one by one:
 * - easy to learn ........ 3 methods, all in domain language
 * - easy to use .......... no lifecycle, no ordering rules (see folder 05)
 * - easy to extend ....... new providers extend it without touching this file
 * - hard to misuse ....... typed ids, no raw Strings, no way to leak a PAN
 * - functionally complete  from the CALLER's perspective, which is the only
 *   perspective that matters for an API
 */
interface Good_PaymentApi {

    /**
     * Authorise `amount` against the buyer's stored payment method for this order.
     *
     * Idempotent on OrderId: calling twice for the same order returns the same
     *                        PaymentResult rather than double-charging. This matters because the Order
     *                        Service retries - see folder 05, where retries under timeout caused the
     *                        8%-failure incident.
     */
    PaymentResult authorise(OrderId order, Money amount);

    /** Capture a previously authorised payment. Seller has shipped; take the money. */
    PaymentResult capture(PaymentId payment);

    /** Refund a captured payment, in full or in part. */
    PaymentResult refund(PaymentId payment, Money amount);
}
