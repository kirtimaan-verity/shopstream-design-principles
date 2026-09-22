# 13 - Conceptual Integrity

*Similar problems should get similar solutions, everywhere in the system.*

> This folder is about consistency of solutions. Folder
> [22](../22-cross-cutting-concepts/) is where the system-wide ones get decided and written
> down, which is how that consistency is actually achieved rather than hoped for.

Three parts to it:

- **uniformity** (homogeneity, consistency) of solutions for similar problems,
- as the route to the **principle of least surprise** (POLA),
- with **Liskov substitution** as the type-level guarantee that keeps it honest.

## The concept

**Similar problems get similar solutions, everywhere in the system.**

Conceptual integrity is the quality that lets somebody who has learned one part of
ShopStream predict how the rest of it behaves. It is what makes a large system feel like
it was designed by one mind, which Fred Brooks argued is the most important consideration
in system design.

It is unusual among the design principles in that **any single local decision can be
defensible while the whole is still broken**. Three teams each picked a reasonable
error-handling convention. The system now has three, and every developer working across
them has to remember which is which.

### The principle of least surprise

A developer who has used one ShopStream service should be able to guess the shape of the
next one. When they cannot, the cost shows up in three places:

- **Bugs.** Someone assumes `find()` returns null because that is what the Product Service
  does, and the Order Service throws instead.
- **Incident time.** During an outage, nobody has spare attention for "which convention
  does this service use?" This is a direct contributor to **pain point P4**, four hours to
  root cause.
- **Onboarding.** Every service has to be learned separately, so ShopStream's plan to grow
  the team ahead of Series B gets more expensive per hire.

### Liskov, and why it belongs here

LSP is usually taught as the L of SOLID. Filing it under conceptual integrity is the more
useful framing:

> A subtype must be usable **anywhere** its supertype is expected, without the caller
> needing to know the difference.

A `PayTabsGateway` that implements `refund()` by silently doing nothing has broken the
concept, not just the type. Every caller that reasoned correctly about `PaymentGateway`
now reasons incorrectly about this one, and the failure is silent. That is conceptual
integrity destroyed from the inside.

## The ShopStream tie-in

Three services, grown at different times by different people, each internally reasonable:

| Decision | Product Service | Order Service | Payment Service |
|---|---|---|---|
| Not found | returns `null` | throws | returns `Optional.empty()` |
| Identifiers | `String` | `long` | `UUID` |
| Money | `double` | `long` fils | `BigDecimal` |
| Pagination | `page` / `size` | `offset` / `limit` | cursor |
| Errors | `{"error": "..."}` | `{"message": "..."}` | RFC 7807 |
| Timestamps | epoch millis | `"2026-09-10 14:30:00"` | ISO-8601 with zone |

The `double` for money in the Product Service is the one that costs real money. Everything
else costs attention, which during an incident is the scarcer resource.

## Before and after

| `Bad_InconsistentServices.java` | `Good_ConsistentServices.java` |
|---|---|
| 3 not-found conventions | one: `Optional` |
| 3 identifier types | typed ids everywhere |
| 3 money representations | `Money`, minor units |
| 3 pagination models | one `Page` |
| a gateway that lies about refunds (LSP) | limitation declared, fails loudly |

## The real artifact of conceptual integrity

It is not a class. It is a **written, short, enforced conventions document**, plus a
mechanism that makes deviation harder than compliance.

`Good_ConsistentServices.java` ends with ShopStream's conventions written out. On a real
project that page lives beside the architecture documentation and is used in review. If it
lives only in the heads of the three people who were there at the start, it degrades with
every hire, which is the trajectory ShopStream is already on.

Mechanisms that work better than good intentions: shared base types that make the right
thing the easy thing, an API linter in CI, a service template that new services start
from, and one architect who reviews cross-service interfaces.

## Impact

| Quality attribute | Effect |
|---|---|
| **Analysability** | one mental model instead of six (P4) |
| **Modifiability** | a cross-cutting change has one shape, not six |
| **Correctness** | wrong assumptions across boundaries stop compiling |
| **Onboarding** | learn once, apply everywhere |

## Discussion questions

1. Every inconsistency in `Bad_InconsistentServices` was defensible when it was made. What
   process would have prevented the aggregate, and who owns it?
2. `PayTabsGateway.refund()` returns success without refunding anything. Name the
   principle broken and the observable business consequence.
3. ShopStream is adding a sixth service for auction mode. What would you hand the team on
   day one so that this folder's problem does not recur?
