/*
 * ============================================================================
 * INHERITANCE USED FOR REUSE
 * ============================================================================
 *
 * The origin story is always the same and always reasonable:
 *
 * "Product, Order and Payment all need a DB session, a logger, auth and
 * metrics. Let's pull that into a base class so we don't repeat ourselves."
 *
 * That sentence contains a real principle (DRY, folder 11) applied through the
 * wrong mechanism. What was needed: three services each HOLDING the collaborators
 * they need. What was built: three services each BEING a BaseService.
 */
abstract class BaseService {

    /*
     * `protected` is the giveaway. It means "part of my contract with my
     * subclasses" - a second public API, with all the compatibility obligations
     * of a public API and none of the visibility. You cannot change the TYPE of
     * this field without breaking every subclass, including ones in other repos.
     */
    protected PostgresSession db;
    protected Logger log;
    protected AuthContext auth;
    protected MetricsRegistry metrics;

    protected BaseService() {
        // Every subclass now boots a DB pool, a metrics registry and an auth
        // context - in production, in tests, and in a one-line CLI script.
        this.db = PostgresSession.open("jdbc:postgresql://10.0.3.14/shopstream");
        this.log = Logger.forService(getClass().getSimpleName());
        this.auth = AuthContext.current();
        this.metrics = MetricsRegistry.global();
    }

    /** Every subclass inherits this. Including the ones that must not have it. */
    protected PostgresSession getDbSession() {
        return db;
    }

    /**
     * ---- THE FRAGILE BASE CLASS, IN ONE METHOD ---------------------------
     *
     * Compliance asks for PCI-DSS field masking. The obvious place is "the base
     * class, so every service gets it." One line, ships Friday.
     *
     * What actually happens:
     *
     * - PRODUCT SERVICE now runs a regex over every log line on the catalogue
     *   search path. Search was already degrading to 12 seconds under peak
     *   (pain point P2). It just got slower, and the change is invisible in the
     *   Product Service's own diff - a Product engineer bisecting the slowdown
     *   will never look in this file.
     *
     * - ORDER SERVICE logs order ids that match the card-number pattern by
     *   coincidence. They are now masked. Ops loses the ability to grep for an
     *   order id during an incident (pain point P4, 4-hour root cause).
     *
     * - Neither of those services changed. Neither team was consulted. Neither
     *   test suite covers it.
     *
     * That is fragile base class: a locally correct change with non-local,
     * invisible, cross-team consequences.
     */
    protected void logInfo(String message) {
        log.info(PciMasker.maskCardNumbers(message));
        metrics.increment("log.info");
    }

    /** Subclasses may override. Some do. That is where it gets interesting. */
    protected void save(Object entity) {
        db.persist(entity);
    }
}

/* -------------------------------------------------------------------------- */

class Bad_ProductService extends BaseService {

    /*
     * Product uses `db` and `log`. It has no use for `auth` (the catalogue is
     * public) or for `metrics` at this granularity - but it pays for both at
     * construction and carries both in its API surface forever.
     *
     * It also inherits `save(Object)`, which means any caller holding a
     * Bad_ProductService can persist an arbitrary object into the shared schema.
     * That is not an API anybody designed. It is an API that leaked downward.
     */
    java.util.List<ProductId> search(String query) {
        logInfo("search: " + query);
        return getDbSession().query("SELECT id FROM products WHERE name ILIKE ?", query);
    }
}

class Bad_OrderService extends BaseService {

    void createOrder(OrderId id, Money total) {
        logInfo("creating order " + id.value());
        save(id);
    }
}

class Bad_PaymentService extends BaseService {

    /*
     * ---- INHERITING WHAT YOU ARE FORBIDDEN TO HAVE -----------------------
     *
     * PCI-DSS Level 1 says cardholder data must not reach the shared application
     * schema. This class inherits `getDbSession()`, pointing at exactly that
     * schema. There is no language mechanism preventing the next developer from
     * writing:
     *
     * getDbSession().persist(cardDetails);   // compiles, ships, fails audit
     *
     * The ONLY control is a human code review that must never have a bad day.
     * Compare with the composition version, where Payment is simply never given
     * a database and the line above does not compile.
     *
     * State the principle plainly in class: a compliance constraint enforced by
     * vigilance is not enforced. A compliance constraint enforced by the type
     * system is.
     */

    /**
     * ---- AND AN LSP VIOLATION, BECAUSE THE PARENT PROMISED TOO MUCH -------
     *
     * `BaseService.save()` promises "this persists the entity." Payment cannot
     * keep that promise, so it breaks it loudly.
     *
     * Now any code written against `BaseService` - a generic retry wrapper, a
     * batch importer, an admin tool - will throw when handed a payment service,
     * at runtime, having compiled cleanly. The subtype is NOT substitutable for
     * the supertype. See folder 13.
     */
    @Override
    protected void save(Object entity) {
        throw new UnsupportedOperationException("Payment data must not enter the shared schema");
    }
}

// ---- Supporting cast, elided ------------------------------------------------
class PostgresSession   { static PostgresSession open(String url) { return new PostgresSession(); }
                          <T> java.util.List<T> query(String sql, Object... args) { return java.util.List.of(); }
                          void persist(Object entity) { } }
class Logger            { static Logger forService(String n) { return new Logger(); }
                          void info(String m) { } }
class AuthContext       { static AuthContext current() { return new AuthContext(); } }
class MetricsRegistry   { static MetricsRegistry global() { return new MetricsRegistry(); }
                          void increment(String key) { } }
class PciMasker         { static String maskCardNumbers(String s) { return s; } }
