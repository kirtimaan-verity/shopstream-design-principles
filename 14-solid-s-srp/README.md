# 14 - SOLID: S, the Single Responsibility Principle

*"One reason to change" is the usable form of this principle. "A class should do one
thing" is not.*

SOLID is a set of heuristics with **benefits and limitations**, not a checklist. Each of
the three SOLID folders here ends with the limitations of its principle, because a team
that treats them as rules will over-apply them and produce something worse than what
they started with.

## The concept, stated correctly

The common paraphrase, "a class should do one thing", is vague enough to be useless.
Two people will disagree about what "one thing" means and neither can be shown wrong.

Robert Martin's later formulation is the operational one:

> A module should have **one, and only one, reason to change**.
> A module should be responsible to **one, and only one, actor**.

The second sentence is the usable test, because "actor" is a person or a department you
can name.

### The test, in practice

For each method, ask: **who would file the ticket that changes this?**

If the answers are different people in different departments, the class has more than one
responsibility, however tidy it looks.

That reframing is what makes SRP concrete. A 400-line class that only finance ever asks
about is fine. A 40-line class that finance, marketing and compliance all send tickets to
is a problem, because those three will eventually want incompatible things and they will
want them at the same time.

## The relationship to cohesion

SRP is the class-level view of what folder 07 covered at the module level. Same test,
different altitude. Worth saying explicitly, because they are usually introduced under
different headings and people often learn them as unrelated ideas.

## The ShopStream tie-in

`Bad_OrderService_GodService.placeOrder()` serves four actors:

| Code | Actor | What they will ask for |
|---|---|---|
| VAT calculation | Finance | Egypt at 14% |
| Email copy | Marketing | new template, weekly |
| Payment audit logging | Compliance | extra fields for a PCI-DSS audit |
| Retry and timeout | Ops | shorter budget after the outage |

Four departments, one file. The failure is not hypothetical: during the National Day
incident, Ops needed to change the retry budget urgently, in a file that also contained
marketing's email copy and finance's tax logic. Every hurried change carried the risk of
the other three, and the review had to involve people who were busy with the incident.

## Before and after

| `Bad_OrderService_GodService.java` | `Good_SeparatedByActor.java` |
|---|---|
| 4 actors, one class | one actor per class |
| Ops edits a file containing tax rules | Ops edits `PaymentRetryPolicy` |
| any change risks all four concerns | change surface matches the requester |
| cannot test tax without SMTP | `VatCalculator` is a pure function |

## The limitations, which matter as much as the principle

SRP taken too far produces a different problem:

- **Class explosion.** One class per method, each with an interface and a factory. The
  logic is now spread so thin that following a single use case means opening nine files.
  That fails the readability goal SRP was meant to serve.
- **"Reason to change" is a judgement, not a measurement.** Two competent architects will
  draw the line differently, and neither is wrong. The actor test narrows the disagreement
  but does not remove it.
- **Premature splitting.** Splitting before you know the actors is guessing, and folder 10
  applies. Wait until a class has been changed a few times and look at who asked.

The honest position: SRP is a strong *diagnostic* and a weak *rule*. Use it to explain why
a class is painful, not to justify splitting a class that nobody has complained about.

## Impact

| Quality attribute | Effect |
|---|---|
| **Modifiability** | change surface matches the change request |
| **Testability** | pure business rules testable with no infrastructure |
| **Operability** | Ops changes retry policy without touching tax logic |
| **Reviewability** | a reviewer needs one domain of expertise, not four |

## Discussion questions

1. Name the actor behind each block of `placeOrder()`. Which two would you expect to want
   changes in the same week, and what happens when they do?
2. `Good_SeparatedByActor` has five classes where there was one. At what point does that
   stop helping? What evidence would tell you that you had gone too far?
3. ShopStream needs Egypt VAT at 14% and a new confirmation email, both this sprint.
   Compare the two designs in terms of merge conflicts, review load and release risk.
