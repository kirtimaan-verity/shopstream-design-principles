/*
 * ============================================================================
 * GOOD: one model per context, each enforcing its own rules
 * ============================================================================
 *
 * The same word, "Product", modelled four times. These are not duplicates to be
 * consolidated - they are four different concepts that happen to share a name,
 * which is the argument folder 11 makes about two Address records.
 *
 * Read the four together and note how small each one is. That is the point: a
 * model scoped to one context is complete for its purpose and contains nothing
 * else.
 */

// ---------------------------------------------------------------------------
// CONTEXT: catalogue          language: sellers, merchandising
// ---------------------------------------------------------------------------
// package shopstream.catalogue

/**
 * In the catalogue, a product is something sellable. It has stock, a price, and
 * search terms, and it protects one rule absolutely: stock never goes negative.
 */
class Listing {                       // named as the catalogue names it

    private final ProductId id;
    private final String title;
    private Money price;
    private int stock;                // private, so the invariant is enforceable

    Listing(ProductId id, String title, Money price, int stock) {
        if (stock < 0) throw new IllegalArgumentException("stock cannot be negative");
        this.id = id; this.title = title; this.price = price; this.stock = stock;
    }

    /*
     * The rule lives with the data it constrains.
     *
     * In the Bad_ version this check was in OrderService, and two other code
     * paths forgot it. Here there is no way to reduce stock except through this
     * method, so there is no path that can forget.
     */
    Reservation reserve(int qty) {
        if (qty <= 0)     throw new IllegalArgumentException("quantity must be positive");
        if (qty > stock)  throw new InsufficientStock(id, qty, stock);
        stock -= qty;
        return new Reservation(id, qty);
    }

    Money price() { return price; }
    ProductId id() { return id; }
}

// ---------------------------------------------------------------------------
// CONTEXT: orders             language: commerce, finance
// ---------------------------------------------------------------------------
// package shopstream.orders

/**
 * THE AGGREGATE. Order is the root; OrderLine exists only inside it.
 *
 * Three rules, all enforced here and nowhere else:
 *   1. one entry point   - callers hold an Order, never an OrderLine
 *   2. invariants always - the total always equals the sum of the lines
 *   3. reference by id   - a line holds a ProductId, never a Listing
 */
class Order {

    private static final int MAX_LINES = 50;

    private final OrderId id;
    private final BuyerId buyer;
    private final java.util.List<OrderLine> lines = new java.util.ArrayList<>();
    private OrderStatus status = OrderStatus.PLACED;

    Order(OrderId id, BuyerId buyer) { this.id = id; this.buyer = buyer; }

    /*
     * RULE 1 AND 2. Compare with the Bad_ version's
     * `order.getLines().add(line)` plus a manual total recalculation at every
     * call site.
     *
     * Here the rules cannot be bypassed, because there is no other way in, and
     * they cannot be forgotten, because the total is derived rather than stored.
     */
    void addLine(ProductId product, int qty, Money priceCharged) {
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException("Cannot change a settled order");
        }
        if (lines.size() >= MAX_LINES) {
            throw new IllegalStateException("An order may have at most " + MAX_LINES + " lines");
        }
        lines.add(new OrderLine(product, qty, priceCharged));
    }

    /*
     * Derived, not stored. It cannot drift out of step with the lines, because
     * there is nothing to keep in step.
     */
    Money total() {
        return lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(Money.aed(0), Money::plus);
    }

    /** Callers get a snapshot, never the live list. Folder 09. */
    java.util.List<OrderLine> lines() { return java.util.List.copyOf(lines); }
}

/**
 * RULE 3: a line holds a ProductId, not a Listing.
 *
 * And it records `priceCharged` rather than reading the catalogue's current
 * price. That single field answers "what did this cost at the time?" forever,
 * which the Bad_ version could not answer once a seller changed the price.
 *
 * The wider payoff: because Order references catalogue data only by id, the two
 * contexts share no object graph. Orders can move to its own module, its own
 * schema and eventually its own service, and the change is mechanical. That is
 * folder 17's argument, and this is the line of code that enables it.
 */
record OrderLine(ProductId product, int quantity, Money priceCharged) {
    Money lineTotal() { return priceCharged.times(quantity); }
}

// ---------------------------------------------------------------------------
// CONTEXT: streaming          language: sellers on air, production
// ---------------------------------------------------------------------------
// package shopstream.streaming

/**
 * Streaming's model of a product: where it sits on screen and when it is
 * highlighted. No price, no stock, no tax category.
 *
 * In the Bad_ version these three fields were on the shared Product class, so
 * every order and every payment carried overlay coordinates.
 */
record DemonstratedItem(ProductId product, int overlayX, int overlayY,
                        java.time.Duration highlightAt) { }

// ---------------------------------------------------------------------------
// CONTEXT: payments
// ---------------------------------------------------------------------------
// Payments has no model of a product at all. It knows an amount and an order id.
// The absence is the design: a context should contain what it needs and nothing
// else, and payments genuinely does not need to know what was bought.

/*
 * ============================================================================
 * THE ANTICORRUPTION LAYER
 * ============================================================================
 *
 * Checkout.com has customers, payment sources and its own status vocabulary.
 * ShopStream has buyers and outcomes. This class is the only place the two
 * meet.
 *
 * Compare with the Bad_ version's Buyer, which carried `cko_customer_id` and
 * `paytabs_profile_ref` as permanent domain fields. Once a vendor's concepts
 * are in your domain model they never leave, because something somewhere reads
 * them and nobody can prove otherwise.
 */
class CheckoutComAntiCorruptionLayer {

    private final java.util.Map<BuyerId, String> vendorCustomerIds;   // mapping lives HERE

    CheckoutComAntiCorruptionLayer(java.util.Map<BuyerId, String> vendorCustomerIds) {
        this.vendorCustomerIds = vendorCustomerIds;
    }

    /*
     * Domain in, domain out. The vendor's identifiers, request shapes, status
     * codes and exceptions all stop at this boundary.
     *
     * This is the same class as the adapter in folder 12. Worth naming both
     * ways: at the code level it is an Adapter; at the design level it is an
     * anticorruption layer protecting a bounded context. Same class, two
     * altitudes of vocabulary.
     */
    PaymentOutcome authorise(OrderId order, BuyerId buyer, Money amount) {
        String vendorCustomer = vendorCustomerIds.get(buyer);
        // var request = new CheckoutComRequest(vendorCustomer, amount.minorUnits(), ...);
        // var response = client.post("/payments", request);
        // return translate(response);       their status table becomes our outcome
        return PaymentOutcome.AUTHORISED;
    }
}

/*
 * ============================================================================
 * THE SHARED KERNEL, and why it is deliberately tiny
 * ============================================================================
 *
 * Money and the typed identifiers (folder 00) are shared by every context.
 *
 * That is a Shared Kernel, and it carries a real cost: changing it requires
 * agreement from every context that uses it, so it is the one place where the
 * contexts are genuinely coupled to each other.
 *
 * Which is exactly why it contains six value types and nothing else. Every
 * addition to a shared kernel is a new coordination point between teams. The
 * temptation is always to put "just one more" shared concept in it, and that is
 * how the Bad_ version's thirty-field Product class began.
 *
 * ============================================================================
 * DOMAIN EVENTS: how contexts talk without sharing a model
 * ============================================================================
 *
 * OrderPlaced (folder 04) is the Published Language between orders and
 * everything downstream. It carries the shared kernel types and nothing
 * context-specific, so the notification service does not learn what an Order
 * looks like internally, and orders does not learn that notifications exist.
 *
 * The layering of these ideas is worth stating explicitly, because they are
 * usually taught separately:
 *
 *      bounded context   the boundary                     (folder 21, here)
 *      module            how the boundary is enforced     (folder 17)
 *      published language  what crosses it                (folder 04)
 *      anticorruption layer  what protects it from outside (folder 12)
 *
 * Four names for four parts of one decision about where a line goes.
 */

class InsufficientStock extends RuntimeException {
    InsufficientStock(ProductId p, int wanted, int available) {
        super("Wanted " + wanted + " of " + p.value() + ", only " + available + " available");
    }
}
record Reservation(ProductId product, int qty) { }
enum PaymentOutcome { AUTHORISED, DECLINED, UNAVAILABLE }
