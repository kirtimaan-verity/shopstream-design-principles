# 01 - Structuring a system

*How do I cut one system into parts?* These four are the patterns you reach for before any
network is involved. Getting them right is what makes the patterns in group 02 optional
rather than urgent.

---

## Layers

**What it is.** Stack the system into horizontal layers, each offering services to the one
above and using only the one below. Presentation, application, domain, infrastructure is
the common cut.

**The shape.**

```text
  Presentation   HTTP, JSON, status codes
        |
  Application    use cases, transaction boundaries, orchestration
        |
  Domain         business rules, no framework, no I/O
        |
  Infrastructure database, gateways, message brokers
```

A **strict** layering allows calls only to the layer immediately below. A **relaxed**
layering lets a layer skip downward. Strict is easier to reason about and more tedious;
relaxed is convenient and erodes into no layering at all if nothing enforces it.

**The ShopStream case.** Checkout, decomposed: an endpoint that speaks HTTP, a use case
that orchestrates, a pricing policy that is pure, and adapters for the database and the
payment gateway. That is exactly `Good_CheckoutLayers.java` in folder
[08](../../08-soc-and-loose-coupling/).

**What it buys.** Separation of concerns you can point at. Each layer is testable with the
one below faked. It is the cheapest structure to teach a new joiner.

**What it costs.** A change to one field can touch every layer, which is the standard
complaint and usually a sign the layers were made too thin. Layers also say nothing about
*features*, so a purely layered top-level package structure produces the defect in folder
[17](../../17-modularity/): every ticket touches every package. **Layer inside a module,
not instead of modules.**

**Elsewhere in this repo.** [08 SoC](../../08-soc-and-loose-coupling/) builds it;
[17 Modularity](../../17-modularity/) explains where it belongs.

---

## Pipes and Filters

**What it is.** Decompose processing into independent **filters** that transform a stream,
connected by **pipes** that carry data. Each filter knows only its input and output
format, never its neighbours.

**The shape.**

```text
  source
    |
    v
  [ decode ]
    |
    v
  [ watermark ]
    |
    +-------------------------------+
    |                               |
    v                               v
  [ transcode 1080p ]           [ transcode 720p ]
    |                               |
    v                               v
  [ package ]                   [ package ]
    |                               |
    v                               v
   sink                            sink
```

**The ShopStream case.** The video ingest pipeline. A seller's stream arrives, and is
decoded, watermarked, transcoded to several bitrates, packaged for the CDN. Each stage is
a separate filter, so the 1080p and 720p transcodes run in parallel, and adding an AV1
output later means adding a filter rather than editing a pipeline.

**What it buys.** Filters are independently testable, independently scalable, and
reorderable. A slow stage can be given more workers without touching the others - the
answer to the resource-starvation problem in folder [07](../../07-cohesion/).

**What it costs.** Everything must be expressible as a stream transformation, so
interactive or transactional work fits badly. Error handling across stages is awkward:
when stage four fails, stages one to three have already done work. Latency is the sum of
the stages, and each pipe is a serialisation cost.

**Where you already know it.** Unix shell pipelines, CI stages, log processing, and every
stream-processing framework.

---

## Ports and Adapters

*Synonyms: Hexagonal Architecture, Onion Architecture, Clean Architecture. They differ in
diagram and vocabulary, not in substance.*

**What it is.** The application core defines **ports** - interfaces in its own language -
and everything external attaches through an **adapter** that implements one. The core has
no outbound dependency on any technology.

**The shape.**

```text
     DRIVING SIDE                                 DRIVEN SIDE
     (things that use us)                         (things we use)

     HTTP endpoint   \                         /   PostgreSQL adapter
     CLI tool         }  [PORT]  CORE  [PORT] {    Checkout.com adapter
     Test harness    /                         \   SMTP adapter

     calls arrive through a port           the core calls out through a port
     ................................      ................................
                    the CORE owns both ports
```

The distinction that carries the weight: **the core owns both ports.** A driven port is
not "the database's interface", it is the core's statement of what it requires.

**The ShopStream case.** `Good_InvertedDependencies.java` in folder
[16](../../16-solid-d-dip/) is this pattern end to end: `Orders`, `Catalogue`, `Payments`
and `Notifier` are ports declared by the domain, with Postgres, Checkout.com and SMTP
adapters implementing them.

**What it buys.** The core is testable with no infrastructure at all. Technology decisions
become deferrable and reversible - which matters enormously under the nine-month bare-metal
constraint, because it turns the eventual migration into writing adapters.

**What it costs.** More types, and a translation step at every boundary. Applied to a small
CRUD service it is pure ceremony. The honest threshold: use it where the technology is
genuinely expected to change or where the core genuinely needs testing in isolation.

**Elsewhere in this repo.** [16 DIP](../../16-solid-d-dip/) is the principle;
[12 Abstraction](../../12-abstraction/) covers what makes a port a real boundary rather
than a leaky one.

---

## Plugin

**What it is.** A host defines an extension contract; independent plugins implement it and
are discovered and loaded at configuration, startup or run time. The host has no
compile-time knowledge of any plugin.

**The shape.**

```text
  HOST
    |  defines
    v
  ExtensionPoint (interface)
    ^
    |  implemented by
    |
    +-- PluginA
    +-- PluginB
    +-- PluginC   (added later, host unchanged)

  discovered through:  a registry, a ServiceLoader, or configuration
```

**The ShopStream case.** The payment provider registry in folder
[15](../../15-solid-o-ocp/): `PaymentProvider` is the extension point, Checkout.com and
PayTabs are plugins, and the Nigeria launch is one new class plus one registration line.

**What it buys.** New capability without modifying the host - Open/Closed made concrete.
Third parties, or separate teams, can extend the system without touching its code.

**What it costs.** The extension contract has to be right early, because every plugin
depends on it and widening it breaks all of them at once. Dynamic loading brings a class
of startup and versioning failures that are hard to diagnose, and behaviour is assembled at
run time so it is not visible in any source file. On a system in PCI-DSS scope, an
unrestricted plugin loader is an audit finding - see the plugin loader in folder
[10](../../10-kiss-and-yagni/) for a plugin mechanism that had no plugins.

**Elsewhere in this repo.** [15 OCP](../../15-solid-o-ocp/) is the principle;
[10 YAGNI](../../10-kiss-and-yagni/) is the counterweight.
