/*
 * ============================================================================
 * BAD: business rules that import a database driver
 * ============================================================================
 *
 * The dependency direction here follows the direction of control: the use case
 * calls the database, so the use case depends on the database.
 *
 * That feels natural, and it is the default outcome of writing code without
 * thinking about direction. It means ShopStream's business rules cannot be
 * compiled, read, tested or reasoned about without PostgreSQL, SMTP and a
 * payment vendor's SDK.
 */

// The high-level policy: what it means to place an order at ShopStream.
// This is the most valuable and longest-lived code in the system. Below, it is
// welded to three pieces of infrastructure that will all be replaced.
class Bad_HighLevelDependsOnLowLevel {

    /*
     * PROBLEM 1: CONCRETE INFRASTRUCTURE TYPES IN A POLICY CLASS
     *
     * Three fields, three concrete low-level classes.
     *
     * Note that these are injected through the constructor. This is Dependency
     * Injection, done properly, and it has bought testability with mocks. It has
     * NOT inverted anything: this class still names PostgresOrderDao, so the
     * dependency still points from high-level policy down to low-level detail.
     *
     * That distinction is discussion question 2 and it is the most commonly
     * confused pair of ideas in this whole area.
     */
    private final PostgresOrderDao orderDao;
    private final SmtpMailer mailer;
    private final CheckoutComClient paymentClient;

    Bad_HighLevelDependsOnLowLevel(PostgresOrderDao orderDao, SmtpMailer mailer,
                                   CheckoutComClient paymentClient) {
        this.orderDao = orderDao;
        this.mailer = mailer;
        this.paymentClient = paymentClient;
    }

    Order placeOrder(BuyerId buyer, ProductId product, int qty, String market) {

        /*
         * PROBLEM 2: SQL INSIDE THE BUSINESS RULE
         *
         * This method now knows there is a table called `products`, that it has
         * a column called `price_fils`, and that the query dialect is
         * PostgreSQL's.
         *
         * This is discussion question 3. ShopStream's plan to split the shared
         * schema (A5 Problem 4) so each service owns its data requires changing
         * every place that names a table. With SQL inside use cases, that is not
         * a data migration - it is a rewrite of the business logic, and it has to
         * happen inside the six-month MVP window.
         *
         * The line below is the reason the schema split is expensive.
         */
        long priceFils = orderDao.queryOne(
                "SELECT price_fils FROM products WHERE id = ?", product.value());
        Money net = new Money(priceFils * qty, "AED");

        // Business rule, buried between two infrastructure calls and therefore
        // untestable without a database. This is discussion question 1.
        int vatBasisPoints = market.equals("SA") ? 1500 : 500;
        Money gross = net.plus(new Money(net.minorUnits() * vatBasisPoints / 10_000, "AED"));

        /*
         * PROBLEM 3: A VENDOR SDK IN A POLICY CLASS
         *
         * `chargeCard` is Checkout.com's method name, taking Checkout.com's
         * argument shapes. The business rule "an order must be paid for before
         * it is confirmed" is now expressed in a vendor's vocabulary.
         *
         * Folder 12 covered why this leaks. Here the point is direction: the
         * arrow runs from ShopStream's most valuable code to a third party's
         * client library.
         */
        CheckoutComResponse response = paymentClient.chargeCard(
                buyer.value(), gross.minorUnits(), "aed");

        Order order = new Order(OrderId.next(), buyer, null, null, java.util.List.of(),
                "10000".equals(response.code) ? OrderStatus.AUTHORISED : OrderStatus.CANCELLED);

        orderDao.insert("INSERT INTO orders (id, buyer_id, total_fils, status) VALUES (?,?,?,?)",
                order.id().value(), buyer.value(), gross.minorUnits(), order.status().name());

        // PROBLEM 4: SMTP, in a class about placing orders.
        mailer.sendSmtp(lookupEmail(buyer), "Order confirmed", "Thanks!");

        return order;
    }

    private String lookupEmail(BuyerId b) { return "buyer@example.com"; }
}

/*
 * ============================================================================
 * And the version that looks like it fixed it, but did not
 * ============================================================================
 *
 * This is the trap worth showing the room, because most codebases that have
 * "adopted clean architecture" are here rather than at the Good_ file.
 *
 * There are interfaces. There is injection. There is a persistence package.
 * The package structure would satisfy most reviewers.
 *
 * Look at where the interface is DEFINED.
 */

// ---- package: persistence ---------------------------------------------------
interface OrderRepository {                       // defined in the persistence layer
    void save(Order order);
    java.util.Optional<Order> findById(OrderId id);

    /*
     * And it is shaped by the database, not by the caller. `findAllByStatus`
     * with a string status, `saveAll` for batch efficiency, and a `flush` that
     * only means anything to an ORM. No use case asked for any of these; the
     * persistence layer offered them.
     *
     * An interface written by the low-level module, for the low-level module's
     * convenience, is not an inversion. It is the implementation's public API
     * with the word "interface" in front of it.
     */
    java.util.List<Order> findAllByStatus(String status);
    void saveAll(java.util.List<Order> orders);
    void flush();
}

// ---- package: domain --------------------------------------------------------
class StillNotInverted {

    // The domain package now imports from the persistence package.
    private final OrderRepository repository;

    StillNotInverted(OrderRepository repository) {
        this.repository = repository;
    }

    /*
     * Apply the compile test from the README:
     *
     *     "Could you delete the persistence package and still compile domain?"
     *
     * No. The domain still depends on persistence; the only change is that it
     * depends on an interface in persistence rather than a class in persistence.
     * The arrow points the same way it always did.
     *
     * The interface moved. The dependency did not. And because the shape of the
     * interface is dictated by the database, the domain has also inherited
     * `flush()` - a concept that means nothing in the business language and
     * exists purely because an ORM is involved.
     */
    void confirm(OrderId id) {
        Order order = repository.findById(id).orElseThrow();
        repository.save(order.withStatus(OrderStatus.CONFIRMED));
        repository.flush();
    }
}

// ---- Supporting cast, elided ------------------------------------------------
class PostgresOrderDao   { long queryOne(String sql, Object... a) { return 28000L; }
                           void insert(String sql, Object... a) { } }
class SmtpMailer         { void sendSmtp(String to, String subject, String body) { } }
class CheckoutComClient  { CheckoutComResponse chargeCard(String token, long minor, String cur) {
                               return new CheckoutComResponse(); } }
class CheckoutComResponse{ String code = "10000"; }
