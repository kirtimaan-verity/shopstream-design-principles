# 21 - Domain-Driven Design

*Folder [17](../17-modularity/) says to cut the system by business capability and gives
five criteria for where boundaries go. This folder is the method that actually finds
them - and the vocabulary for talking about what happens where two boundaries meet.*

## The one idea underneath all of it

**The structure of the software should follow the structure of the business, and both
should use the same words.**

Everything else is technique in service of that. When a seller says "listing", the
catalogue code should say `Listing`, and the meeting, the ticket, the database column and
the class name should all agree. Every translation step between the business's words and
the code's words is a place where meaning gets lost, and those losses compound.

## Strategic design: where the boundaries go

This half matters more, and gets skipped more. Getting the tactical patterns right inside
the wrong boundaries buys nothing.

### Ubiquitous language

One language per context, used by everyone, in conversation and in code. Not a glossary
nobody reads - the actual words in the actual class names.

The diagnostic: sit in a requirements discussion and note every term that gets translated
on the way into code. Each translation is a defect waiting to happen.

### Bounded context

**The boundary within which one model, and one meaning per word, applies.**

This is the central idea, and it resolves an argument teams have endlessly. In ShopStream,
"Product" means four different things:

| Context | What "Product" is | What it needs |
|---|---|---|
| Catalogue | something sellable | title, images, price, stock, search terms |
| Orders | a line on an order | id, price at time of order, quantity |
| Payments | nothing at all | it only knows an amount and an order id |
| Streaming | something being demonstrated | id, thumbnail, on-screen position, highlight timing |

The wrong answer, and the common one: build one `Product` class with the union of all four,
about thirty fields, most null at any moment. Every context is now coupled to every other
context's requirements, and a change for streaming can break checkout.

The right answer: **four models, one per context, each small and each complete for its
own purpose.** They are not duplicates. They are different concepts that share a name -
exactly the argument in folder [11](../11-dry/) about two `Address` records that must stay
separate.

> **The test:** if two parts of the business would answer "what is a Product?" differently,
> they are different contexts and should have different models.

### Context mapping

Once you have several contexts, the interesting question is what happens between them. The
named relationship patterns, and when each applies:

| Pattern | What it means | ShopStream |
|---|---|---|
| **Shared Kernel** | two contexts share a small model, changed only by agreement | `Money` and the typed ids in folder [00](../00-shopstream-domain/) |
| **Customer / Supplier** | downstream's needs are a priority for upstream | Orders needs specific data from Catalogue; Catalogue accommodates it |
| **Conformist** | downstream just accepts upstream's model, no translation | using the CDN vendor's own stream metadata shape |
| **Anticorruption Layer** | downstream translates, to protect its own model | the payment gateway adapters in folder [12](../12-abstraction/) |
| **Open Host Service** | upstream publishes a general-purpose protocol for many consumers | the public API for seller integrations |
| **Published Language** | a well-documented shared exchange format | the `OrderPlaced` event schema in folder [04](../04-message-coupling/) |
| **Separate Ways** | no integration at all, duplicate the small bit you need | the marketing email list does not integrate with orders |

**Anticorruption Layer is the one to know.** It is the reason the Checkout.com adapter
exists: without it, Checkout.com's model of a payment leaks into ShopStream's domain and
the vendor's vocabulary becomes ShopStream's vocabulary. Folder
[12](../12-abstraction/) shows exactly that leak happening.

**Separate Ways is the one nobody considers.** Sometimes the right integration is none.

## Tactical design: what goes inside a context

| Building block | What it is | ShopStream example |
|---|---|---|
| **Value Object** | defined by its value, immutable, no identity | `Money`, `ShippingAddress` |
| **Entity** | has identity and a lifecycle; equal by id, not by value | `Order`, `LiveStream` |
| **Aggregate** | a cluster of objects treated as one unit for changes, with a **root** as the only entry point | `Order` with its `OrderLine`s |
| **Repository** | collection-like access to aggregates | `Orders` in folder [16](../16-solid-d-dip/) |
| **Domain Service** | a domain operation that belongs to no single entity | `VatPolicy`, routing a payment |
| **Domain Event** | something meaningful that happened, past tense | `OrderPlaced` |
| **Factory** | encapsulates complex creation | building an `Order` from a cart |

### Aggregates are the part worth slowing down on

An aggregate is a **consistency boundary**. Three rules:

1. **One entry point.** Outside code holds a reference to the root only, never to something
   inside it. `order.addLine(...)`, never `order.getLines().add(...)` - which is folder
   [09](../09-information-hiding/)'s argument exactly.
2. **Invariants hold inside, always.** The root enforces them on every change. "An order's
   total equals the sum of its lines" is true after every operation, not eventually.
3. **Reference other aggregates by id, not by object.** `Order` holds a `ProductId`, not a
   `Product`. This keeps aggregates small, keeps transactions small, and keeps contexts from
   dragging each other's object graphs around.

Rule 3 is the one people resist and the one that pays off most. It is what makes the split
into separate modules, and later separate services, mechanical rather than archaeological.

## When not to do this

DDD is expensive. It costs modelling time, vocabulary discipline, and a team willing to
argue about words. It pays for itself where **the domain is complex and the complexity is
the point.**

It does not pay where:

- the domain is CRUD with a UI on top - most admin tools, most reporting,
- the complexity is technical rather than domain (a video transcoder, a proxy),
- nobody on the business side is available to build the language with,
- the system is small enough for one person to hold in their head.

Applying tactical patterns without the strategic half is the common failure: aggregates,
repositories and value objects arranged inside a single model that spans the whole
business. That is ceremony with no boundary, and it is slower than the CRUD it replaced.

## Before and after

| `Bad_SharedAnaemicModel.java` | `Good_BoundedContexts.java` |
|---|---|
| one `Product` for the whole system, 30 fields | four models, each small and complete |
| entities are field bags, logic lives in services | aggregates enforce their own invariants |
| `order.getLines().add(...)` from anywhere | `order.addLine(...)`, invariants hold |
| `Order` holds a `Product` object | `Order` holds a `ProductId` |
| the vendor's vocabulary reaches the domain | an anticorruption layer translates at the edge |
| one word, four meanings, endless meetings | one word per context, each unambiguous |

## Discussion questions

1. Pick a noun your team argues about. Ask two people from different parts of the business
   to define it. If the definitions differ, where is the context boundary?
2. `Order` holding a `ProductId` rather than a `Product` makes some code less convenient.
   What does it buy, and when would you accept the object reference instead?
3. Where in a system you work on is there an anticorruption layer that should exist and
   does not? What vendor's vocabulary has become yours as a result?
