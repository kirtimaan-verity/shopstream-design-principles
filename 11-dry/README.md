# 11 - DRY (Don't Repeat Yourself)

*Every piece of knowledge should have one authoritative representation - and identical
code is not always the same knowledge.*

DRY is **one option** for avoiding repetition, not a law. There are conditions under
which applying it makes the system worse, and this folder spends as much time on those
as on the principle itself.

## What DRY actually says

The original formulation is about knowledge, not text:

> Every piece of **knowledge** must have a single, unambiguous, authoritative
> representation within a system.

Two blocks of identical code are not necessarily a DRY violation. Two places that encode
**the same rule** are, even if they look nothing alike.

- Two `for` loops with the same shape and different meanings: **not** a violation.
- A VAT rate written as `5` in Java and `0.05` in a SQL report: a violation, and the
  duplication is invisible to every duplicate-code detector you own.

The failure mode is **drift**: one copy is updated, the others are not. Nothing breaks
loudly. The system just starts producing two different answers to the same question, and
you find out from a customer or an auditor.

## The ShopStream tie-in

The VAT rate appears in four places: the cart calculation, the invoice renderer, the
seller payout calculation, and the finance CSV report. Saudi Arabia raised VAT from 5% to
15%. Three places were updated. One was not.

The consequence: sellers were paid out on a 5% assumption while buyers were charged 15%.
The discrepancy accumulated quietly, and it surfaced as a reconciliation gap during a
period when ops was already working the National Day incident.

Nothing threw an exception. No test failed. This is what makes duplicated knowledge worse
than most defects: **there is no error, only a wrong answer.**

## Before and after

| `Bad_DuplicatedRules.java` | `Good_SingleSourceOfTruth.java` |
|---|---|
| VAT in 4 places, one already drifted | `VatPolicy`, one authority |
| retry policy copy-pasted in 3 services | one `RetryPolicy` object |
| "valid order" rules in 2 places | one `OrderValidation` |
| a rate change means finding all copies | a rate change means one edit |

## The important counter-case

`Good_WhenNotToApplyDry.java` is the file that stops the room over-applying this, and it
is the part most treatments of DRY leave out.

Two `Address` classes, one on `Order` and one on `Seller`, with identical fields. The
instinct is to merge them. The instinct is wrong:

- The **order's** address is a shipping destination. It changes when postal formats change
  or when a new market has a different address shape.
- The **seller's** address is a legal and tax registration address. It changes when tax
  law changes or when a compliance regime demands a new field.

Merging them creates a class that two unrelated departments file tickets against, and a
change requested by tax compliance now risks breaking shipping. The duplication was not
knowledge duplication. It was **coincidental similarity**, and the giveaway is that the
two copies have different reasons to change - the same test as cohesion (folder 07) and
SRP (folder 14).

> **The rule:** deduplicate by *reason to change*, not by *appearance*.
> Identical code that changes for different reasons must stay separate.

Sandi Metz's version is worth quoting to a room: duplication is cheaper than the wrong
abstraction. Removing duplication couples the call sites to each other. If they were not
already coupled by a shared rule, you have just created coupling that did not exist.

## Impact

| Quality attribute | Effect of applying DRY correctly |
|---|---|
| **Correctness** | one authority means no silent drift |
| **Modifiability** | a rate change is one edit, verifiable |
| **Compliance** | an auditor has one file to check |
| **Analysability** | Note: over-applied DRY hurts it - a shared helper with six flags is harder to read than three simple copies |

## Discussion questions

1. `Bad_DuplicatedRules` has four VAT calculations. Which of the four would you notice was
   wrong, and how? Which would go unnoticed for months?
2. Both `Address` classes have the same five fields. What evidence would make you merge
   them, and what evidence keeps them apart?
3. ShopStream's retry logic is duplicated across three services in three repositories. Is
   extracting a shared library the right fix? What new coupling does that create, and what
   does it do to independent deployability?
