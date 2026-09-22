# 20 - Quality-driven architecture

*Every folder in this repo ends with an "Impact" table naming a quality attribute. This
folder is where those tables come from, and why a design decision without one is a
preference rather than an argument.*

## "Quality" means two different things

- In quality management it means **excellence**: is this good?
- In architecture it means **a specific property of the system**: how fast, how available,
  how changeable, how secure.

This folder is entirely about the second. "Improve quality" is not actionable. "p99
checkout latency under 800ms during a peak of 14,000 transactions per hour" is.

Several taxonomies exist for naming these properties - ISO 25010 is the common reference,
and the arc42 quality model is a more practical starting list. Some taxonomies separate
*functionality* from *quality*; others treat functionality as one quality among many.
Which taxonomy you use matters far less than picking one and using its words consistently
(folder [13](../13-conceptual-integrity/) applies to vocabulary too).

One requirement often touches several qualities at once. "Buyers must be able to pay with
a saved card" is functionality, security and usability in one sentence, and the three pull
in different directions.

## The tool that does the work: quality scenarios

A quality scenario turns an adjective into something you can design for, test, and argue
about. Five parts:

| Part | Question | ShopStream example |
|---|---|---|
| **Context / source** | Under what conditions, and from whom? | during a National Day peak, 14,000 tx/hour |
| **Stimulus** | What happens? | a buyer submits checkout |
| **Artifact** | To which part? | the checkout path |
| **Response** | What should the system do? | the order is accepted and the buyer is told the outcome |
| **Measure** | How much, measured how? | p99 under 800ms; measured from access logs |

Compare:

```text
  Vague:     "checkout must be fast and reliable"

  Scenario:  During a peak of 14,000 transactions per hour, when a buyer submits
             checkout, the system responds with a definite outcome (accepted,
             declined or pending) for 99.9% of requests, with p99 latency under
             800ms, measured from gateway access logs over the peak window.
```

The second one can be designed for, tested, and failed. The first cannot be failed, which
is why it always passes.

**A quality requirement should also name its method of analysis.** If nobody can say how
the requirement will be checked, it is not a requirement - it is an aspiration with a
number attached.

## Qualities pull against each other

This is the part that makes architecture a design activity rather than a checklist. There
is no configuration in which every quality is maximised.

| Trade | ShopStream instance |
|---|---|
| configurability vs reliability | the plugin loader in folder [10](../10-kiss-and-yagni/): every extension point is another way to boot into a broken state |
| memory vs performance | caching the catalogue makes search fast and the heap large |
| security vs usability | requiring re-authentication per purchase cuts fraud and cuts conversion |
| runtime flexibility vs maintainability | resolving providers at run time means the running behaviour is not visible in any source file |
| consistency vs availability | folder [05](../05-temporal-coupling/): accepting orders while the gateway is down means orders that may not settle |

**The architect's job is not to maximise a quality. It is to make the trade explicitly, in
public, with the losing side written down.** An unrecorded trade-off gets re-litigated
every six months by whoever is annoyed by the losing side that week.

## Prioritise, or you have decided nothing

If every quality is important, none is. The usual instrument is a **quality tree**: the
qualities that matter, refined into scenarios, each rated for **business importance** and
**architectural difficulty**. The scenarios that are high on both are the ones your design
has to actually address.

ShopStream, from the brief:

| Scenario | Business importance | Difficulty | Verdict |
|---|---|---|---|
| Checkout completes during peak | high | high | drives the design |
| Payment data stays compliant | high | medium | non-negotiable constraint |
| Buyer data stays in-country | high | medium | non-negotiable constraint |
| Catalogue search under 500ms at peak | high | high | drives the design |
| New market added without a rewrite | high | medium | drives the design |
| Seller report renders in under 5s | medium | low | do not design for it |
| AR try-on latency | low | unknown | not a requirement yet, folder [10](../10-kiss-and-yagni/) |

## Ways to find out whether you got it

Different qualities need different methods, and most systems need several:

| Method | Good for | ShopStream use |
|---|---|---|
| Acceptance test results | functional correctness | does checkout produce the right order |
| Quantitative runtime measurement | performance, availability | p99 latency, error rate during peak |
| Qualitative evaluation (interviews, surveys, penetration tests) | usability, security, operability | can ops actually diagnose an incident |
| Scenario-based analysis | modifiability, extensibility | walk "add a Nigerian payment provider" through the design |
| Coupling metrics (inbound and outbound dependencies) | maintainability | the efferent count in folder [02](../02-call-coupling/) |
| Source metrics (size, cyclomatic complexity) | maintainability, testability | which modules resist change |
| Cost-benefit analysis | prioritising remediation | fix the shared schema, or ship the market launch |
| Structured trade-off analysis | conflicting qualities across stakeholders | the Day-3 style review of the whole design |

**Scenario-based analysis deserves special mention** because it needs no running system.
You can walk a change scenario through a design on a whiteboard and count the building
blocks it touches. That is how you evaluate modifiability before writing any code, and it
is the cheapest architecture evaluation there is.

## Goodhart's law, which will happen to you

> When a measure becomes a target, it ceases to be a good measure.

Set "p99 latency under 800ms" as a team target and someone will eventually meet it by
returning an empty result quickly, moving work to an unmeasured background job, or
excluding the slow requests from the percentile.

This is not an argument against measuring. It is an argument for measuring **the outcome**
alongside the proxy: pair latency with conversion rate, pair test coverage with defect
escape rate, pair deployment frequency with change failure rate. A single number as a
target is a number that will be gamed, usually without anybody intending to.

## Before and after

| `Bad_UnmeasuredQualityGoals.java` | `Good_QualityScenarios.java` |
|---|---|
| "must be fast" in a comment | a scenario with a number and a window |
| tuning constants with no stated target | each constant traced to the scenario that justifies it |
| optimisation chosen by intuition | optimisation chosen by measurement |
| nothing can fail, so nothing is checked | scenarios expressed as executable checks |
| trade-offs made silently in code | trade-offs named, with the losing side recorded |

## Discussion questions

1. Take "the system must be scalable" and write it as a scenario with all five parts,
   using numbers from the case study. What did you have to ask someone to find out?
2. Two scenarios in the quality tree above are rated high importance and high difficulty.
   What would you build differently if only one of them were true?
3. Pick a quality target your team currently measures. How would you game it without
   technically lying, and what second measure would catch you?
