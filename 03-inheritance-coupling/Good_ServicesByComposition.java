/*
 * ============================================================================
 * COMPOSITION - each service holds exactly what it needs
 * ============================================================================
 *
 * The same four capabilities (database, logging, auth, metrics) are still shared.
 * DRY is still satisfied - `AuditLog` is written once, not three times. What
 * changed is the MECHANISM: `has-a` instead of `is-a`.
 *
 * Run the comparison in class as a table:
 *
 *   BaseService.logInfo() changes
 *       Bad:   3 services affected, silently
 *       Good:  only the services holding an AuditLog, and it is visible in
 *              their constructors
 *
 *   Payment must not touch the DB
 *       Bad:   a code-review rule
 *       Good:  a compile error
 *
 *   Test ProductService
 *       Bad:   boots a DB pool, an auth context and a metrics registry
 *       Good:  pass two fakes to the constructor
 */

class Good_ProductService {

    /*
     * Product needs a catalogue store and a log. It is given a catalogue store
     * and a log. Nothing else. The constructor IS the dependency documentation -
     * you can read this class's entire coupling in three lines, which you could
     * not do when half of it was inherited.
     */
    private final CatalogueStore catalogue;
    private final AuditLog audit;

    Good_ProductService(CatalogueStore catalogue, AuditLog audit) {
        this.catalogue = catalogue;
        this.audit = audit;
    }

    java.util.List<ProductId> search(String query) {
        audit.record("catalogue.search", query);
        return catalogue.findByName(query);
    }

    /*
     * PCI masking is NOT applied here, and that is deliberate. The catalogue
     * search path never sees cardholder data, so it does not pay for masking.
     * The 12-second search regression from the Bad_ version simply cannot occur:
     * there is no shared parent through which a compliance change can arrive
     * uninvited.
     */
}

/* -------------------------------------------------------------------------- */

class Good_OrderService {

    private final Orders orders;
    private final AuditLog audit;

    Good_OrderService(Orders orders, AuditLog audit) {
        this.orders = orders;
        this.audit = audit;
    }

    void createOrder(Order order) {
        audit.record("order.created", order.id().value());
        orders.save(order);
    }
}

/* -------------------------------------------------------------------------- */

class Good_PaymentService {

    /*
     * ---- THE COMPLIANCE CONSTRAINT, ENFORCED BY THE COMPILER --------------
     *
     * Read the field list. There is no database here.
     *
     * Not "there is a database that we agreed not to use." Not "there is a
     * database guarded by a code-review checklist." There is no field, no
     * inherited accessor, and no way to reach the shared schema from inside this
     * class. A developer who tries to persist cardholder data does not get a
     * failed audit six months later - they get a compile error this afternoon.
     *
     * This is the single most valuable thing composition bought us, and it is
     * worth pausing on: we did not add a control, we REMOVED a capability. Those
     * are very different security postures, and only one of them survives staff
     * turnover.
     */
    private final PciVault vault;        // tokenised card data only, PCI-scoped
    private final AuditLog audit;        // masking configured for THIS service only

    Good_PaymentService(PciVault vault, AuditLog audit) {
        this.vault = vault;
        this.audit = audit;
    }

    PaymentResult authorise(OrderId order, Money amount) {
        audit.record("payment.authorise", order.value());
        return vault.charge(order, amount);
    }

    /*
     * There is no `save(Object)` here to override, so there is no LSP violation
     * to commit. The Bad_ version had to break a promise it never wanted to make.
     * This version was never asked to make it.
     *
     * General form of the lesson: most LSP violations are not bad subclasses.
     * They are inheritance hierarchies that should not have existed.
     */
}

/*
 * ============================================================================
 * THE SHARED CAPABILITIES - reused by composition, configured per user
 * ============================================================================
 */

/**
 * The logging capability that used to live in `BaseService.logInfo()`.
 *
 * Written once (DRY is satisfied), but now each service is handed an instance
 * CONFIGURED FOR ITSELF. Payment gets one with PCI masking on. Product gets one
 * with masking off, because Product has no cardholder data and no CPU to spare on
 * the search path.
 *
 * With inheritance that variation was impossible without a flag on the base
 * class - and a flag on a base class is a switch statement waiting to happen
 * (folder 15).
 */
class AuditLog {
    private final boolean maskSensitiveFields;

    AuditLog(boolean maskSensitiveFields) {
        this.maskSensitiveFields = maskSensitiveFields;
    }

    void record(String event, String detail) {
        String safe = maskSensitiveFields ? PciMasker.maskCardNumbers(detail) : detail;
        // ... emit with a correlation id, which is how P4's 4-hour root cause
        //     becomes a 4-minute one. See folder 08 for why tracing is a
        //     cross-cutting concern rather than a base-class method.
    }
}

/** Narrow, purpose-named store. Not a general `PostgresSession` handed to everyone. */
interface CatalogueStore {
    java.util.List<ProductId> findByName(String query);
}

/** The PCI-scoped boundary. Cardholder data exists behind here and nowhere else. */
interface PciVault {
    PaymentResult charge(OrderId order, Money amount);
}

/*
 * ============================================================================
 * BEING FAIR TO INHERITANCE: when `extends` is the RIGHT answer
 * ============================================================================
 *
 * Do not let the room leave believing inheritance is always wrong. The rule is
 * "inherit to be substitutable, compose to reuse", and this is the substitutable
 * case:
 *
 *     sealed interface PaymentOutcome permits Authorised, Declined, GatewayTimeout { }
 *
 *     record Authorised(PaymentId id, String maskedCard)   implements PaymentOutcome { }
 *     record Declined(String reason)                       implements PaymentOutcome { }
 *     record GatewayTimeout(java.time.Duration waited)     implements PaymentOutcome { }
 *
 * Why this is legitimate inheritance:
 *
 *   - It genuinely means "is-a-kind-of". A decline IS a payment outcome.
 *   - `sealed` CLOSES the hierarchy. There is no fragile base class problem,
 *     because there are no unknown subclasses in other repositories.
 *   - There is no behaviour to inherit: no protected state, no template method,
 *     nothing a subclass can break.
 *   - Substitutability is total. Every outcome can be used wherever an outcome
 *     is expected, with no method that throws UnsupportedOperationException.
 *   - The compiler enforces exhaustive handling, so adding a fourth outcome
 *     produces errors at every call site instead of a silent default branch.
 *
 * Contrast that with `Bad_PaymentService extends BaseService`, which meant
 * "is-a-kind-of" nothing at all - it meant "please give me your fields."
 */
