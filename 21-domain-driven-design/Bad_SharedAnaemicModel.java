/*
 * ============================================================================
 * BAD: one model for the whole business, and no behaviour in it
 * ============================================================================
 *
 * Two defects at once, and they reinforce each other:
 *
 *   1. NO BOUNDED CONTEXTS. One Product class serves catalogue, orders,
 *      payments and streaming, so it is the union of four unrelated sets of
 *      requirements.
 *
 *   2. ANAEMIC MODEL. The classes hold data and no behaviour, so every rule
 *      lives in a service and no object can protect its own invariants.
 */

/**
 * The Product class, as it exists after two years of four teams adding fields.
 *
 * Count the fields. Then ask, for any given instance, how many are meaningful.
 * A product being demonstrated in a stream has no `orderQuantity`. A product on
 * an order line has no `streamHighlightStart`. In practice most fields are null
 * most of the time, and no reader can tell which ones are supposed to be.
 */
class Product {

    // Needed by the catalogue
    public String id;
    public String title;
    public String description;
    public List<String> imageUrls;
    public long priceFils;
    public String currency;
    public int stockCount;
    public List<String> searchKeywords;
    public String categoryId;

    // Added by orders, meaningless in the catalogue
    public int orderQuantity;
    public long priceAtTimeOfOrderFils;
    public String orderLineId;

    // Added by streaming, meaningless in orders
    public String streamId;
    public int streamOverlayX;
    public int streamOverlayY;
    public long streamHighlightStartMs;
    public boolean currentlyBeingDemonstrated;

    // Added by payments, which should not know products exist at all
    public String taxCategoryCode;
    public boolean requiresAgeVerification;

    // Added by the seller dashboard
    public long lifetimeUnitsSold;
    public double conversionRate;

    /*
     * PUBLIC MUTABLE FIELDS, AND THEREFORE NO INVARIANTS.
     *
     * "Stock is never negative" is a rule somebody believes in. It is enforced
     * nowhere, because any code anywhere can write `product.stockCount = -5`.
     *
     * Folder 09 covers this at the class level. Here it compounds with the
     * missing boundaries: since every context can reach every field, every
     * context can break every other context's rules.
     */
}

/*
 * ============================================================================
 * THE ANAEMIC PART: rules that should be in the model, living in a service
 * ============================================================================
 */
class OrderService {

    void addLineToOrder(Order order, Product product, int qty) {

        /*
         * The invariants of an order are being maintained OUT HERE, by a
         * service, on an object that cannot protect itself.
         *
         * Which means these three rules hold only if every caller remembers
         * them. There is a second code path in the bulk-import job that forgot
         * the stock check, and a third in the admin tool that forgot the total
         * recalculation, and both were written by people who had no way to know
         * these rules existed.
         */
        if (product.stockCount < qty) {
            throw new IllegalStateException("Insufficient stock");
        }
        if (order.lines.size() >= 50) {
            throw new IllegalStateException("Too many lines");
        }

        OrderLine line = new OrderLine();
        line.productId = product.id;
        line.quantity = qty;
        line.unitPriceFils = product.priceFils;
        order.lines.add(line);

        // Recalculating a derived field by hand, at every call site that
        // touches lines. Miss one and the order total is silently wrong -
        // the same class of defect as the VAT drift in folder 11.
        order.totalFils = 0;
        for (OrderLine l : order.lines) {
            order.totalFils += l.unitPriceFils * l.quantity;
        }
    }
}

class Order {
    public String id;
    public String buyerId;

    /*
     * THE OBJECT REFERENCE THAT DRAGS EVERYTHING WITH IT.
     *
     * An order line holds a whole Product. So loading an order loads products,
     * which carry streaming overlay coordinates and seller dashboard analytics.
     *
     * Consequences:
     *   - The transaction that saves an order touches product rows.
     *   - Orders cannot be moved to their own module or database without
     *     untangling this reference (folder 17).
     *   - "What did this cost at the time?" is unanswerable once the product's
     *     price changes underneath the order, because the order points at the
     *     live product rather than recording the price it charged.
     *
     * That last one is a real financial defect produced by an object reference.
     */
    public List<OrderLine> lines;
    public long totalFils;
    public String status;         // a String, so any value is legal (folder 09)
}

class OrderLine {
    public String productId;
    public Product product;       // both an id AND the object, which is worse
    public int quantity;
    public long unitPriceFils;
}

/*
 * ============================================================================
 * AND THE VENDOR'S VOCABULARY, ARRIVING UNINVITED
 * ============================================================================
 *
 * No anticorruption layer, so the payment gateway's model of the world is
 * simply adopted. `cko_customer_id` and `payment_source_type` are Checkout.com's
 * concepts, now permanent fields on a ShopStream domain object.
 *
 * When ShopStream adds a provider that has neither concept, these fields stay -
 * meaningless, populated with defaults, and impossible to remove because
 * something somewhere reads them.
 */
class Buyer {
    public String id;
    public String email;
    public String cko_customer_id;        // Checkout.com's identifier
    public String payment_source_type;    // Checkout.com's enumeration
    public String paytabs_profile_ref;    // and then the second vendor's
}

interface List<T> { int size(); void add(T item); }
