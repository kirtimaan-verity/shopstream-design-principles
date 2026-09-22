# 12 - Abstraction

*Depend on abstractions rather than on implementations. The common failure is not a
missing interface - it is an interface that leaks.*

Abstraction shows up in three related senses, and it is worth separating them:

- as a means of deriving useful **generalisations**,
- as a design technique in which components depend on **abstractions rather than
  implementations**,
- and, concretely in Java, as **interfaces**.

## The concept

An abstraction keeps what matters to the caller and discards what does not. `Payments`
says *a payment can be authorised*. It does not say whether that involves HTTP, which
vendor, what their JSON looks like, or how they signal a decline.

The value is measured by what the caller no longer needs to know.

### A good abstraction, tested three ways

1. **Could you write a second implementation without pain?**
   If the interface only fits one vendor, it is that vendor's API with a Java accent.
2. **Can the caller be written without knowing which implementation will run?**
   If the caller catches `CheckoutComHttpException`, the answer is no.
3. **Does it use the caller's vocabulary or the implementation's?**
   `authorise(order, amount)` versus `postPaymentRequest(payload, apiKey)`.

### The leaky abstraction

The most common failure is not a missing interface. It is an interface that **exists and
leaks**: correct package structure, real `implements` keywords, and vendor types in the
signatures.

```java
interface PaymentGateway {
    CheckoutComResponse charge(CheckoutComRequest request) throws CheckoutComHttpException;
}
```

Every caller now imports `CheckoutComRequest`, handles `CheckoutComHttpException`, and
reads `CheckoutComResponse.responseSummary()`. Adding PayTabs means implementing an
interface written in Checkout.com's language, which is either impossible or absurd. The
interface delivered the cost of abstraction and none of the benefit, and it is worse than
no interface at all because it looks solved on the architecture diagram.

Leaks to watch for in review: vendor types in parameters, vendor exceptions in `throws`,
vendor status codes as return values, vendor pagination in a signature, and any method
name that mentions a protocol.

## The ShopStream tie-in

Two payment providers today, five new markets on the roadmap, a CDN vendor, and an
infrastructure change (bare metal to cloud) scheduled for roughly nine months out.

Each of those is a place where the implementation is known to change and the callers
should not care. That is the criterion for where to spend abstraction effort - not "wrap
everything", but "wrap what the brief says is going to move".

## Before and after

| `Bad_LeakyAbstraction.java` | `Good_PaymentGateway.java` |
|---|---|
| interface uses vendor request/response types | domain types only |
| `throws CheckoutComHttpException` | domain result, no vendor exception |
| callers switch on vendor status codes | callers read a domain outcome |
| second implementation is impossible | second implementation exists and is boring |
| no fake for tests | in-memory fake in 15 lines |

## The cost, so the room stays balanced

Abstraction is not free:

- A layer of indirection when reading and debugging.
- A translation step in each adapter, which is real code with real bugs.
- **Lost capability**: if PayTabs supports partial refunds and Checkout.com does not, the
  common interface has to either drop the feature, expose it awkwardly, or lie. Folder 01
  handles this with a `default` method that throws; folder 13 explains why lying about it
  is the worst of the three options.
- A wrong abstraction is expensive to remove, which is why folder 10 says to build the
  second case before you build the abstraction.

## Impact

| Quality attribute | Effect |
|---|---|
| **Modifiability** | vendor swap is contained in one adapter |
| **Testability** | a fake replaces a vendor sandbox |
| **Portability** | new market provider = new class |
| **Analysability** | Note: one more hop when reading a stack trace |

## Discussion questions

1. Find three separate leaks in `Bad_LeakyAbstraction`. For each, name what a caller is
   forced to know.
2. PayTabs supports partial refunds; Checkout.com does not. Where does that difference
   belong? List the options and their costs before choosing.
3. `Good_InMemoryPaymentGateway` is 15 lines and replaces a vendor sandbox in every test.
   What does that do to the build time and the reliability of ShopStream's test suite?
