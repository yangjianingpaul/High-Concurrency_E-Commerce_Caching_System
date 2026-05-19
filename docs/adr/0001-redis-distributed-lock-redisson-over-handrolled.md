---
status: accepted
---

# Redisson RLock over the hand-rolled MutexRedisLock for production locking

## Context

The codebase contains a hand-rolled Redis lock, `MutexRedisLock` (implements
`ILock`): acquire is a single atomic `SET key token NX EX ttl`; release is an
atomic get-compare-del Lua script (`unlock.lua`) keyed on an owner token
`ID_PREFIX + Thread.currentThread().getId()`, where `ID_PREFIX` is one static
UUID per JVM/classload. Characterization tests (see
`docs/distributed-lock-failure-modes.md`, evidenced by `MutexRedisLockTest`)
established its real behavior: acquisition is atomic; the Lua release correctly
rejects a *genuinely different thread*; but the lease is fixed with no
watchdog/renewal (it can expire while its holder is still in the critical
section), the lock is non-reentrant, and the ownership unit is the per-thread
token — so any code on the *same thread* (even via a different lock instance)
can release it.

`MutexRedisLock` is **dormant**: the production seckill path uses Redisson
`RLock` (`VoucherOrderServiceImpl` lines ~170–181); the only `MutexRedisLock`
references outside its own file are an unused import and a commented-out line.

## Decision

Production distributed locking uses **Redisson `RLock`**. `MutexRedisLock` is
**kept, not deleted and not re-wired**, as a documented teaching / failure-mode
artifact with its characterization tests.

Redisson is chosen for the properties the hand-rolled lock provably lacks under
test: a watchdog that renews the lease while the holder is still active
(removing the fixed-lease expiry window), and reentrancy. Its acquisition
atomicity and foreign-owner rejection are not improvements over the hand-rolled
lock — those are already correct there; the decision rests specifically on
lease renewal and reentrancy.

## Considered options

- **Keep `MutexRedisLock` in production** — rejected: the fixed-lease window
  and non-reentrancy are real correctness hazards for a flash-sale critical
  section, demonstrated by `testLeaseExpiresWhileHolderStillActive` and
  `testLockReentrancy`.
- **Delete `MutexRedisLock`** — rejected: its characterized failure modes are
  a deliberately preserved teaching artifact; deleting them destroys that value
  and the test evidence.

## Consequences

- Production behaviour is unchanged by this slice (Redisson was already the live
  path); this ADR records *why*, backed by tests, not a code change.
- `MutexRedisLock` stays compiled and tested so its failure modes remain
  reproducible. It must not be reintroduced into a production path without
  revisiting this ADR.
- This ADR does not claim Redisson is formally correct, nor that a distributed
  lock was authored here — only that, given the characterized limitations, the
  battle-tested library is the right production choice.
