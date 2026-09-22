/*
 * ============================================================================
 * BAD: an interface that abstracts nothing
 * ============================================================================
 *
 * This one is worth spending time on, because it passes review at most
 * organisations. There is an interface. Classes implement it. The package
 * structure is correct. An architecture diagram drawn from this code shows a
 * clean boundary between the Order Service and the payment provider.
 *
 * Read the SIGNATURES rather than the structure. Every one of them names
 * Checkout.com.
 */
interface Bad_LeakyAbstraction {

    /*
     * LEAK 1: A VENDOR TYPE IN THE PARAMETER
     *
     * To call this, the Order Service must construct a CheckoutComRequest: their
     * field names, their required fields, their idea of how an amount is
     * represented, their nested "source" object.
     *
     * The Order Service now depends on Checkout.com's data model. The interface
     * did not prevent that dependency, it documented it.
     *
     * LEAK 2: A VENDOR TYPE AS THE RETURN
     *
     * Callers read CheckoutComResponse.responseCode(), which is a string like
     * "10000" meaning approved. So business logic in the Order Service now
     * contains Checkout.com's status code table. When PayTabs returns "A" for
     * approved, every one of those comparisons is wrong.
     *
     * LEAK 3: A VENDOR EXCEPTION IN THE THROWS CLAUSE
     *
     * This is the worst of the three, because it is contagious. Every caller
     * must catch it or declare it, so the vendor type propagates up through the
     * use case layer, into the HTTP handler, and often into a shared error
     * mapper - the exact opposite of containment.
     *
     * It also means the ARCHITECTURE cannot be fixed without touching every
     * caller, even though the "abstraction" is already in place.
     */
    CheckoutComResponse charge(CheckoutComRequest request) throws CheckoutComHttpException;

    /*
     * LEAK 4: THE VENDOR'S PAGINATION MODEL, AND AN HTTP CONCEPT IN THE NAME
     *
     * `cursor` is how Checkout.com pages. PayTabs uses page numbers. Any second
     * implementation has to either fake a cursor or break the contract.
     *
     * `httpGet` in a method name is a protocol detail on the wrong side of a
     * boundary. If the next provider offers a message-based integration rather
     * than REST, this method name is a lie.
     */
    CheckoutComPagedResult httpGetTransactions(String cursor, int limit);

    /*
     * LEAK 5: VENDOR CONFIGURATION IN THE LIFECYCLE
     *
     * A secret key and a base URL are Checkout.com's authentication model. A
     * provider that uses mutual TLS, or a signed JWT, or an on-premises HSM,
     * cannot implement this method honestly. It will implement it by ignoring
     * both arguments, which is a silent lie the compiler will happily accept.
     */
    void configure(String secretKey, String baseUrl);
}

/*
 * ============================================================================
 * What this costs a caller
 * ============================================================================
 */
class Bad_OrderServiceUsingLeakyGateway {

    private final Bad_LeakyAbstraction gateway;

    Bad_OrderServiceUsingLeakyGateway(Bad_LeakyAbstraction gateway) {
        this.gateway = gateway;
    }

    /*
     * The dependency is injected and typed as an interface. By the rules of
     * folder 02 this looks decoupled. Now read the body.
     */
    OrderStatus pay(Order order) {
        // Constructing a vendor request object, in the Order Service.
        CheckoutComRequest request = new CheckoutComRequest();
        request.amount = order.total().minorUnits();
        request.currency = order.total().currency().toLowerCase();  // their convention
        request.reference = order.id().value();
        request.source = new CheckoutComSource("tok_...");          // their nested model

        try {
            CheckoutComResponse response = gateway.charge(request);

            // Checkout.com's status code table, now business logic in the Order
            // Service. Nobody outside Checkout.com knows what 10000 means, and
            // this line will still say 10000 long after the provider changes.
            if ("10000".equals(response.responseCode)) {
                return OrderStatus.AUTHORISED;
            }
            if ("20051".equals(response.responseCode)) {   // insufficient funds
                return OrderStatus.CANCELLED;
            }
            return OrderStatus.CANCELLED;

        } catch (CheckoutComHttpException e) {
            /*
             * And here the vendor's exception type is caught inside the Order
             * Service. This class cannot be compiled without Checkout.com's
             * library on the classpath.
             *
             * Ask the room the question that settles it:
             *
             *     "Delete Checkout.com. What still compiles?"
             *
             * Not this class. Not the use case above it. Not the tests. The
             * interface bought nothing, and the diagram that showed a clean
             * boundary was wrong.
             */
            return OrderStatus.PLACED;
        }
    }
}

// ---- Vendor types, which should never have been visible outside the adapter --
class CheckoutComRequest    { long amount; String currency; String reference; CheckoutComSource source; }
class CheckoutComSource     { String token; CheckoutComSource(String token) { this.token = token; } }
class CheckoutComResponse   { String id; String responseCode; String responseSummary; }
class CheckoutComPagedResult{ java.util.List<CheckoutComResponse> data; String nextCursor; }
class CheckoutComHttpException extends Exception { int statusCode; }
