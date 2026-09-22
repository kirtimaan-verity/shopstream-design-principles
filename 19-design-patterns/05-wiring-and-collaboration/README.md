# 05 - Wiring and collaboration

*How do parts find each other, and how do they work together when the flow of control is
not a simple call chain?*

---

## Dependency Injection

**What it is.** A component does not construct or locate its collaborators. They are
supplied from outside - through the constructor, a setter, or a container - so the decision
about *which* implementation to use is made at wiring time rather than inside the component.

**The shape.**

```text
  Without:  class OrderService {
                private final PaymentGateway gw = new CheckoutComClient();   // decides
            }

  With:     class OrderService {
                OrderService(Payments payments) { ... }                     // is told
            }

  Composition root: new OrderService(new CheckoutComPayments(httpClient));
```

**The ShopStream case.** `Good_OrderService_InjectedPorts.java` in folder
[02](../../02-call-coupling/), and the composition root in folder
[16](../../16-solid-d-dip/) that names every concrete type in one boring, readable file.

**What it buys.** Testability, first and foremost: collaborators can be replaced with fakes,
so business rules are testable with no infrastructure. Creation coupling disappears, and
the runtime shape of the system becomes readable in one place.

**What it costs.** Object graph construction moves away from the point of use, so "what
actually ran here?" becomes a runtime question. Container-based DI with annotations can
make the graph invisible entirely, and failures move from compile time to startup time.

**The distinction that matters most.** Dependency **injection** is a mechanism for
supplying collaborators. Dependency **inversion** is a rule about *who owns the interface*.
Injecting a concrete `CheckoutComClient` is DI with no inversion at all: you have gained
testability and reduced nothing. Folder [16](../../16-solid-d-dip/) works through the
difference, and folder [02](../../02-call-coupling/) makes the same point from the coupling
side - swapping a static dependency for a dynamic one does not necessarily reduce the
coupling underneath.

---

## Blackboard

**What it is.** For problems with no known deterministic algorithm. A shared **blackboard**
holds the evolving partial solution. Independent **knowledge sources**, each expert in one
narrow aspect, watch it and contribute when they see something they can improve. A
**control** component decides who acts next and when the result is good enough.

**The shape.**

```text
        +---------------------------------+
        |          BLACKBOARD             |  shared, evolving hypothesis
        +---------------------------------+
           ^        ^         ^        ^
        [KS 1]   [KS 2]    [KS 3]   [KS 4]     independent specialists
           \________\___ CONTROL __/________/   picks who contributes next
```

**The ShopStream case.** Real-time fraud scoring at checkout. No single rule decides
fraud. A device-fingerprint source, a velocity source, a geolocation source, a
payment-history source and a behavioural source each contribute evidence to a shared risk
assessment; a control component decides when there is enough signal to allow, hold or
decline. Sources can be added, removed or reweighted without any of the others changing.

**What it buys.** A structure for problems where the solution path is not known in advance.
Specialists stay genuinely independent and can be developed by different people. Adding
expertise is additive.

**What it costs.** The shared blackboard is shared mutable state, with everything that
implies for concurrency. Control strategy is hard to get right and harder to debug: the
system's behaviour is emergent, so "why did it decide that?" may have no short answer.
Testing is difficult because outcomes depend on interaction between sources.

**When it is the wrong reach.** If the problem *does* have a known algorithm, use it. This
pattern earns its complexity only where the alternative is genuinely no algorithm at all -
speech recognition, sensor fusion, diagnosis, heuristic scoring.
