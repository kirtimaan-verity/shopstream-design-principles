/*
 * ============================================================================
 * GOOD: one module, one job, one reason to change
 * ============================================================================
 *
 * The same six responsibilities, redistributed so that each module has a single
 * requester and a single reason to change.
 *
 * Note that this is the Single Responsibility Principle (folder 14) viewed from
 * the module level rather than the class level. Cohesion and SRP are the same
 * idea at two different altitudes, which is worth saying out loud because the
 * they are usually introduced under different headings and people sometimes
 * learn them as unrelated ideas.
 */

/**
 * Module 1: the catalogue.
 *
 * Requester: merchandising and product management.
 * Reason to change: ranking, filtering, indexing, search performance.
 *
 * What this separation bought, concretely: search can now be deployed on its own
 * schedule, scaled on its own, given its own read replica, and moved behind a
 * search index - none of which were possible while it shared a process with
 * image resizing. Pain point P2 has somewhere to be fixed.
 */
class Good_ProductCatalogue {

    private final CatalogueIndex index;

    Good_ProductCatalogue(CatalogueIndex index) {
        this.index = index;
    }

    java.util.List<ProductId> search(String query, String category, Page page) {
        return index.find(query, category, page);
    }

    java.util.List<ProductId> trendingIn(StreamId stream) {
        return index.trending(stream);
    }

    /*
     * One collaborator. Contrast with the five in the Bad_ version.
     *
     * The constructor is a cohesion metric you can read at a glance: if a class
     * needs collaborators from four unrelated technical domains, it is doing four
     * unrelated jobs. You do not need a tool for this; you need to read the
     * constructor.
     */
}

/**
 * Module 2: order placement.
 *
 * Requester: the commerce team.
 * Reason to change: order rules, pricing, checkout flow.
 *
 * Functional cohesion: every method here exists to move an order through its
 * lifecycle, and they all operate on the same concept.
 */
class Good_OrderBook {

    private final Orders orders;
    private final Payments payments;

    Good_OrderBook(Orders orders, Payments payments) {
        this.orders = orders;
        this.payments = payments;
    }

    Order place(BuyerId buyer, SellerId seller, StreamId stream, OrderLine line) {
        Order order = new Order(OrderId.next(), buyer, seller, stream,
                java.util.List.of(line), OrderStatus.PLACED);
        orders.save(order);
        return order;
    }

    Order confirm(OrderId id, PaymentId payment) {
        Order order = orders.byId(id).orElseThrow();
        Order confirmed = order.withStatus(OrderStatus.CONFIRMED);
        orders.save(confirmed);
        return confirmed;
    }

    Order cancel(OrderId id, String reason) {
        Order order = orders.byId(id).orElseThrow();
        Order cancelled = order.withStatus(OrderStatus.CANCELLED);
        orders.save(cancelled);
        return cancelled;
    }
}

/**
 * Module 3: payment settlement.
 *
 * Requester: finance, and ops during an incident.
 * Reason to change: retry policy, provider behaviour, reconciliation rules.
 *
 * Worth pointing out in class: this is the module ops edits at 3am when the
 * gateway is degrading. It is now 30 lines about payments, not 300 lines
 * containing marketing email templates. The blast radius of a hurried change
 * under pressure is now proportional to the change itself.
 */
class Good_PaymentSettlement {

    private final Payments payments;
    private final FailedPayments failed;

    Good_PaymentSettlement(Payments payments, FailedPayments failed) {
        this.payments = payments;
        this.failed = failed;
    }

    void retryPending() {
        for (OrderId id : failed.pendingRetry()) {
            PaymentResult result = payments.authorise(id, failed.amountFor(id));
            if (result.authorised()) {
                failed.markSettled(id);
            }
        }
    }
}

/**
 * Module 4: media processing.
 *
 * Requester: the mobile team, or whoever owns the CDN bill.
 * Reason to change: image formats, sizes, CDN vendor.
 *
 * This is the module whose separation directly addresses pain point P2. Resizing
 * is CPU-bound and bursty; catalogue search is latency-sensitive and steady.
 * Different workload shapes belong in different deployment units, because that
 * is the only way to give them different resource budgets.
 *
 * Once separated, a seller's 400-photo bulk upload saturates the media workers
 * and nothing else. Search does not notice. That is the difference between a
 * degraded feature and a degraded platform.
 */
class Good_MediaProcessing {

    private final ImageProcessor imaging;
    private final BlobStore blobs;

    Good_MediaProcessing(ImageProcessor imaging, BlobStore blobs) {
        this.imaging = imaging;
        this.blobs = blobs;
    }

    void generateThumbnails(ProductId product, byte[] original) {
        for (int width : new int[]{ 120, 480, 1080 }) {
            blobs.put("products/" + product.value() + "/w" + width,
                    imaging.resize(original, width, width));
        }
    }
}

/*
 * ============================================================================
 * Where did marketing email and the CSV export go?
 * ============================================================================
 *
 * Deliberately not here, and the reason is the lesson.
 *
 * MARKETING EMAIL is not part of the commerce domain at all. It belongs to the
 * Notification Service, and it should be driven by events (folder 04) rather
 * than called directly. Once it subscribes to OrderPlaced, marketing can change
 * copy weekly without any commerce code being touched, reviewed or released.
 *
 * SELLER CSV EXPORT is a reporting concern, and reporting has a different
 * workload shape again: long-running, read-heavy, tolerant of stale data,
 * intolerant of nothing. Running it against the transactional database is what
 * makes an out-of-memory error in a report kill checkout. It belongs against a
 * replica, in a module whose failure cannot touch the order path.
 *
 * The general move: when you break up a low-cohesion module, some of the pieces
 * turn out not to belong in that module's SYSTEM either. Splitting the class is
 * what makes that visible. This is discussion question 1 - the class did not become
 * incoherent, it was assembled one plausible method at a time by people who each
 * had a good reason and no view of the whole.
 */

// ---- Supporting cast, elided ------------------------------------------------
interface CatalogueIndex { java.util.List<ProductId> find(String q, String cat, Page p);
                           java.util.List<ProductId> trending(StreamId s); }
record   Page(int number, int size) { }
interface Orders         { void save(Order o); java.util.Optional<Order> byId(OrderId id); }
interface Payments       { PaymentResult authorise(OrderId order, Money amount); }
interface FailedPayments { java.util.List<OrderId> pendingRetry(); Money amountFor(OrderId id); void markSettled(OrderId id); }
interface ImageProcessor { byte[] resize(byte[] src, int w, int h); }
interface BlobStore      { void put(String key, byte[] content); }
