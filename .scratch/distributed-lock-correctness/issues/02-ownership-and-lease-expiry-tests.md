# 02 — Add/adjust ownership and lease-expiry characterization tests

Status: done
Type: AFK

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## What to build

Add characterization tests that pin the documented-but-unverified claims in
`MutexRedisLock`'s javadoc. Tests assert what the code *does*, not what it
should do. No change to the lock implementation.

Pin at least:

- **Lease expiry under hold:** acquire with `tryLock(timeoutSec)`, stay inside
  the critical section longer than `timeoutSec`, show the lock auto-expires
  while the original holder still believes it owns it (contradicting the
  "Auto-Expiration prevents deadlocks" claim — it also enables a second holder).
- **Non-reentrancy:** same thread re-acquiring the same lock name fails (the
  existing `testLockReentrancy` already asserts this — confirm and keep it as
  the canonical reentrancy characterization).
- **Same-thread token sharing:** two different `MutexRedisLock` instances, same
  thread, same lock name → second instance *can* release the first's lock,
  because the owner token is `ID_PREFIX + Thread.currentThread().getId()` with a
  single static `ID_PREFIX` per JVM. Pin this explicitly.
- **Different-thread unlock attempt:** a genuinely different thread attempting
  `unlock()` does **not** release the owner's lock. This is the real
  wrong-owner test the existing suite lacks.

Recycled-thread-id token aliasing is **out of scope as a test** — at most a
one-line research note for issue 03's analysis, not a deterministic test here.

## Acceptance criteria

- [ ] Lease-expiry-under-hold behavior pinned by a test
- [ ] Non-reentrancy confirmed/retained as characterization
- [ ] Same-thread / same-token cross-instance release pinned by a test
- [ ] Different-thread unlock attempt pinned (true wrong-owner behavior)
- [ ] No assertion depends on tuned sleeps beyond what the expiry semantics
      require; unique key prefixes + cleanup used
- [ ] No production lock-path code changed; suite committed green/known-red

## Blocked by

`01-characterization-baseline.md`

## Comments

### Characterization tests added (2026-05-18) — suite GREEN, Issue 03 unblocked

Two tests added to `MutexRedisLockTest` (test-only; production unchanged):

- `testDifferentThreadCannotReleaseForeignOwnersLock` — owner acquires on a
  single-thread executor; the JUnit thread (a real different thread, different
  `Thread.getId()` so a different owner token) attempts unlock and the key is
  preserved; the owning thread then releases successfully. This is the genuine
  foreign-owner protection the original same-thread test could never exercise.
- `testLeaseExpiresWhileHolderStillActive` — holder acquires a 1s lease, stays
  active 1.5s, a second caller then acquires concurrently. Framed as a
  fixed-lease limitation (no watchdog/renewal), explicitly NOT a `SET NX EX`
  defect.

Same-thread/same-token behavior was already pinned in Issue 01
(`testSameThreadDifferentInstanceSharesOwnerToken`); optional item 3 skipped to
avoid duplication. The three behaviors are now distinct and named:

- same-thread same-token release (Issue 01)
- different-thread foreign-owner rejection (this issue)
- fixed-TTL expiry under an active holder (this issue)

Result: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 1` (perf test still
quarantined). BUILD SUCCESS. `MutexRedisLock`/`ILock`/Redisson untouched.
Sleep budget kept minimal (single 1.5s wait for the 1s-lease expiry case).

Issue 03 (ADR + failure-mode analysis) is unblocked: characterized evidence now
exists for every javadoc claim it must map.
