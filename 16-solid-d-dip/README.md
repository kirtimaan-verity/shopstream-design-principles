# 16 - SOLID: D, the Dependency Inversion Principle

*The word doing the work is **inversion**: the high-level module owns the interface, so
the dependency arrow points against the flow of control.*

Like OCP, this one wears two hats: it is the D of SOLID, and it is the technique that
makes modular decomposition actually hold. The usual mechanism for externalising a
dependency - deciding the concrete type at install time or run time - is dependency
injection, which is **not** the same thing as dependency inversion. That distinction is
worth ten minutes on its own and gets a section below.

## The concept

> - High-level modules should not depend on low-level modules. Both should depend on
>   abstractions.
> - Abstractions should not depend on details. Details should depend on abstractions.

The word doing the work is **inversion**. Without it, dependencies flow the way control
flows: the use case calls the repository, so the use case depends on the repository, so
the business rules depend on PostgreSQL.

DIP reverses that arrow by making the **high-level module own the interface**:

```text
   WITHOUT DIP                        WITH DIP

   PlaceOrderUseCase                  PlaceOrderUseCase
        |                                   |
        | depends on                        | depends on
        v                                   v
   PostgresOrderDao                      Orders  (interface, defined
        |                                   ^      next to the use case)
        v                                   | implements
   PostgreSQL driver                  PostgresOrderRepository
                                            |
                                            v
                                      PostgreSQL driver
```

The arrow at the bottom now points **upward, against the flow of control**. That is the
inversion. Control still goes use case -> database; the source dependency goes database ->
use case.

## DIP is not DI, and this confusion is worth ten minutes

They are constantly conflated. They are different things.

| | Dependency **Injection** | Dependency **Inversion** |
|---|---|---|
| What it is | a mechanism for supplying collaborators | a rule about who owns the interface |
| Question it answers | "how does this object get its dependencies?" | "which direction does the source dependency point?" |
| Achieved by | constructor parameters, a framework | defining the interface in the high-level module |

**You can have DI without DIP.** Inject a `PostgresOrderDao` through a constructor and you
have DI, perfect testability with a mock, and no inversion at all: the use case still
names a database class.

**You can have DIP without a DI framework.** Define `Orders` next to the use case, `new`
the Postgres implementation in `main()`, pass it in. Inverted, no framework.

Folder 02 makes the same point from the coupling side: forgoing a static dependency in
favour of a dynamic one does not necessarily reduce the coupling underneath. An injected
concrete class is still a concrete dependency.

## Where the interface lives is the whole principle

This is the part that gets lost.

```text
BAD (not inverted)                    GOOD (inverted)

  persistence/                          domain/
    OrderRepository  (interface)          PlaceOrderUseCase
    PostgresOrderRepository               Orders            (interface)
  domain/                               persistence/
    PlaceOrderUseCase                       PostgresOrderRepository
      depends on: persistence                 depends on: domain
```

Both have an interface. Only the second inverts anything. In the first, the domain still
depends on the persistence package; the interface just moved the dependency without
changing its direction.

**A quick test:** could you delete the `persistence` package and still compile the domain?
If not, nothing was inverted.

**A naming test:** an interface named after its implementation (`IPostgresRepository`,
`SmtpMailer`) was written from the implementation's point of view. An interface named for
what the caller needs (`Orders`, `Notifier`) was written from the caller's.

## The ShopStream tie-in

This is **A5 Problems 1 and 4** at the same time.

Problem 4, the shared schema: three services read and write one PostgreSQL schema, so a
column rename by Product breaks Order at runtime with no compile-time warning. Splitting
that schema is on the roadmap, and it is only possible if the domain logic does not name
tables. Every service that does `SELECT * FROM products` inside a use case is a service
that cannot be separated.

Problem 1, the UI reaching the database directly, is the same defect one layer out: the
highest-level component in the system depends directly on the lowest-level one.

Combined with the nine-month bare-metal constraint, DIP is what makes the eventual
infrastructure migration a matter of writing new adapters rather than rewriting business
logic.

## Before and after

| `Bad_HighLevelDependsOnLowLevel.java` | `Good_InvertedDependencies.java` |
|---|---|
| use case imports `PostgresOrderDao`, `SmtpMailer` | use case imports nothing infrastructural |
| SQL strings inside business logic | SQL lives in an adapter |
| interfaces owned by the persistence layer | interfaces owned by the domain |
| cannot compile domain without a JDBC driver | domain compiles alone |
| schema split is a rewrite | schema split is a new adapter |

## The limitations

- **Not everything deserves inversion.** Inverting a dependency on `java.time` or on a
  logging facade buys nothing and costs a layer. Invert what will change or what you need
  to fake in a test.
- **The wiring has to live somewhere.** DIP concentrates knowledge of concrete types in a
  composition root. That file is big and boring on purpose, and it should stay in one
  place rather than being spread through the system as annotations.
- **Indirection has a debugging cost.** "Which implementation ran?" becomes a runtime
  question. Correlation ids and clear naming pay part of that back.

## Impact

| Quality attribute | Effect |
|---|---|
| **Testability** | domain logic tests with no database and no mail server |
| **Modifiability** | swapping infrastructure does not touch business rules |
| **Portability** | bare metal now, something else in nine months |
| **Analysability** | Note: one extra hop when reading a stack trace |

## Discussion questions

1. In `Bad_HighLevelDependsOnLowLevel`, which line makes it impossible to unit test the
   pricing rule? What would you have to stand up to test it as written?
2. Both versions could use constructor injection. Explain in one sentence why only one of
   them inverts anything.
3. ShopStream wants to split the shared schema so each service owns its data. Which
   version makes that a contained change, and which line of code is the reason?
