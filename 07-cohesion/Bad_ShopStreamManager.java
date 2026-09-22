/*
 * ============================================================================
 * BAD: coincidental cohesion, also known as "the file everyone edits"
 * ============================================================================
 *
 * What holds these six methods together? Only the word "ShopStream" in the class
 * name. That is the definition of coincidental cohesion, the weakest rung on the
 * ladder.
 *
 * The exercise that works in the room: read the six method names and write down,
 * for each one, WHO AT SHOPSTREAM would ask for it to change.
 *
 *   searchProducts - product management, merchandising
 *   createOrder - commerce team
 *   retryFailedPayments - finance, plus ops during an incident
 *   sendMarketingEmail - marketing
 *   exportSellerReport - finance and the seller success team
 *   resizeProductImage - the mobile team, or whoever owns the CDN bill
 *
 * Five or six different requesters, one file. Every one of them can break the
 * other five, and every one of their changes needs a review from people who do
 * not understand their domain.
 */
class Bad_ShopStreamManager {

    private final PostgresConnection db;
    private final SmtpClient smtp;
    private final S3Client storage;
    private final PaymentGateway gateway;
    private final ImageProcessor imaging;

    Bad_ShopStreamManager(PostgresConnection db, SmtpClient smtp, S3Client storage,
                          PaymentGateway gateway, ImageProcessor imaging) {
        this.db = db;
        this.smtp = smtp;
        this.storage = storage;
        this.gateway = gateway;
        this.imaging = imaging;
    }

    /*
     * The constructor is the diagnosis. Five collaborators from five unrelated
     * technical domains: a database, a mail server, object storage, a payment
     * gateway and an image library.
     *
     * A cohesive module's constructor reads like a sentence about one job. This
     * one reads like an inventory of the company's infrastructure.
     *
     * Practical consequence: you cannot write a unit test for search without
     * providing an SMTP client and a payment gateway. So nobody writes one.
     */

    // ---- Responsibility 1: catalogue search --------------------------------
    // Changes when: merchandising changes ranking rules, or search gets slow.
    java.util.List<ProductId> searchProducts(String query, String category, int page) {
        return db.query("SELECT id FROM products WHERE name ILIKE ? AND category = ? LIMIT 50 OFFSET ?",
                query, category, page * 50);
    }

    // ---- Responsibility 2: order creation ----------------------------------
    // Changes when: commerce rules change. Nothing to do with search.
    OrderId createOrder(BuyerId buyer, ProductId product, int qty) {
        OrderId id = OrderId.next();
        db.insert("INSERT INTO orders ...", id, buyer, product, qty);
        return id;
    }

    // ---- Responsibility 3: payment retry -----------------------------------
    // Changes when: finance changes retry policy, or ops needs a manual sweep
    // at 3am during an incident. Note that the person who most needs to change
    // this method is under maximum time pressure, editing a file that also
    // contains marketing email logic.
    void retryFailedPayments() {
        for (OrderId id : db.<OrderId>query("SELECT id FROM orders WHERE status = 'PAYMENT_FAILED'")) {
            gateway.retry(id);
        }
    }

    // ---- Responsibility 4: marketing email ---------------------------------
    // Changes when: marketing wants different copy. Weekly, in practice.
    //
    // So the file containing the payment retry sweep and the catalogue search is
    // modified every week for a copy change, and every one of those releases
    // carries the risk of the other five features.
    void sendMarketingEmail(java.util.List<BuyerId> audience, String subject, String body) {
        for (BuyerId buyer : audience) {
            smtp.send(lookupEmail(buyer), subject, body);
        }
    }

    // ---- Responsibility 5: seller CSV export -------------------------------
    // Changes when: finance changes the report format. Also: this method loads
    // every order for a seller into memory. For a seller with 200,000 orders it
    // is an out-of-memory error that kills the process - and with it, search,
    // checkout and payment retry, because they all live here too.
    String exportSellerReport(SellerId seller) {
        StringBuilder csv = new StringBuilder("order_id,amount,status\n");
        for (Object row : db.query("SELECT * FROM orders WHERE seller_id = ?", seller)) {
            csv.append(row).append('\n');
        }
        return csv.toString();
    }

    // ---- Responsibility 6: image resizing ----------------------------------
    //
    // This is the one that produced pain point P2.
    //
    // Resizing is CPU-bound and memory-hungry. It shares a thread pool, a heap
    // and a deployment unit with catalogue search, which is latency-sensitive.
    // When a seller bulk-uploads 400 product photos before a big stream, the
    // pool saturates and search queues behind it. Search goes from under 500ms
    // to 12 seconds.
    //
    // Nobody wrote a bug. Two features that have no relationship were placed in
    // the same box, and the box became the coupling. Every engineer looking for
    // the cause of slow search will profile the search code, where there is
    // nothing to find.
    byte[] resizeProductImage(byte[] original, int width, int height) {
        return imaging.resize(original, width, height);
    }

    private String lookupEmail(BuyerId buyer) { return "buyer@example.com"; }
}

// ---- Supporting cast, elided ------------------------------------------------
interface PostgresConnection { <T> java.util.List<T> query(String sql, Object... a); void insert(String sql, Object... a); }
interface SmtpClient         { void send(String to, String subject, String body); }
interface S3Client           { void put(String key, byte[] data); }
interface PaymentGateway     { void retry(OrderId order); }
interface ImageProcessor     { byte[] resize(byte[] src, int w, int h); }
