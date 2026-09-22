/*
 * ============================================================================
 * BAD: every module invents its own answer to the same question
 * ============================================================================
 *
 * Nobody did anything unreasonable here. Four teams each solved error handling,
 * logging, caching and configuration in a way that was fine locally.
 *
 * The result is a system with no answers, only precedents - and an incident
 * response that starts with "which convention does this service use?"
 */

// ---------------------------------------------------------------------------
// CONCERN 1: ERROR HANDLING, answered four ways
// ---------------------------------------------------------------------------

class CatalogueService {
    /** Returns null when not found. Callers must remember to check. */
    Listing find(ProductId id) { return null; }
}

class OrderService {
    /** Throws a checked exception. Callers must catch or declare. */
    Order find(OrderId id) throws OrderNotFoundException { throw new OrderNotFoundException(); }
}

class PaymentService {
    /** Returns an Optional. */
    java.util.Optional<Payment> find(PaymentId id) { return java.util.Optional.empty(); }
}

class StreamService {
    /**
     * Returns a result object with a boolean and a message string.
     *
     * Four services, four contracts for the same question. A developer moving
     * between them writes `catalogueService.find(id).getTitle()` out of habit
     * and ships a null pointer exception, because in the service they came from
     * that call was safe.
     *
     * The cost is not any single convention being wrong. Any one of these four
     * would be fine if it were the only one.
     */
    Result<LiveStream> find(StreamId id) { return null; }
}

// ---------------------------------------------------------------------------
// CONCERN 2: LOGGING, with no shared format and no request identity
// ---------------------------------------------------------------------------

class CheckoutHandler {
    void handle(String orderId, String buyerId, String rawRequestBody) {

        // Team A: printf style, no timestamp, no identity
        System.out.println("Processing checkout for " + orderId);

        // Team B: a logger, but the message is unstructured prose
        log.info("checkout started, buyer=" + buyerId + " at " + System.currentTimeMillis());

        /*
         * Team C, during an incident: log the whole request "just until we find
         * the bug". It was never removed.
         *
         * This is the logging-times-compliance intersection, failed. The raw
         * body can carry cardholder data, so this one line puts the checkout
         * service into PCI-DSS audit scope and creates a finding that costs
         * assessor time every year.
         *
         * Note that no logging concept was violated, because there was none.
         */
        log.debug("full request: " + rawRequestBody);
    }

    /*
     * AND THE THING THAT IS MISSING FROM ALL THREE: a correlation id.
     *
     * There is no way to tie these lines to the same request, or to follow that
     * request into payments and out to the notification consumer. During the
     * National Day incident this is why root cause took four hours: the
     * evidence existed, in five formats, with nothing to join it on.
     */
    private static final Logger log = new Logger();
}

// ---------------------------------------------------------------------------
// CONCERN 3: CACHING, hand-rolled per service, with the intersections missed
// ---------------------------------------------------------------------------

class ProductCache {

    private final java.util.Map<String, Money> cache = new java.util.HashMap<>();

    Money priceOf(ProductId product) {
        /*
         * THE CACHE KEY IS THE PRODUCT ID, AND NOTHING ELSE.
         *
         * Two intersections missed at once, and both are the kind of defect that
         * looks like a caching bug and is actually a design omission:
         *
         *   CACHING x INTERNATIONALISATION. Prices differ by market. An Egyptian
         *   buyer warms the cache with EGP pricing, and the next buyer in the
         *   UAE is quoted it. Nothing throws; the number is simply wrong.
         *
         *   CACHING x SECURITY. Sellers with subscription tiers get negotiated
         *   prices. Cache one, and it is served to everybody until it expires.
         *   That is a data leak produced by a missing key component.
         *
         * A caching concept that does not state what is in the key is not a
         * concept. And notice neither of these is findable by testing caching in
         * isolation - both need two users.
         */
        return cache.get(product.value());
    }

    /*
     * Meanwhile the order service has its own cache, with a different eviction
     * policy, and the seller dashboard has a third with no eviction at all,
     * which is the source of a slow memory leak nobody has connected to it yet.
     */
}

// ---------------------------------------------------------------------------
// CONCERN 4: CONFIGURATION AND SECRETS, three sources
// ---------------------------------------------------------------------------

class PaymentConfig {
    // Team A: environment variable
    String gatewayKey  = System.getenv("CHECKOUT_KEY");

    // Team B: a properties file on disk, path hard-coded (folder 06)
    String payTabsKey  = readFile("/etc/shopstream/paytabs.properties");

    /*
     * Team C: a constant, added "temporarily" for a demo.
     *
     * It is in git history permanently, so rotating it means rotating a secret
     * that is in every clone of the repository. And because there is no
     * configuration concept, no reviewer had a rule to point at when this was
     * proposed - only a personal objection, which is much easier to overrule
     * under deadline.
     *
     * That is the strongest argument for writing concepts down: a written
     * concept makes the objection impersonal.
     */
    String sandboxKey  = "sk_test_4eC39HqLyjWDarjtT1zdp7dc";

    private String readFile(String path) { return ""; }
}

// ---- Supporting cast, elided ------------------------------------------------
class Logger { void info(String m) { } void debug(String m) { } }
class OrderNotFoundException extends Exception { }
class Result<T> { boolean ok; String message; T value; }
class Listing { String getTitle() { return ""; } }
class Payment { }
class LiveStream { }
