/*
 * ============================================================================
 * GOOD: the domain owns the interfaces, the infrastructure implements them
 * ============================================================================
 *
 * Apply the compile test:
 *
 *     "Could you delete every adapter and still compile the domain?"
 *
 * Yes. The domain below has no knowledge of PostgreSQL, SMTP, Checkout.com,
 * HTTP or JSON. It declares what it needs, in its own words, and something else
 * satisfies that at wiring time.
 *
 *      +--------------------------------------------------+
 *      |  DOMAIN  (owns both the policy and the contracts) |
 *      |                                                   |
 *      |   Good_PlaceOrderUseCase                          |
 *      |   VatPolicy                                       |
 *      |                                                   |
 *      |   interface Orders                                |
 *      |   interface Catalogue                             |
 *      |   interface Payments                              |
 *      |   interface Notifier                              |
 *      +--------------------------------------------------+
 *                        ^          ^
 *                        |          |  implements  (arrows point UP,
 *                        |          |               against control flow)
 *      +-----------------+--+   +---+------------------+
 *      | PostgresOrders     |   | CheckoutComPayments  |
 *      | SmtpNotifier       |   | HttpCatalogue        |
 *      +--------------------+   +----------------------+
 *              INFRASTRUCTURE (depends on the domain)
 */

/*
 * ============================================================================
 * The domain's contracts, defined by and for the domain
 * ============================================================================
 *
 * Compare each of these with the OrderRepository in the Bad_ file. The names
 * come from the business, not from a persistence framework. There is no
 * `flush()`, no `saveAll()`, no `findAllByStatus(String)`, because no use case
 * ever needed those - the ORM did.
 *
 * The naming test from the README: `Orders` and `Notifier` describe what the
 * caller needs. `PostgresOrderDao` and `SmtpMailer` describe what the
 * implementation is. Only the first pair could be satisfied by an
 * implementation nobody has written yet.
 */

interface Orders {
    void save(Order order);
    java.util.Optional<Order> byId(OrderId id);
}

interface Catalogue {
    Money priceOf(ProductId product);
    SellerId sellerOf(ProductId product);
    boolean isAvailable(ProductId product, int qty);
}

interface Payments {
    PaymentResult authorise(OrderId order, Money amount);
}

/**
 * Note the name and the vocabulary.
 *
 * Not `Mailer`, and certainly not `SmtpMailer`. The domain does not know or care
 * whether a buyer is told by email, push notification, SMS or a message in the
 * stream overlay. It knows that a confirmation is owed. Choosing the channel is
 * an infrastructure decision, and the Pakistan launch adding SMS should not
 * reach this file.
 */
interface Notifier {
    void orderConfirmed(BuyerId buyer, OrderId order, Money total);
}

/*
 * ============================================================================
 * The high-level policy
 * ============================================================================
 */
class Good_PlaceOrderUseCase {

    private final Catalogue catalogue;
    private final Orders orders;
    private final Payments payments;
    private final Notifier notifier;
    private final VatPolicy vat;

    Good_PlaceOrderUseCase(Catalogue catalogue, Orders orders, Payments payments,
                           Notifier notifier, VatPolicy vat) {
        this.catalogue = catalogue;
        this.orders = orders;
        this.payments = payments;
        this.notifier = notifier;
        this.vat = vat;
    }

    /*
     * Read this method and note what is not in it: no SQL, no table names, no
     * SMTP, no vendor SDK, no HTTP status codes, no framework annotations.
     *
     * It is a description of what ShopStream means by "placing an order", in
     * ShopStream's language, at one level of abstraction.
     *
     * Practical consequences:
     *
     * - It can be unit tested with four small fakes, in milliseconds, with no
     *     database and no network. That is discussion question 1: in the Bad_
     *     version, testing the VAT rule required a PostgreSQL instance.
     *
     * - It survives the infrastructure migration. Bare metal to cloud after
     *     Series B changes the adapters and leaves this file alone.
     *
     * - It survives the schema split (A5 Problem 4). When Product, Order and
     *     Payment each get their own database, `Orders` and `Catalogue` get new
     *     implementations. This file does not change, because it never named a
     *     table. That is discussion question 3.
     */
    Order execute(BuyerId buyer, ProductId product, int qty, String market) {

        if (!catalogue.isAvailable(product, qty)) {
            throw new OutOfStock(product);
        }

        Money unitPrice = catalogue.priceOf(product);
        Money net = unitPrice.times(qty);
        Money gross = net.plus(vat.on(net, market));

        Order order = new Order(OrderId.next(), buyer, catalogue.sellerOf(product), null,
                java.util.List.of(new OrderLine(product, qty, unitPrice)), OrderStatus.PLACED);
        orders.save(order);

        PaymentResult payment = payments.authorise(order.id(), gross);
        Order settled = order.withStatus(
                payment.authorised() ? OrderStatus.AUTHORISED : OrderStatus.CANCELLED);
        orders.save(settled);

        if (payment.authorised()) {
            notifier.orderConfirmed(buyer, settled.id(), gross);
        }
        return settled;
    }
}

/** Pure business rule, no dependencies at all. Testable in microseconds. */
class VatPolicy {
    private static final java.util.Map<String, Integer> BASIS_POINTS = java.util.Map.of(
            "AE", 500, "SA", 1500, "EG", 1400, "JO", 1600, "PK", 1700, "NG", 750);

    Money on(Money net, String market) {
        Integer bp = BASIS_POINTS.get(market);
        if (bp == null) throw new IllegalArgumentException("No VAT rate for market: " + market);
        return new Money(net.minorUnits() * bp / 10_000, net.currency());
    }
}

/*
 * ============================================================================
 * The adapters: low-level details, depending upward on the domain
 * ============================================================================
 *
 * Each of these imports the domain. The domain imports none of them. That is the
 * inversion, and it is visible in the import statements rather than in a diagram
 * or a package name.
 */

class PostgresOrders implements Orders {

    private final java.sql.Connection connection;

    PostgresOrders(java.sql.Connection connection) {
        this.connection = connection;
    }

    /*
     * All the SQL in the system lives in classes like this one.
     *
     * When ShopStream splits the shared schema, this class is rewritten or
     * replaced and nothing above it notices. When they add a read replica for
     * the catalogue to fix pain point P2, that decision lives in
     * PostgresCatalogue and stops there.
     */
    @Override
    public void save(Order order) {
        // INSERT ... ON CONFLICT UPDATE, table and column names, dialect quirks
    }

    @Override
    public java.util.Optional<Order> byId(OrderId id) {
        return java.util.Optional.empty();
    }
}

class SmtpNotifier implements Notifier {

    private final Object smtpClient;

    SmtpNotifier(Object smtpClient) { this.smtpClient = smtpClient; }

    /*
     * The domain said "tell the buyer their order is confirmed". This class
     * decides that means an email, picks the template, resolves the address and
     * handles the SMTP failure.
     *
     * The Pakistan launch adding SMS is a new adapter next to this one, or a
     * composite that does both. Good_PlaceOrderUseCase is not opened.
     */
    @Override
    public void orderConfirmed(BuyerId buyer, OrderId order, Money total) {
        // resolve address, select template, send, handle failure
    }
}

class CheckoutComPayments implements Payments {

    private final Object vendorClient;

    CheckoutComPayments(Object vendorClient) { this.vendorClient = vendorClient; }

    /*
     * Vendor request shapes, status code translation and vendor exceptions all
     * stop here, exactly as in folder 12. DIP and abstraction are two views of
     * the same boundary: folder 12 asks whether the interface speaks the
     * caller's language, this folder asks which side of the boundary owns it.
     * A boundary needs both to be true.
     */
    @Override
    public PaymentResult authorise(OrderId order, Money amount) {
        return new PaymentResult(new PaymentId("pay_1"), true, "**** 4242", null);
    }
}

/*
 * ============================================================================
 * The composition root: where the concrete types are finally named
 * ============================================================================
 *
 * DIP does not make knowledge of concrete types disappear. Something has to
 * decide that Orders means PostgresOrders today. DIP CONCENTRATES that knowledge
 * in one place instead of spreading it through every class that needed a
 * collaborator.
 *
 * This file is deliberately boring and deliberately the only place in the system
 * that mentions every concrete type. Reading it tells you the entire runtime
 * shape of ShopStream, which is a genuinely useful property during an incident.
 *
 * Note there is no DI framework here. DIP is achieved by where the interfaces
 * are declared, not by an annotation processor. A framework is a convenience for
 * managing this file when it gets long; it is not what does the inverting. That
 * is discussion question 2, and the one-sentence answer: injection decides how an
 * object receives its collaborators, inversion decides who owns the contract
 * between them.
 */
class ShopStreamComposition {

    static Good_PlaceOrderUseCase placeOrder(java.sql.Connection db, Object smtp, Object vendor) {

        Orders orders = new PostgresOrders(db);
        Catalogue catalogue = new PostgresCatalogue(db);
        Notifier notifier = new SmtpNotifier(smtp);

        /*
         * Decorators are applied here too, which is why folder 08's audit
         * wrapper and folder 14's PaymentAuditTrail cost nothing to add or
         * remove. The business logic never learns it is being audited.
         */
        Payments payments = new CheckoutComPayments(vendor);

        return new Good_PlaceOrderUseCase(catalogue, orders, payments, notifier, new VatPolicy());
    }

    /*
     * And the test wiring, which is the same shape with different implementations:
     *
     *     new Good_PlaceOrderUseCase(
     *             new InMemoryCatalogue(),
     *             new InMemoryOrders(),
     *             new AlwaysDeclinesPayments(),      // exercise the decline path
     *             new RecordingNotifier(),
     *             new VatPolicy());
     *
     * No database, no mail server, no vendor sandbox, no framework bootstrap.
     * Milliseconds per test rather than seconds, and every failure path -
     * including the gateway timeout that caused the 8% incident - is reachable
     * on demand.
     *
     * When a team says their architecture is testable, this is the observable
     * claim: the composition root has a second version with fakes in it, and
     * nothing in the domain had to change to make that possible.
     */
}

class PostgresCatalogue implements Catalogue {
    PostgresCatalogue(java.sql.Connection connection) { }
    @Override public Money priceOf(ProductId product) { return Money.aed(28000); }
    @Override public SellerId sellerOf(ProductId product) { return new SellerId("s1"); }
    @Override public boolean isAvailable(ProductId product, int qty) { return true; }
}

class OutOfStock extends RuntimeException {
    OutOfStock(ProductId p) { super("Out of stock: " + p.value()); }
}
