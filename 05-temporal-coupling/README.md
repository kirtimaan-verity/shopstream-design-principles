# 05 - Temporal Coupling

*The dependency no static analysis tool can see: A has to be available, or has to run,
at the moment B needs it.*

> This folder is "expect errors" applied to **time**: assume the far end will be slow or
> absent, bound every wait, define what happens when there is no answer. Folder
> [18](../18-postels-law/) applies the same instinct to **content**.

## The concept

**Two building blocks are temporally coupled when one constrains *when* the other must
run.** Not *whether*, not *how* - *when*.

Temporal coupling is invisible to every static analysis tool you own. Your dependency
graph can be a perfect DAG, every class can depend only on interfaces it owns, and the
system can still fall over because A must be up at the exact moment B runs.

### Two distinct flavours - teach them separately

**(a) Runtime temporal coupling - "A must be available *while* B runs."**
The synchronous call chain. Order calls Payment and *waits*. Availability multiplies:

| Chain | Availability |
|---|---|
| Order alone | 99.9% |
| Order -> Payment (sync) | 99.8% |
| Order -> Payment -> Notify -> Analytics (sync) | **99.6%** |

99.6% is **~3.5 hours of downtime per month**. Nobody chose that number; it was assembled
one `await` at a time. And it is *optimistic* - it assumes independent failures.

**(b) Order-of-calls temporal coupling - "you must call me in this sequence."**
`init()` -> `setProvider()` -> `setAmount()` -> `execute()`. Any other order fails, usually
at runtime, usually with a `NullPointerException` rather than a useful message. The
sequence lives in documentation, in a wiki, or in nobody's head at all.

## The ShopStream tie-in - this is **pain point P1**

> *"payment service timeouts affecting **8% of transactions** during a 90-minute peak
> window"*

Why 8% and not 100%? Because the payment gateway did not fail - it got *slow*. Under a
synchronous chain, "slow" and "down" are the same thing to the caller. Checkout threads
piled up waiting, the pool exhausted, and requests that had nothing to do with payment
started failing too.

Then the retries made it worse: each timed-out checkout retried, doubling load on an
already-struggling gateway. **A retry without idempotency and without a circuit breaker
is a denial-of-service attack you launch against yourself.**

## Before and after

| `Bad_SynchronousCheckout.java` | `Good_ResilientCheckout.java` |
|---|---|
| 4 blocking calls on the buyer's thread | 1 blocking call (payment), 3 published as events |
| availability = product of all four | availability = payment's, plus a fallback |
| timeout = 30s, no circuit breaker | 3s budget, breaker, idempotent retry |
| slow gateway => thread pool exhausted => site down | slow gateway => orders queue as PENDING |
| `Bad_SequenceDependentApi.java` - 5 calls in a fixed order | `Good_SelfContainedApi.java` - one call, one object |

## The nuance that keeps this honest

**Asynchrony is not free, and it is not always right.**

- **Payment stays synchronous.** The buyer must know within seconds whether their order
  went through. "Your order is being processed, we'll email you" is a *worse product*.
  The fix is not "make everything async" - it is **shorten the critical path to the
  things that genuinely belong on it**, then make those resilient.
- Async buys availability and pays with **complexity**: idempotency, ordering,
  compensation for partial failure, and a debugging story with no stack trace
  (pain point P4 again).
- Removing temporal coupling often *introduces* eventual consistency. Somebody must
  decide what the buyer sees in the gap. That is an architecture decision with a
  business owner, not a technical detail. It belongs in an architecture decision record.

## Impact - quality attributes

| Quality attribute | Effect of the fix |
|---|---|
| **Availability** | 99.6% -> ~99.9%; ~3.5h/month -> ~43 min/month |
| **Performance** | checkout p99 no longer includes SMTP and analytics |
| **Resilience** | breaker + fallback => degraded, not dead (P1) |
| **Usability** | Caution: eventual consistency the buyer can perceive - design for it |
| **Analysability** | Caution: harder; mandate correlation ids (see folder 04) |

## Discussion questions

1. Which of the four calls in `Bad_SynchronousCheckout` genuinely belongs on the buyer's
   critical path? Defend your answer to a product manager, not to an architect.
2. The `Good_` version returns `PENDING` when the breaker is open. What does the buyer
   see? Who at ShopStream decides that - and where is that decision written down?
3. `Bad_SequenceDependentApi` never crosses a network. Is it still temporal coupling?
   What tells you? (Yes - and this is the flavour that gets overlooked, because people
   associate temporal coupling only with distributed systems.)
