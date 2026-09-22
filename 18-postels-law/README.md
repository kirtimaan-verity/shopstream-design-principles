# 18 - Postel's Law and the Robustness Principle

*"Be conservative in what you do, be liberal in what you accept from others."*
Jon Postel, RFC 761 / RFC 793, 1980-81.

Generalised, it is a design stance rather than a protocol rule: **expect errors**. Assume
your inputs will be malformed, your peers will be down, and your own output will outlive
your assumptions about who reads it.

## The two halves are not equally safe

The first half is uncontroversial and underused. The second half is the one that has aged
badly, and a folder that teaches only the slogan does more harm than good.

**"Be conservative in what you do"** - emit exactly what the contract specifies. Canonical
format, no clever shortcuts, no reliance on the other side being forgiving. This half is
close to free and almost always right.

**"Be liberal in what you accept"** - taken literally, this is how you get a system nobody
can ever change. The IETF has walked it back substantially (RFC 9413, *Maintaining Robust
Protocols*, 2023). Three things go wrong:

1. **The bug gets baked in.** A producer emits something malformed. Your lenient parser
   quietly repairs it. Nobody is ever told. Six months later the malformed form is what
   everything emits, and fixing the producer is now the breaking change.
2. **The de-facto spec becomes "whatever the tolerant implementation accepts."** The
   written contract stops being the contract. Every new implementation has to
   reverse-engineer the tolerant one's quirks.
3. **Lenient parsers are a security surface.** Two components that disagree about what a
   message means is the shape of request smuggling, parser-differential attacks, and a
   long tail of authentication bypasses.

## The usable version

> Be **strict** about what you must understand. Be **liberal** only about what you can
> safely ignore. Tolerate nothing silently.

That resolves into three rules, and they are what the `Good_` file implements:

| | Rule | Why |
|---|---|---|
| **Ignore** | Unknown *additional* fields | This is what makes forward compatibility work. A consumer that rejects a message because a producer added a field cannot be deployed independently. |
| **Reject** | Missing or wrong-typed *required* fields | If you cannot understand it, you cannot act on it. Guessing is how a payment gets marked settled. |
| **Report** | Everything you tolerated | Tolerance without a metric is how problem 1 above happens. If you accepted something odd, someone must be able to find out. |

The third rule is the one that makes the first two safe, and it is the one everybody
skips.

### Never be liberal about these

- **Money, amounts and currency.** No coercion, no defaulting, no guessing.
- **Anything security- or authorisation-related.** A missing signature is a rejection, not
  a default.
- **Status and outcome fields.** "Absent means success" is a decision that will eventually
  settle an order nobody paid for.
- **Identity.** A malformed id is an error, not something to normalise.

## The other half: expect errors

The conservative half generalises into resilience, which folder 05 covers from the timing
side: assume the far end will be slow or absent, bound every wait, have a defined
behaviour for "no answer". Postel's law and the circuit breaker in
[05](../05-temporal-coupling/) are the same instinct applied to content and to time.

## The ShopStream tie-in

The payment gateway webhook. It arrives from outside the trust boundary, from a vendor
whose format changes without notice, and it decides whether an order is paid.

`Bad_LenientWebhookHandler.java` is maximally liberal: it accepts the amount as a number,
a string, or a string with a currency symbol; it guesses the currency when absent; it
treats a missing status as success; and it swallows anything it cannot parse. Every one of
those was added to stop an incident, and together they produce the failure mode from the
brief: **orders marked settled that were never paid**, discovered weeks later as a
reconciliation gap - the same class of silent, non-throwing defect as the VAT drift in
folder [11](../11-dry/).

## Before and after

| `Bad_LenientWebhookHandler.java` | `Good_RobustBoundary.java` |
|---|---|
| amount accepted in 3 formats, coerced | one format, rejected otherwise |
| currency guessed when missing | required, no default |
| missing status defaults to success | required, unknown value rejected |
| parse failures swallowed and logged at debug | rejected to a dead-letter queue, alerted |
| unknown fields rejected (too strict, wrongly) | unknown fields ignored (correctly liberal) |
| signature checked "when present" | always, before anything else is read |
| tolerance is invisible | every tolerated deviation is counted and reported |

Note the two reversals in that table. The Bad version is liberal about the things it must
be strict about (money, status) and strict about the one thing it should tolerate (unknown
fields). That inversion is extremely common, because both halves are decided in a hurry
during an incident rather than as a contract.

## Impact

| Quality attribute | Effect of the strict-but-forward-compatible version |
|---|---|
| **Correctness** | no order is settled on a guess |
| **Security** | one parse, one meaning; signature checked first |
| **Modifiability** | producers can add fields without coordinating a release |
| **Analysability** | tolerated deviations are visible instead of absorbed |
| **Operability** | a dead-letter queue you can replay, not a swallowed exception |

## Discussion questions

1. Each leniency in `Bad_LenientWebhookHandler` was added to stop a real incident. Take
   them one at a time: what should have been done instead, and who would have had to be
   told?
2. The `Good_` version ignores unknown fields but rejects unknown *status values*. Both
   are "something I do not recognise". Why does one get tolerated and the other rejected?
3. Your consumer starts receiving a field it has tolerated-and-ignored for a year, now
   carrying meaning. How would you find out? What would have made that visible on day one?
