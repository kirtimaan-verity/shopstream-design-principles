/*
 * ============================================================================
 * "WE USE EVENTS, SO WE'RE DECOUPLED"
 * ============================================================================
 *
 * ShopStream's team read about event-driven architecture and introduced a
 * message bus. The synchronous calls are gone. The architecture diagram now
 * shows a broker in the middle and looks reassuringly loose.
 *
 * Read the publish call below and ask the room the only question that matters:
 *
 * "If I deleted every consumer, would this producer still be correct?"
 *
 * If the answer is no, the coupling never left.
 */
class Bad_UntypedEventBus {

    private final MessageBus bus;

    Bad_UntypedEventBus(MessageBus bus) {
        this.bus = bus;
    }

    void placeOrder(BuyerId buyer, String buyerEmail, ProductId product, int qty, Money total) {

        OrderId orderId = OrderId.next();
        // ... persist the order ...

        /*
         * ---- PROBLEM 1: THE UNTYPED PAYLOAD --------------------------------
         *
         * A Map<String, Object> is a schema that exists only in the author's
         * head and in whatever the runtime happened to put in it.
         *
         * Nothing here is checked. Not the key names, not the value types, not
         * whether a required field is present. The consumer's contract is
         * "whatever the producer felt like sending on the day."
         *
         * Failure mode: someone renames "orderId" to "order_id" for consistency
         *               with the REST API. It compiles. It deploys. It passes the producer's
         *               tests, because the producer's tests assert on what the producer sent.
         *               Consumers silently start receiving null, and orders stop being
         *               fulfilled. Nobody is paged, because nothing threw. ShopStream learns
         *               about it from a buyer complaint.
         *
         * This is A5 Problem 4 - the shared schema with no compile-time warning -
         * rebuilt on the message bus by a team that thought they were escaping it.
         */
        bus.publish("order.placed", java.util.Map.of(
                "orderId",   orderId.value(),
                "buyerId",   buyer.value(),
                "productId", product.value(),
                "qty",       qty,
                "total",     total.minorUnits() / 100.0,   // <- and now money is a double

        /*
         * ---- PROBLEM 2: THE COMMAND WEARING AN EVENT'S CLOTHES -------------
         *
         * Everything from here down is the Order Service issuing INSTRUCTIONS to
         * services it claims not to know about.
         *
         * Go through them one at a time with the room. Each field is a fact about
         * a CONSUMER that has leaked into a PRODUCER:
         */
                "sendEmailTo",              buyerEmail,
                //  ^ Order knows an email will be sent, and to where. It is doing
                //    the Notification Service's addressing for it. When ShopStream
                //    adds SMS for the Pakistan launch, THIS file changes.

                "emailTemplate",            "order_confirm_v2",
                //  ^ Order knows the notification service's TEMPLATE NAMES and
                //    their VERSION NUMBERS. Marketing changing copy is now a
                //    change to the checkout path. There is no universe in which
                //    that is the Order Service's business.

                "alsoNotifySellerDashboard", true,
                //  ^ Order knows the Seller Dashboard exists and decides whether
                //    it gets told. That is a boolean feature flag for someone
                //    else's service, shipped inside a payment-critical message.

                "skipAnalyticsIfTestOrder",  false
                //  ^ Order knows the Analytics service exists and knows one of
                //    its internal behaviours well enough to override it.
        ));

        /*
         * SCORE THE TRADE HONESTLY:
         *
         *   Gained:  the checkout thread no longer blocks on SMTP. That is real
         *            and it matters - pain point P1.
         *
         *   Kept:    Order still knows about notifications, templates, the
         *            seller dashboard and analytics. Four consumers, four leaked
         *            facts. Deleting the Notification Service leaves this
         *            producer wrong, which is what message coupling was supposed
         *            to prevent.
         *
         *   Lost:    the compiler. Every one of those key names is now a string
         *            literal that no tool will ever check.
         *
         *   Added:   a broker to operate, at-least-once delivery to make
         *            idempotent, and a debugging story with no stack trace -
         *            straight into pain point P4's 4-hour root cause.
         *
         * Net: they paid the full price of asynchrony and bought roughly none of
         * the decoupling. This is the distributed monolith, and this file is how
         * teams get there while believing they are doing the right thing.
         */
    }
}

/* -------------------------------------------------------------------------- */

/**
 * And here is the consumer, guessing.
 *
 * `payload.get("orderId")` returns Object. It might be a String. It might be null
 * because someone renamed the field. It might be an Integer because a different
 * producer publishes to the same topic with a different idea of the schema.
 * The cast on the next line is a runtime coin flip.
 */
class Bad_NotificationConsumer {

    void onMessage(String topic, java.util.Map<String, Object> payload) {
        String orderId = (String) payload.get("orderId");        // ClassCastException waiting
        String email   = (String) payload.get("sendEmailTo");    // null if renamed upstream
        String template = (String) payload.get("emailTemplate");

        if (email == null) {
            // What is the right behaviour here? Nobody knows. So it gets logged
            // and swallowed, and the buyer never receives a confirmation.
            return;
        }

        // The consumer is a puppet: the producer decided the template, so this
        // service cannot change its own notification strategy without asking
        // the Order Service team for a release.
        sendEmail(email, template, orderId);
    }

    private void sendEmail(String to, String template, String orderId) { }
}

// ---- Supporting cast, elided ------------------------------------------------
interface MessageBus {
    void publish(String topic, java.util.Map<String, Object> payload);
}
