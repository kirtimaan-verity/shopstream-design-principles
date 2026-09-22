/*
 * ============================================================================
 * AN EVENT THAT IS ACTUALLY AN EVENT
 * ============================================================================
 *
 * Two rules produced this file, and they are the whole lesson:
 *
 * RULE 1 - STATE A FACT, NOT AN INSTRUCTION.
 * Every field answers "what happened?", never "what should you do?"
 *
 * RULE 2 - MAKE THE SCHEMA A TYPE.
 * You gave up the compiler when you left method calls. Get as much of
 * it back as you can: a record, not a Map. A registry, not a string.
 */

/**
 * A fact: this order was placed. Past tense, immutable, addressed to nobody.
 *
 * Read the field list and note what is ABSENT: no email address, no template
 * name, no `alsoNotify...` flag, no consumer named anywhere. The Order Service
 * does not know that notifications exist. Delete the Notification Service and
 * this event is still correct - which is the test from the README, passed.
 */
record Good_OrderPlacedEvent(
        /** Schema version. Consumers check it; see the evolution rules below. */
        int schemaVersion,

        /** Identity of the fact, for idempotent consumers (delivery is at-least-once). */
        String eventId,
        java.time.Instant occurredAt,

        /**
         * Correlation id, propagated from the buyer's original HTTP request.
         *
         * This single field is what buys back the debuggability that asynchrony
         * costs you. Pain point P4 - 4 hours to root cause - happens when a
         * request crosses a broker and the stack trace stops. With a correlation
         * id on every event, ops greps one id and gets the whole causal chain
         * across all six services.
         *
         * Non-negotiable on every event. Not "nice to have."
         */
        String correlationId,

        /* ---- the fact itself ---------------------------------------------- */
        OrderId orderId,
        BuyerId buyerId,
        SellerId sellerId,
        StreamId streamId,
        java.util.List<OrderLine> lines,
        Money total
) {
    static final String TOPIC = "shopstream.orders.placed.v1";
    //     ^ A constant, not a string literal sprinkled across the codebase.
    //       The version is in the topic name so that a genuinely breaking change
    //       can run as a parallel topic during migration instead of a big bang.
}

/* -------------------------------------------------------------------------- */

/**
 * The producer. Compare it against the Bad_ version line by line.
 */
class Good_OrderPublisher {

    private final EventPublisher events;

    Good_OrderPublisher(EventPublisher events) {
        this.events = events;
    }

    void placeOrder(Order order, String correlationId) {
        // ... persist the order first; then announce it ...

        events.publish(Good_OrderPlacedEvent.TOPIC, new Good_OrderPlacedEvent(
                1,
                java.util.UUID.randomUUID().toString(),
                java.time.Instant.now(),
                correlationId,
                order.id(), order.buyer(), order.seller(), order.stream(),
                order.lines(), order.total()));

        /*
         * That is the entire method.
         *
         * The Order Service's knowledge of the rest of ShopStream is now exactly
         * one string constant and one record type. Adding the co-host
         * notification for the co-streaming feature, or a fraud-scoring
         * subscriber, or a Nigeria-specific tax reporter, changes NOTHING here.
         *
         * THIS is the property that "we use events" is supposed to buy, and the
         * Bad_ version did not have it.
         */
    }
}

/* -------------------------------------------------------------------------- */

/**
 * A consumer that owns its own decisions.
 *
 * The producer sent facts. This service decides - by itself, using its own
 * configuration - that a fact of this kind deserves an email, in which language,
 * from which template. Marketing can change the template without a checkout
 * release. The Pakistan launch can add SMS here without the Order Service
 * knowing.
 */
class Good_NotificationConsumer {

    private final BuyerDirectory buyers;      // this service looks the address up ITSELF
    private final TemplateCatalogue templates;
    private final ProcessedEvents seen;

    Good_NotificationConsumer(BuyerDirectory buyers, TemplateCatalogue templates, ProcessedEvents seen) {
        this.buyers = buyers;
        this.templates = templates;
        this.seen = seen;
    }

    void on(Good_OrderPlacedEvent event) {

        /*
         * IDEMPOTENCY IS THE CONSUMER'S JOB.
         *
         * Every real broker delivers at-least-once. Without this guard, a
         * redelivery during a broker failover sends the buyer a second
         * confirmation email - or, on a payment consumer, charges them twice.
         *
         * Note that this obligation did not exist with synchronous calls. It is
         * part of the price of message coupling, and it belongs on the slide next
         * to the benefits.
         */
        if (seen.alreadyHandled(event.eventId())) {
            return;
        }

        /*
         * The consumer resolves what it needs. The producer did not hand it an
         * email address, so this service asks the Buyer Directory.
         *
         * Note: BE HONEST ABOUT WHAT THIS COSTS - this is discussion question 1:
         *
         * We traded a leaked field for a runtime lookup. If the Buyer
         * Directory is down, this consumer cannot proceed; it must retry or
         * dead-letter. We removed coupling from the CONTRACT and added it to
         * the RUNTIME.
         *
         * Sometimes the leak is the better trade - if the buyer's email at the
         * moment of ordering is legally the right one to use (invoicing,
         * dispute records), then it is a FACT ABOUT THE ORDER and belongs on
         * the event after all.
         *
         * The deciding question is never "is this field convenient?" It is
         * "is this a fact about the thing that happened, or an instruction to
         * a consumer?" Buyer's email at time of order: a fact. Template name:
         * an instruction. Same-shaped fields, opposite answers.
         */
        var buyer = buyers.lookup(event.buyerId());

        var template = templates.select("order_confirmation", buyer.preferredLanguage());
        // ... send, then:
        seen.markHandled(event.eventId());
    }
}

/*
 * ============================================================================
 * SCHEMA EVOLUTION RULES - the discipline that replaces the compiler
 * ============================================================================
 *
 * You gave up build-time verification. These rules are what you put in its place,
 * and they belong in ShopStream's architecture documentation, not in tribal memory.
 *
 * SAFE (additive, no consumer changes needed):
 *
 *   + add an OPTIONAL field
 *   + add a new event type on a new topic
 *   + add a consumer
 *
 * BREAKING (needs a new topic version and a migration window):
 *
 *   - remove a field
 *   - rename a field
 *   - change a field's type or unit             <- the `total` double in Bad_
 *   - tighten a field from optional to required
 *   - change the MEANING of a field while keeping its name, which is the
 *     nastiest of the five because no tool can detect it and no test will fail
 *
 * ENFORCEMENT, because otherwise the rules above are decoration:
 *
 *   1. Schemas live in a registry (Avro/Protobuf/JSON Schema), versioned in git.
 *   2. CI fails the build on an incompatible schema change. This is how you get
 *      your compile-time check back: you rebuild it in the pipeline, on purpose.
 *   3. Consumer-driven contract tests. Each consumer publishes what it needs,
 *      and the producer's build verifies it still provides it.
 *   4. Consumers ignore unknown fields (Postel's law: be conservative in what you
 *      send, liberal in what you accept). Folder 18 covers where that second
 *      half stops being good advice, and how to tolerate without concealing.
 *
 * Rule 2 is the one teams skip, and it is the one that makes the difference
 * between message coupling as an architecture and message coupling as a hazard.
 */

// ---- Supporting cast, elided ------------------------------------------------
interface EventPublisher      { void publish(String topic, Object event); }
interface BuyerDirectory      { BuyerProfile lookup(BuyerId id); }
record   BuyerProfile         (BuyerId id, String email, String preferredLanguage) { }
interface TemplateCatalogue   { Object select(String kind, String language); }
interface ProcessedEvents     { boolean alreadyHandled(String eventId); void markHandled(String eventId); }
