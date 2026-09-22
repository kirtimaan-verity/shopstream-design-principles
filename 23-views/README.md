# 23 - Architectural views

*No single diagram can serve everyone. A view is a projection of the system that answers
one kind of question for one kind of reader - and, just as importantly, leaves out
everything else.*

## Why more than one picture

A new architect's instinct is to draw **the** diagram: every component, every server,
every call, one canvas. It fails predictably, because the questions people bring are not
the same question:

| Reader | What they need to know |
|---|---|
| Product owner, new joiner | what is this system, and what does it touch |
| Developer | where does my change go |
| Operations | what runs where, and what happens if a node dies |
| Security, compliance | where does regulated data live and cross a boundary |
| Tester | what interacts with what, in which order |

One diagram serving all five serves none. It is too detailed for the first, too abstract
for the third, and unreadable for everyone.

> **A view is defined as much by what it excludes as by what it shows.** If your context
> diagram has a database on it, it has stopped being a context diagram.

## The four core views

Between them these answer most questions. Everything else is added when a specific
stakeholder concern demands it.

| View | The question | Shows | Must not show |
|---|---|---|---|
| **Context** | what is inside, what is outside | the system as one box, external parties, what crosses the boundary | anything internal |
| **Building block** | how is it decomposed | static structure, nested in levels | runtime order, servers |
| **Runtime** | what happens when | interactions over time, state changes | infrastructure, every possible path |
| **Deployment** | what runs where | nodes, networks, and which building blocks sit on them | internal logic |

---

## 1. Context view

The system is **one box**. The value is entirely in the boundary: what is ours, what is
theirs, and what crosses.

```text
   EXTERNAL PARTY               WHAT CROSSES THE BOUNDARY

   Buyer (web, mobile)          browse, watch a stream, buy, get notified
   Seller (studio app)          broadcast, manage listings, read payouts
   Checkout.com                 authorise, capture, refund, settlement reports
   PayTabs                      authorise, capture, refund, settlement reports
   CDN vendor                   video ingest, packaged renditions
   Email and push provider      outbound buyer notifications
   Tax authority (per market)   periodic VAT returns
   Operations and support       dashboards, incident tooling

                       +----------------------------+
                       |         ShopStream         |
                       |   one box, deliberately    |
                       +----------------------------+
```

**Why one box.** The moment you draw internals here, the reader starts asking internal
questions and stops checking the boundary - which is the only thing this view exists to
get right. Missing an external party at this stage is the most expensive mistake available,
because every later view inherits the omission.

Where it helps to split it: a **business context** (who interacts, and why) and a
**technical context** (protocols, formats, endpoints). Same boundary, two audiences.

**Read by:** everyone. It is the first diagram in any documentation and the one that gets
looked at most.

---

## 2. Building block view

The static decomposition: what the system is made of, nested in **levels**. Level 1 is the
whole system opened up once; level 2 opens one of those blocks; and so on, only where the
detail earns its place.

```text
   LEVEL 1 - ShopStream, opened once

   +--------------------------------------------------------------+
   |                          ShopStream                          |
   |                                                              |
   |   +-----------+  +----------+  +----------+  +-----------+   |
   |   | Catalogue |  |  Orders  |  | Payments |  | Streaming |   |
   |   +-----------+  +----------+  +----------+  +-----------+   |
   |                                                              |
   |   +------------------------------------------------------+   |
   |   |  Platform:  Money, typed ids, audit log, config      |   |
   |   +------------------------------------------------------+   |
   +--------------------------------------------------------------+

   LEVEL 2 - Payments, opened

   +--------------------------------------------------------------+
   |                          Payments                            |
   |                                                              |
   |   PaymentApi          the contract Orders calls              |
   |   PaymentRouter       chooses a provider for a market        |
   |   PaymentProviderSpi  the contract providers implement       |
   |                                                              |
   |   +--------------------+  +--------------------+             |
   |   | CheckoutCom        |  | PayTabs            |             |
   |   | adapter            |  | adapter            |             |
   |   +--------------------+  +--------------------+             |
   |                                                              |
   |   PciVault            the only holder of cardholder data     |
   +--------------------------------------------------------------+
```

**The discipline that makes this useful:** each block gets a short description of its
**responsibility** and its **interface**, not just a name in a box. A box labelled
"Payments" with no responsibility statement communicates nothing that the folder name did
not.

**Read by:** developers, mostly. It is the map for "where does my change go", and it is
the view that should match folder [17](../17-modularity/)'s module boundaries exactly. If
the diagram and the package structure disagree, the diagram is wrong.

---

## 3. Runtime view

What actually happens, in order. Static structure cannot show sequence, concurrency,
timing or failure behaviour, and those are where systems break.

You do not need UML for this. A numbered list is a legitimate runtime view and is often
clearer:

```text
   CHECKOUT, happy path

   1.  Buyer          submits checkout                  to  API Gateway
   2.  API Gateway    validates token, forwards         to  Orders
   3.  Orders         asks price and reservation        from Catalogue
   4.  Catalogue      reserves stock, returns price     to  Orders
   5.  Orders         requests authorisation            from Payments
   6.  Payments       calls the provider adapter        (3s budget, breaker)
   7.  Payments       returns the outcome               to  Orders
   8.  Orders         saves the order, publishes        OrderPlaced
   9.  Orders         returns the outcome               to  Buyer
   10. Notifications  consumes OrderPlaced              (off the critical path)

   FAILURE PATH, gateway degraded
   6a. budget exceeded, breaker opens
   6b. order saved as PENDING, buyer told "confirming"
   6c. reconciliation settles or cancels it later
```

**Draw the failure path.** A runtime view showing only the happy path is the one that gets
drawn and the one that is useless during an incident. The 8% failure in the case study
lives entirely in steps 6a to 6c, and no happy-path diagram would ever have surfaced it.

**Pick the scenarios that matter**, not all of them: the most common path, the most
business-critical, the one that failed last time, and anything crossing a trust boundary.
Three or four documented scenarios is a good runtime view. Twenty is a transcript.

**Read by:** developers, testers, operations.

---

## 4. Deployment view

Hardware, infrastructure, and the mapping of building blocks onto it. This is where
constraints become visible.

```text
   +--------------------+  +--------------------+  +--------------------+
   |  bare-metal node 1 |  |  bare-metal node 2 |  |  bare-metal node 3 |
   |                    |  |                    |  |                    |
   |  ShopStream app    |  |  ShopStream app    |  |  ShopStream app    |
   |  (all modules)     |  |  (all modules)     |  |  (all modules)     |
   |  media workers     |  |  media workers     |  |  media workers     |
   +--------------------+  +--------------------+  +--------------------+
             |                       |                        |
             +-----------------------+------------------------+
                                     |
                      +--------------------------+
                      |   PostgreSQL (primary)   |
                      +--------------------------+
                          co-location facility

   Residency:  UAE buyer data on UAE nodes, KSA data on KSA nodes
   External:   CDN is a vendor service, not deployed by us
```

**What this view makes obvious**, and no other view does: three nodes and one database
means one shared schema and a single point of failure. The 23-minute outage from losing
one node is visible here as soon as you ask "what happens if a box disappears" - and it is
invisible in every other view.

Deployment views are also where regulatory constraints stop being abstract. "Data
residency" is a paragraph until it is a line on this diagram.

**Read by:** operations, security, compliance, and anyone doing capacity planning.

---

## Additional views, when a concern demands one

Add a view when a stakeholder concern is not answered by the four above. Do not add one
because a template has a slot for it.

| View | Answers | Worth it when |
|---|---|---|
| **Information / data** | what data exists, who owns it, how it flows and is retained | regulated data, residency, retention rules |
| **Operational** | how it is run, monitored, backed up, recovered | there is a real operations team |
| **User interface** | screen flows and navigation | the UI is a significant part of the product |
| **Safety / security** | trust boundaries, threats, controls | certification or a hostile threat model |

For the case study, an **information view** earns its place immediately: cardholder data,
buyer personal data and residency rules cut across all four core views and are fully
visible in none of them.

---

## Notation

Use whatever the readers can read. Consistency matters far more than formality.

| View | Common UML | Common alternatives |
|---|---|---|
| Context | component, package | box-and-list, as above |
| Building block | component, package, class | nested boxes |
| Runtime | sequence, activity, state machine | numbered lists, flow charts, BPMN |
| Deployment | deployment | boxes for nodes |

**State machines** deserve a mention: for anything with a lifecycle - an order, a stream, a
payment - a state diagram answers questions no sequence diagram can, particularly "which
transitions are illegal". Folder [05](../05-temporal-coupling/) makes those illegal
transitions fail to compile; the diagram is how you agree on them first.

**Always include a legend.** A box means something different in every diagram anyone has
ever drawn, and a reader who guesses wrong will not know they guessed.

---

## Before and after

The common failure is one diagram doing all four jobs:

```text
   THE EVERYTHING DIAGRAM

   Buyer, browser, node 1, OrderService, PostgreSQL, Checkout.com, the
   REST endpoint, the thread pool, Kafka, the CDN, a class called
   PriceHelper, and the seller's phone - all on one canvas, connected by
   lines that variously mean "calls", "contains", "deploys onto" and
   "sends a message to", with no legend.
```

| The everything diagram | Four separate views |
|---|---|
| one canvas, all readers | one view per question |
| lines mean four different things | one relationship type per view |
| no levels, so no way in | levels, so a reader can stop at the right depth |
| happy path only | failure paths documented in the runtime view |
| stale within a month | small enough that updating is cheap |
| nobody can find anything | each reader knows which page is theirs |

## Keeping views true

A diagram that no longer matches the code is worse than no diagram, because people trust
it. Three things that help:

1. **Fewer, smaller views.** A view nobody can update will not be updated.
2. **Generate what you can.** Dependency graphs and deployment topology can be produced
   from the system itself.
3. **Test the claims that matter.** "Orders does not depend on a payment provider" is a
   sentence in a building block view and an architecture test in the build - see folder
   [20](../20-quality-driven-architecture/). The test is what keeps the sentence honest.

## Discussion questions

1. Take the everything-diagram description above and split it into the four views. Which
   elements belong in more than one, and which belong in none?
2. Your runtime view documents three scenarios. Which three, and what makes a fourth worth
   adding?
3. Which view would have made the 23-minute single-node outage obvious before it happened?
   Which view would have made the 12-second search degradation obvious? Why are they
   different views?
