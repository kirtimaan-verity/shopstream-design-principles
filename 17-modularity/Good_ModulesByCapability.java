/*
 * ============================================================================
 * GOOD: cut by business capability, with the interior actually hidden
 * ============================================================================
 *
 *     shopstream/
 *       catalogue/
 *         Catalogue.java              PUBLIC  - the whole contract, one type
 *         CatalogueModule.java        PUBLIC  - the factory that builds it
 *         internal/                           - package-private, nobody's business
 *           CatalogueService, ProductRepository, ProductRow, PricingRules
 *       orders/
 *         Orders.java                 PUBLIC
 *         OrderPlaced.java            PUBLIC  - the event it publishes
 *         internal/ ...
 *       payments/
 *         Payments.java               PUBLIC
 *         internal/ ...
 *       streaming/
 *         Streams.java                PUBLIC
 *         internal/ ...
 *       platform/                             - genuinely shared, deliberately tiny
 *         Money, OrderId, ProductId, BuyerId, SellerId
 *
 * Same six classes per capability as the Bad_ version. Same layering - controller,
 * service, repository - except it now lives INSIDE each module rather than being
 * the top-level cut.
 *
 * Re-run the ticket from the Bad_ file:
 *
 *     "Sellers want to set a per-stream discount on a product."
 *
 *       catalogue/internal/PricingRules      the rule
 *       catalogue/Catalogue                  one method on the contract, if the
 *                                            outside world needs to see the price
 *
 * One module. One owner. One review. Compare with six files across four packages.
 */

/*
 * ============================================================================
 * A module's published contract: one interface, and it is small on purpose
 * ============================================================================
 */

// package: shopstream.catalogue          (PUBLIC - the only type others may use)
public interface Catalogue {

    /*
     * Everything the rest of ShopStream is allowed to ask of the catalogue.
     * Four methods.
     *
     * Note what is NOT here: no ProductRow, no ProductRepository, no
     * PricingRules, no SQL, no "give me the product entity so I can read its
     * fields". Those exist, and they are package-private in catalogue.internal,
     * which means the compiler will not let orders/ or payments/ touch them.
     *
     * That is the difference between this file and the Bad_ version, and it is
     * enforced by the language rather than by a diagram.
     */
    Money priceOf(ProductId product);

    boolean isAvailable(ProductId product, int qty);

    SellerId sellerOf(ProductId product);

    /*
     * The catalogue owns its stock, so the catalogue is the only code that may
     * change it. Orders ASKS; it does not reach in and decrement a row.
     *
     * Contrast with the Bad_ version, where OrderService called
     * productRepo.decrementStock(...) directly. Same outcome, completely
     * different property: when the catalogue introduces reservations, expiry or
     * an oversell allowance, that change happens behind this method and no
     * caller is affected. In the Bad_ version, every caller that decremented a
     * row is now subtly wrong and nobody knows how many there are.
     */
    Reservation reserve(ProductId product, int qty);
}

record Reservation(String id, ProductId product, int qty, java.time.Instant expiresAt) { }

/*
 * The module's front door: how the composition root gets an instance without
 * learning what is inside.
 *
 * This is the only other public type in the module. It exists so that the
 * concrete CatalogueService, its repository and its connection pool can all stay
 * package-private.
 */
// package: shopstream.catalogue          (PUBLIC)
class CatalogueModule {
    static Catalogue create(java.sql.Connection ownDatabase) {
        return null;   // new internal.CatalogueService(new internal.ProductRepository(...))
    }
}

/*
 * ============================================================================
 * The interior: package-private, and therefore genuinely private
 * ============================================================================
 */

// package: shopstream.catalogue.internal      (no modifier = package-private)
class CatalogueService implements Catalogue {

    private final ProductRepository products;      // not visible outside the module
    private final PricingRules pricing;            // not visible outside the module

    CatalogueService(ProductRepository products, PricingRules pricing) {
        this.products = products;
        this.pricing = pricing;
    }

    @Override public Money priceOf(ProductId product) { return pricing.priceFor(product); }
    @Override public boolean isAvailable(ProductId p, int qty) { return true; }
    @Override public SellerId sellerOf(ProductId p) { return new SellerId("s1"); }
    @Override public Reservation reserve(ProductId p, int qty) { return null; }

    /*
     * The equivalent of the Bad_ version's `rewritePriceWithoutAudit`. Still
     * needed internally, still slightly dangerous - and now genuinely
     * unreachable from outside this package.
     *
     * It cannot accumulate callers it was never designed for, because the
     * compiler will not allow the first one. In the Bad_ version the same method
     * had three callers within a year and nobody had decided that.
     */
    void rewritePriceDuringMigration(ProductId product, Money newPrice) { }
}

// package: shopstream.catalogue.internal
class ProductRepository {
    /*
     * THE CATALOGUE'S DATA, OWNED BY THE CATALOGUE.
     *
     * This class is package-private, so no code outside shopstream.catalogue can
     * read or write a product row. Not orders, not payments, not a reporting job.
     *
     * That single fact fixes the shared-schema defect at its source. "Who writes
     * to the products table?" now has an answer you can verify in seconds: this
     * package, and nothing else. Which in turn is what makes splitting the
     * database into per-module schemas a mechanical job rather than an
     * archaeology project.
     */
    Money selectPrice(ProductId product) { return Money.aed(28000); }
    void decrementStock(ProductId product, int qty) { }
}

// package: shopstream.catalogue.internal
class PricingRules {
    Money priceFor(ProductId product) { return Money.aed(28000); }
}

/*
 * ============================================================================
 * A second module, consuming the first through its contract only
 * ============================================================================
 */

// package: shopstream.orders.internal
class OrderService {

    /*
     * Orders depends on the Catalogue INTERFACE. It could not depend on
     * ProductRepository even if someone wanted to, because that type is not
     * visible here.
     *
     * The dependency between modules is therefore exactly one type wide, and it
     * is a type the catalogue's owner published deliberately and can version.
     */
    private final Catalogue catalogue;
    private final OrderRepository orders;          // orders owns its own data
    private final DomainEvents events;

    OrderService(Catalogue catalogue, OrderRepository orders, DomainEvents events) {
        this.catalogue = catalogue;
        this.orders = orders;
        this.events = events;
    }

    void placeOrder(BuyerId buyer, ProductId product, int qty) {

        Money price = catalogue.priceOf(product);          // ask, do not read the table
        Reservation held = catalogue.reserve(product, qty); // ask, do not decrement a row

        orders.insert(buyer, product, qty, price);

        /*
         * Cross-module notification by event rather than by call (folder 04).
         *
         * Worth being precise about why: a direct call would be perfectly fine
         * here and much easier to debug. The event earns its place only where
         * the consumer set is genuinely open - notifications, analytics, the
         * seller dashboard, and whatever gets added next. For a two-party
         * interaction with a known counterpart, `catalogue.priceOf(...)` above
         * is the right shape and a broker would be ceremony.
         *
         * Modularity does not mean every module speaks to every other module
         * asynchronously. It means every module speaks to every other module
         * through a published contract, and a method call is a published
         * contract.
         */
        events.publish(new OrderPlaced(OrderId.next(), buyer, product, qty, price));
    }
}

// package: shopstream.orders             (PUBLIC - part of the orders contract)
record OrderPlaced(OrderId order, BuyerId buyer, ProductId product, int qty, Money total) { }

// package: shopstream.orders.internal
class OrderRepository {
    void insert(BuyerId b, ProductId p, int qty, Money price) { }
}

/*
 * ============================================================================
 * The composition root: the only place that knows all the modules
 * ============================================================================
 */
class ShopStreamApplication {

    static void wire(java.sql.Connection catalogueDb, java.sql.Connection ordersDb) {

        /*
         * Each module gets its OWN database handle. Not one shared connection to
         * one shared schema - a handle to the data that module owns.
         *
         * Today those may point at the same PostgreSQL instance, because the
         * hardware constraint says so. But the code is already written as though
         * they are separate, so separating them later is a configuration change
         * and a data migration rather than a code change.
         *
         * That is the pattern worth naming: MAKE THE FUTURE BOUNDARY EXPLICIT IN
         * THE CODE NOW, EVEN IF THE INFRASTRUCTURE CANNOT HONOUR IT YET. It costs
         * nothing today and it is the entire difference between an extraction
         * that takes a sprint and one that takes a quarter.
         */
        Catalogue catalogue = CatalogueModule.create(catalogueDb);

        // orders receives the catalogue's CONTRACT, never its internals
        // Orders orders = OrdersModule.create(ordersDb, catalogue, events);
    }
}

/*
 * ============================================================================
 * Extracting payments into its own service - discussion question 3
 * ============================================================================
 *
 * FROM THE BAD_ VERSION:
 *   Find payment logic in services/, its data access in repositories/, its types
 *   in models/, its endpoints in controllers/ - each mixed with four other
 *   capabilities. Untangle every caller that reached into payment tables
 *   directly. Discover the callers by grepping. Rewrite. Estimate in months, and
 *   the estimate is unreliable because you do not know what you will find.
 *
 * FROM THIS VERSION:
 *   1. Move the payments package into its own build.
 *   2. Write a remote adapter that implements the SAME `Payments` interface and
 *      talks HTTP or messaging instead of calling in-process.
 *   3. Change one line in the composition root.
 *
 *   No caller changes, because no caller ever knew where payments ran. The
 *   `Payments` interface was the boundary all along; only its implementation
 *   moves across a network.
 *
 * The line of code that is the difference is the field declaration in
 * OrderService above: `private final Catalogue catalogue;` - a published
 * interface - versus the Bad_ version's `public ProductRepository productRepo;`
 * - somebody else's data access class.
 *
 * ============================================================================
 * ENFORCEMENT, which is the part that decays if you skip it
 * ============================================================================
 *
 * Package-private visibility does most of the work here, and it is free. Where
 * that is not enough - because a module spans several packages, or because the
 * team keeps adding `public` to get past a compile error - add one of:
 *
 *   - the Java Platform Module System, with an explicit `exports` list per module,
 *   - build-level dependency rules (separate Gradle/Maven modules, so an illegal
 *     dependency fails the build rather than the review),
 *   - an architecture test in CI asserting that nothing outside a module imports
 *     its internal package.
 *
 * Any one of the three turns the boundary from an intention into a fact. Without
 * at least one, the structure in this file degrades back into the Bad_ version
 * within about two quarters, one urgent Friday fix at a time.
 */

interface DomainEvents { void publish(Object event); }
