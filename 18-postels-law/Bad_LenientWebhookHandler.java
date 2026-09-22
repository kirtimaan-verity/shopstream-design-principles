/*
 * ============================================================================
 * BAD: liberal about everything, including the things that decide who gets paid
 * ============================================================================
 *
 * The payment gateway webhook. It arrives from outside the trust boundary, from
 * a vendor whose payload changes without notice, and what it says determines
 * whether an order is treated as paid.
 *
 * Read the leniencies below and note that NOT ONE OF THEM WAS UNREASONABLE AT
 * THE TIME. Each was added during an incident, by someone under pressure, to
 * stop webhooks failing. That is exactly how this file gets written, and it is
 * why "be liberal in what you accept" is dangerous advice when handed to a team
 * without its qualifiers.
 */
class Bad_LenientWebhookHandler {

    private final Orders orders;

    Bad_LenientWebhookHandler(Orders orders) {
        this.orders = orders;
    }

    void handle(java.util.Map<String, Object> payload, String signatureHeader) {

        /*
         * LENIENCY 1: THE SIGNATURE IS CHECKED ONLY WHEN PRESENT
         *
         * Added because the vendor's sandbox did not send signatures and the
         * integration tests were failing.
         *
         * What it means in production: anybody who can reach this endpoint and
         * OMITS the signature header is trusted completely. The check is not a
         * check, it is an opt-in. An attacker does not need to forge a
         * signature; they need to not send one.
         *
         * This is the canonical example of why authentication must never be on
         * the liberal side of Postel's law. Absence of a credential is a
         * rejection, never a default.
         */
        if (signatureHeader != null && !verify(payload, signatureHeader)) {
            return;
        }

        try {
            String orderId = String.valueOf(payload.get("order_id"));

            /*
             * LENIENCY 2: THE AMOUNT IS ACCEPTED IN THREE FORMATS
             *
             * Added because the vendor changed from a JSON number to a string
             * in a minor release, then added a currency symbol for one market.
             * Rather than raising it with the vendor, the parser grew.
             *
             * Three problems, in increasing severity:
             *
             *   - The vendor's bug is now permanent. They were never told, so
             *     they never fixed it, and now all three formats are in
             *     production traffic forever. This is the "bake the bug in"
             *     failure: leniency removed the feedback that would have fixed
             *     the root cause.
             *
             *   - "AED 280.00" parses to 280.00. So does "280.00". So does
             *     "$280.00", which is 280 US dollars, roughly four times the
             *     value. The currency symbol is stripped and discarded, so the
             *     two are indistinguishable by the time the value is used.
             *
             *   - The double. Folder 00 explains why money is never a double;
             *     here it also silently truncates anything the vendor sends with
             *     three decimal places, which some currencies use.
             */
            Object rawAmount = payload.get("amount");
            double amount;
            if (rawAmount instanceof Number n) {
                amount = n.doubleValue();
            } else {
                String s = String.valueOf(rawAmount).replaceAll("[^0-9.]", "");
                amount = s.isEmpty() ? 0.0 : Double.parseDouble(s);
                //       ^^^^^^^^^^^^^^^^
                // An unparseable amount becomes ZERO and processing continues.
                // A zero-amount settlement is not a smaller problem than a
                // crash; it is a much larger one, because nothing reports it.
            }

            /*
             * LENIENCY 3: THE CURRENCY IS GUESSED
             *
             * Added when the vendor omitted the field for one market. AED was
             * chosen as the default because it was the busiest market at the
             * time.
             *
             * The Egypt launch is now a silent financial defect: EGP amounts
             * arrive without a currency field, get labelled AED, and are
             * reconciled at roughly twenty times their value. Nothing throws.
             * Nothing is logged. The dashboards look healthy, which is the
             * signature of this whole class of bug.
             */
            String currency = payload.containsKey("currency")
                    ? String.valueOf(payload.get("currency"))
                    : "AED";

            /*
             * LENIENCY 4: A MISSING STATUS MEANS SUCCESS
             *
             * The worst line in the file.
             *
             * It was added because a webhook retry occasionally arrived without
             * the field and orders were getting stuck in PENDING, generating
             * support tickets. Defaulting to "success" made the tickets stop.
             *
             * It also means that ANY payload that reaches this handler without a
             * status field settles an order. A truncated body, a partial write,
             * a malformed retry, a probe from a scanner - all of them mark an
             * order paid.
             *
             * This is the reconciliation gap in the brief, in one line. The
             * defect does not throw, does not log, does not fail a test, and is
             * invisible until finance cannot make the numbers agree weeks later.
             *
             * The rule it breaks: never be liberal about a field whose value IS
             * the decision. Absence of an outcome is not an outcome.
             */
            String status = payload.containsKey("status")
                    ? String.valueOf(payload.get("status"))
                    : "success";

            /*
             * LENIENCY 5: UNRECOGNISED STATUS VALUES FALL THROUGH TO SUCCESS
             *
             * Anything that is not literally "failed" or "declined" is treated
             * as payment received. When the vendor introduces "pending_review"
             * for its fraud-hold flow, every held payment is settled
             * immediately - which is precisely the case the hold exists for.
             */
            if (!status.equals("failed") && !status.equals("declined")) {
                orders.markPaid(orderId, amount, currency);
            }

        } catch (Exception e) {
            /*
             * LENIENCY 6: EVERYTHING ELSE IS SWALLOWED
             *
             * Added because a malformed payload was crashing the endpoint and
             * the vendor was retrying it in a loop.
             *
             * Suppressing the crash was correct. Suppressing the INFORMATION was
             * not. Logging at debug means it is off in production. There is no
             * counter, no alert, and no copy of the payload, so a webhook that
             * fails every time is indistinguishable from one that never arrived.
             *
             * Postel's law says tolerate. It does not say conceal. Every
             * deviation you accept has to be counted somewhere, or leniency
             * becomes an outage you cannot see.
             */
            log.debug("Could not process webhook", e);
        }
    }

    /*
     * ============================================================================
     * AND THE INVERSION THAT MAKES IT WORSE
     * ============================================================================
     *
     * The one place this handler is strict is the one place it should be liberal.
     *
     * Elsewhere in the codebase, the schema validator rejects any payload
     * containing a field it does not recognise. So when the vendor adds a
     * harmless `settlement_batch_id`, every webhook fails until ShopStream ships
     * a release.
     *
     * Line these two up and the pattern is clear:
     *
     *     Money and status ...... maximally liberal   (should be strict)
     *     Unknown extra fields .. maximally strict    (should be liberal)
     *
     * Both halves of Postel's law applied to exactly the wrong things. This is
     * common, and it happens for a structural reason: the leniencies are decided
     * one at a time during incidents, and the strictness is decided once, up
     * front, by a schema tool nobody revisits. Neither decision is ever made
     * against the contract as a whole.
     */

    private boolean verify(java.util.Map<String, Object> payload, String signature) { return true; }
    private static final Logger log = new Logger();
}

// ---- Supporting cast, elided ------------------------------------------------
interface Orders { void markPaid(String orderId, double amount, String currency); }
class Logger     { void debug(String message, Throwable t) { } }
