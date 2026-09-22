# 00 - ShopStream: the system under the microscope

Read this once before anything else. Every other folder assumes this vocabulary.

## The building blocks

```text
                        +------------------+
                        |  UI / Web Client |  buyers + sellers, video playback,
                        +--------+---------+  browsing, cart, checkout
                                 | HTTPS (sync)
                        +--------v---------+
                        |   API Gateway    |  single entry point, auth tokens, routing
                        +--+------+-----+--+
              internal HTTP|      |     |
              (sync)       |      |     |
          +----------------v+ +---v--------+ +v--------------+
          | Product Service | |   Order    | |    Payment    |
          | catalogue,      | |  Service   | |    Service    |
          | inventory,      | | orders,    | | Checkout.com  |
          | prices, search  | | status     | | + PayTabs     |
          +--------+--------+ +-----+------+ +-------+-------+
                   |                |                |
                   +----------------+----------------+
                                    | read/write
                        +-----------v------------+
                        | Shared DB (PostgreSQL) |  ONE schema for all three
                        +------------------------+
```

Plus, outside the audit diagram but in the brief: **Streaming / CDN**, **Notification
Service** (email + push), **Seller Dashboard & Analytics**.

## The four planted defects

1. **UI -> Product DB directly**, bypassing the gateway and every service contract.
2. **Order <-> Payment is bidirectional** - Order calls Payment to initiate, Payment calls
   Order to confirm. Neither can be deployed alone.
3. **API Gateway is coupled to all three services** - a new service means a gateway change.
4. **One shared schema** - a column rename by Product silently breaks Order at runtime.

## Scale and constraints you must keep in your head

| | Today | 3-year target |
|---|---|---|
| Registered sellers | 200,000 | 2,000,000 |
| Monthly active buyers | 5,000,000 | 50,000,000 |
| Concurrent live streams | 1,200 | 10,000 |
| Viewers per stream (peak) | 80,000 | - |
| Transactions per peak hour | ~14,000 | - |
| Markets | GCC | + Egypt, Jordan, Pakistan, India, Nigeria |

**Non-negotiable constraints**

- **Bare metal only** for production for ~9 months (investor terms until Series B closes).
- **PCI-DSS Level 1** for every payment flow.
- **Data residency**: buyer personal data stays in-country for UAE and Saudi Arabia.
- **6 months** to a live MVP, in time for the National Day event.

> These constraints are what make the "right" answer *architectural* rather than
> a matter of taste. When a participant says *"just put it on Kubernetes"* or
> *"just use a service mesh"* - point at the bare-metal constraint. When they say
> *"just log the card number for debugging"* - point at PCI-DSS.

## Files here

- `ShopStreamVocabulary.java` - the shared value types every other folder reuses.

## Discussion questions

1. Which of the four defects would you fix *first*, given a 6-month MVP deadline?
2. Which defect is invisible at compile time? Why does that make it more dangerous?
3. The brief lists "Auction mode" and "AR product try-on" as future features. Should
   today's design accommodate them? (Hold that thought until folder `10-kiss-and-yagni`.)
