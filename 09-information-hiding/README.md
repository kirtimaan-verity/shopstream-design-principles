# 09 - Information Hiding and Encapsulation

*Encapsulation is a language mechanism. Information hiding is a design decision. You can
have the first and none of the second.*

> This folder hides decisions inside a class. Folder [17](../17-modularity/) does the same
> thing one level up, hiding them inside a module - and shows why a package where
> everything is `public` hides nothing at all.

## The concept, and the distinction people miss

**Encapsulation** is a language mechanism: `private` fields, accessors, a class boundary.

**Information hiding** is a design decision, and it came first. Parnas, 1972:

> A module should hide a **design decision that is likely to change**.

You can have perfect encapsulation and zero information hiding. A class with private
fields and a public getter and setter for each one has encapsulated its fields and hidden
nothing at all. Every caller still knows the exact shape of the internal data, and every
caller breaks when it changes. The `private` keyword bought a compile step and no
freedom.

The question to ask is not *"are my fields private?"*. It is:

> **Which decision here is most likely to change, and can I change it without anyone
> noticing?**

## What ShopStream should be hiding

| Decision likely to change | Hidden behind | Why it will change |
|---|---|---|
| Viewers stored in an in-memory list | `join()` / `viewerCount()` | 80,000 viewers per stream will not fit in one process |
| Order state transitions | `withStatus` on the aggregate | rules change as auction mode arrives |
| Which payment provider ran | the `PaymentApi` (folder 01) | five new markets |
| Where product images live | a `BlobStore` port (folder 06) | bare metal now, object storage after Series B |
| Cardholder data | never leaves the vault (folder 03) | PCI-DSS is not negotiable |

The list has a shape: each row is a decision the brief tells you is going to change. That
is where hiding effort belongs, and it is why information hiding is an architectural
activity rather than a coding style.

## The ShopStream tie-in

`LiveStream` is the class that will be rewritten. At 80,000 concurrent viewers per stream
and a target of 10,000 concurrent streams, an `ArrayList<BuyerId>` of viewers in the
application heap is not a design that survives the year.

If callers only ever ask `stream.viewerCount()` and `stream.join(buyer)`, moving that set
to Redis is one class's problem. If callers do `stream.getViewers().add(buyer)`, the
storage decision has leaked into every call site, and the migration becomes a
codebase-wide change during the run-up to the National Day event.

The second connection is PCI-DSS. `getCardNumber()` existing at all makes every class that
can reach it part of the audit scope. Removing the accessor removes the scope.

## Before and after

| `Bad_LiveStream_Exposed.java` | `Good_LiveStream.java` |
|---|---|
| public mutable fields | private, final where possible |
| `getViewers()` returns the live list | `join()`, `leave()`, `viewerCount()` |
| callers enforce the capacity rule | the class enforces its own invariant |
| `Map<String,Object> getConfig()` | typed, intentional accessors |
| `getCardNumber()` exists | it does not exist, so it cannot be called |
| storage choice visible to all callers | storage choice is one class's business |

## The counter-argument, so the room stays honest

Information hiding is not free and not always right.

- A **data transfer object** or an event payload (folder 04) is supposed to be transparent.
  Adding behaviour and hiding fields on a wire format makes it worse.
- Hiding something nobody was going to change buys nothing and costs a layer.
- Over-hiding produces the anaemic wrapper: a class with `getX`/`setX` for every field,
  which has all of the ceremony and none of the benefit.

Hide the decisions that will change. Leave the rest visible so people can read the system.

## Impact

| Quality attribute | Effect |
|---|---|
| **Modifiability** | the viewer-storage rewrite touches one class |
| **Integrity** | invariants cannot be bypassed, because there is no path around them |
| **Security** | PCI audit scope is bounded by what the type system exposes |
| **Analysability** | "who can change this?" has a short, checkable answer |

## Discussion questions

1. `Bad_LiveStream_Exposed` has private fields and public getters. Is it encapsulated?
   Is it information-hiding? Explain the difference using this class.
2. Which single design decision in `Good_LiveStream` is most likely to change in the next
   18 months, and can it change without any caller noticing?
3. The `Good_` version returns an unmodifiable copy of the viewer list from
   `viewersForModeration()`. What does that cost at 80,000 viewers, and what would you do
   instead?
