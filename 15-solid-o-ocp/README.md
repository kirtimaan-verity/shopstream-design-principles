# 15 - SOLID: O, the Open/Closed Principle

*New behaviour should arrive as new code, not as edits to code that already works.*

OCP earns its own folder because it is doing two jobs at once: it is one of the five
SOLID principles, and it is also one of the main techniques for keeping modules
independently changeable.

## The concept

> A module should be **open for extension** but **closed for modification**.

New behaviour should arrive as **new code**, not as edits to code that already works and
has already been tested.

The reason is risk, not elegance. Code that has been running in production for a year has
been debugged by reality. Every edit to it puts that back into play. Adding a class next
to it does not.

### The signal to look for

A `switch` or `if/else` chain **over a type or a category**, repeated in more than one
place. One switch is fine. The same switch appearing in three methods means every new case
requires three coordinated edits, and eventually somebody finds two of the three.

Be precise about which switches count. A switch over a **sealed type** where the compiler
enforces exhaustiveness is the opposite problem: adding a case there *should* break every
call site, because every call site has a decision to make. That is a feature, and folder
12 uses it deliberately.

## Deliberate tension with folder 10

Folder 10 says: do not build extension points for requirements you do not have.
This folder says: build the extension point.

Both are right, and the difference is evidence:

| Question | Answer for ShopStream payments |
|---|---|
| Is the variation named in the brief? | Yes: Egypt, Jordan, Pakistan, India, Nigeria |
| Does more than one case exist today? | Yes: Checkout.com and PayTabs |
| Do we know the shape of the variation? | Yes: authorise, capture, refund per provider |
| Is there a date? | Yes, within the 3-year plan |

Four yeses. The abstraction is earned. For AR try-on the same four questions give four
noes, and folder 10 applies.

Teach these two folders back to back. The skill being assessed is not "do you know OCP",
it is "can you tell when it applies" - which is another way of saying: explain how the
requirements determine which principle should be applied.

## The ShopStream tie-in

This is also **A5 Problem 3**:

> *"API Gateway is directly coupled to ALL 3 downstream services - any new service
> requires a gateway change."*

Same defect, same shape. The gateway has a routing table baked into its code, so extending
the system means modifying the one component that must never go down.

In `Bad_PaymentRouter_Switch.java` the provider switch appears three times: in
`authorise`, in `refund` and in `reconcile`. Adding a Nigerian provider means three edits
to a class that handles all payment traffic. The reconcile switch is the one that gets
forgotten, because it runs nightly and nobody tests it in the launch checklist.

## Before and after

| `Bad_PaymentRouter_Switch.java` | `Good_PaymentRouter.java` |
|---|---|
| the same switch in 3 methods | no switch at all |
| new provider = 3 edits to tested code | new provider = 1 new class + 1 registration |
| forgetting one switch is silent | impossible to forget |
| routing rules scattered across methods | routing is one lookup |
| adding a provider risks all payments | adding a provider risks nothing existing |

## The limitations

- **You must guess the axis of variation correctly.** OCP makes a system open along *one*
  dimension. Open for new providers is not open for new currencies. Guess wrong and you
  have paid for flexibility on an axis nothing moves along.
- **Extension points are not free.** Each one is indirection to trace through when
  debugging, and a place where behaviour is assembled at runtime rather than visible in
  source.
- **Two is the threshold.** With one implementation you are guessing. With two you can see
  the shape. Folder 10's rule applies: build the second case, then extract.

## Impact

| Quality attribute | Effect |
|---|---|
| **Modifiability** | new market is additive |
| **Reliability** | working payment code is not edited to add a provider |
| **Time to market** | a market launch does not queue behind a payments release |
| **Testability** | a new provider is tested in isolation |

## Discussion questions

1. Count the edits needed to add a Nigerian provider in each version. Then count the edits
   that could be *forgotten* in each version.
2. OCP opens `Good_PaymentRouter` for new providers. Name a change it is *not* open for,
   and say what you would do if that change arrived.
3. Folder 10 argued against building extension points. Reconcile the two folders in one
   sentence you could say to a CTO.
