/*
 * ============================================================================
 * BAD: encapsulated, but hiding nothing
 * ============================================================================
 *
 * This class will pass a code review at most companies. The fields are private.
 * There are getters. There is a setter with a validation check in it. It looks
 * like textbook object orientation.
 *
 * It hides no design decision whatsoever, and every caller is coupled to the
 * internal data structures. That is the distinction this folder exists to make.
 */
class Bad_LiveStream_Exposed {

    private StreamId id;
    private SellerId seller;
    private String status;                        // "SCHEDULED", "LIVE", "ENDED"
    private java.util.List<BuyerId> viewers;      // the decision that will change
    private java.util.Map<String, Object> config; // the decision nobody can see
    private String cardNumberForTestPurchases;    // the decision that fails an audit
    private int maxViewers;

    /*
     * PROBLEM 1: THE GETTER THAT HANDS OUT THE INTERNALS
     *
     * This returns the actual list, not a copy. Every caller can now do:
     *
     *      stream.getViewers().add(buyer);      // bypasses the capacity check
     *      stream.getViewers().clear();         // ejects everyone, no audit trail
     *      stream.getViewers().size();          // couples the caller to List
     *
     * Two separate failures:
     *
     *   INVARIANTS ARE UNENFORCEABLE. `maxViewers` exists and is checked in
     *   addViewer() below, but addViewer() is not the only way in. The class
     *   cannot guarantee its own rule, which means the rule is not a rule.
     *
     *   THE STORAGE DECISION HAS LEAKED. Every caller now depends on viewers
     *   being a java.util.List living in this process's heap.
     *
     * The second one is the expensive one. ShopStream targets 10,000 concurrent
     * streams at up to 80,000 viewers each. That set is going to move to Redis or
     * to a presence service. With this signature, that migration is not a change
     * to this class - it is a change to every call site in the codebase, during
     * the run-up to National Day. Parnas's rule, violated exactly: the decision
     * most likely to change is the one most exposed.
     */
    java.util.List<BuyerId> getViewers() {
        return viewers;
    }

    /*
     * PROBLEM 2: THE VALIDATION THAT CAN BE WALKED AROUND
     *
     * The check is correct and it is pointless, because getViewers() above
     * offers an unguarded path to the same list.
     *
     * Worth saying plainly in class: a validation that has a bypass is worse than
     * no validation, because the team believes the rule is enforced. The
     * capacity limit exists in the code, in the tests, and in the design
     * document, and not in the running system.
     */
    void addViewer(BuyerId buyer) {
        if (viewers.size() >= maxViewers) {
            throw new IllegalStateException("Stream is full");
        }
        viewers.add(buyer);
    }

    /*
     * PROBLEM 3: THE UNTYPED CONFIGURATION BAG
     *
     * Same defect as the untyped event payload in folder 04, in a different
     * costume.
     *
     *      stream.getConfig().put("bitrate", "high");     // was it an int?
     *      stream.getConfig().get("max_bitrate");         // or maxBitrate?
     *      stream.getConfig().remove("region");           // who relied on that?
     *
     * There is no schema, no validation, no discoverability and no compile-time
     * check. The set of legal keys exists only in whatever code happens to read
     * them. Renaming one is undetectable until the behaviour silently changes.
     */
    java.util.Map<String, Object> getConfig() {
        return config;
    }

    /*
     * PROBLEM 4: THE ACCESSOR THAT EXPANDS THE PCI-DSS AUDIT SCOPE
     *
     * Added during testing "just for the sandbox", two years ago.
     *
     * PCI-DSS Level 1 scope covers every system component that stores, processes
     * or transmits cardholder data. Because this getter is public, every class
     * that can reach a Bad_LiveStream_Exposed can reach a card number. The audit
     * scope is therefore the whole application.
     *
     * That is not a hypothetical cost. It is weeks of assessor time, per year,
     * caused by one accessor that nobody removed.
     *
     * The fix is not "make it private and add a comment". The fix is that the
     * field does not exist on this class. If there is no field, there is nothing
     * to leak, nothing to log by accident during an incident, and nothing for an
     * assessor to trace. See folder 03 for the same move applied to database
     * access.
     */
    String getCardNumberForTestPurchases() {
        return cardNumberForTestPurchases;
    }

    /*
     * PROBLEM 5: THE STATUS STRING WITH A PUBLIC SETTER
     *
     * The stream lifecycle is a state machine: SCHEDULED -> LIVE -> ENDED. This
     * setter allows any caller to write any string at any time, including
     * "ENDED" -> "LIVE", including "live" in lower case, including "LVIE".
     *
     * The state machine exists in the team's shared understanding and nowhere in
     * the code. Compare with folder 05, where each state is its own type and the
     * illegal transitions do not compile.
     */
    void setStatus(String status) {
        this.status = status;
    }
}
