# 06 - Object-level design patterns

*How do a few classes collaborate?* Smaller scale than groups 01 to 05, and much cheaper to
get wrong: a misapplied Strategy costs an afternoon, a misapplied Microservices costs a
quarter.

These are language-independent. Adapter is a shape, not a Java feature - it appears in
Python, Go, TypeScript and in systems with no classes at all.

---

## The three interfacing patterns

Adapter, Facade and Proxy all sit between a caller and something else, and all three are
routinely confused. **The difference is what they change.**

| Pattern | Changes the interface? | Changes the behaviour? | Answers |
|---|---|---|---|
| **Adapter** | yes | no | "the shapes do not match" |
| **Facade** | yes, simplifies | no | "this is too complicated to use" |
| **Proxy** | no, identical | yes, adds something | "I need to do something around every call" |

### Adapter

**What it is.** Convert one interface into another that a caller expects, so two things
designed independently can work together.

```java
// The core wants this. Checkout.com offers something else. The adapter translates.
class CheckoutComGateway implements PaymentGateway {
    private final CheckoutComClient vendor;

    public PaymentOutcome authorise(OrderId order, Money amount, PaymentToken token) {
        var response = vendor.post("/payments", toVendorRequest(order, amount, token));
        return toDomainOutcome(response);      // their status codes become ours
    }
}
```

**The ShopStream case.** Every payment provider class in folders
[01](../../01-api-vs-spi/) and [12](../../12-abstraction/). The adapter is the only place
that knows a vendor exists.

**Cost.** A translation layer that is real code with real bugs, and a capability mismatch
you must handle honestly: PayTabs supports partial refunds, Checkout.com does not. Folder
[13](../../13-conceptual-integrity/) covers why an adapter must never *pretend* to support
something it cannot.

### Facade

**What it is.** One simple entry point in front of a complicated subsystem. It does not
hide the subsystem - callers with unusual needs can still go around it - it just makes the
common case easy.

```java
// Six subsystems, one call, for the 90% case.
class StreamPublishingFacade {
    void goLive(SellerId seller, StreamId stream) {
        transcoder.startPipeline(stream);
        cdn.provisionEdge(stream);
        catalogue.attachProductList(stream);
        notifications.alertFollowers(seller);
        analytics.beginSession(stream);
        streams.markLive(stream);
    }
}
```

**Cost.** Facades attract logic. The moment one starts making decisions rather than
sequencing calls, it is a use case that should be named as one - and eventually a god
class, as in folder [07](../../07-cohesion/).

### Proxy

**What it is.** Same interface as the real thing, standing in for it, adding behaviour
around the call. Callers cannot tell the difference. Common kinds: caching, remote,
protection, lazy-loading, logging.

```java
// Same interface, so it can be inserted at wiring time and removed in one line.
class CachingCatalogue implements Catalogue {
    private final Catalogue delegate;
    private final Cache cache;

    public Money priceOf(ProductId p) {
        Money cached = cache.get(p);
        if (cached != null) {
            return cached;
        }
        Money fresh = delegate.priceOf(p);
        cache.put(p, fresh);
        return fresh;
    }
}
```

**The ShopStream case.** A caching proxy in front of the catalogue is a direct answer to
the 12-second search degradation, and it requires no change to any caller. The audit
wrapper in folder [08](../../08-soc-and-loose-coupling/) and the payment audit trail in
folder [14](../../14-solid-s-srp/) are the same shape used for a cross-cutting concern.

**Cost.** Invisible behaviour. A caller cannot tell it is talking to a proxy, which is the
point and also the debugging problem: stale cache and unexpected latency both look like the
real object misbehaving.

---

## Strategy and Template Method

Two answers to "this algorithm varies". They differ in *how* the variation is expressed,
and the choice between them is a coupling decision.

### Strategy

**What it is.** Put each variant behind a common interface and select one at run time. The
context holds a strategy and delegates to it.

```java
interface PricingStrategy { Money totalFor(PricingRequest request); }

class FixedPricing   implements PricingStrategy { ... }
class AuctionPricing implements PricingStrategy { ... }

// chosen at run time, per stream
Money total = strategyFor(stream).totalFor(request);
```

**The ShopStream case.** Fixed-price versus auction pricing when auction mode ships. Folder
[10](../../10-kiss-and-yagni/) makes the timing argument: extract the strategy when the
second real implementation exists, not when someone mentions auctions on a roadmap.

**Buys.** Variants are independent, separately testable, and addable without touching the
others - Open/Closed at class scale. **Costs.** More types, and the selection logic has to
live somewhere.

### Template Method

**What it is.** A base class defines the skeleton of an algorithm and leaves named steps
for subclasses to fill in. The order of steps is fixed by the parent.

```java
abstract class SettlementRun {
    final void execute() {          // final: the skeleton is not negotiable
        var batch = loadBatch();
        var valid = validate(batch);
        var result = settle(valid);   // the varying step
        report(result);
    }
    protected abstract SettlementResult settle(Batch batch);
}
```

**Buys.** Shared structure is written once; only the varying step is implemented per case.

**Costs.** It is inheritance, with everything folder [03](../../03-inheritance-coupling/)
documents: the subclass is bound to the parent's whole surface, and a change to the
skeleton reaches every subclass. **Prefer Strategy unless the fixed skeleton is genuinely
the point** - composition over inheritance applies here as everywhere.

---

## Observer

**What it is.** A subject maintains a list of dependents and notifies them when it changes.
Observers register and unregister themselves; the subject does not know who they are.

```java
class LiveStream {
    private final List<StreamObserver> observers = new ArrayList<>();

    void join(BuyerId buyer) {
        viewers.add(buyer);
        for (StreamObserver o : observers) {
            o.viewerCountChanged(id, viewers.size());
        }
    }
}
```

**The ShopStream case.** Viewer count changes updating the stream overlay, the seller
dashboard and the analytics collector, with the stream knowing none of them.

**Buys.** The subject is decoupled from an open set of dependents. **Costs.** Notification
order is usually unspecified. Observers that never unregister are a classic memory leak.
Synchronous notification means a slow observer slows the subject - the temporal coupling
from folder [05](../../05-temporal-coupling/), in miniature.

**Relationship to messaging.** Observer is publish-subscribe within one process. The
messaging patterns in group [03](../03-data-events-and-integration/) are the same idea
across a network, with the durability, ordering and delivery concerns that follow. Folder
[04](../../04-message-coupling/) covers the coupling that survives either version.

---

## Visitor

**What it is.** Separate an operation from the object structure it runs over. Add new
operations over a stable set of types without modifying those types.

```java
interface OrderLineVisitor {
    void visitPhysical(PhysicalItem item);
    void visitDigital(DigitalItem item);
    void visitSubscription(SubscriptionItem item);
}

// New operation = new visitor. The item types are not touched.
class VatCalculatingVisitor implements OrderLineVisitor { ... }
class ShippingCostVisitor   implements OrderLineVisitor { ... }
```

**The ShopStream case.** Order lines of several kinds, with operations that keep being
added: VAT, shipping cost, invoice rendering, export formatting, fraud scoring.

**The trade to state clearly.** Visitor makes **adding operations** easy and **adding
types** hard - every new item type forces a change to every visitor. Use it only when the
type set is stable and the operation set is not. If types change more often than
operations, Visitor is exactly backwards and a method on each type is right.

---

## Interpreter

**What it is.** Represent a small language's grammar as a class per rule, and evaluate
expressions by walking that structure.

```java
interface SellerRule { boolean matches(Order order); }

record AmountAbove(Money threshold)                 implements SellerRule { ... }
record InMarket(String marketCode)                  implements SellerRule { ... }
record And(SellerRule left, SellerRule right)       implements SellerRule { ... }
```

**The ShopStream case.** Seller-defined promotion conditions: "over AED 200, in the UAE,
during a live stream". A small, closed grammar the seller composes in a UI.

**Costs, and the warning.** One class per grammar rule gets heavy fast, and performance is
poor for anything large. More importantly: a hand-rolled language replaces a well-tooled
one (with an IDE, compiler, debugger and test framework) with one your team must build and
maintain. The rule DSL in folder [10](../../10-kiss-and-yagni/) is this pattern applied
where nobody had asked for it. Use it for genuinely small, genuinely closed grammars that
a non-developer will really author.

---

## Combinator

**What it is.** Build complex behaviour by composing small pieces with functions that take
and return the same type. There is no framework - the composition operators *are* the
design.

```java
interface Validator<T> {
    Optional<String> check(T value);

    default Validator<T> and(Validator<T> next) {          // the combinator
        return v -> check(v).or(() -> next.check(v));
    }
}

Validator<Order> checkout = hasItems()
        .and(withinQuantityLimit(50))
        .and(totalAbove(Money.aed(100)))
        .and(buyerInServedMarket());
```

**The ShopStream case.** Checkout validation rules, retry policies, and pricing adjustments
- anywhere the requirement is "these rules, in this combination, for this case".

**Buys.** Extremely small pieces, each trivially testable. New combinations need no new
types. It composes at the call site, so the behaviour is readable where it is used, rather
than assembled at run time from a registry.

**Costs.** Unfamiliar to teams without a functional background. Stack traces through
composed lambdas are poor. Over-composition produces expressions that are elegant to write
and hard to read six months later.

**The comparison worth drawing.** Combinator and Interpreter solve overlapping problems.
Interpreter builds a data structure representing the rule and walks it - inspectable,
serialisable, storable. Combinator builds a function - lighter, faster, and opaque, because
you cannot ask a composed lambda what it is. If sellers need to *save and edit* their rules,
Interpreter. If the rules are composed in code, Combinator.
