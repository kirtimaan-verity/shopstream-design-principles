# 08 - Separation of Concerns, and loose but sufficient coupling

*One concern, one place - plus the half that gets forgotten: coupling should be loose
**but functionally sufficient**, not minimal.*

> This folder separates concerns inside one use case. Folder
> [22](../22-cross-cutting-concepts/) handles the ones that refuse to stay in one place,
> and are decided once for the whole system.

> SoC is one of the three ingredients of modularity. Folder [17](../17-modularity/)
> applies it, with cohesion and information hiding, to the question of where module
> boundaries actually go.

That qualifier matters. The goal is not "minimal coupling" and certainly not "no
coupling". A system with an interface per class and a factory per interface is not
loosely coupled, it is unreadable.

## Separation of Concerns

A **concern** is anything the system cares about that has its own reason to change:
HTTP handling, business rules, persistence, tax law, authentication, logging, currency
formatting.

SoC says: **one concern, one place.** When two concerns are tangled in one method, you
cannot change either without understanding both, and you cannot test either without
setting up both.

### Two kinds of concern, handled differently

**Vertical (functional):** catalogue, orders, payments, streaming. Separate these into
modules with contracts between them. This is what folder 07 was about.

**Horizontal (cross-cutting):** logging, authentication, tracing, transactions, rate
limiting. These genuinely apply everywhere, which is why folder 03's `BaseService` was
tempting. The fix is not inheritance and not copy-paste. It is to handle them at a
**boundary** the request passes through anyway: a gateway, a filter chain, a decorator,
an interceptor.

The test: **can I delete the cross-cutting concern without touching business logic?**
If tracing is a decorator, yes. If it is a line in every method, no.

## Loose but functionally sufficient

The second half of the principle is a warning against over-applying the first half.

Coupling is not a cost to be minimised to zero. It is a budget to be spent deliberately.
Every abstraction you add to reduce coupling costs you:

- a layer of indirection somebody has to trace through when debugging,
- an interface that has to be kept meaningful as requirements change,
- a place where the "real" behaviour is no longer visible at the call site.

**Under-coupling is a real failure mode.** A system with an interface per class, a factory
per interface and an event per method call is not loosely coupled. It is a system where
nobody can find anything, and it fails ShopStream's six-month MVP constraint just as
surely as a big ball of mud would.

The question to ask of every abstraction, out loud, in the design review:

> **What change am I buying the ability to make cheaply, and is that change actually
> coming?**

If you can name the change and point at where it is written down (the brief lists five
new markets, two payment providers, a co-host feature), the abstraction is earned. If the
answer is "you never know", you are guessing, and folder 10 has the counter-argument.

## The ShopStream tie-in

`Bad_CheckoutEverything.checkout()` is one method containing seven concerns: HTTP parsing,
authentication, validation, pricing and tax, persistence, invoice rendering and email.

The connection to the brief is **pain point P4**: four hours to identify root cause after
an incident. When every concern is in one method, a stack trace tells you the method
failed and nothing else. You cannot tell whether the tax rule was wrong, the database was
slow, or the PDF library threw, because there is one frame for all of it and no boundary
between them to instrument.

## Before and after

| `Bad_CheckoutEverything.java` | `Good_CheckoutLayers.java` |
|---|---|
| 7 concerns in one method | 4 layers, one concern each |
| tax rule change means editing an HTTP handler | tax lives in a policy object |
| cannot test pricing without a web server | pricing is testable with no infrastructure |
| tracing copy-pasted into every method | tracing is a decorator, deleted in one line |
| stack trace has one frame (P4) | each boundary is instrumented separately |

## Impact

| Quality attribute | Effect |
|---|---|
| **Modifiability** | change surface matches the concern being changed |
| **Testability** | business rules testable without infrastructure |
| **Analysability** | boundaries are where you attach metrics and traces (P4) |
| **Reusability** | pricing is reachable from checkout, quotes and reports |

## Discussion questions

1. Name the seven concerns in `Bad_CheckoutEverything.checkout()`. For each one, say who
   would request a change to it.
2. `Good_CheckoutLayers` has four types where there was one. Where is the line between
   "separated concerns" and "over-engineered"? What evidence would you bring to a review
   to argue either side?
3. ShopStream needs PCI-DSS audit logging on every payment operation. Which of the two
   designs lets you add it without touching business logic, and what is the mechanism?
