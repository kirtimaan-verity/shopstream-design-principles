# 03 - Inheritance Coupling

*Why `extends` is the most expensive dependency the language offers, and what to reach
for instead.*

## The concept

`extends` is **the strongest static coupling the Java language can express.**

A method call couples you to a *signature*. Inheritance couples you to:

- every **method** the parent has - including ones added after you shipped
- every **protected field** and its representation
- the parent's **constructor** contract and initialisation order
- the parent's **invariants**, which you can break by accident
- the parent's **transitive parents**, forever
- and it is **permanent** - Java has single inheritance, so you have spent the one
  slot you had

You cannot subscribe to part of a superclass. You inherit all of it, publicly, and your
subclass now claims *"I am substitutable for that"* - a promise you may not be able to keep.

### The two named failure modes

**Fragile Base Class.** A change to the parent that is safe *in isolation* breaks
subclasses. Adding a method, changing which internal method a public method calls,
tightening an invariant - all safe locally, all potentially catastrophic downstream.
The parent's author cannot know, because subclasses may live in another repository.

**Inheriting what you must not have.** A subclass gets capabilities that are *actively
wrong* for it. See the PCI-DSS example below: `PaymentService` inherits
`getDbSession()` and can therefore write cardholder data to the shared schema. Nothing
in the language prevents it. The only control is a code review that must never miss.

## The ShopStream tie-in

`BaseService` is the class every Django-monolith-in-migration grows. Somebody noticed
that Product, Order and Payment all need a DB session, logging, auth and metrics, and
factored the commonality "up". Reasonable intent. Now:

- Afferent coupling on `BaseService` is **3** - and rising, because Notification and
  Streaming will extend it too.
- Adding PCI-DSS field masking to `BaseService.log()` changes behaviour for the Product
  Service, which does not want it and now pays the CPU cost on the **12-second search
  path** (pain point P2).
- `PaymentService` inherits a shared-schema DB session it is forbidden by compliance to
  use (A5 Problem 4 meets PCI-DSS).

## Before and after

| `Bad_BaseService_Inheritance.java` | `Good_ServicesByComposition.java` |
|---|---|
| `extends BaseService` x 3 | each service holds what it needs |
| Payment inherits `getDbSession()` | Payment is never given a database |
| parent change => 3 services retested | change one collaborator => its users only |
| `save()` overridden to throw (LSP break) | no `save()` to break |
| one inheritance slot spent | slots free, collaborators swappable |

## The rule to put on the slide

> **Inherit to be substitutable. Compose to reuse.**
>
> If your reason for `extends` is *"so I don't have to write that code twice"*, you
> wanted a field, not a parent. Reuse is the *worst* justification for inheritance and
> unfortunately the most common one.

The honest counter-example (be fair to inheritance): a **sealed interface + records**
modelling `PaymentOutcome = Authorised | Declined | GatewayTimeout` is inheritance used
correctly - it expresses "is-a-kind-of", it is closed, and substitutability is total.

## Impact - quality attributes

| Quality attribute | Risk |
|---|---|
| **Modifiability** | Every `BaseService` change is a 3-service regression test |
| **Security / compliance** | Payment inherits DB access it must not have (PCI-DSS) |
| **Testability** | Instantiating Product for a test boots auth and metrics |
| **Performance** | Product pays for masking logic it does not use (P2, 12s search) |

## Discussion questions

1. Which is worse - `OrderService extends BaseService`, or `OrderService` holding a
   `BaseService` field? Both depend on `BaseService`. Name the difference precisely.
2. `PaymentService` overrides `save()` to throw `UnsupportedOperationException`.
   Which SOLID principle does that break, and what *observable* failure does it cause?
   (Folder 13 has the answer.)
3. ShopStream wants request tracing on every service to fix pain point P4 (4-hour root
   cause). Would you add it to `BaseService`? What is the alternative that does not
   increase inheritance coupling?
