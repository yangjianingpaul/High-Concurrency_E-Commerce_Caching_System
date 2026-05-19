# MutexRedisLock — claimed vs characterized behaviour

A precise map of each behaviour `MutexRedisLock`'s Javadoc claims to what its
characterization tests (`MutexRedisLockTest`, Issues 01–02) actually pin. This
is the evidence base for [ADR-0001](adr/0001-redis-distributed-lock-redisson-over-handrolled.md).
No production code was changed to produce it.

## Mechanism (verified)

- **Acquire:** `stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl,
  SECONDS)` — a single atomic `SET NX EX`. Atomicity confirmed; not a
  check-then-set race.
- **Release:** `unlock.lua` does an atomic `get` → compare to the caller's
  token → `del` only on match. Atomic get-compare-del.
- **Owner token:** `ID_PREFIX + Thread.currentThread().getId()`, where
  `ID_PREFIX` is one static UUID per JVM/classload. The ownership unit is the
  *thread*, not the acquisition and not the lock object.

## Claim vs characterized reality

| Javadoc claim | Characterized reality | Test evidence |
| --- | --- | --- |
| "Atomic Operation: SETNX + EXPIRE in single operation" | True. Single atomic `SET NX EX`; release is atomic get-compare-del. | basic acquire/release; concurrent + high-contention tests show no lost updates |
| "Auto-Expiration prevents deadlocks if thread crashes" | True for the crash case — but the lease is *fixed* with no watchdog/renewal, so it also expires while a holder is still active, allowing a second concurrent acquirer. Deadlock-prevention holds; mutual exclusion under a long critical section does not. | `testLeaseExpiresWhileHolderStillActive` |
| "Ownership Verification: only the thread that acquired the lock can release it" | Correct for a *genuinely different thread* — `unlock.lua` rejects a foreign token. But the ownership unit is the per-thread token, so any code on the *same thread*, even via a different `MutexRedisLock` instance, releases it. This is a consequence of the owner-token design, not object identity. | `testDifferentThreadCannotReleaseForeignOwnersLock` (foreign thread rejected); `testSameThreadDifferentInstanceSharesOwnerToken` (same-thread cross-instance release) |
| Thread-ID format "ensures global uniqueness across multiple JVM instances" | Cross-JVM uniqueness holds (per-JVM UUID). Intra-JVM the token is per thread id with no per-acquisition nonce — hence non-reentrancy and same-thread cross-instance release. | derived from the two ownership tests + reentrancy test |
| (not claimed) reentrancy | The lock is **non-reentrant**: the same thread re-acquiring the same key fails. | `testLockReentrancy` |

## Theoretical note (not tested)

Thread ids can be recycled after a thread dies; a new thread with a reused id
would alias a stale token. This is a recognised limitation, deliberately *not*
turned into a deterministic test (it is timing/JVM-dependent). Recorded for
completeness only.

## Net

`MutexRedisLock` is internally honest where it is simple (atomic acquire, atomic
owner-checked release, foreign-thread rejection) and limited where distributed
locks are hard: fixed lease without renewal, and non-reentrancy. Those two
limitations — not any acquisition or foreign-owner defect — are why production
uses Redisson `RLock`. See ADR-0001.
