# 10 - KISS and YAGNI

*Two rules for spending complexity: keep it simple, and do not build for a requirement
you do not have.*

## The concepts

**KISS** - Keep It Simple, Stupid. Prefer the solution a competent colleague can
understand without you in the room. Simple is not the same as easy, and it is not the
same as short: a clever one-liner is short and not simple.

**YAGNI** - You Aren't Gonna Need It. Do not build for a requirement you do not have.
Not because the future does not exist, but because your guess about its shape is
usually wrong, and a wrong abstraction is more expensive than no abstraction.

### Why speculative flexibility costs more than it looks

Every unused extension point charges rent:

- somebody reads it and has to work out whether it is used,
- it appears in every stack trace and every debugging session,
- it must be kept working through refactorings, forever,
- and when the real requirement arrives, it usually does not fit the shape you guessed,
  so you pay to remove it *and* pay to build the right thing.

The last one is the important one. A plugin architecture built for imagined auction rules
does not accommodate real auction rules. It obstructs them.

## Deliberate tension with folder 15

Read this folder immediately after **15 (Open/Closed)** or immediately before it, and let
the room argue.

- Folder 15 says: build the extension point, because five markets are coming.
- This folder says: do not build the extension point, because you are guessing.

Both are correct. The distinguishing question is not a matter of taste:

> **Is the variation written down, with a date and an owner?**

| Evidence | Verdict |
|---|---|
| "5 new markets: Egypt, Jordan, Pakistan, India, Nigeria" in the brief | build the extension point (folder 15) |
| "two payment providers today" plus those markets | build the SPI (folder 01) |
| "PCI-DSS Level 1" as a stated constraint | build the boundary (folders 03, 09) |
| "AR product try-on (future)" with no date | YAGNI, do not build |
| "you never know, we might need it" | YAGNI, do not build |

The skill being exercised is not "do you know YAGNI" or "do you know OCP". It is
**explaining how the requirements determine which principle applies**. The principles do
not rank each other; the requirements decide.

## The ShopStream tie-in

The MVP deadline is **6 months**, before the National Day event. The engineering effort
spent on a rules DSL and a plugin loader for auction mode, which is on the 3-year roadmap
with no specification, is effort not spent on the four coupling defects that caused the
last outage.

That is the real argument. YAGNI is not about elegance. It is about where a team with a
fixed deadline spends the weeks it has.

## Before and after

| `Bad_OverEngineeredPricing.java` | `Good_SimplePricing.java` |
|---|---|
| abstract factory, strategy registry, rule DSL, plugin loader | one class, one method |
| reflection-based extension for unspecified features | none |
| approximately 200 lines and 7 types | approximately 25 lines and 1 type |
| a new market means editing the DSL grammar | a new market means one map entry |
| stack traces cross 6 frames of framework | the arithmetic is visible at the call site |

Note the direction of the punchline: the "flexible" version is **harder** to extend for
the change that actually arrives. That surprises people, and it is the point.

## When YAGNI does not apply

Be fair here, or the room will over-apply this and under-design everything afterwards.

- **Things that are expensive to retrofit.** Security boundaries, audit trails, data
  residency, identifier schemes and money representation are painful to add later.
  Cost-of-delay, not speculation.
- **Things the brief already states.** Five markets are not speculation.
- **Reversible versus irreversible decisions.** Deferring a reversible decision is cheap.
  Deferring an irreversible one is a gamble. Choosing a database is closer to
  irreversible than choosing a caching library.

## Impact

| Quality attribute | Effect of the simple version |
|---|---|
| **Modifiability** | new market = one line, understood in one reading |
| **Analysability** | no framework frames between the bug and the reader |
| **Time to market** | weeks returned to the 6-month MVP |
| **Testability** | pure function, no plugin loader to bootstrap |

## Discussion questions

1. Find the requirement in the ShopStream brief that would justify each abstraction in
   `Bad_OverEngineeredPricing`. How many can you find?
2. Auction mode ships in year two and needs bid-based pricing. Which version is easier to
   extend, honestly? Why does the answer feel backwards?
3. Name one thing ShopStream should build now even though nobody has asked for it, and
   defend it using cost-of-delay rather than "we might need it".
