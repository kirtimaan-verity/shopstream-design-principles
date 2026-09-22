/*
 * ============================================================================
 * The counter-case: when merging duplicates makes the system worse
 * ============================================================================
 *
 * Walk this file immediately after Good_SingleSourceOfTruth.java. The room has
 * just been convinced that duplication is bad; this is the correction that stops
 * them over-applying it for the rest of the course.
 *
 * Below are two classes with IDENTICAL fields. Every instinct says merge them.
 * Merging them would be a mistake.
 */

/**
 * Where an order is shipped.
 *
 * Owned by: the commerce team.
 * Changes when: a new market has a different address format (Nigeria has no
 * postcodes in the sense the GCC uses), when the courier integration needs a
 * landmark field, when delivery instructions become a feature.
 */
record ShippingAddress(String line1, String line2, String city,
                       String postcode, String countryCode) {

    /** Behaviour that belongs to shipping and to nothing else. */
    boolean isDeliverable() {
        return line1 != null && !line1.isBlank() && city != null;
    }
}

/**
 * Where a seller is legally registered.
 *
 * Owned by: legal and finance.
 * Changes when: tax law changes, when a market requires a trade licence number
 * on the record, when a regulator demands a registered-agent field.
 *
 * Identical fields to ShippingAddress today. Completely different reasons to
 * change, completely different owners, completely different validation.
 */
record RegisteredAddress(String line1, String line2, String city,
                         String postcode, String countryCode) {

    /** Behaviour that belongs to compliance and to nothing else. */
    boolean isValidForTaxRegistration() {
        return postcode != null && countryCode != null
                && java.util.Set.of("AE", "SA", "EG", "JO", "PK", "NG").contains(countryCode);
    }
}

/*
 * ============================================================================
 * What merging them would actually cost
 * ============================================================================
 *
 * Suppose the team "removes the duplication" and creates one Address record used
 * by both. Then, in month four:
 *
 *   1. Nigeria launches. Nigerian addresses do not use postcodes the way the GCC
 *      does, so the commerce team makes `postcode` optional on Address.
 *
 *   2. That silently weakens isValidForTaxRegistration(), because seller tax
 *      registration genuinely does require a postcode. A shipping requirement
 *      has just changed a compliance rule. Nobody in legal was in the review,
 *      because the ticket said "make postcode optional for Nigeria".
 *
 *   3. Legal then needs a trade licence number on seller addresses. It goes on
 *      the shared Address, so now every buyer shipping address carries a nullable
 *      trade licence field that is meaningless for buyers, and every piece of
 *      shipping code has to ignore it.
 *
 *   4. Within a year the merged Address has ten fields, six of which are null in
 *      any given instance, and a comment explaining which combinations are
 *      legal. That comment is the documentation of an abstraction that should
 *      never have existed.
 *
 * THE DIAGNOSIS
 *
 * These two records were never duplicated KNOWLEDGE. They were coincidentally
 * similar STRUCTURE. DRY is about knowledge:
 *
 *      "Every piece of KNOWLEDGE must have a single, unambiguous,
 *       authoritative representation within a system."
 *
 * The VAT rate is knowledge: there is one true Saudi rate, and two copies can
 * disagree with reality. "An address has a line1 and a city" is not knowledge in
 * that sense. There is no fact of the matter that two copies could contradict.
 *
 * THE TEST, which is the same test as cohesion (folder 07) and SRP (folder 14):
 *
 *      Do these two things change for the SAME REASON, at the request of the
 *      SAME PEOPLE?
 *
 *      Yes -> one representation. The VAT rate: finance, tax law, one reason.
 *      No  -> leave them separate, however similar they look.
 *
 * WHY THE MISTAKE IS EXPENSIVE IN ONE DIRECTION ONLY
 *
 * Merging two things that should be separate creates coupling between two teams
 * who did not previously have to talk to each other. Unpicking it later means
 * finding every call site and deciding which of the two meanings it wanted, long
 * after the person who merged them has moved on.
 *
 * Leaving two similar things separate costs a little typing and, occasionally, a
 * bug fixed twice.
 *
 * The asymmetry is the argument, and it is the same asymmetry as in folder 10:
 * adding structure later is cheap, removing a wrong abstraction is not. That is
 * why "duplication is cheaper than the wrong abstraction" is good advice rather
 * than a licence to copy-paste - it is a claim about relative repair costs, not
 * about duplication being harmless.
 *
 * FOR DEBRIEF QUESTION 3 - the shared retry library across three repositories:
 *
 * The same test applies, and the answer is less comfortable. The retry MECHANISM
 * is genuinely shared knowledge, so a library is defensible. But a shared library
 * across three independently deployable services creates:
 *
 * - a version-upgrade coordination problem (all three must adopt a fix),
 * - a release-blocking dependency (the library team is now on the critical
 *     path for three services),
 * - and a tempting place for someone to add "just one" service-specific flag,
 *     which is how shared libraries become distributed god objects.
 *
 * For ShopStream, with one engineering team and a six-month deadline, a small
 * shared library is probably right. For three teams in three time zones, copying
 * forty lines of retry logic and accepting the drift is often the better trade.
 * The correct answer depends on the ORGANISATION, not on the code - which is
 * itself a useful thing for a room of architects to sit with.
 */
