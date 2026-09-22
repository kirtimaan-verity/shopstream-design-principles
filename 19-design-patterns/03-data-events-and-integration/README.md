# 03 - Data, events and integration

*How does information move between parts, and how is it stored?* These three are often
proposed together and are independent decisions. You can adopt messaging without event
sourcing, and event sourcing without CQRS.

---

## Integration and messaging patterns

**What they are.** A catalogue of named solutions for moving messages between systems that
do not share a process. The core vocabulary, worth knowing by name because it turns
integration arguments into short conversations:

| Pattern | What it does |
|---|---|
| **Message Channel** | a named path a message travels along |
| **Publish-Subscribe** | one message, delivered to every interested subscriber |
| **Point-to-Point** | one message, consumed by exactly one receiver |
| **Message Router** | inspects a message and forwards it to the right channel |
| **Message Translator** | converts between two systems' formats |
| **Message Endpoint** | the adapter connecting an application to the channel |
| **Competing Consumers** | several workers on one channel, for throughput |
| **Dead Letter Channel** | where messages go when they cannot be processed |
| **Idempotent Receiver** | a consumer safe to deliver the same message to twice |
| **Correlation Identifier** | ties a response, or a whole chain, back to its origin |

**The ShopStream case.** `OrderPlaced` published to a channel with the notification,
analytics and seller-dashboard services as competing or independent subscribers. The
correlation id on every event is what makes a request traceable across the broker, and the
dead letter channel is where the rejected webhooks from folder
[18](../../18-postels-law/) land so they can be replayed rather than lost.

**What they buy.** Temporal decoupling: the producer does not wait, and a consumer being
down is not the producer's problem. New consumers cost the producer nothing.

**What they cost.** At-least-once delivery is the norm, so **idempotent receivers are
mandatory, not optional**. Ordering guarantees are weak and expensive to strengthen. And
you lose the stack trace, which is why correlation identifiers move from nice-to-have to
required.

**Elsewhere in this repo.** [04 Message coupling](../../04-message-coupling/) covers the
coupling you keep when you adopt these; [05 Temporal coupling](../../05-temporal-coupling/)
covers what they buy.

---

## Event Sourcing

**What it is.** Store the **sequence of events** that happened, not the current state.
Current state is derived by replaying events. The event log is the system of record and is
append-only.

**The shape.**

```text
  Traditional:  orders table, row updated in place, previous values gone

  Event sourced:
     OrderPlaced  ...  PaymentAuthorised  ...  ItemShipped  ...  RefundIssued
     (append only, in order; current state = fold over the events)
```

**The ShopStream case.** Payments and order lifecycle. Every state change is recorded with
its cause and time, so "why is this order in this state?" is answerable by reading the log
rather than inferring from a row. That directly addresses the four-hour root-cause problem
and the reconciliation gaps, and PCI-DSS and financial audit both want exactly this
property.

**What it buys.** A complete, immutable audit trail. Time travel: state as of any moment.
Debugging by replay. New projections built retroactively over history you already have.

**What it costs.** Substantial. Querying current state requires a projection, so simple
questions get harder. Event schemas are permanent - you cannot migrate history the way you
alter a table, so versioning has to be right from the start. Snapshots become necessary for
long streams. And the mental model is unfamiliar enough that team ramp-up is a real line
item.

**The honest scope.** Apply it where the history *is* the value - money, compliance, audit.
Do not event-source the product catalogue because it was interesting on the payments side.

---

## CQRS

*Command Query Responsibility Segregation.*

**What it is.** Separate the model that **changes** state from the model that **reads** it.
Commands validate and write; queries read from a model shaped for reading. The two can have
different schemas, different stores, and different scaling.

**The shape.**

```text
  command                                              query
     |                                                    |
     v                                                    v
  [ write model ]      ...events...      [ read model(s) ]
    normalised,                            denormalised,
    invariants                             one per use case
```

**The ShopStream case.** This is the pattern that actually fits the 12-second catalogue
search. Writes are seller product updates: low volume, need consistency and validation.
Reads are buyer searches during a live stream: enormous volume, tolerate data a second
stale, and want a denormalised search index rather than normalised tables. One model
serving both is what made search degrade under load, and the two workloads can then scale
independently.

**What it buys.** Read and write paths optimised and scaled separately. Read models shaped
per use case rather than compromised across all of them.

**What it costs.** Two models to maintain and keep in step. Eventual consistency becomes
visible to users - a seller updates a price and may not see it reflected immediately, which
is a product decision needing a product owner. More moving parts to operate.

**The warning.** CQRS is the most over-applied pattern in this folder. The full version -
separate stores, asynchronous projection - is justified by a genuine and large asymmetry
between read and write load. Most systems that reach for it need an index, a cache, or a
read replica, all of which are dramatically cheaper. Folder
[10](../../10-kiss-and-yagni/) applies. Note also that a *logical* separation of command
and query methods in one model is cheap, reversible, and gets most of the clarity benefit.
