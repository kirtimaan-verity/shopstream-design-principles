/*
 * ============================================================================
 * ShopStream - shared vocabulary
 * ============================================================================
 *
 * These are the value types every other folder in this repo reuses. They are
 * gathered here so the concept examples stay short: when you see `Money` or
 * `OrderId` in folder 05, this is where it came from.
 *
 * Two design decisions are baked in already, and they are worth naming out loud
 * in class because they show up again in folder 13 (Conceptual Integrity):
 *
 * 1. Money is NEVER a `double`. Ever. AED 280 average basket * 14,000 tx/hour
 *    means rounding drift becomes real money inside a week. We store minor
 *    units (fils) in a long.
 *
 * 2. Identifiers are NEVER bare Strings. `charge(orderId, sellerId)` and
 *    `charge(sellerId, orderId)` both compile if both are String. With
 *    dedicated types, the second one does not.
 *
 * Both are examples of using the TYPE SYSTEM as an architectural constraint -
 * the cheapest enforcement mechanism you have.
 */

// ---------------------------------------------------------------------------
// Identifiers
// ---------------------------------------------------------------------------

record OrderId(String value) {
    /** Used by the examples in folder 02 and beyond. */
    static OrderId next() {
        return new OrderId("ord_" + java.util.UUID.randomUUID());
    }
}
record ProductId(String value) { }
record SellerId(String value) { }
record BuyerId(String value) { }
record StreamId(String value) { }
record PaymentId(String value) { }

// ---------------------------------------------------------------------------
// Money
// ---------------------------------------------------------------------------

/**
 * Amount in MINOR units (fils for AED, piastres for EGP...) plus an ISO-4217 code.
 *
 * ShopStream is entering 5 new markets, so a single-currency `long amountInFils`
 * would have been a decision we'd have to unpick in year two. The currency code
 * is here from day one - that is not speculative generality (folder 10), it is a
 * known, dated, written-down requirement from the brief.
 */
record Money(long minorUnits, String currency) {

    static Money aed(long fils) {
        return new Money(fils, "AED");
    }

    Money plus(Money other) {
        // Deliberate: mixing currencies is a bug, not a feature. Fail loudly.
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add " + currency + " to " + other.currency);
        }
        return new Money(minorUnits + other.minorUnits, currency);
    }

    Money times(int quantity) {
        return new Money(minorUnits * quantity, currency);
    }
}

// ---------------------------------------------------------------------------
// Order
// ---------------------------------------------------------------------------

enum OrderStatus {
    /** Buyer clicked buy. Nothing has been charged yet. */
    PLACED,
    /** Payment authorised by the gateway. Money is reserved, not captured. */
    AUTHORISED,
    /** Money captured, seller notified, stock decremented. */
    CONFIRMED,
    /** Payment failed, buyer cancelled, or the stream ended before checkout. */
    CANCELLED
}

record OrderLine(ProductId product, int quantity, Money unitPrice) {
    Money lineTotal() {
        return unitPrice.times(quantity);
    }
}

record Order(OrderId id, BuyerId buyer, SellerId seller, StreamId stream,
             java.util.List<OrderLine> lines, OrderStatus status) {

    /**
     * Returns a COPY with a new status rather than mutating in place.
     *
     * An immutable order is not decoration. It means no other building block can
     * change an order behind the Order Service's back - which is precisely the
     * failure mode in folder 02, where the Payment Service reached in and drove
     * the order's state machine.
     */
    Order withStatus(OrderStatus newStatus) {
        return new Order(id, buyer, seller, stream, lines, newStatus);
    }

    Money total() {
        return lines.stream().map(OrderLine::lineTotal)
                .reduce(Money.aed(0), Money::plus);
    }
}

// ---------------------------------------------------------------------------
// Payment
// ---------------------------------------------------------------------------

/**
 * Note what is NOT in here: no PAN, no CVV, no expiry.
 *
 * PCI-DSS Level 1 is a non-negotiable constraint from the brief. The cheapest way
 * to stay compliant is to make non-compliance impossible to express in the type
 * system - if there is no field to put a card number in, nobody logs a card number
 * by accident at 2am during an incident. See folder 09 (Information Hiding).
 */
record PaymentResult(PaymentId id, boolean authorised, String maskedCard, String declineReason) { }
