# 01 - API vs SPI

*Two interfaces that point in opposite directions, and what goes wrong when a codebase
merges them into one.*

## The concept in one sentence each

| | **API** - *Application Programming Interface* | **SPI** - *Service Provider Interface* |
|---|---|---|
| Who **calls** it | Your client code | Your own framework/service |
| Who **implements** it | You (the platform) | A third party (the provider) |
| Direction of the call | client -> you | you -> provider |
| What "adding one more" means | a new *caller* | a new *implementation* |
| Breaking change hurts | your callers | your providers |
| Grows by | adding methods (rarely) | adding implementations (often) |

> The clean mnemonic for the room: **an API is what you offer. An SPI is what you require.**
> Widen an API and you have to support the new surface forever. Widen an SPI and every
> existing provider breaks. They are opposites, so they must not live in the same interface.

**Real-world proof** every Java developer already has in muscle memory: `java.sql.Connection`
is the **API** (your code calls it). `java.sql.Driver` is the **SPI** (Postgres and Oracle
implement it, and the JDBC machinery calls it, discovered via `ServiceLoader`).

## The ShopStream tie-in

Payments. The brief gives us **two providers today** (Checkout.com and PayTabs) and
**five new markets** in the 3-year target (Egypt, Jordan, Pakistan, India, Nigeria) -
none of which are served by those two gateways. So: *providers will be added*, and
*callers should not care*.

## The problem: `Bad_PaymentService_MixedInterface.java`

One fat interface that mixes both directions. The Order Service (a caller) and the
Checkout.com adapter (a provider) both `implements`/depend on the same type. Result:

- Adding a provider changes a type the Order Service compiles against.
- Callers can see - and call - provider-only lifecycle methods.
- The interface can never be versioned, because its two audiences need to move at
  different speeds.

This is also an **Interface Segregation Principle** violation: no client uses all of it.

## The fix: split it in two

```text
   Order Service
          |  calls
          v
   PaymentApi                            (what ShopStream OFFERS)
          ^
          |  implemented by
          |
   PaymentServiceImpl                    (the pivot)
          |
          |  calls
          v
   PaymentProviderSpi                    (what ShopStream REQUIRES)
          ^
          |  implemented by
          |
          +-- CheckoutComProvider
          +-- PayTabsProvider
          +-- Nigeria provider   (added 2027, nothing above changes)
```

`PaymentServiceImpl` is the pivot: it **implements** the API and **consumes** the SPI.
Adding a Nigerian provider in 2027 is one new class and one registry line - the Order
Service does not recompile, does not redeploy, and does not get reviewed.

## Impact - quality attributes

| Quality attribute | Effect |
|---|---|
| **Modifiability** | New market = new SPI implementation, zero caller change |
| **Testability** | The SPI is trivially fakeable; API tests need no gateway sandbox |
| **Portability** | Provider swap is a deployment concern, not a code change |
| **Security (PCI-DSS)** | The SPI is the *only* place raw card data can exist; the API surface never carries a PAN, so the compliance audit scope shrinks to one package |

## Cost - say this out loud

Three types instead of one. A junior developer will ask *"why do I need two interfaces
to charge a card?"* The honest answer: **you don't, until the second provider arrives.**
With two providers already in production and five markets on the roadmap, ShopStream is
past that line. A single-provider startup is not - see folder `10-kiss-and-yagni`.

## Discussion questions

1. If ShopStream needs to add a **refund** capability, which interface changes - API, SPI,
   or both? What does that tell you about who you are about to break?
2. PayTabs supports partial refunds; Checkout.com does not. Where does that difference
   belong - in the API, in the SPI, or in `PaymentServiceImpl`? (There is no comfortable
   answer. That discomfort is the lesson - see folder `13-conceptual-integrity`.)
3. Internal and external interfaces deserve different treatment. Which of these three is
   external, and what changes about how you version it?
