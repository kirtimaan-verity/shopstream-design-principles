# 07 - Cohesion

*Coupling is about the arrows between boxes. Cohesion is about what is inside one box.*

> Cohesion is one of the three ingredients of modularity. Folder
> [17](../17-modularity/) puts it together with SoC and information hiding to draw actual
> module boundaries.

## The concept

**Cohesion measures how strongly the things inside one building block belong together.**

Coupling is about the arrows between boxes. Cohesion is about what is inside a box.
They are two halves of the same judgement, which is why they are always taught together
and why the classic formulation is *high cohesion, loose coupling*.

The operational test is not "is this class big?". It is:

> **When this building block changes, do all of its parts change together?**
> And: **who asks for the change?**

A 600-line class that only ever changes for one reason is more cohesive than a 60-line
class that three different departments send tickets about.

### The cohesion ladder, weakest to strongest

Worth drawing on the whiteboard, because participants can usually place their own code
on it immediately.

| Level | What holds it together | ShopStream example |
|---|---|---|
| Coincidental | nothing | `ShopStreamUtils` |
| Logical | same broad category, selected by a flag | `handleRequest(type)` with a switch |
| Temporal | runs at the same time | `onStartup()` doing six unrelated things |
| Procedural | runs in the same order | `processOrderBatch()` |
| Communicational | operates on the same data | everything that touches the order row |
| Sequential | output of one feeds the next | parse, then validate, then price |
| Functional | one well-defined job | `VatCalculator` |

Aim for functional. Communicational is usually acceptable. Anything at or below temporal
is a defect worth a ticket.

### The relationship to coupling that people get backwards

Splitting a low-cohesion class does not increase coupling. It **reveals** the coupling
that was already there, hidden inside the class as method calls between unrelated parts.

If splitting a class produces a tangle of cross-references, that tangle existed before
you could see it. That is information, not damage.

## The ShopStream tie-in

`ShopStreamManager` is where a two-year-old startup monolith ends up. It handles catalogue
search, order creation, payment retry, email, CSV export and thumbnail resizing.

The consequence that connects to the brief is **pain point P2**: catalogue search
degrading from under 500ms to 12 seconds. In a class like this, search shares a thread
pool, a database connection, a deployment unit and a memory space with thumbnail resizing.
When a seller uploads 400 product photos, image resizing saturates the pool and search
queues behind it. The two features have nothing to do with each other, and the only reason
they can hurt each other is that somebody put them in the same box.

Low cohesion is not an aesthetic problem. It is how unrelated features acquire the ability
to take each other down.

## Before and after

| `Bad_ShopStreamManager.java` | `Good_CohesiveModules.java` |
|---|---|
| 6 unrelated responsibilities | 4 modules, one job each |
| 5 departments file tickets against one file | each module has one requester |
| image resize can starve catalogue search | separate modules, separately deployable |
| every change risks every feature | change surface matches change reason |
| cannot scale search independently | search scales on its own |

## Impact

| Quality attribute | Risk in the `Bad_` version |
|---|---|
| **Performance** | unrelated work shares a pool (P2) |
| **Scalability** | cannot scale one feature without scaling all six |
| **Modifiability** | any change requires understanding all six |
| **Testability** | testing search requires an SMTP config |
| **Availability** | one bad feature takes the whole unit down |

## Discussion questions

1. Place `Bad_ShopStreamManager` on the cohesion ladder. Now place each method on it
   separately. What does the difference tell you about how the class got this way?
2. `Good_CohesiveModules` has four classes where there was one. Is that more complex?
   Distinguish between complexity you can see and complexity you can reason about.
3. ShopStream wants to scale catalogue search independently for the National Day peak.
   Which version lets them, and what does that say about the relationship between
   cohesion and deployability?
