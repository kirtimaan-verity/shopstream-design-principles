# 22 - Cross-cutting concerns and concepts

*Folder [08](../08-soc-and-loose-coupling/) separates concerns inside one use case. This
folder is about the concerns that refuse to stay in one place - and about deciding each of
them once, for the whole system, instead of forty times by forty people.*

## Concern and concept are two different words

- A **cross-cutting concern** is a recurring need that appears in nearly every building
  block: persistence, error handling, logging, security, concurrency.
- A **cross-cutting concept** is **your system's single answer to one of them**: not
  "we need logging" but "structured JSON to stdout, one correlation id per request,
  propagated across every boundary, card data masked at the emitter".

The concern is universal. The concept is a decision, and it is the deliverable.

> **The failure this folder is about is not "we forgot to log".** It is "we log in six
> different ways, so nobody can follow a request through the system" - which is how a root
> cause takes four hours instead of four minutes.

That makes this the system-wide companion to folder
[13](../13-conceptual-integrity/): conceptual integrity is uniformity of solutions for
similar problems, and a cross-cutting concept is where that uniformity is decided.

## The catalogue

Not exhaustive, and not all of these need a written concept in every system. The ones that
do are the ones where inconsistency actually costs you something.

| Concern | The decision a concept records | ShopStream |
|---|---|---|
| **Persistence** | how state is stored, mapped, migrated | per-module schemas, forward-only migrations |
| **Communication / integration** | how building blocks talk | synchronous for outcomes the buyer waits for, events otherwise |
| **Error handling** | what fails, how far it propagates, what the caller sees | domain failures as values, infrastructure failures as exceptions, translated at the boundary |
| **Concurrency** | threading, locking, ordering, idempotency | request-per-thread, optimistic locking on aggregates, idempotent consumers |
| **Logging and monitoring** | format, levels, what is always included | structured, correlation id mandatory, card data masked |
| **Security** | authentication, authorisation, secrets, transport | token at the gateway, authorisation in the use case, secrets from the environment |
| **Configuration** | where settings come from, how they change | environment per deployment, no environment names in code (folder [06](../06-location-coupling/)) |
| **Transactions** | boundaries, and what happens across them | one aggregate per transaction, outbox for cross-boundary work |
| **Caching** | what, where, for how long, keyed by what | read-through, 60s catalogue TTL, key includes market and locale |
| **Internationalisation** | language, currency, formats, address shapes | locale is a request property, never a global |
| **UI** | shared interaction and component decisions | one component library, one error presentation |
| **Build, deployment, migration** | how code reaches production | one pipeline, one artifact per module |
| **Testing** | what is tested where | domain tested with fakes, one integration test per adapter |
| **Energy efficiency** | where compute is spent | transcoding batched off peak |

**Concepts are reusable across systems.** A logging or security concept that works is
usually a company asset rather than a project one, and adopting a proven concept is
cheaper and safer than inventing a fifth.

## Interdependencies, which is the part that bites

Concepts are not independent. Deciding one constrains others, and the interesting defects
live exactly at those intersections. Identifying them is a real part of the work.

| Interaction | What goes wrong |
|---|---|
| **Caching x security** | a response cached without the identity in the key gets served to a different buyer. A caching concept that does not mention authorisation is a data-leak concept. |
| **Logging x compliance** | "log the full request for debugging" plus PCI-DSS is a finding. The logging concept must name what must never be emitted. |
| **Caching x internationalisation** | a cache key without locale or market serves Egyptian prices to a buyer in the UAE. |
| **Transactions x messaging** | you cannot atomically write to the database and publish to a broker. Either the order exists without the event or the reverse. This forces a transactional outbox, and that requirement comes from the *combination*, not from either concept alone. |
| **Concurrency x persistence** | optimistic locking needs a version column; pessimistic locking needs a lock-ordering rule. The concurrency decision writes itself into the schema. |
| **Error handling x resilience** | a retry concept that ignores idempotency turns one failure into several charges - the incident in folder [05](../05-temporal-coupling/). |
| **Configuration x deployment** | secrets in configuration files plus per-market deployments means rotation is a release. |

> When a design review asks "have we thought about caching?", the useful follow-up is
> "what does that do to authorisation, and to locale?" Most cross-cutting defects are
> found at intersections, not inside a single concept.

## Where ShopStream's concepts are forced

Three of the constraints make specific concepts non-optional:

- **PCI-DSS** forces a security concept, a logging concept that names what must never be
  emitted, and a persistence concept that keeps cardholder data out of the shared schema.
- **In-country data residency** forces persistence and deployment concepts to be
  market-aware, and makes the location decision a runtime one (folder [06](../06-location-coupling/)).
- **Bare metal for nine months** forces configuration and deployment concepts that do not
  assume managed services, and that can be swapped later without touching application code.

A constraint that does not produce a written concept has not really been accepted.

## How to write one down

One page per concept, and short enough that people read it:

```text
  CONCEPT: Error handling

  Problem     Failures arise everywhere. Without one answer, each module
              invents its own, and callers cannot tell what to expect.

  Decision    Domain failures are return values (a sealed result type).
              Infrastructure failures are exceptions, caught at the module
              boundary and translated. No exception crosses a module boundary.
              Every error carries the correlation id.

  How to      Use the shared Result type in domain code. Wrap adapter calls
  apply       at the boundary class. Never catch Throwable. Never swallow.

  Exceptions  The webhook endpoint rejects to a dead-letter queue instead of
              propagating, because the caller is a third party we cannot fix.

  Owner       Platform team. Reviewed at each release.
```

The last two fields do most of the work. **Exceptions** stop the concept being quietly
abandoned the first time it does not fit, and **Owner** gives it someone to defend it.

A concept with no enforcement decays, exactly as in folder [17](../17-modularity/): a
shared base type that makes the right thing easy, a linter or architecture test in the
build, and a review that actually checks.

## Before and after

| `Bad_ScatteredConcerns.java` | `Good_CrossCuttingConcepts.java` |
|---|---|
| errors handled four different ways | one error concept, translated at boundaries |
| logging invented per class | one format, correlation id always present |
| caching hand-rolled per service | one caching concept, keys include market and identity |
| secrets read three different ways | one configuration concept |
| a request cannot be followed across modules | one id follows it end to end |
| intersections unconsidered | cache key names authorisation and locale explicitly |

## Discussion questions

1. Pick a concern from the catalogue and count how many different answers your current
   system has to it. What would it cost to converge on one, and what would it buy?
2. Work through the caching-times-security intersection for the ShopStream catalogue. What
   is in the cache key, and what happens to a logged-in buyer's personalised prices?
3. A concept has an "Exceptions" section. When does a growing exceptions list mean the
   concept is wrong rather than the exceptions being wrong?
