/*
 * ============================================================================
 * BAD: seven concerns, one method
 * ============================================================================
 *
 * The exercise before the commentary: read the method once and mark where one
 * concern ends and the next begins. Then count how many DIFFERENT KINDS OF
 * EXPERTISE a reviewer needs in order to approve a change to it.
 *
 * The answer is roughly: HTTP, authentication, validation, tax law, SQL, PDF
 * rendering and SMTP. Nobody has all seven. So the method gets approved by
 * people who each understand a seventh of it, which is how a VAT rate ends up
 * wrong in production for three weeks.
 */
class Bad_CheckoutEverything {

    private final java.sql.Connection db;
    private final SmtpClient smtp;
    private final PdfRenderer pdf;

    Bad_CheckoutEverything(java.sql.Connection db, SmtpClient smtp, PdfRenderer pdf) {
        this.db = db;
        this.smtp = smtp;
        this.pdf = pdf;
    }

    /**
     * Handles POST /checkout.
     */
    String checkout(HttpRequest request) throws Exception {

        // ---- CONCERN 1: HTTP and serialisation ------------------------------
        // Changes when the API contract changes, or a client sends something new.
        String buyerId = request.param("buyer_id");
        String productId = request.param("product_id");
        int qty = Integer.parseInt(request.param("qty"));
        String market = request.header("X-Market");

        // ---- CONCERN 2: authentication and authorisation --------------------
        // Changes when the security model changes. Security cannot audit this
        // without reading the tax logic forty lines below, and they will not.
        String token = request.header("Authorization");
        if (token == null || !TokenVerifier.isValid(token)) {
            return "401 Unauthorized";
        }

        // ---- CONCERN 3: input validation -------------------------------------
        // Changes when business rules about valid orders change.
        if (qty <= 0 || qty > 50) {
            return "400 Bad Request: invalid quantity";
        }

        // ---- CONCERN 4: pricing and tax --------------------------------------
        //
        // This is the part with actual business value, and it is buried at line
        // 40 of an HTTP handler, tangled with SQL.
        //
        // Consequences:
        // - It cannot be tested without a web request and a database.
        // - It cannot be reused by the quote endpoint or the seller report, so
        //     it gets copy-pasted there. Folder 11.
        // - A finance change requires editing a file called CheckoutHandler,
        //     which nobody in finance will ever look at.
        // - The Egypt launch (14% VAT, not 5%) means editing this method.
        var rs = db.prepareStatement("SELECT price_fils FROM products WHERE id = ?")
                .executeQuery();
        long unitPrice = rs.getLong("price_fils");
        long net = unitPrice * qty;
        long vat = market.equals("SA") ? (net * 15 / 100) : (net * 5 / 100);
        long gross = net + vat;

        // ---- CONCERN 5: persistence -------------------------------------------
        // Changes when the schema changes, or when ShopStream splits the shared
        // database (A5 Problem 4). Note the raw SQL: the storage decision is not
        // hidden behind anything, so changing it means changing this method.
        String orderId = java.util.UUID.randomUUID().toString();
        db.prepareStatement(
                "INSERT INTO orders (id, buyer_id, product_id, qty, total_fils, status) " +
                "VALUES ('" + orderId + "','" + buyerId + "','" + productId + "'," + qty + "," + gross + ",'PLACED')")
          .execute();

        // ---- CONCERN 6: document rendering ------------------------------------
        // Changes when finance changes the invoice layout. On the buyer's request
        // thread, so a slow PDF library is a slow checkout (folder 05).
        byte[] invoice = pdf.renderInvoice(orderId, gross);

        // ---- CONCERN 7: notification -------------------------------------------
        // Changes when marketing changes the copy. Also on the request thread.
        smtp.sendWithAttachment(lookupEmail(buyerId), "Your ShopStream order", invoice);

        return "200 OK: " + orderId;
    }

    /*
     * WHY THIS PRODUCES PAIN POINT P4 (four hours to root cause)
     *
     * During the National Day incident, checkout was failing. The stack trace
     * pointed at checkout(). That is the entire diagnostic signal, because
     * everything is one frame.
     *
     * Ops could not distinguish between:
     *
     * - the database being slow
     * - the PDF renderer running out of heap on a large invoice
     * - the SMTP relay refusing connections
     * - the token verifier timing out against the auth service
     *
     * All four look identical from outside: "checkout is slow". There is no
     * boundary between the concerns, so there is nowhere to attach a timer, a
     * counter or a span. You cannot instrument what you have not separated.
     *
     * This is the strongest practical argument for SoC and it has nothing to do
     * with code beauty. Separation of concerns is what makes a system
     * OBSERVABLE, because boundaries are the only places you can measure.
     */

    private String lookupEmail(String buyerId) { return "buyer@example.com"; }
}

// ---- Supporting cast, elided ------------------------------------------------
interface HttpRequest  { String param(String name); String header(String name); }
interface SmtpClient   { void sendWithAttachment(String to, String subject, byte[] file); }
interface PdfRenderer  { byte[] renderInvoice(String orderId, long totalFils); }
class TokenVerifier    { static boolean isValid(String token) { return true; } }
