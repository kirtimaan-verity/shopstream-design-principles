/*
 * ============================================================================
 * GOOD: strict about meaning, liberal about additions, silent about nothing
 * ============================================================================
 *
 * The same webhook, with the two halves of Postel's law applied to the right
 * things:
 *
 *     STRICT   about every field this handler must UNDERSTAND to act:
 *              signature, amount, currency, status, identity.
 *
 *     LIBERAL  about fields it does not need: unknown keys are ignored, so the
 *              vendor can add `settlement_batch_id` on a Tuesday without
 *              ShopStream shipping a release.
 *
 *     LOUD     about everything tolerated or rejected: a counter, a reason and
 *              a replayable copy. Tolerance without a metric is how the Bad_
 *              version's leniencies survived for years.
 */
class Good_RobustBoundary {

    private final Orders orders;
    private final DeadLetterQueue rejected;
    private final Metrics metrics;
    private final SignatureVerifier signatures;

    Good_RobustBoundary(Orders orders, DeadLetterQueue rejected,
                        Metrics metrics, SignatureVerifier signatures) {
        this.orders = orders;
        this.rejected = rejected;
        this.metrics = metrics;
        this.signatures = signatures;
    }

    void handle(String rawBody, String signatureHeader, String correlationId) {

        /*
         * STEP 1: AUTHENTICATE BEFORE PARSING ANYTHING
         *
         * Note the order. The signature is checked against the RAW BODY before a
         * single field is read, because parsing untrusted input is itself an
         * attack surface and there is no reason to do it for a message that is
         * not going to be trusted anyway.
         *
         * There is no `!= null` here. A missing signature is not a special case
         * to be accommodated, it is the most suspicious message you will receive
         * all day. The sandbox problem that motivated the Bad_ version's
         * leniency is solved by configuring a sandbox key, not by making the
         * check optional in production code.
         */
        if (!signatures.isValid(rawBody, signatureHeader)) {
            reject(rawBody, correlationId, "signature_invalid_or_missing");
            return;
        }

        Json body;
        try {
            body = Json.parse(rawBody);
        } catch (RuntimeException malformed) {
            reject(rawBody, correlationId, "unparseable_body");
            return;
        }

        /*
         * STEP 2: REQUIRED FIELDS, STRICTLY TYPED, NO DEFAULTS
         *
         * Every one of these five is a field the handler must understand in
         * order to act correctly. So every one of them is required, read at
         * exactly one type, and rejected rather than coerced.
         *
         * `require` throws on absent-or-wrong-type. There is no fallback branch,
         * no "if it is a string then...", no default constant. That is the whole
         * difference from the Bad_ version, and it is roughly thirty fewer lines.
         */
        try {
            OrderId order = new OrderId(body.requireString("order_id"));

            // Money as minor units, integer, exact. Not a double, not a string
            // with a symbol stripped out of it. If the vendor sends the wrong
            // type, that is a vendor bug we want to find out about today.
            long minorUnits = body.requireLong("amount_minor_units");

            // Required. No default. The Bad_ version defaulted to AED and turned
            // the Egypt launch into a silent twenty-fold reconciliation error.
            String currency = body.requireString("currency");

            // Required, and validated against a CLOSED set - see below.
            GatewayStatus status = GatewayStatus.parse(body.requireString("status"));

            // Vendor's idempotency key. Webhooks are delivered at least once.
            String eventId = body.requireString("event_id");

            /*
             * STEP 3: LIBERAL WHERE IT IS SAFE
             *
             * Everything not read above is ignored, deliberately.
             *
             * This is the half of Postel's law that genuinely works, and it is
             * what forward compatibility means in practice: the vendor can add
             * fields, and consumers keep working without a coordinated release.
             * Folder 04 lists the same rule from the producer's side of a
             * message contract.
             *
             * But ignoring is not the same as not noticing. Unknown fields are
             * counted, by name. When `settlement_batch_id` starts appearing in
             * every payload, someone can see it on a dashboard and decide
             * whether ShopStream should be reading it - rather than discovering
             * a year later that a field everybody ignored had been carrying
             * meaning the whole time. That is discussion question 3, and this
             * counter is the answer to it.
             */
            for (String unknown : body.fieldsOtherThan(
                    "order_id", "amount_minor_units", "currency", "status", "event_id")) {
                metrics.count("webhook.unknown_field", unknown);
            }

            if (status == GatewayStatus.SETTLED) {
                orders.markPaid(order, new Money(minorUnits, currency), eventId);
            } else {
                orders.markUnsettled(order, status, eventId);
            }

        } catch (FieldMissingOrWrongType e) {
            /*
             * STEP 4: REJECT LOUDLY AND REPLAYABLY
             *
             * Compare with the Bad_ version's `log.debug` and carry on.
             *
             * The message goes to a dead-letter queue with its correlation id
             * and a machine-readable reason. Three properties the swallow did
             * not have:
             *
             *   - It is COUNTED, so a webhook failing every time looks different
             *     from a webhook that never arrived.
             *   - It is REPLAYABLE, so once the cause is fixed the backlog can
             *     be processed rather than reconstructed from the vendor's logs.
             *   - It is ATTRIBUTABLE, because the reason names the field.
             *
             * And crucially: the vendor finds out. That closes the feedback loop
             * the Bad_ version cut, which is what stops a vendor bug from
             * becoming a permanent feature of the contract.
             */
            reject(rawBody, correlationId, e.reason());
        }
    }

    private void reject(String rawBody, String correlationId, String reason) {
        metrics.count("webhook.rejected", reason);
        rejected.store(rawBody, correlationId, reason);
    }
}

/*
 * ============================================================================
 * Why unknown FIELDS are ignored but unknown STATUS VALUES are rejected
 * ============================================================================
 *
 * This is discussion question 2, and the answer is the sharpest form of the
 * principle in this folder.
 *
 * Both are "something I do not recognise". They get opposite treatment because
 * of what happens if you are wrong about them:
 *
 *   AN UNKNOWN FIELD is additive. Ignoring it leaves this handler doing exactly
 *   what it did yesterday. The worst case is that ShopStream is late to use a
 *   new capability - a missed opportunity, not a defect.
 *
 *   AN UNKNOWN STATUS is a claim about THE OUTCOME - the one thing this handler
 *   exists to interpret. Ignoring it, or mapping it to a default, means acting
 *   on a meaning you do not have. The Bad_ version treated everything that was
 *   not "failed" as success, so when the vendor added "pending_review" for
 *   fraud holds, every held payment settled immediately.
 *
 * The general rule, worth putting on a slide:
 *
 *     Be liberal where being wrong means DOING NOTHING.
 *     Be strict where being wrong means DOING THE WRONG THING.
 *
 * A closed enum is how that gets enforced. It cannot silently absorb a value it
 * was not designed for.
 */
enum GatewayStatus {
    SETTLED, DECLINED, FAILED, PENDING_REVIEW, REVERSED;

    static GatewayStatus parse(String raw) {
        return switch (raw) {
            case "settled", "captured"  -> SETTLED;
            case "declined"             -> DECLINED;
            case "failed"               -> FAILED;
            case "pending_review"       -> PENDING_REVIEW;
            case "reversed", "refunded" -> REVERSED;
            /*
             * No default that guesses. An unrecognised status is a rejection to
             * the dead-letter queue and an alert, because it means the vendor
             * has introduced a concept ShopStream has not yet been told about -
             * which is exactly when a human should look, and exactly when the
             * Bad_ version was at its most confident.
             */
            default -> throw new FieldMissingOrWrongType("unknown_status:" + raw);
        };
    }
}

/*
 * ============================================================================
 * The other half: be conservative in what you send
 * ============================================================================
 *
 * The half that gets forgotten, because nothing breaks immediately when you get
 * it wrong. Your output is somebody else's untrusted input, and it will be
 * consumed by parsers you will never see.
 */
class Good_ConservativeSender {

    Json orderConfirmation(Order order) {
        return Json.object()
                /*
                 * ONE canonical representation for each concept, matching the
                 * published contract exactly.
                 *
                 * Not "amount" sometimes a number and sometimes a string. Not a
                 * currency symbol embedded in a value. Not a timestamp without a
                 * zone. Every one of those, on the receiving side, is what
                 * produced the Bad_ version's three-format amount parser -
                 * somebody's conservative-sender failure became somebody else's
                 * permanent leniency.
                 */
                .put("order_id", order.id().value())
                .put("amount_minor_units", order.total().minorUnits())
                .put("currency", order.total().currency())
                .put("occurred_at", java.time.Instant.now().toString())   // ISO-8601, UTC

                /*
                 * Explicit version. It costs one field and it is the difference
                 * between "we can change this" and "we can never change this".
                 */
                .put("schema_version", 1);

        /*
         * WHAT CONSERVATIVE SENDING RULES OUT, and why each one matters:
         *
         *   - Do not omit a field because it happens to be empty or zero. The
         *     receiver cannot tell "absent" from "zero" and will guess. See
         *     leniency 4 in the Bad_ file for how that ends.
         *
         *   - Do not send a field whose type varies by case. One key, one type,
         *     always.
         *
         *   - Do not rely on the receiver being forgiving. Their tolerance is
         *     not part of your contract, it is a bug in their code that you are
         *     depending on, and it will be fixed without telling you.
         *
         *   - Do not quietly change a field's MEANING while keeping its name.
         *     No parser on earth catches that, and no test will fail. Folder 04
         *     lists it as the nastiest of the breaking changes for the same
         *     reason.
         */
    }
}

/*
 * ============================================================================
 * The modern caveat, stated plainly
 * ============================================================================
 *
 * "Be liberal in what you accept" was written for a network of a few hundred
 * hosts where getting anything to interoperate at all was the hard problem. At
 * internet scale its second-order effects became visible, and the IETF has since
 * revisited it (RFC 9413, Maintaining Robust Protocols, 2023).
 *
 * The failure mode is not dramatic, which is why it took decades to name:
 *
 *   1. A producer emits something slightly wrong.
 *   2. A tolerant consumer repairs it silently.
 *   3. Nobody is told, so nobody fixes it.
 *   4. The malformed form spreads, because it works.
 *   5. The written contract is now fiction. The real contract is "whatever the
 *      dominant implementation happens to tolerate", which is not written down
 *      anywhere and cannot be implemented from a specification.
 *
 * Step 3 is the only one you control, and it is cheap. This file's `metrics` and
 * dead-letter queue exist precisely to break that chain: ShopStream still
 * tolerates what it can safely tolerate, and always knows that it did.
 *
 * Which is the whole folder in one sentence:
 *
 *     Tolerate what you can safely ignore, reject what you must understand,
 *     and report both.
 */

// ---- Supporting cast, elided ------------------------------------------------
interface Orders            { void markPaid(OrderId order, Money amount, String eventId);
                              void markUnsettled(OrderId order, GatewayStatus status, String eventId); }
interface DeadLetterQueue   { void store(String rawBody, String correlationId, String reason); }
interface Metrics           { void count(String metric, String tag); }
interface SignatureVerifier { boolean isValid(String rawBody, String signatureHeader); }

class FieldMissingOrWrongType extends RuntimeException {
    private final String reason;
    FieldMissingOrWrongType(String reason) { super(reason); this.reason = reason; }
    String reason() { return reason; }
}

interface Json {
    static Json parse(String raw) { return null; }
    static Json object() { return null; }
    String requireString(String field);
    long requireLong(String field);
    java.util.List<String> fieldsOtherThan(String... known);
    Json put(String field, Object value);
}
