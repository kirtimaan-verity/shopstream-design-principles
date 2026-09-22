# 02 - Distributing a system

*How do parts talk across a boundary?* Every pattern here converts an in-process call into
a network call. That trade is never free: you gain independent scaling and deployment, and
you pay in latency, partial failure, versioning and operations.

> Read folder [17](../../17-modularity/) first. A module boundary is cheap to move and a
> service boundary is not, so the sequence that works is: get the modules right in one
> process, then distribute the ones that need it.

---

## Microservices

**What it is.** Build the system as a set of small, independently deployable services, each
owning one business capability and its own data, communicating over the network.

**The shape.**

```text
  [catalogue]   [orders]   [payments]   [streaming]
      |            |           |             |
   own DB       own DB      own DB        own DB
      \___________\____ network ____/____________/
```

The two non-negotiables that people skip: **independently deployable** (if you must
release them together, they are one service in several processes) and **own data** (if
they share a schema, you have a distributed monolith with the costs of both).

**The ShopStream case.** The three-year target - 10,000 concurrent streams, 50 million
buyers, five new markets - is the case *for*. The constraints are the case *against*:
bare metal for nine months, a six-month deadline, and one engineering team. The defensible
answer under those constraints is a modular monolith now with capability boundaries
already drawn, extracting payments first because compliance isolation gives it the
clearest independent justification.

**What it buys.** Independent deployment and scaling, team autonomy, technology choice per
service, and failure isolation - if the boundaries are right.

**What it costs.** All of distributed computing: network latency on every call, partial
failure as a permanent condition, eventual consistency, distributed tracing, per-service
operations, and cross-service versioning. Plus an organisational cost people underestimate:
you need the ability to deploy and operate services independently before the pattern pays
back anything.

**The failure mode.** Boundaries drawn before the seams are known. Then every feature
touches three services, each with its own release, and you have bought every cost and
none of the autonomy.

---

## Service-Oriented Architecture

**What it is.** Organise the system as services aligned to business functions, with
explicit contracts, typically coordinated through shared infrastructure such as a service
bus, and often with shared governance of contracts and canonical data models.

**How it differs from microservices.** Same idea, different centre of gravity. SOA tends
toward coarser services, shared middleware carrying routing and transformation logic, and
centralised contract governance. Microservices tend toward finer services, dumb pipes with
smart endpoints, and decentralised ownership.

**The ShopStream case.** If ShopStream needed to integrate with several external partners
- logistics providers, tax authorities in five markets, two payment gateways - a mediating
layer that owns protocol translation and routing to those partners is SOA thinking, and is
reasonable. The historic mistake was letting that layer accumulate business logic.

**What it buys.** Reuse of coarse business capabilities, a single place for cross-cutting
integration concerns, and central governance where regulation demands it.

**What it costs.** The shared bus becomes a bottleneck for both traffic and change: a
component every team must queue behind. Canonical data models tend toward a schema that
suits nobody. This is the trap the "smart endpoints, dumb pipes" slogan was coined against.

---

## Broker

**What it is.** A broker decouples clients from servers. Clients ask the broker; the broker
locates the right server, forwards the request, and returns the result. Neither side knows
where the other is.

**The shape.**

```text
  1.  client   sends a request    to    [ BROKER ]
  2.  BROKER   consults its             registry  (who provides what, and where)
  3.  BROKER   forwards the request to  server
  4.  server   returns a result   to    [ BROKER ]
  5.  BROKER   returns the result to    client

  neither side ever learns where the other one is
```

**The ShopStream case.** The service registry in folder
[06](../../06-location-coupling/) is the discovery half of this. A caller asks for
`product-service` and the registry resolves a healthy instance - which is what turns the
23-minute single-server outage into a health-check interval.

**What it buys.** Location transparency: servers can move, scale and fail over without any
client changing. It is the direct countermeasure to location coupling.

**What it costs.** The broker is a component to run and a potential single point of
failure, and it adds a hop. Debugging gains an indirection: "which instance actually served
this?" becomes a question you need tooling to answer.

**Modern forms.** Message brokers, API gateways, service meshes, and DNS-based discovery
are all this pattern with different emphases.

---

## Remote Procedure Call

**What it is.** Make a call to another process look like a local method call. The stub
marshals arguments, sends them, waits, and unmarshals the result.

**The shape.**

```text
  caller
    |
    v
  [ client stub ]     marshals the arguments
    |
    v
   network            the part that can be slow, or fail, or answer twice
    |
    v
  [ server stub ]     unmarshals them
    |
    v
  callee
```

**The ShopStream case.** Every internal HTTP call between the gateway and the downstream
services is RPC in style, whatever the transport. gRPC and the older SOAP or CORBA stacks
are the explicit forms.

**What it buys.** Familiar programming model, strong typing when the IDL is generated, and
low ceremony. For request/response interactions where the caller genuinely needs an answer
before continuing, it is the honest choice.

**What it costs.** This is the pattern with the most dangerous marketing, because the
thing it promises - that a remote call looks local - is not true and cannot be made true.
A local call does not have latency, does not partially fail, does not time out ambiguously,
and does not need a version negotiated. Code written as though it does is exactly
`Bad_SynchronousCheckout.java` in folder [05](../../05-temporal-coupling/), and it is how
an 8% failure rate happens.

**The rule.** Use RPC where you genuinely need an answer now, and then treat it as remote:
a bounded time budget, a circuit breaker, an idempotency key, and a defined behaviour when
there is no answer. Where you do not need an answer now, group
[03](../03-data-events-and-integration/) has better options.
