# 17 - Modularity

*Cohesion, separation of concerns and information hiding are the ingredients. Modularity
is the thing you actually build with them: a decomposition into units that can be
understood, changed, tested, replaced and deployed independently.*

> Folder [21](../21-domain-driven-design/) is the companion to this one: modularity says
> boundaries matter and gives criteria, and bounded contexts are the method for finding
> where they actually go.

> **Read this one last, but understand that it comes first in practice.** It is placed at
> the end because it synthesises folders 07 (cohesion), 08 (SoC), 09 (information hiding),
> 12 (abstraction), 15 (OCP) and 16 (DIP). In a real design you draw module boundaries
> before you write any of the code inside them.

## The concept

A **module** is a unit of the system with:

1. a **name** and a reason to exist that someone outside the code can state,
2. a **published contract** - the small set of types other modules may use,
3. a **hidden interior** - everything else, which is nobody else's business,
4. an **owner**, and ideally one team,
5. the ability to be **changed, tested and released** without opening its neighbours.

Point 3 is what most codebases get wrong. If everything in a package is `public`, you have
directories, not modules. A boundary that nothing enforces is a naming convention.

### Module, package, service: three different things

| | What it is | Enforced by |
|---|---|---|
| **Package** | a namespace | the compiler, weakly (`public` defeats it) |
| **Module** | a contract plus a hidden interior | the compiler, if you use visibility properly |
| **Service** | a module with a network boundary and its own process | the network, absolutely |

The useful ordering: **get the module boundaries right first, inside one process.** A
module boundary is cheap to move; a service boundary is not. If you cut the system into
services before you know where the seams are, you have bought every cost of distribution
(latency, partial failure, versioning, operations) to enforce a boundary you guessed.

### The two ways to cut the system

This is the decision the `Bad_` and `Good_` files contrast, and it is the single most
consequential choice in the folder.

**By technical layer** - `controllers`, `services`, `repositories`, `models`.
Feels organised. Every feature change touches all four packages, so no team owns a
feature, no feature can be deleted cleanly, and nothing can be extracted later.

**By business capability** - `catalogue`, `orders`, `payments`, `streaming`.
Each module contains its own controller, service and repository *internally*. A feature
change lands in one module. A module can be deleted, replaced, or lifted out into its own
service without unpicking anything.

> **The test:** open a recent feature ticket and count how many top-level packages you had
> to edit. If the answer is "all of them, always", the packages are layers wearing the
> word "module".

Layering is still right - it just belongs **inside** a module, not as the top-level cut.

## The ShopStream tie-in

The brief describes a monolithic application on three servers that has to reach ten times
its current scale, with a hard constraint: no cloud for roughly nine months, and a
six-month deadline for the first release of the new architecture.

That constraint set rules out "rewrite it as microservices" and rules out "leave it as it
is". What it argues for is a **modular monolith**: one deployable unit, cut internally
into capability modules with enforced boundaries. It ships within the deadline, runs on
the existing hardware, and turns the eventual service extraction into a mechanical job
rather than a project - because the seams are already there and already tested.

It also fixes the shared-schema defect by construction. When `orders` cannot see the
catalogue's tables (only its published interface), a column rename in one module cannot
silently break another.

## Before and after

| `Bad_ModulesByTechnicalLayer.java` | `Good_ModulesByCapability.java` |
|---|---|
| top-level cut: controllers / services / repositories | top-level cut: catalogue / orders / payments |
| every feature change touches 4 packages | a feature change lands in 1 module |
| everything `public` so layers can call across | one public interface per module, rest package-private |
| any class can reach any table | each module owns its data, reachable only via its API |
| no module can be extracted | a module lifts out into a service with no caller changes |
| nobody owns a feature | each module has one owner |

## Choosing the boundaries

The criteria, in the order they actually help:

1. **Rate and reason of change.** Things that change together belong together. Things that
   change for different reasons belong apart. (Same test as folders 07 and 14.)
2. **Data ownership.** One module owns each piece of data and is the only writer. Shared
   mutable data is not a module boundary, it is a shared variable with extra steps.
3. **Team shape.** A boundary that cuts across a team produces constant coordination; a
   boundary that matches a team disappears from the daily standup.
4. **Failure containment.** Work that can starve or crash other work belongs behind its own
   boundary, with its own resource budget.
5. **Vocabulary.** If two parts of the system use the same word to mean different things,
   that is usually a boundary rather than a naming argument to be won.

## The limitations

- **A wrong boundary is worse than no boundary.** It adds ceremony and forces the wrong
  conversations. Do not cut a module until you have seen the system change a few times and
  know where the seams actually are - which is folder 10's argument applied to structure.
- **Boundaries have a cost at every call.** Translation between module types, an interface
  to keep meaningful, one more hop when reading a stack trace.
- **Enforcement is the hard part.** Visibility modifiers, module systems, build-level
  dependency rules or an architecture test in CI. A boundary that is only in a diagram
  will be gone within two quarters.

## Impact

| Quality attribute | Effect |
|---|---|
| **Modifiability** | a change lands in one module instead of four packages |
| **Testability** | a module is testable against its own contract |
| **Scalability** | a module can be extracted and scaled on its own when needed |
| **Analysability** | "where does this behaviour live" has one answer |
| **Team autonomy** | ownership follows the boundary |

## Discussion questions

1. Take a feature you shipped recently. How many top-level packages did it touch? What
   does that number say about your current boundaries?
2. `Good_ModulesByCapability` gives each module its own `Money` translation at the edges
   rather than sharing every type. When is a shared type the right call, and when is it a
   boundary violation in disguise? (Folder 11's counter-case is the same argument.)
3. ShopStream needs to extract payments into its own service for compliance isolation.
   Starting from each version, what is the work? Which line of code is the difference?
