/*
 * ============================================================================
 * BAD: infrastructure for requirements that do not exist yet
 * ============================================================================
 *
 * The current pricing requirement, in full:
 *
 *      total = unit price x quantity, minus any discount, plus market VAT
 *
 * What follows is the machinery a team built for that, because auction mode, AR
 * try-on and seller subscription tiers are "on the roadmap".
 *
 * As you read it, keep a running count of how many of these types are needed to
 * express the requirement above. The answer is one.
 */

// ---- Type 1 ---------------------------------------------------------------
interface PricingRule {
    Money apply(PricingContext context, Money runningTotal);
    int priority();
    boolean appliesTo(PricingContext context);
}

// ---- Type 2 ---------------------------------------------------------------
/**
 * Nineteen fields, of which four are used. The rest are for auction mode, AR
 * try-on and subscription tiers, none of which have a specification, an owner or
 * a date.
 *
 * The cost is not the unused fields. It is that every developer reading this
 * class has to determine which four matter, every time, forever - and that a
 * new field for a real requirement now has to be threaded through a context
 * object that is already too big to reason about.
 */
class PricingContext {
    ProductId product; int quantity; String market; BuyerId buyer;
    SellerId seller; StreamId stream; java.time.Instant when;
    String subscriptionTier;        // subscription tiers: not built, no spec
    String auctionId;               // auction mode: not built, no spec
    Money currentBid;               // auction mode
    Money reservePrice;             // auction mode
    boolean isArTryOnSession;       // AR try-on: listed as "future" in the brief
    String promoCode; String affiliateId; String deviceType;
    String cohort; boolean isFirstPurchase; int loyaltyPoints;
    java.util.Map<String, String> experimentFlags;
}

// ---- Type 3 ---------------------------------------------------------------
interface PricingRuleFactory {
    PricingRule create(java.util.Map<String, String> config);
    String ruleType();
}

// ---- Type 4 ---------------------------------------------------------------
/**
 * A registry, so rules can be discovered at runtime.
 *
 * Nothing discovers rules at runtime. Rules are registered in a startup class,
 * in a fixed order, in the same repository. The indirection exists to support a
 * deployment model nobody has proposed.
 */
class PricingRuleRegistry {
    private final java.util.Map<String, PricingRuleFactory> factories = new java.util.HashMap<>();

    void register(PricingRuleFactory factory) {
        factories.put(factory.ruleType(), factory);
    }

    /*
     * A plugin loader, so third parties can supply pricing rules.
     *
     * There are no third parties. ShopStream is a closed platform with a single
     * engineering team on three bare-metal servers.
     *
     * What this line actually created: a class-loading path, a code-injection
     * surface, a startup failure mode when a jar is missing, and a debugging
     * experience where the pricing rule that ran is not visible in any source
     * file. On a system holding PCI-DSS Level 1, an unrestricted plugin loader
     * is also a finding waiting to be written up.
     */
    void loadPluginsFrom(String directory) {
        // reflection, URLClassLoader, ServiceLoader over a directory of jars
    }

    java.util.List<PricingRule> rulesFor(PricingContext context) {
        return factories.values().stream()
                .map(f -> f.create(java.util.Map.of()))
                .filter(r -> r.appliesTo(context))
                .sorted(java.util.Comparator.comparingInt(PricingRule::priority))
                .toList();
    }
}

// ---- Type 5 ---------------------------------------------------------------
/**
 * A rule expression language, so business users can change pricing without a
 * developer.
 *
 * No business user has asked for this. No business user has been shown it. There
 * is no editor, no validation, no test harness and no way to preview the effect
 * of a change before it applies to live revenue.
 *
 * The trap is subtle and worth naming: a DSL does not remove the need for a
 * developer, it replaces a well-tooled language (Java, with an IDE, a compiler,
 * a debugger and a test framework) with a badly-tooled one that the team has to
 * build and maintain themselves. The maintenance cost is permanent.
 */
class PricingExpressionParser {
    PricingRule parse(String expression) {
        // "IF market == 'SA' THEN vat(15) ELSE vat(5)"
        // a tokeniser, a parser, an AST, an evaluator, and error messages for
        // business users who will never see them
        return null;
    }
}

// ---- Type 6 ---------------------------------------------------------------
abstract class AbstractPricingEngine {
    protected abstract PricingRuleRegistry registry();
    protected abstract PricingContextEnricher enricher();

    public final Money calculate(PricingContext raw) {
        PricingContext enriched = enricher().enrich(raw);
        Money total = Money.aed(0);
        for (PricingRule rule : registry().rulesFor(enriched)) {
            total = rule.apply(enriched, total);
        }
        return total;
    }
}

// ---- Type 7 ---------------------------------------------------------------
interface PricingContextEnricher {
    PricingContext enrich(PricingContext context);
}

/*
 * ============================================================================
 * THE RECKONING
 * ============================================================================
 *
 * Seven types, roughly 200 lines, a reflection-based plugin loader and a
 * hand-written expression language. Delivered capability: multiply two numbers
 * and add a percentage.
 *
 * WHAT IT COST, in terms the brief cares about:
 *
 *   TIME. Weeks, against a six-month MVP deadline, taken from the four coupling
 *   defects that caused the last outage. That is the argument that actually
 *   persuades a CTO, and it is worth making in those words rather than in terms
 *   of elegance.
 *
 *   DEBUGGING. "Why was this buyer charged 47 AED?" now requires knowing which
 *   rules were registered, in what order, with what priorities, from which jars.
 *   During an incident, with pain point P4 already at four hours.
 *
 *   ONBOARDING. A new engineer has to understand seven types before changing a
 *   VAT rate.
 *
 *   SECURITY. A plugin loader on a PCI-DSS Level 1 system.
 *
 * AND THE PART THAT SURPRISES PEOPLE - IT DOES NOT EVEN HELP:
 *
 * Auction mode arrives in year two. Real auction pricing needs bid state, a
 * closing time, reserve-price logic, anti-sniping extensions and concurrency
 * control across thousands of simultaneous bidders. None of that is a
 * "PricingRule that maps a context to a Money". The abstraction was guessed
 * before the requirement existed, and it guessed wrong.
 *
 * So the team pays twice: once to remove this, once to build the right thing.
 * The speculative flexibility was not merely useless, it was an OBSTACLE to the
 * change it was built to accommodate. That is the strongest version of the YAGNI
 * argument and it is worth landing hard, because "keep it simple" on its own
 * sounds like an aesthetic preference rather than an economic one.
 */
