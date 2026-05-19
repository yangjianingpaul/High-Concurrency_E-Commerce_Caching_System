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

### Distributed locking

**Lock Ownership Token**:
The value written under a lock key identifying who holds it; here `ID_PREFIX + threadId`, where `ID_PREFIX` is one static UUID per JVM, so the ownership unit is the thread, not the individual acquisition or the lock object.
_Avoid_: lock id, lock value, owner uuid

**Fixed Lease**:
A lock TTL set once at acquire time and never extended, so the lock can expire while its holder is still inside the critical section.
_Avoid_: timeout, expiry, unqualified "ttl"

**Watchdog / Lease Renewal**:
A background mechanism (Redisson's, not the hand-rolled lock's) that keeps renewing a held lock's lease so it does not expire under an active holder.
_Avoid_: heartbeat, keep-alive, auto-extend

**Foreign-Owner Unlock**:
An unlock attempt whose Lock Ownership Token does not match the stored one; rejected by the release Lua script only when it comes from a genuinely different thread (a different instance on the same thread shares the token).
_Avoid_: wrong-thread unlock, accidental unlock

**Reentrancy**:
Whether the same holder can re-acquire a lock it already holds; the hand-rolled lock is **not** reentrant, Redisson `RLock` is.
_Avoid_: recursive lock, nested lock

**Characterization Test**:
A test that pins the *current actual* behaviour of code (including where it contradicts its own docs) before any change, so a refactor is provably behaviour-preserving.
_Avoid_: regression test, unit test (when behaviour-pinning is what is meant)

## Relationships

- A **Negative Cache Entry** is the defence against **Cache Penetration**
- A **Rebuild Lock** is the defence against **Cache Breakdown** for **Logical Expiration** keys
- A **Stale-on-Expiry Read** is what every reader except the **Rebuild Lock** holder receives once an entry is logically expired
- A **Fixed Lease** without **Watchdog / Lease Renewal** is the core limitation that makes the hand-rolled lock unsafe for long critical sections; Redisson `RLock` adds renewal and **Reentrancy**
- A **Foreign-Owner Unlock** is rejected via the **Lock Ownership Token** check, but only across genuinely different threads
- Each lock-behaviour claim above is pinned by a **Characterization Test** before any refactor

## Example dialogue

> **Dev:** "If the shop doesn't exist, what's in Redis?"
> **Domain expert:** "A **Negative Cache Entry** — so the next lookup for that missing id is answered from cache, not the database. That's our **Cache Penetration** defence."
> **Dev:** "And when a hot key's **Logical Expiration** passes?"
> **Domain expert:** "One reader takes the **Rebuild Lock** and refreshes in the background. Everyone else gets a **Stale-on-Expiry Read** — old data beats hammering the database."
>
> **Dev:** "Why not keep the hand-rolled lock in production?"
> **Domain expert:** "Its **Fixed Lease** has no **Watchdog / Lease Renewal**, and it has no **Reentrancy** — both pinned by a **Characterization Test**. Redisson `RLock` is the production choice; see ADR-0001."

## Flagged ambiguities

- "null cache" / "empty cache" were used for the absence sentinel — resolved to **Negative Cache Entry**.
- "mutex" / "cache lock" were used for the rebuild guard — resolved to **Rebuild Lock** (it guards reconstruction, not mutual exclusion of reads).
- "lock timeout" / "lock expiry" conflated two things — resolved: a **Fixed Lease** is the unrenewed TTL; **Watchdog / Lease Renewal** is the missing mechanism that would extend it.
- "ownership check" was treated as thread-safe — resolved: it is a **Lock Ownership Token** keyed per thread, so a **Foreign-Owner Unlock** is only rejected across different threads, not different instances on one thread.
