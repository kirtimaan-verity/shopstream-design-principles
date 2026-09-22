/*
 * ============================================================================
 * API and SPI collapsed into ONE interface
 * ============================================================================
 *
 * This is the shape ShopStream's payment code is in today. It started as
 * `PaymentService.charge(...)` - perfectly reasonable with one gateway. Then
 * PayTabs was added, and the provider-facing concerns were bolted onto the same
 * interface because "it's all payment stuff."
 *
 * Look at the method list and ask: WHO CALLS THIS METHOD, and WHO IMPLEMENTS IT?
 * The answers are not the same for all methods. That is the smell.
 */
interface Bad_PaymentService_MixedInterface {

    // ---- These are API methods: the ORDER SERVICE calls them. ----------------
    //      ShopStream implements them. Callers are internal ShopStream code.

    PaymentResult charge(OrderId order, Money amount);

    PaymentResult refund(PaymentId payment, Money amount);

    // ---- These are SPI methods: SHOPSTREAM calls them. ----------------------
    //      A gateway adapter (Checkout.com, PayTabs) implements them.
    //      The Order Service has no business seeing any of this.

    /** Provider lifecycle - called once at boot by ShopStream, never by a caller. */
    void initialiseGatewayConnection(String apiKey, String endpointUrl);

    /** Provider capability probe - ShopStream asks, the provider answers. */
    boolean supportsCurrency(String isoCode);

    /** Provider webhook sink - the gateway POSTs here; ShopStream routes it in. */
    void handleGatewayWebhook(String rawJsonPayload);

    /** Provider health - ShopStream polls this, callers must never see it. */
    boolean isGatewayReachable();

    // ---- And then this appeared, which is neither. --------------------------

    /**
     * Added during the National Day incident so ops could force a provider switch.
     * Now the Order Service can call it. Nobody remembers if anything does.
     */
    void setActiveProvider(String providerName);
}

/*
 * WHAT THIS COSTS SHOPSTREAM
 * --------------------------
 *
 * 1. ADDING A PROVIDER TOUCHES CALLERS.
 *    The Nigeria launch needs a provider that requires a `merchantCategoryCode`.
 *    Adding `initialiseGatewayConnection(key, url, mcc)` changes an interface that
 *    the Order Service compiles against. A payments change now needs an orders
 *    code review, an orders build, and an orders deployment. Efferent coupling
 *    from Order Service to a type that has no reason to ever change for Order.
 *
 * 2. CALLERS CAN DO THINGS THEY SHOULD NOT.
 *    Nothing stops an Order Service developer calling `setActiveProvider("paytabs")`
 *    from inside a checkout request handler. Nothing stops them calling
 *    `handleGatewayWebhook(...)` with a hand-rolled JSON string. The compiler is
 *    not helping, because the interface says these are legitimate operations.
 *
 * 3. IT CANNOT BE VERSIONED.
 *    The API side should be stable for years (callers are internal but numerous).
 *    The SPI side changes every time a market is added. One interface, two
 *    incompatible change rates - so it changes at the FASTER rate, and every
 *    caller pays for it.
 *
 * 4. IT VIOLATES INTERFACE SEGREGATION (the "I" in SOLID).
 *    No client uses all seven methods. The Order Service uses two. A gateway
 *    adapter uses four. `setActiveProvider` is used by ops tooling. Three clients,
 *    three disjoint subsets, one interface.
 *
 * 5. PCI-DSS SCOPE EXPLODES.
 *    `handleGatewayWebhook(String rawJsonPayload)` carries raw gateway payloads,
 *    which may contain cardholder data. Because it sits on the interface the Order
 *    Service depends on, an auditor must now treat the Order Service as in-scope
 *    for PCI-DSS Level 1. That is weeks of audit work caused by an interface shape.
 */
