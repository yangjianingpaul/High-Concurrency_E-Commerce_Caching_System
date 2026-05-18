# High-Concurrency E-Commerce Caching System

The domain language of a Redis-fronted e-commerce backend whose core concern is keeping the database protected under read- and write-heavy load (cache strategies and flash-sale ordering).

## Language

### Cache strategy

**Negative Cache Entry**:
A sentinel value stored under a key to record that the underlying record is known to be absent, so repeat lookups for a missing key are answered from Redis instead of the database.
_Avoid_: null cache, empty cache, blank value

**Cache Penetration**:
Repeated lookups for a key that has no backing record, each falling through to the database. Mitigated by the Negative Cache Entry.
_Avoid_: cache miss storm

**Cache Breakdown**:
The database load spike when a single hot key expires and many concurrent readers rebuild it at once.
_Avoid_: cache stampede, dogpile

**Logical Expiration**:
An expiry timestamp carried inside the cached payload rather than as a Redis TTL; the key never physically expires, so an expired entry is still readable.
_Avoid_: soft TTL, virtual expiry

**Rebuild Lock**:
A single-holder marker that lets exactly one reader reconstruct a logically expired key in the background while other readers are served the existing entry.
_Avoid_: mutex, refresh lock, cache lock

**Stale-on-Expiry Read**:
Returning the existing (logically expired) cached entry to a reader while a Rebuild Lock holder refreshes it asynchronously.
_Avoid_: stale read, dirty read

## Relationships

- A **Negative Cache Entry** is the defence against **Cache Penetration**
- A **Rebuild Lock** is the defence against **Cache Breakdown** for **Logical Expiration** keys
- A **Stale-on-Expiry Read** is what every reader except the **Rebuild Lock** holder receives once an entry is logically expired

## Example dialogue

> **Dev:** "If the shop doesn't exist, what's in Redis?"
> **Domain expert:** "A **Negative Cache Entry** — so the next lookup for that missing id is answered from cache, not the database. That's our **Cache Penetration** defence."
> **Dev:** "And when a hot key's **Logical Expiration** passes?"
> **Domain expert:** "One reader takes the **Rebuild Lock** and refreshes in the background. Everyone else gets a **Stale-on-Expiry Read** — old data beats hammering the database."

## Flagged ambiguities

- "null cache" / "empty cache" were used for the absence sentinel — resolved to **Negative Cache Entry**.
- "mutex" / "cache lock" were used for the rebuild guard — resolved to **Rebuild Lock** (it guards reconstruction, not mutual exclusion of reads).
