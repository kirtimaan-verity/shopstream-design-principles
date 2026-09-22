/*
 * ============================================================================
 * BAD: cut by technical layer, which produces directories rather than modules
 * ============================================================================
 *
 * The package layout every codebase reaches for first, because it looks tidy:
 *
 *     shopstream/
 *       controllers/     ProductController, OrderController, PaymentController,
 *                        StreamController, SellerController
 *       services/        ProductService, OrderService, PaymentService,
 *                        StreamService, NotificationService
 *       repositories/    ProductRepository, OrderRepository, PaymentRepository
 *       models/          Product, Order, Payment, Stream, Buyer, Seller
 *       util/            everything nobody could place
 *
 * Four top-level packages. Now trace one ordinary ticket through it:
 *
 *     "Sellers want to set a per-stream discount on a product."
 *
 *       controllers/ProductController      accept the new field
 *       services/ProductService            apply the rule
 *       repositories/ProductRepository     persist it
 *       models/Product                     carry it
 *       services/OrderService              honour it at checkout
 *       models/Order                       record which discount applied
 *
 * Six files, four packages, one small feature. That is the diagnosis: THE
 * PACKAGES ARE NOT UNITS OF CHANGE. Every change cuts across all of them, every
 * time, which means the boundaries are not separating anything.
 */

/*
 * ============================================================================
 * PROBLEM 1: everything must be public, so nothing is hidden
 * ============================================================================
 *
 * A layered cut forces cross-package calls for every single operation:
 * controllers call services, services call repositories. Java's default
 * (package-private) is useless across packages, so every class and every method
 * is declared `public`.
 *
 * The consequence is that the codebase has NO INTERIOR. Every type is reachable
 * from every other type. Folder 09's information hiding cannot be applied at all,
 * because there is no unit that owns anything privately.
 */

// package: shopstream.controllers
class ProductController {
    public ProductService products;         // public, because it must cross a package
    public OrderService orders;             // and nothing stops it reaching sideways
}

// package: shopstream.services
class ProductService {
    /*
     * Public, because ProductController needs it.
     *
     * And therefore also callable by OrderService, PaymentService,
     * NotificationService, the CSV exporter, and a scheduled job somebody adds
     * in eighteen months. The intended caller was one class; the actual audience
     * is the entire application, forever.
     */
    public Money priceOf(ProductId id) { return Money.aed(28000); }

    /*
     * This one was written for internal use during a migration. It is public
     * because everything here is public. Three other services now call it.
     *
     * Nobody decided that. It is what happens when a boundary is a convention.
     */
    public void rewritePriceWithoutAudit(ProductId id, Money newPrice) { }
}

/*
 * ============================================================================
 * PROBLEM 2: any class can reach any table
 * ============================================================================
 *
 * The repositories package holds every repository, all public. So OrderService
 * can read and write product rows directly, and PaymentService can read order
 * rows directly.
 *
 * This is the shared-schema defect from the reference diagram, and note WHERE it
 * comes from: not from a database decision, but from a PACKAGE LAYOUT decision.
 * Nothing in the code says "orders may not write catalogue data", so eventually
 * something does, usually to fix a bug quickly on a Friday.
 *
 * A year later, "who writes to the products table?" cannot be answered without
 * grepping the whole repository, and the schema cannot be split because the
 * answer is "sixteen places".
 */

// package: shopstream.services
class OrderService {
    public ProductRepository productRepo;       // reaching into another capability's data
    public PaymentRepository paymentRepo;       // and another one
    public OrderRepository orderRepo;

    public void placeOrder(BuyerId buyer, ProductId product, int qty) {
        // Reads the catalogue's table directly. No contract, no interface, no
        // way for the catalogue's owner to know this caller exists.
        Money price = productRepo.selectPrice(product);

        // And decrements the catalogue's stock, which is a catalogue invariant
        // being maintained by a module that does not own it. When the catalogue
        // adds a reservation concept, this line silently becomes wrong.
        productRepo.decrementStock(product, qty);

        orderRepo.insert(buyer, product, qty, price);
    }
}

/*
 * ============================================================================
 * PROBLEM 3: no feature can be deleted, extracted or owned
 * ============================================================================
 *
 * DELETION. "We are dropping the seller analytics feature." Where is it? Spread
 * across five packages, tangled with everything else in each. So it is not
 * deleted, it is disabled with a flag, and the code stays forever.
 *
 * EXTRACTION. "Payments must run in its own process for compliance isolation."
 * Payment logic lives in services/, its data access in repositories/, its types
 * in models/, its endpoints in controllers/ - each mixed in with four other
 * capabilities. There is no seam to cut along. The extraction is a rewrite, and
 * it is competing with a six-month deadline.
 *
 * OWNERSHIP. Who owns services/? Everybody, which means nobody. Every team edits
 * every package, so every change needs a review from people with no context, and
 * merge conflicts follow the layer rather than the feature.
 *
 * ============================================================================
 * PROBLEM 4: the layering is not even enforced
 * ============================================================================
 *
 * The one thing this structure was supposed to guarantee - controllers call
 * services, services call repositories, nobody skips a layer - is guaranteed by
 * nothing at all. It is a convention in a diagram.
 */

// package: shopstream.controllers
class OrderController {
    public OrderRepository orderRepo;   // straight past the service layer

    public String getOrder(String id) {
        /*
         * The layering violated in the one direction it was built to prevent,
         * in the component that is deployed most often.
         *
         * Nothing failed. No test broke. No build rule objected. Somebody needed
         * a field the service did not expose and took the short path, and the
         * structure had no opinion about it.
         *
         * A boundary that nothing enforces is not a boundary. That is the whole
         * lesson of this file, and it applies equally to a well-chosen boundary:
         * getting the cut right and leaving it unenforced buys you a diagram,
         * not a module.
         */
        return orderRepo.selectRaw(id);
    }
}

// ---- Supporting cast, elided ------------------------------------------------
class ProductRepository { public Money selectPrice(ProductId p) { return Money.aed(28000); }
                          public void decrementStock(ProductId p, int qty) { } }
class OrderRepository   { public void insert(BuyerId b, ProductId p, int q, Money m) { }
                          public String selectRaw(String id) { return "{}"; } }
class PaymentRepository { }
class PaymentService    { }
