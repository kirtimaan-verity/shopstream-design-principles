/*
 * ============================================================================
 * BAD: one class, four departments
 * ============================================================================
 *
 * Run the actor test before reading the commentary. For each block in
 * placeOrder(), write down WHO AT SHOPSTREAM would file the ticket that changes
 * it.
 *
 * Four different answers means four reasons to change, which means this class
 * will be edited four times as often as any of its parts needs to be, and every
 * one of those edits carries the risk of the other three.
 */
class Bad_OrderService_GodService {

    private final java.sql.Connection db;
    private final SmtpClient smtp;
    private final PaymentGateway gateway;
    private final AuditLog auditLog;

    Bad_OrderService_GodService(java.sql.Connection db, SmtpClient smtp,
                                PaymentGateway gateway, AuditLog auditLog) {
        this.db = db;
        this.smtp = smtp;
        this.gateway = gateway;
        this.auditLog = auditLog;
    }

    Order placeOrder(BuyerId buyer, ProductId product, int qty, String market) {

        Money unitPrice = lookupPrice(product);
        Money net = unitPrice.times(qty);

        /*
         * ACTOR 1: FINANCE
         *
         * Changes when tax law changes or a new market launches. Finance owns
         * this rule and cannot read it, because it is embedded in a method they
         * would never open.
         *
         * The Egypt launch is a change to this block, in this file.
         */
        int vatBasisPoints = switch (market) {
            case "SA" -> 1500;
            case "AE" -> 500;
            default   -> 500;      // silently wrong for every new market
        };
        Money gross = net.plus(new Money(net.minorUnits() * vatBasisPoints / 10_000, net.currency()));

        /*
         * ACTOR 2: OPS
         *
         * Changes after an incident. Three attempts, one second apart, with a
         * thirty second timeout on each - so a degraded gateway holds this thread
         * for ninety seconds.
         *
         * After the National Day outage, Ops needed to change these numbers
         * urgently. They had to do it in a file containing tax rules and
         * marketing copy, at 3am, under incident pressure, with a review from
         * whoever was awake. That is the SRP failure expressed as an operational
         * risk rather than a design opinion.
         */
        PaymentResult payment = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            payment = gateway.charge(product.value(), gross, 30_000);
            if (payment.authorised()) break;
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
        }

        /*
         * ACTOR 3: COMPLIANCE
         *
         * Changes when a PCI-DSS assessor asks for a different audit record.
         * Compliance owns the requirement and cannot verify it without reading a
         * method that also calculates VAT and sends email.
         *
         * Worth noting: an assessor reviewing this code has to read the whole
         * method to establish that no cardholder data is logged anywhere in it.
         * A dedicated audit class would take them two minutes.
         */
        auditLog.record("payment_attempt", buyer.value(), gross.minorUnits(), payment.authorised());

        OrderId orderId = OrderId.next();
        // ... persist ...

        /*
         * ACTOR 4: MARKETING
         *
         * Changes weekly. Subject lines, body copy, links, tone, seasonal
         * variations.
         *
         * So the file containing the payment retry policy, the tax rules and the
         * compliance audit record is modified every week for a copy change. Every
         * one of those releases is a release of the checkout path.
         *
         * This is the sentence that lands the principle with a room: MARKETING
         * CANNOT CHANGE AN EMAIL SUBJECT LINE WITHOUT REDEPLOYING THE CODE THAT
         * TAKES MONEY.
         */
        String subject = "Your ShopStream order is confirmed";
        String body = "Thanks for shopping with us! Your order " + orderId.value()
                + " for " + gross.minorUnits() / 100.0 + " AED is on its way.";
        smtp.send(lookupEmail(buyer), subject, body);

        return null;
    }

    private Money lookupPrice(ProductId p) { return Money.aed(28000); }
    private String lookupEmail(BuyerId b) { return "buyer@example.com"; }
}

// ---- Supporting cast, elided ------------------------------------------------
interface SmtpClient     { void send(String to, String subject, String body); }
interface PaymentGateway { PaymentResult charge(String ref, Money amount, int timeoutMs); }
interface AuditLog       { void record(String event, String subject, long amount, boolean ok); }
