# 02 - Call Coupling (coupling via use / delegation)

*Coupling via use and delegation: the ordinary method call, and the four dials that
decide whether it costs you anything.*

## The concept

**A is call-coupled to B when A must know B exists in order to do its job.** It is the
most common and most visible dependency in any codebase: a method call.

Call coupling is not a defect. Software that calls nothing does nothing. What matters is
*how much* A knows about B, and *how many* B's A knows about.

### The four dials - turn these, not "coupling" in the abstract

| Dial | Tight | Loose |
|---|---|---|
| **What A knows** | B's concrete class, its constructor, its fields | one narrow interface |
| **Who creates B** | A does (`new B()`) | someone else, passed in |
| **How many B's** | 6 collaborators | 2-3 |
| **Direction** | A->B and B->A (a cycle) | one way only |

### Two classifications worth naming

- **Static vs dynamic** - static coupling is visible to the compiler (`import`, `new`,
  `extends`, a parameter type). Dynamic coupling only exists while the program runs
  (which object actually ends up on the other end of that interface).
- **Efferent vs afferent** - *efferent* (Ce) = arrows leaving A, the things A depends on.
  *Afferent* (Ca) = arrows arriving at A, the things that depend on A. High efferent
  coupling makes A **fragile**. High afferent coupling makes A **rigid** - expensive to
  change because so many depend on it.

> Order Service in the `Bad_` file has efferent coupling of **6**. The shared
> `PostgresConnection` has afferent coupling of **3** (A5 Problem 4) - which is exactly
> why nobody dares change its schema.

## The ShopStream tie-in - this is **A5 Problem 2**

> *"Order Service and Payment Service have a BIDIRECTIONAL dependency - creating a
> circular coupling that makes either service impossible to deploy independently."*

Order calls Payment to initiate. Payment calls Order back to confirm. A cycle.

**Why a cycle is qualitatively worse than a long chain:**

- You cannot deploy either service alone - releases must be choreographed.
- You cannot test either in isolation without faking the other.
- You cannot reason about failure: if Payment's callback into Order fails, is the order
  paid or not? During the National Day incident, this is why ops needed **4 hours** to
  establish root cause.
- You cannot draw a layered architecture diagram, because there is no "below".

## Before and after

| `Bad_OrderService_DirectCalls.java` | `Good_OrderService_InjectedPorts.java` |
|---|---|
| `new PaymentService()` inside the method | ports passed to the constructor |
| depends on 6 concrete classes | depends on 3 narrow interfaces |
| Payment calls back into Order (cycle) | Payment **returns a value**; Order decides |
| static utility calls (`TaxUtils.calc`) | policy injected as a collaborator |
| untestable without a real gateway | fully testable with three fakes |

## The trap - read this before the room over-learns the lesson

**Dependency Injection does not remove coupling.** Trading a static dependency for a
dynamic one does not necessarily reduce the coupling underneath it.

If you replace `new CheckoutComClient()` with an injected `CheckoutComClient`, you have
moved the coupling from compile time to wiring time. The Order Service *still* cannot
function without a Checkout.com client. You have made it **testable** and **deferrable**,
not **decoupled**.

The coupling only actually drops when the injected type is an **abstraction the caller
owns** (folder 16) and the caller is genuinely indifferent to which implementation shows
up. Make participants say which of the two they achieved.

## Impact - quality attributes

| Quality attribute | Risk in the `Bad_` version |
|---|---|
| **Modifiability** | Any Payment change may force an Order change, and vice versa |
| **Testability** | No unit test possible without a payment sandbox and an SMTP server |
| **Deployability** | Cycle => lock-step releases => slower than the 6-month MVP allows |
| **Analysability** | Cycle => no root cause in under 4 hours |

## Discussion questions

1. Count the efferent coupling of `Bad_OrderService_DirectCalls`. Which of those six
   dependencies is *appropriate* and which should be removed? (Not all six are wrong -
   "zero coupling" is not the goal; **loose but functionally sufficient** is.)
2. The `Good_` version still calls the payment port synchronously. Is it decoupled?
   (No - see folder 05 for the temporal dimension it has not addressed yet.)
3. Which is worse for ShopStream right now: high efferent coupling in Order Service, or
   high afferent coupling on the shared PostgreSQL schema? Justify with the brief.
