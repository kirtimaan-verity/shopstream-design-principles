/*
 * ============================================================================
 * GOOD: the decisions likely to change are the decisions that are hidden
 * ============================================================================
 *
 * The test applied to every member of this class was Parnas's question:
 *
 *      "Is this a design decision that is likely to change? If so, can I change
 *       it without any caller noticing?"
 *
 * Concretely, the decision this class is protecting is HOW AND WHERE VIEWERS ARE
 * STORED. At 80,000 viewers per stream and 10,000 concurrent streams, that
 * decision has a scheduled expiry date. Everything else here follows from
 * wanting to be able to change it in an afternoon.
 */
class Good_LiveStream {

    private final StreamId id;
    private final SellerId seller;
    private final int capacity;

    /*
     * The viewer set is behind an interface, not a List.
     *
     * Today the implementation is an in-process HashSet, which is correct for
     * the current 1,200 concurrent streams and cheap to run on three bare-metal
     * servers. Tomorrow it is a Redis set, or a presence service.
     *
     * The migration is: write one new implementation of ViewerSet, change one
     * line of wiring. No caller of this class changes, because no caller has
     * ever been told how viewers are stored.
     *
     * Note the difference from folder 06's registry, which moved a decision from
     * code into configuration. This moves a decision from EVERY CALLER into ONE
     * CLASS. Both are information hiding; they hide different kinds of decision.
     */
    private final ViewerSet viewers;

    private StreamState state;

    Good_LiveStream(StreamId id, SellerId seller, int capacity, ViewerSet viewers) {
        this.id = id;
        this.seller = seller;
        this.capacity = capacity;
        this.viewers = viewers;
        this.state = StreamState.SCHEDULED;
    }

    /*
     * BEHAVIOUR, NOT DATA ACCESS
     *
     * Compare the two vocabularies:
     *
     *      Bad:   stream.getViewers().add(buyer)
     *      Good:  stream.join(buyer)
     *
     * The first is a caller reaching in and manipulating a data structure. The
     * second is a caller asking the stream to do something, in the language the
     * business uses.
     *
     * Because there is exactly one way in, the invariants below are actually
     * enforced rather than merely written down.
     */
    void join(BuyerId buyer) {
        if (state != StreamState.LIVE) {
            throw new IllegalStateException("Cannot join a stream that is not live");
        }
        if (viewers.size() >= capacity) {
            throw new StreamAtCapacity(id, capacity);
        }
        viewers.add(buyer);
    }

    void leave(BuyerId buyer) {
        viewers.remove(buyer);
    }

    /*
     * The question callers actually ask.
     *
     * Nobody outside this class ever needed the list of viewers. They needed the
     * COUNT, for the viewer badge on the stream overlay. Exposing the list to
     * satisfy a request for a number is how the leak started.
     *
     * When ViewerSet moves to Redis this becomes a SCARD, and the change is
     * invisible from here. When it needs caching because the overlay polls it
     * every second across 10,000 streams, that cache goes inside ViewerSet and
     * is also invisible from here.
     */
    int viewerCount() {
        return viewers.size();
    }

    /*
     * Where a caller genuinely needs the members - moderation tooling has to
     * list viewers to ban one - the class returns an unmodifiable snapshot.
     *
     * Honest note, which is discussion question 3: at 80,000 viewers this copy is
     * expensive and this signature is a trap. The right long-term shape is a
     * paged or streamed query on ViewerSet, or a dedicated moderation read
     * model. It is left here deliberately as a discussion point, because
     * "unmodifiable copy" is the standard textbook answer and it stops being the
     * right answer at ShopStream's scale.
     *
     * The general lesson: information hiding does not mean copying everything
     * defensively. It means the caller does not learn how the data is stored,
     * and a paged query hides that just as well as a copy while costing far less.
     */
    java.util.Set<BuyerId> viewersForModeration() {
        return java.util.Set.copyOf(viewers.snapshot());
    }

    /*
     * State transitions are METHODS, not a setter.
     *
     *      Bad:   stream.setStatus("LIVE")     any string, any time, any order
     *      Good:  stream.goLive()              legal transitions only
     *
     * The state machine now lives in the code that owns it. An illegal
     * transition fails here, at the source, with a message that names the actual
     * problem, instead of surfacing later as a stream that accepts orders after
     * it has ended.
     *
     * Folder 05 takes this one step further and makes illegal transitions fail at
     * COMPILE time by giving each state its own type. That is stronger and costs
     * more types; for a three-state machine this is usually the right amount.
     */
    void goLive() {
        if (state != StreamState.SCHEDULED) {
            throw new IllegalStateException("Only a scheduled stream can go live");
        }
        state = StreamState.LIVE;
    }

    void end() {
        if (state != StreamState.LIVE) {
            throw new IllegalStateException("Only a live stream can end");
        }
        state = StreamState.ENDED;
        viewers.clear();
    }

    boolean isAcceptingOrders() {
        return state == StreamState.LIVE;
    }

    /*
     * WHAT IS NOT HERE, and why each absence is deliberate:
     *
     *   No getViewers()          the storage decision stays inside
     *   No setStatus()           the state machine stays enforceable
     *   No getConfig()           configuration is typed and passed in, not a bag
     *   No card number           PCI-DSS audit scope excludes this class entirely
     *   No getId().setValue()    identity is immutable, as identity should be
     *
     * A useful review question when reading somebody else's class: not "what does
     * this expose?" but "what could I change in here tomorrow without anybody
     * finding out?" If the answer is nothing, the class hides nothing, whatever
     * its fields are marked.
     */

    StreamId id() { return id; }
    SellerId seller() { return seller; }
}

/**
 * The hidden decision, expressed as a port.
 *
 * Today: InMemoryViewerSet, a HashSet. Correct for 1,200 concurrent streams on
 * three bare-metal servers, and it costs nothing to run.
 *
 * When the numbers move: RedisViewerSet. One new class, one wiring line.
 *
 * This is the same pattern as folder 01's SPI and folder 06's registry. Each one
 * names a decision that the brief says will change, and puts an interface in
 * front of it. The pattern is cheap. Choosing WHICH decisions deserve it is the
 * architecture work, and getting that wrong in either direction is what folders
 * 10 and 15 are about.
 */
interface ViewerSet {
    void add(BuyerId buyer);
    void remove(BuyerId buyer);
    int size();
    void clear();
    java.util.Collection<BuyerId> snapshot();
}

enum StreamState { SCHEDULED, LIVE, ENDED }

class StreamAtCapacity extends RuntimeException {
    StreamAtCapacity(StreamId id, int capacity) {
        super("Stream " + id.value() + " is at capacity (" + capacity + ")");
    }
}
