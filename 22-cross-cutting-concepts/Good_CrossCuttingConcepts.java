/*
 * ============================================================================
 * GOOD: one decision per concern, applied at a boundary
 * ============================================================================
 *
 * Four concepts, each decided once and written down (see the README for the
 * one-page format), each applied through a mechanism rather than through
 * everyone remembering.
 *
 * The mechanism matters as much as the decision. A concept applied by
 * discipline lasts until the first deadline; a concept applied by a shared type
 * or a wrapper lasts because deviating from it is more work than complying.
 */

/*
 * ============================================================================
 * CONCEPT 1: ERROR HANDLING
 * ============================================================================
 *
 *   Decision   Domain failures are return values, using one shared result type.
 *              Infrastructure failures are exceptions, caught at the module
 *              boundary and translated. No exception crosses a module boundary.
 *   Applied by the shared Result type, plus one boundary class per module.
 *
 * Four services, one contract. A developer moving between modules can read a
 * signature and know what happens when the thing is not there - which is what
 * the Bad_ version's four conventions destroyed.
 */
sealed interface Result<T> permits Found, NotFound, Failed { }

record Found<T>(T value)            implements Result<T> { }
record NotFound<T>(String what)     implements Result<T> { }
record Failed<T>(String reason, String correlationId) implements Result<T> { }

interface Catalogue { Result<Listing> find(ProductId id); }
interface Orders    { Result<Order>   find(OrderId id); }
interface Payments  { Result<Payment> find(PaymentId id); }
interface Streams   { Result<LiveStream> find(StreamId id); }

/*
 * Because Result is sealed, a caller that forgets to handle one case does not
 * compile. The convention is enforced by the compiler rather than by review,
 * which is the difference between a concept and a wish.
 */

/*
 * ============================================================================
 * CONCEPT 2: LOGGING AND TRACING
 * ============================================================================
 *
 *   Decision   Structured records, never string concatenation. A correlation id
 *              is mandatory on every record and propagated across every
 *              boundary, including the message broker. A fixed deny-list of
 *              fields is never emitted.
 *   Applied by one AuditLog type; there is no other logger on the classpath.
 *
 * The deny-list is the logging-times-compliance intersection, handled in the
 * one place that can enforce it. In the Bad_ version a debug line put the whole
 * service into audit scope, and nothing structural prevented it.
 */
final class AuditLog {

    /*
     * Fields that must never be emitted, whatever a caller passes.
     *
     * Enforced at the emitter, not asked for at the call site. A rule that
     * depends on every caller remembering it is not a control - the same
     * argument as removing the database from the payment service in folder 03.
     */
    private static final java.util.Set<String> NEVER_EMIT =
            java.util.Set.of("pan", "card_number", "cvv", "expiry", "raw_body", "authorization");

    void record(String event, String correlationId, java.util.Map<String, Object> fields) {
        var safe = new java.util.HashMap<String, Object>();
        fields.forEach((k, v) -> {
            if (!NEVER_EMIT.contains(k.toLowerCase())) {
                safe.put(k, v);
            }
        });
        // emit(event, correlationId, safe) as one structured record
    }

    /*
     * Why the correlation id is a required parameter rather than an optional
     * one: an id that can be omitted will be omitted, and a trace with a gap in
     * it is not a trace. Making it part of the signature is the cheapest
     * enforcement available.
     *
     * This single decision is what turns the four-hour root cause into a grep.
     */
}

/*
 * ============================================================================
 * CONCEPT 3: CACHING, with its intersections written into the key
 * ============================================================================
 *
 *   Decision   Read-through caching, 60 second TTL for catalogue reads.
 *              THE KEY ALWAYS INCLUDES market, locale and the authorisation
 *              scope of the caller.
 *   Applied by one CacheKey type. Constructing a key without those parts is
 *              not possible, because the record requires them.
 *
 * This is the fix for both defects in the Bad_ version, and note how it works:
 * not by remembering to add market and locale, but by making a key that lacks
 * them inexpressible.
 */
record CacheKey(String resource, ProductId id, String market, String locale, String scope) {

    CacheKey {
        java.util.Objects.requireNonNull(market,  "market is part of every cache key");
        java.util.Objects.requireNonNull(locale,  "locale is part of every cache key");
        java.util.Objects.requireNonNull(scope,   "authorisation scope is part of every cache key");
    }

    /*
     * `scope` is the caching-times-security intersection made explicit. For a
     * public catalogue price it is "public". For a seller's negotiated tier
     * price it is that seller's tier, so one seller's price can never be served
     * to another.
     *
     * Neither of the two defects in the Bad_ version is findable by testing the
     * cache in isolation: both need two different users. Encoding the rule in
     * the key means the test is not the only line of defence.
     */
    static CacheKey publicPrice(ProductId id, String market, String locale) {
        return new CacheKey("price", id, market, locale, "public");
    }

    static CacheKey sellerPrice(ProductId id, String market, String locale, SellerId seller) {
        return new CacheKey("price", id, market, locale, "seller:" + seller.value());
    }
}

/*
 * ============================================================================
 * CONCEPT 4: CONFIGURATION AND SECRETS
 * ============================================================================
 *
 *   Decision   All configuration arrives through one typed object, built once
 *              at startup from the environment. No file paths in code, no
 *              literals, no environment names. Missing required values fail at
 *              startup, not at first use.
 *   Applied by this record being the only way to reach a setting.
 */
record PlatformConfig(String market, String checkoutComKey, String payTabsKey,
                      String catalogueDbUrl, java.time.Duration gatewayBudget) {

    static PlatformConfig fromEnvironment(java.util.function.Function<String, String> env) {
        return new PlatformConfig(
                require(env, "SHOPSTREAM_MARKET"),
                require(env, "CHECKOUT_COM_KEY"),
                require(env, "PAYTABS_KEY"),
                require(env, "CATALOGUE_DB_URL"),
                java.time.Duration.ofMillis(3_000));
    }

    /*
     * Fail at startup, loudly, naming the variable.
     *
     * The alternative - a null that surfaces as an authentication failure
     * against a payment gateway at peak - is the same class of defect as the
     * silent defaults in folder 18: a missing value guessed at rather than
     * refused.
     */
    private static String require(java.util.function.Function<String, String> env, String name) {
        String value = env.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required configuration missing: " + name);
        }
        return value;
    }
}

/*
 * ============================================================================
 * APPLYING THEM: at a boundary, not in every method
 * ============================================================================
 *
 * All four concepts land on this one class rather than being sprinkled through
 * the domain. Same decorator shape as folders 08 and 14: it implements the
 * interface it wraps, so it is inserted at wiring time and removed in one line.
 *
 * The business logic underneath knows nothing about logging, caching or
 * correlation ids, which is what keeps folder 08's separation intact while
 * still applying a system-wide decision.
 */
class CatalogueBoundary implements Catalogue {

    private final Catalogue delegate;
    private final Cache cache;
    private final AuditLog audit;
    private final RequestContext context;      // carries correlation id, market, locale, scope

    CatalogueBoundary(Catalogue delegate, Cache cache, AuditLog audit, RequestContext context) {
        this.delegate = delegate;
        this.cache = cache;
        this.audit = audit;
        this.context = context;
    }

    @Override
    public Result<Listing> find(ProductId id) {

        CacheKey key = new CacheKey("listing", id,
                context.market(), context.locale(), context.authorisationScope());

        Listing cached = cache.get(key);
        if (cached != null) {
            return new Found<>(cached);
        }

        try {
            Result<Listing> result = delegate.find(id);
            if (result instanceof Found<Listing> found) {
                cache.put(key, found.value(), java.time.Duration.ofSeconds(60));
            }
            audit.record("catalogue.find", context.correlationId(),
                    java.util.Map.of("product", id.value(), "hit", false));
            return result;

        } catch (RuntimeException infrastructureFailure) {
            /*
             * The error concept, applied at the boundary: an infrastructure
             * exception is caught here and becomes a domain value. It does not
             * cross the module boundary, so no caller anywhere needs to know
             * that this module talks to a database.
             */
            audit.record("catalogue.find.failed", context.correlationId(),
                    java.util.Map.of("product", id.value()));
            return new Failed<>("catalogue_unavailable", context.correlationId());
        }
    }
}

/*
 * ============================================================================
 * WHAT MAKES THIS SURVIVE
 * ============================================================================
 *
 * Every concept above is enforced by something other than good intentions:
 *
 *   Error handling   a sealed type, so an unhandled case does not compile
 *   Logging          one AuditLog on the classpath, deny-list at the emitter,
 *                    correlation id a required parameter
 *   Caching          a key record that cannot be built without market, locale
 *                    and scope
 *   Configuration    one typed object, missing values fail at startup
 *
 * None of these depend on anyone reading the concept document. That is the
 * test to apply to a cross-cutting concept: if the only thing stopping a
 * deviation is that someone might notice it in review, the concept will be gone
 * within two quarters - the same decay described at the end of folder 17.
 *
 * The document still matters. It records WHY, and the exceptions, and who owns
 * it. But the document is the explanation; the type is the control.
 */

// ---- Supporting cast, elided ------------------------------------------------
interface Cache { Listing get(CacheKey key); void put(CacheKey key, Listing value, java.time.Duration ttl); }
interface RequestContext {
    String correlationId(); String market(); String locale(); String authorisationScope();
}
class Listing { }
class Payment { }
class LiveStream { }
