/*
 * ============================================================================
 * GOOD: location decided at runtime, in one place
 * ============================================================================
 *
 * Nothing in this file knows an IP address, a port, a database host or a
 * filesystem path. It knows LOGICAL NAMES, and asks something else where those
 * names currently live.
 *
 * The honest framing for the room: location coupling has not been removed. It
 * has been moved out of six services and into one registry plus one config file.
 * That is the whole win, and it is a big one, because the registry can now be
 * changed without redeploying anything that uses it.
 */
class Good_ResolvedEndpoints {

    private final ServiceRegistry registry;
    private final BlobStore blobs;

    Good_ResolvedEndpoints(ServiceRegistry registry, BlobStore blobs) {
        this.registry = registry;
        this.blobs = blobs;
    }

    java.util.List<ProductId> searchProducts(String query) {

        /*
         * The logical name is stable for the lifetime of the system. The address
         * behind it changes whenever ops needs it to: a maintenance drain, a new
         * server, a failover, a move to a different rack after Series B.
         *
         * Replay pain point P3 against this code. 10.0.3.14 loses its network.
         * Its health check fails. The registry stops returning it. The next call
         * resolves to one of the other two instances. Recovery time is the health
         * check interval, seconds rather than the 23 minutes it took to get a
         * human to edit a constant and ship a release.
         */
        Endpoint endpoint = registry.resolve("product-service");

        return HttpClient.get(endpoint.baseUrl() + "/products?q=" + query);
    }

    void storeProductImage(ProductId product, byte[] image) {
        /*
         * No path. The caller says WHAT it is storing; the adapter behind
         * BlobStore decides WHERE. On bare metal today that adapter writes to a
         * local disk or to MinIO. After Series B it can write to object storage,
         * and this line does not change.
         *
         * That is the same argument as the registry: the infrastructure is known
         * to be temporary, so the code should not encode it.
         */
        blobs.put("product-images/" + product.value(), image);
    }
}

/*
 * ============================================================================
 * The registry: the one place that knows where things are
 * ============================================================================
 */
interface ServiceRegistry {

    /**
     * Resolve a logical service name to a currently healthy instance.
     *
     * Deliberately minimal, because ShopStream is on bare metal for another nine
     * months and cannot run a service mesh. A first implementation can be a YAML
     * file plus a health-check loop, which one engineer can build in a week.
     *
     * The point of the interface is not the sophistication of today's
     * implementation. It is that replacing it later is a wiring change rather
     * than a six-service refactor. Design for the change you KNOW is coming
     * (the constraint has a date on it: Series B, approximately nine months),
     * which is different from designing for changes you are guessing at
     * (see folder 10).
     */
    Endpoint resolve(String logicalName);

    /**
     * Resolve for a specific market, so that data residency is enforced by
     * routing rather than by convention.
     *
     * A UAE buyer's personal data resolves to the UAE datacentre because the
     * BUYER'S MARKET says so, not because a developer typed the right hostname.
     * The mapping lives in configuration where a compliance auditor can read it
     * without opening an IDE, which is discussion question 3.
     */
    Endpoint resolveInMarket(String logicalName, String marketCode);
}

record Endpoint(String baseUrl, String instanceId, String marketCode) { }

/**
 * Storage as a capability, not a path.
 *
 * The service says what it wants stored. The adapter owns where. Today: a disk
 * or MinIO on bare metal. Later: object storage. The calling code cannot tell,
 * which is what makes the migration a deployment task instead of a project.
 */
interface BlobStore {
    void put(String key, byte[] content);
    byte[] get(String key);
    String publicUrl(String key);
}

/*
 * ============================================================================
 * A5 Problem 1, fixed: the browser goes through the gateway
 * ============================================================================
 *
 * The UI no longer holds a database URL, database credentials, or any knowledge
 * of the product schema. It calls one origin and gets JSON.
 *
 * What that restores, item by item against the four failures in the Bad_ file:
 *
 *   SECURITY       no credentials past the trust boundary; the DB is not
 *                  routable from the internet at all
 *   COUPLING       the UI depends on a JSON contract that can be versioned and
 *                  deprecated, not on a SQL schema that cannot
 *   AVAILABILITY   the gateway pools connections; 80,000 viewers share a pool
 *                  instead of each holding a connection
 *   OBSERVABILITY  all traffic is visible to gateway metrics, rate limits and
 *                  traces, so the dashboards stop lying during an incident (P4)
 *
 * And the performance problem that caused the shortcut in the first place
 * (pain point P2, 12 second search) gets fixed where it belongs: behind the
 * Product Service, with a read replica or a search index. That option only
 * became available once the database URL stopped being a compile-time constant.
 */
class Good_WebClientViaGateway {

    /*
     * One origin, injected from the environment. Not a constant, because staging,
     * production and the five new markets all need a different value, and none of
     * them should require a rebuild of the front end.
     */
    private final String apiOrigin;

    Good_WebClientViaGateway(String apiOrigin) {
        this.apiOrigin = apiOrigin;
    }

    java.util.List<ProductId> searchFromBrowser(String query) {
        return HttpClient.get(apiOrigin + "/api/v1/products?q=" + query);
    }
}

/*
 * ============================================================================
 * Discussion question 1, answered
 * ============================================================================
 *
 * "The Good_ version still contains the string \"product-service\". Is that
 *  location coupling?"
 *
 * No. It is NAME coupling, and it is the coupling you keep.
 *
 * The difference that matters is what has to change, and when:
 *
 *   "http://10.0.3.14:8080"   changes when HARDWARE changes - often,
 *                             unpredictably, and at the worst possible moment,
 *                             which is during an incident
 *
 *   "product-service"         changes when the ARCHITECTURE changes - rarely,
 *                             deliberately, with a design discussion attached
 *
 * The second is a dependency on a building block's IDENTITY, which is exactly
 * the dependency you meant to have. If the Product Service is renamed or split,
 * you SHOULD have to think about every caller. That is not accidental coupling,
 * that is the architecture doing its job.
 *
 * The general form, worth putting on a slide: you cannot reduce coupling to
 * zero, and trying to is how systems get an abstraction layer per developer.
 * What you do is make sure the coupling that remains is coupling to things that
 * change SLOWLY and DELIBERATELY, rather than to things that change QUICKLY and
 * ACCIDENTALLY. The target state has a name: loose, but functionally sufficient
 * coupling. See folder 08.
 */

class HttpClient { static <T> java.util.List<T> get(String url) { return java.util.List.of(); } }
