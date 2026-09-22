/*
 * ============================================================================
 * BAD: the deployment topology, compiled into the application
 * ============================================================================
 *
 * Before reading the commentary, run the exercise that works best in the room:
 *
 *      "Ops needs to drain 10.0.3.14 for a disk replacement on Saturday.
 *       List everything that has to happen."
 *
 * The answer involves a code change, a code review, a build, a release, and a
 * coordinated deploy of at least three services - for a hardware maintenance
 * task. That gap between "an ops action" and "a release" is what location
 * coupling costs, and it is why nobody drained that server before it failed.
 */
class Bad_HardcodedEndpoints {

    /*
     * PROBLEM 1: SERVICE ADDRESSES AS CONSTANTS
     *
     * Three bare-metal servers. Each service pinned to one of them by IP.
     *
     * This is pain point P3 in source form. When 10.0.3.14 lost its network,
     * every caller of the Product Service kept dialling a machine that was not
     * answering. The other two servers were healthy, idle, and unreachable,
     * because nothing in the system knew they were an option.
     *
     * A retry does not help here, and neither does a longer timeout. Both make
     * it worse, because they hold threads open against a destination that is
     * definitionally unavailable. This is why P3 lasted 23 minutes: the recovery
     * path was a human editing a constant.
     */
    private static final String PRODUCT_SERVICE   = "http://10.0.3.14:8080/products";
    private static final String ORDER_SERVICE     = "http://10.0.3.15:8080/orders";
    private static final String PAYMENT_SERVICE   = "http://10.0.3.16:8080/payments";

    /*
     * PROBLEM 2: THE DATABASE URL, IN APPLICATION CODE
     *
     * Host, port, database name, and in this case the credentials too. This
     * string is in git history forever, which means the password is in git
     * history forever, which means rotating it is a repository operation.
     *
     * Note also what it prevents. ShopStream cannot introduce a read replica for
     * the catalogue search that is degrading to 12 seconds (pain point P2),
     * because "which database do I read from" is not a decision this code is
     * capable of making. The performance fix is blocked by a coupling problem.
     */
    private static final String DB_URL =
            "jdbc:postgresql://10.0.3.14:5432/shopstream?user=app&password=hunter2";

    /*
     * PROBLEM 3: FILESYSTEM PATHS
     *
     * Product images and stream thumbnails, written to a local disk path.
     *
     * This silently makes the service STATEFUL. It can no longer be moved,
     * replicated or restarted on another machine without the files coming with
     * it. Somebody will eventually solve that with an NFS mount, which converts
     * a location coupling into a shared-storage single point of failure.
     */
    private static final String UPLOAD_DIR = "/var/shopstream/uploads";

    /*
     * PROBLEM 4: THE CDN AND THE GATEWAY KEYS
     *
     * Third-party endpoints and the environment baked in together. Note
     * "prod" in the URL: staging either points at production, or somebody keeps
     * a locally edited copy of this file that must never be committed. Both of
     * those happen, and the second one is how a test order reaches a real card.
     */
    private static final String CDN_INGEST = "https://ingest-prod.cdnvendor.com/v2/live";
    private static final String CHECKOUT_COM_URL = "https://api.checkout.com/payments";

    java.util.List<ProductId> searchProducts(String query) {
        return HttpClient.get(PRODUCT_SERVICE + "?q=" + query);
    }
}

/*
 * ============================================================================
 * A5 PROBLEM 1, in code: the browser talks straight to the database
 * ============================================================================
 *
 * From the audit diagram: "UI -> Product Service DB (DIRECT - bypasses API
 * Gateway for product search)".
 *
 * The reason this exists is always the same and always sympathetic: search
 * through the gateway was slow, someone measured a direct query as faster, and
 * it shipped during a crunch. Pain point P2 (12 second search) is what made it
 * look like a good idea.
 *
 * What it actually bought:
 */
class Bad_WebClientDirectDbAccess {

    private static final String PRODUCT_DB =
            "jdbc:postgresql://10.0.3.14:5432/shopstream?user=readonly&password=ro_pass";

    java.util.List<ProductId> searchFromBrowser(String query) {
        /*
         *   SECURITY: database credentials are now on the client side of the
         *   trust boundary. "readonly" is not a mitigation; readonly still reads
         *   every row of every table it can reach, including any buyer data
         *   sharing that schema (A5 Problem 4). This is a data-residency
         *   incident waiting for someone to notice.
         *
         *   COUPLING: the UI is now coupled to the SQL SCHEMA. Renaming a column
         *   breaks the front end. There is no contract, no version, no
         *   deprecation window and no compile-time warning, in the component
         *   that is deployed most often and tested least.
         *
         *   AVAILABILITY: no pooling and no gateway means every browser holds a
         *   connection. At 80,000 concurrent viewers per stream, the connection
         *   limit is reached long before the CPU is. Search does not degrade,
         *   it stops.
         *
         *   OBSERVABILITY: this traffic is invisible to the gateway, so it does
         *   not appear in the request metrics, the rate limits, or the traces.
         *   During an incident the dashboards show a healthy system while a
         *   third of the traffic is failing. This is a direct contributor to
         *   pain point P4 and the 4 hour root cause.
         *
         * The lesson worth stating: this shortcut was taken to fix a PERFORMANCE
         * problem and it created a SECURITY, COUPLING, AVAILABILITY and
         * OBSERVABILITY problem. The right fix for slow search was a read replica
         * or an index, both of which were unavailable precisely because of the
         * location coupling above.
         */
        return Jdbc.query(PRODUCT_DB, "SELECT id FROM products WHERE name ILIKE ?", query);
    }
}

// ---- Supporting cast, elided ------------------------------------------------
class HttpClient { static <T> java.util.List<T> get(String url) { return java.util.List.of(); } }
class Jdbc       { static <T> java.util.List<T> query(String url, String sql, Object... a) { return java.util.List.of(); } }
