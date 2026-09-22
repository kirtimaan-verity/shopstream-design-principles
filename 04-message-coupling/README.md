# 04 - Message Coupling (messaging / events)

*Trading a static dependency for a dynamic one does not necessarily reduce the
underlying coupling. This folder exists to prove it.*

Putting a broker between two components is the most commonly misread refactoring in
distributed design: the arrows disappear from the diagram while the coupling stays
exactly where it was.

## The concept

With **call coupling**, A holds a reference to B and invokes a method. The compiler
verifies the contract.

With **message coupling**, A publishes to a topic. B subscribes. A has never heard of B.
No compiler verifies anything.

### What you actually traded

| | Call coupling | Message coupling |
|---|---|---|
| Coupled to | a **signature** | a **schema** |
| Verified by | the **compiler**, at build time | **nobody**, until 3am in production |
| Who knows whom | A knows B | A knows the **topic**; B knows the topic |
| Adding a consumer | change the producer | change nothing |
| Temporal | A waits for B | A does not wait - see folder 05 |
| Failure mode | `NoSuchMethodError` at build | silent data loss, wrong behaviour |

> **You did not remove coupling. You moved it from the compiler to the schema, and from
> build time to runtime.** That is often an excellent trade - ShopStream needs it for the
> 8%-timeout problem - but it is a *trade*, and calling it "decoupling" is how teams end
> up with a distributed monolith that is *harder* to change than the original.

## The two ways message coupling goes wrong

### 1. The untyped payload

`Map<String, Object>` or raw JSON. The producer thinks the field is `orderId`; a consumer
written eight months later reads `order_id` and gets `null`. Nothing fails. The order is
simply never fulfilled. ShopStream would find out from a buyer complaint, not from a
monitor. This is A5 Problem 4 (the shared schema) reincarnated in the message bus - the
same defect, one layer up.

### 2. The command disguised as an event - **the important one**

```java
// This is NOT an event. It is a remote procedure call with extra latency.
publish("order.placed", Map.of(
    "orderId", id,
    "sendEmailTo", buyerEmail,      // producer is issuing an instruction
    "emailTemplate", "order_confirm_v2",
    "alsoNotifySellerDashboard", true
));
```

The Order Service still knows that emails get sent, which template is used, and that a
seller dashboard exists. Every one of those is a fact about a *consumer*. Delete the
Notification Service and this producer is wrong. **You have call coupling with a message
broker in the middle** - all of the coupling, plus new failure modes, plus infrastructure
to operate.

> **The test:** could you delete every consumer and would the producer still be correct?
> An event says *"this happened"*. A command says *"do this"*. Only the first one decouples.

## The ShopStream tie-in

Checkout today does: create order -> charge -> **send email** -> **push notification** ->
**update seller dashboard** -> **track analytics**. Six things, one synchronous chain, on
the buyer's critical path - during a National Day peak of ~14,000 transactions/hour.

`OrderPlaced` as a real event moves four of those six off the critical path. Checkout
latency stops depending on SMTP. Adding "notify the co-host" for the co-streaming feature
becomes a new subscriber with **zero** producer changes.

## Before and after

| `Bad_UntypedEventBus.java` | `Good_OrderPlacedEvent.java` |
|---|---|
| `Map<String, Object>` payload | immutable record, explicit fields |
| producer names consumers and templates | producer states a fact and stops |
| field typo = silent loss | typo = compile error |
| no version | `schemaVersion`, additive evolution rules |
| consumer count is producer's business | consumer count is unknown to producer |

## Impact - quality attributes

| Quality attribute | Effect of doing it *properly* |
|---|---|
| **Performance** | checkout latency no longer includes SMTP + push + analytics |
| **Availability** | Notification Service down => orders still complete |
| **Modifiability** | new consumer (co-host notify, fraud scoring) = zero producer change |
| **Analysability** | Caution: **worse** - no stack trace crosses a broker (P4, 4-hour root cause). Pay for it with a correlation id on every event. Say this cost out loud. |

## Discussion questions

1. A consumer needs the buyer's **email address**, which is not on `OrderPlaced`. Do you
   add it to the event? (Careful - that is how an event becomes a command. What is the
   alternative, and what does that alternative cost?)
2. ShopStream adds a fraud-scoring service that must **block** an order. Can that be a
   subscriber to `OrderPlaced`? What does the answer tell you about which decisions
   events are *unsuitable* for?
3. Message coupling removed a compile-time check. What must ShopStream add to its build
   pipeline to get an equivalent guarantee back?
