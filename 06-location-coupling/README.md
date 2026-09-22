# 06 - Location Coupling

*When a component has to know where another one physically is, the deployment topology
has been compiled into the application. The countermeasure is to externalise it: decide
concrete locations at install time or run time, not at build time.*

## The concept

A building block is location-coupled to another when it has to know **where the other one
physically is**: a hostname, an IP address, a port, a file path, a queue name, a database
URL, a region.

Location coupling is easy to miss because it usually is not a type dependency at all. It
is a string. `grep` for `http://` and `jdbc:` in any codebase and you will find the
system's real deployment topology, scattered across files that nobody thinks of as
configuration.

### Why it is architecturally expensive

- **You cannot move anything.** Adding a server, draining one for maintenance, or
  failing over to another rack requires a code change, a build, and a deploy.
- **You cannot run more than one environment honestly.** Staging either points at
  production or needs a parallel set of edited constants.
- **You cannot scale horizontally.** A hard-coded host is one host. Load balancing has
  nowhere to happen.
- **Compliance becomes a code problem.** If the destination of buyer data is a compile
  time constant, data residency is enforced by whoever last edited that constant.

## The ShopStream tie-in

Two things in the brief land squarely on this.

**Pain point P3.** One bare-metal server lost network connectivity and 30% of viewers had
a 23 minute partial outage. A third of traffic was pinned to that machine by address.
There was no mechanism for the other two servers to absorb it, because nothing knew that
the other two servers existed.

**A5 Problem 1.** The UI holds a **direct database connection to the Product DB**,
bypassing the API Gateway. That is location coupling from a browser client to a database
instance, and it is simultaneously a security problem (credentials shipped to the client),
an availability problem (no pooling, no failover), and a modifiability problem (the DB
cannot be moved, renamed, sharded or split without breaking the front end).

**Data residency.** Buyer personal data must remain in-country for UAE and Saudi Arabia.
With hard-coded endpoints, "which datacentre does this buyer's data go to" is answered by
a string literal. It has to be a runtime decision driven by the buyer's market, or the
constraint is enforced by hope.

## Before and after

| `Bad_HardcodedEndpoints.java` | `Good_ResolvedEndpoints.java` |
|---|---|
| `http://10.0.3.14:8080/products` | logical name `product-service` |
| `jdbc:postgresql://10.0.3.14/...` in UI code | UI calls the gateway, never a database |
| `/var/shopstream/uploads` | a `BlobStore` port, path is the adapter's business |
| one host, no failover | registry returns healthy instances, breaker skips dead ones |
| residency by convention | residency resolved from the buyer's market at runtime |

## The nuance

**You cannot remove location coupling. You can only move it.** Something, somewhere, has
to know that 10.0.3.14 exists. The goal is to move that knowledge:

- out of **application code** and into **configuration**,
- out of **build time** and into **runtime**,
- out of **many places** and into **one**.

On ShopStream's constraints this matters more than usual. Bare metal for the next nine
months means no cloud load balancer and no managed service discovery. The registry
abstraction is what lets them run something simple now (a config file plus health checks)
and swap in something better after Series B, without touching a single service.

That is the actual argument for the abstraction, and it is worth making explicitly: it is
not "service discovery is best practice", it is "our infrastructure is going to change in
nine months and we do not want to rewrite six services when it does".

## Impact

| Quality attribute | Effect |
|---|---|
| **Availability** | failover becomes possible at all (P3) |
| **Scalability** | new instances join by registering, not by a release |
| **Modifiability** | moving a service is an ops action, not a code change |
| **Security** | no DB credentials in a browser (A5 Problem 1) |
| **Compliance** | residency routing is data-driven and auditable |

## Discussion questions

1. The `Good_` version still contains the string `"product-service"`. Is that location
   coupling? If not, what kind of coupling is it, and is it acceptable?
2. ShopStream cannot use a cloud load balancer for nine months. Does that invalidate the
   registry abstraction, or strengthen the case for it?
3. Where should the mapping from `market -> datacentre` live so that a compliance auditor
   can verify it without reading Java?
