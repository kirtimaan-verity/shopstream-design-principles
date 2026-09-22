# ShopStream - Design Principles & Coupling, in Java

A concept sample repository. Every design principle below is shown twice in Java: once as
the code teams actually write, and once with the principle applied - against a single
running case study, **ShopStream**.

It is built for walkthroughs: workshops, onboarding, study groups, or a reading list you
work through on your own. Nothing in it is tied to a particular course or syllabus.

> **This code is written to be read, not run.**
> It is deliberately not a buildable project - no `pom.xml`, no `build.gradle`, no package
> declarations. Imports, frameworks and plumbing are elided so that the *principle* is the
> only thing on screen. Class names, comments and structure carry the load.

---

## How to use it

Every folder follows the same shape, so a reader always knows where to look:

```text
NN-concept-name/
+-- README.md          notes: concept, case-study tie-in, before/after, discussion
+-- Bad_*.java         principle NOT applied: the code we actually find in the wild
+-- Good_*.java        principle applied, and what it cost to get there
```

**Suggested pass, about 6 minutes per concept:**

1. Open `Bad_*.java`. Ask: *"What breaks when ShopStream adds a third payment provider, a
   fifth market, or a second database?"* Let people find it themselves.
2. Open the folder `README.md`. The **Impact** section names the quality attribute at risk.
3. Open `Good_*.java` and diff it mentally against the Bad version.
4. Close with the folder's **Discussion questions**.

The `Bad_` files are not strawmen. Each one encodes a real pain point from the ShopStream
brief or a real defect from the reference diagram in [`00-shopstream-domain/`](00-shopstream-domain/).

---

## The case study, in one paragraph

A live-commerce platform: sellers broadcast HD video, buyers browse a live catalogue and
buy without leaving the stream. Two years old, approaching a Series B round with a **10x
growth target**. Today it is a **monolithic application on 3 bare-metal servers** handling
video ingest and re-streaming via CDN, product catalogue, real-time orders, two payment
gateways, seller dashboards and buyer notifications. The constraints are non-negotiable:
**bare metal only for roughly 9 months**, **PCI-DSS Level 1**, **in-country data residency
for two markets**, and a **6-month deadline** for the first release of the new architecture.

Start at [`00-shopstream-domain/`](00-shopstream-domain/) for the component map and the
shared vocabulary every example uses.

### The four incidents every example refers back to

| # | What happened | Which concept explains it |
|---|---|---|
| P1 | Payment timeouts hit **8% of transactions** for 90 minutes | [05 Temporal](05-temporal-coupling/), [02 Call](02-call-coupling/) |
| P2 | Catalogue search degraded from **under 500ms to 12s** | [07 Cohesion](07-cohesion/), [08 SoC](08-soc-and-loose-coupling/) |
| P3 | One server lost the network: **23-minute partial outage, 30% of viewers** | [06 Location](06-location-coupling/) |
| P4 | Nobody could find the root cause for **4 hours** | [08 SoC](08-soc-and-loose-coupling/), [13 Conceptual Integrity](13-conceptual-integrity/) |

---

## Index

### Interfaces

| Folder | Concept |
|---|---|
| [01](01-api-vs-spi/) | **API vs SPI** - who calls whom, and who implements |

### Coupling - the kinds, and how to tell them apart

| Folder | Concept | The one-line test |
|---|---|---|
| [02](02-call-coupling/) | **Call coupling** (use / delegation) | "A must *know about* B to work." |
| [03](03-inheritance-coupling/) | **Inheritance coupling** | "A inherits B's *entire* surface, forever." |
| [04](04-message-coupling/) | **Message coupling** | "A and B agree on a *schema*, not a signature." |
| [05](05-temporal-coupling/) | **Temporal coupling** | "A must happen *while* / *before* B." |
| [06](06-location-coupling/) | **Location coupling** | "A must know *where* B physically is." |

> Worth quoting out loud before starting: *forgoing static dependencies in favour of
> dynamic ones does not necessarily reduce the underlying coupling.* Folder
> [04](04-message-coupling/) is built to prove it, and folder [02](02-call-coupling/)
> makes the same point about dependency injection.

### Modularity and complexity

| Folder | Concept |
|---|---|
| [07](07-cohesion/) | **Cohesion** - what belongs inside one box |
| [08](08-soc-and-loose-coupling/) | **Separation of Concerns** + loose-but-sufficient coupling |
| [09](09-information-hiding/) | **Information Hiding** & encapsulation |
| [17](17-modularity/) | **Modularity** - drawing the boundaries these three serve |
| [10](10-kiss-and-yagni/) | **KISS & YAGNI** - complexity reduction |
| [11](11-dry/) | **DRY** - and when *not* to apply it |
| [12](12-abstraction/) | **Abstraction** - depend on abstractions, not implementations |
| [13](13-conceptual-integrity/) | **Conceptual Integrity** - POLA, homogeneity, LSP |

*Folder 17 is numbered last because it synthesises 07, 08, 09, 12, 15 and 16 - but in a
real design you draw module boundaries before you write the code inside them. Read it
last; apply it first.*

### SOLID

| Folder | Letter | Principle |
|---|---|---|
| [14](14-solid-s-srp/) | **S** | Single Responsibility Principle |
| [15](15-solid-o-ocp/) | **O** | Open/Closed Principle |
| [16](16-solid-d-dip/) | **D** | Dependency Inversion Principle |

*(L and I are covered in passing - LSP inside [13](13-conceptual-integrity/), ISP inside
[01](01-api-vs-spi/) - because both are better understood through the principle they serve
than as standalone rules.)*

### Robustness

| Folder | Concept |
|---|---|
| [18](18-postels-law/) | **Postel's Law** and the robustness principle - expect errors |

### Method: deciding, modelling, communicating

| Folder | Concept |
|---|---|
| [20](20-quality-driven-architecture/) | **Quality-driven architecture** - scenarios, trade-offs, analysis methods |
| [21](21-domain-driven-design/) | **Domain-Driven Design** - bounded contexts, ubiquitous language, aggregates |
| [22](22-cross-cutting-concepts/) | **Cross-cutting concerns and concepts** - one decision per concern, and their interdependencies |
| [23](23-views/) | **Architectural views** - context, building block, runtime, deployment |

*Folders 01-18 are properties to design for. These four are the method around them: how
you decide what matters ([20](20-quality-driven-architecture/)), where the boundaries go
([21](21-domain-driven-design/)), what gets decided once for the whole system
([22](22-cross-cutting-concepts/)), and how any of it is communicated
([23](23-views/)).*

### Patterns

| Folder | Contents |
|---|---|
| [19](19-design-patterns/) | **28 architectural and design patterns**, grouped by the question they answer |

*Folders 01-18 are principles: properties you want the design to have. Folder 19 is
patterns: shapes people have already found that deliver those properties, with the costs
already documented. Read a pattern without its principle and you get cargo cult; read a
principle without its patterns and the team agrees on goals then argues for a week about
structure.*

*Read with [05](05-temporal-coupling/): the same instinct applied to content and to time.
Both halves of Postel's law get qualified here, because "be liberal in what you accept",
taken literally, is how a contract stops being a contract.*

---

## Walkthrough routes

Pick the route that matches the session rather than working straight through 01 to 17.

| Session | Length | Folders |
|---|---|---|
| **Coupling deep-dive** | half day | 02 -> 06, then 16 |
| **Design principles** | half day | 07, 08, 09, 17, then 10 -> 13 |
| **SOLID** | 90 minutes | 14, 15, 16 |
| **Interfaces & contracts** | 90 minutes | 01, 08, 12, 18 |
| **Modular monolith / decomposition** | half day | 07, 08, 09, 17, 16 |
| **Boundaries & untrusted input** | 90 minutes | 04, 18, then 09 |
| **Full pass** | 3 days | 20 first, then 01 to 18 (17 after 09), then 21, 22, 19, 23 |
| **Code review calibration** | 60 minutes | the `Bad_` files only, as a spot-the-defect drill |
| **Quality and evaluation** | 60 minutes | [20](20-quality-driven-architecture/) |
| **Domain modelling** | half day | [21](21-domain-driven-design/), then 17 and 11 |
| **Documentation and communication** | 90 minutes | [23](23-views/), then [22](22-cross-cutting-concepts/) |
| **Pattern tour** | half day | [19](19-design-patterns/), groups 01 to 06 |
| **Pattern selection** | 60 minutes | [19](19-design-patterns/) index, then groups 02 and 03 as a trade-off argument |

### The reference diagram's four planted defects

[`00-shopstream-domain/`](00-shopstream-domain/) contains a deliberately flawed
building-block diagram with four defects in it. It works well as an opening exercise: have
people find the defects on a whiteboard first, then hand them the matching `Bad_` file.

| # | Defect | Code |
|---|---|---|
| **1** | UI talks **directly to the product database**, bypassing the gateway | [06](06-location-coupling/) `Bad_HardcodedEndpoints.java`, [16](16-solid-d-dip/) |
| **2** | Order <-> Payment **bidirectional / circular** dependency | [02](02-call-coupling/) `Bad_OrderService_DirectCalls.java` |
| **3** | The gateway is coupled to **all 3** downstream services | [15](15-solid-o-ocp/) `Bad_PaymentRouter_Switch.java` |
| **4** | All 3 services share **one database schema** | [09](09-information-hiding/), [17](17-modularity/), [16](16-solid-d-dip/) |

---

## A note on honesty in the examples

Every `Good_` file also documents **what the principle cost**: more files, more
indirection, a guess about the future that might be wrong. Design is trade-offs, and if
the "good" side looks free, readers will over-apply it the moment they get back to their
own codebase.

Folders [10](10-kiss-and-yagni/) and [15](15-solid-o-ocp/) are deliberately placed in
tension with each other - one argues against building extension points, the other argues
for it. Walk them back to back and let the room argue. The skill on display is not knowing
the principles; it is explaining **how the requirements decide which principle applies**.
