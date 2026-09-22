# 19 - Patterns

*A pattern is a named, reusable solution to a recurring problem in a given context, along
with the trade-offs it makes. The name is half the value: it lets a team discuss a
structure in two words instead of twenty minutes.*

Folders 01 to 18 are **principles** - properties you want the design to have. This folder
is **patterns** - shapes people have already found that deliver those properties. The
relationship is worth stating up front:

> Principles tell you what "good" means. Patterns are known-good answers, with their costs
> already documented by everyone who used them before you.

## Two altitudes, one word

"Pattern" spans two very different scales, and conflating them causes a lot of confused
design discussion.

| | **Architectural patterns** | **Design patterns** |
|---|---|---|
| Scale | whole system or subsystem | a few classes |
| Decides | deployment, data flow, team boundaries | how objects collaborate |
| Cost to change later | high, sometimes prohibitive | low, an afternoon |
| Examples | Layers, Microservices, CQRS | Adapter, Observer, Strategy |
| Sources | POSA (Buschmann et al.), PoEAA (Fowler) | GoF (Gamma et al.), POSA |

Groups [01](01-structuring-a-system/) to [05](05-wiring-and-collaboration/) are
architectural. Group [06](06-object-level-patterns/) is design-level.

The practical consequence: **getting an architectural pattern wrong is expensive and slow
to undo; getting a design pattern wrong is cheap.** Spend your uncertainty budget
accordingly. It is reasonable to try a Strategy and back it out next sprint. It is not
reasonable to try Microservices and back it out next sprint.

## The groups

| Group | Question it answers | Patterns |
|---|---|---|
| [01 Structuring a system](01-structuring-a-system/) | How do I cut one system into parts? | Layers, Pipes and Filters, Ports and Adapters, Plugin |
| [02 Distributing a system](02-distributing-a-system/) | How do parts talk across a boundary? | Microservices, SOA, Broker, Remote Procedure Call |
| [03 Data, events and integration](03-data-events-and-integration/) | How does information move and get stored? | Messaging patterns, Event Sourcing, CQRS |
| [04 Organising a user interface](04-organising-a-user-interface/) | How do I separate UI from logic? | MVC, MVVM, MVU, PAC |
| [05 Wiring and collaboration](05-wiring-and-collaboration/) | How do parts find each other? | Dependency Injection, Blackboard |
| [06 Object-level patterns](06-object-level-patterns/) | How do a few classes collaborate? | Adapter, Facade, Proxy, Observer, Strategy, Template Method, Visitor, Interpreter, Combinator |

Each group README uses the same shape per pattern: **what it is**, **the shape** (a short
sketch), **the ShopStream case**, **what it buys and what it costs**, and **where it shows
up elsewhere in this repo**.

## How to choose one

A pattern is a trade, not an upgrade. The four questions, in order:

1. **What quality am I trying to get?** Modifiability, availability, throughput, team
   autonomy, auditability. If you cannot name it, you are pattern-matching on fashion.
2. **What does this pattern cost?** Every pattern in this folder lists its cost. There is
   no pattern whose cost is zero.
3. **What is the simplest thing that delivers the quality?** Folder
   [10](../10-kiss-and-yagni/) applies with full force here. Most systems that "need
   CQRS" need an index.
4. **Can I reverse it?** Prefer the reversible option while you are still uncertain. A
   modular monolith (folder [17](../17-modularity/)) keeps the microservices option open;
   microservices do not keep the monolith option open.

### The failure mode to name out loud

Patterns are **vocabulary**, not goals. "We are doing hexagonal architecture" is not a
design decision, it is a label applied after one. The useful sentence has this shape:

> *"Payments needs to be swappable per market because five new markets are coming, so we
> put a port in front of it - which is Ports and Adapters."*

Problem, quality, decision, and only then the name. A team that starts from the name ends
up with the ceremony of the pattern and none of the property it exists to provide.

## Where the patterns and the principles meet

Most of the patterns here are a principle from folders 01 to 18, made concrete:

| Pattern | Principle it operationalises |
|---|---|
| Ports and Adapters | [16 Dependency Inversion](../16-solid-d-dip/), [12 Abstraction](../12-abstraction/) |
| Plugin | [15 Open/Closed](../15-solid-o-ocp/) |
| Layers | [08 Separation of Concerns](../08-soc-and-loose-coupling/) |
| Microservices | [17 Modularity](../17-modularity/), [07 Cohesion](../07-cohesion/) |
| Messaging patterns | [04 Message coupling](../04-message-coupling/), [05 Temporal coupling](../05-temporal-coupling/) |
| Dependency Injection | [02 Call coupling](../02-call-coupling/), and *not* the same as inversion - see [16](../16-solid-d-dip/) |
| Adapter, Facade, Proxy | [12 Abstraction](../12-abstraction/), [09 Information hiding](../09-information-hiding/) |
| Strategy | [15 Open/Closed](../15-solid-o-ocp/) |
| Observer | [04 Message coupling](../04-message-coupling/) |

Reading a pattern without its principle produces cargo cult. Reading a principle without
its patterns produces a team that agrees on goals and argues for a week about structure.

## Further reading

- **POSA** - Buschmann et al., *Pattern-Oriented Software Architecture* (vols 1 and 4).
  The reference for architectural patterns.
- **PoEAA** - Fowler, *Patterns of Enterprise Application Architecture*. Information
  systems, data mapping, and the layer patterns.
- **GoF** - Gamma, Helm, Johnson, Vlissides, *Design Patterns*. The object-level catalogue.
- **EIP** - Hohpe and Woolf, *Enterprise Integration Patterns*. Messaging, in depth.

Every technical and application domain has its own catalogue on top of these. Knowing that
the catalogue exists for your domain matters more than memorising any single entry.
