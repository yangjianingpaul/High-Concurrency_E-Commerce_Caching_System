# 01 — Characterization baseline for current MutexRedisLockTest

Status: done
Type: AFK

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## What to build

Pin the **actual current behavior** of `MutexRedisLock` via the existing
`MutexRedisLockTest`, against unmodified production code. This is
characterization, not correction: record what the code *does*, including where
it contradicts its own javadoc.

- From issue 00's captured run, classify each existing test: passes / fails /
  encodes an incorrect guarantee (a test whose assertion claims a safety the
  implementation does not provide — e.g. `testLockOwnershipVerification`, which
  runs `lock1`/`lock2` on the same JUnit thread and therefore characterizes
  same-token behavior, not wrong-owner rejection).
- Produce a written baseline table: test name → real outcome → what invariant
  it actually pins (vs what it appears to claim).
- Quarantine brittle timing assertions (e.g. `testLockPerformance`'s `< 10ms`
  average) up front, as the CacheClient slice did, so later issues need zero
  unrelated test edits.
- Do **not** add new behavior tests here (that is issue 02). Do not change the
  lock implementation.

## Acceptance criteria

- [ ] Every existing `MutexRedisLockTest` test classified: pass / fail /
      encodes-incorrect-guarantee, with the real pinned invariant stated
- [ ] `testLockOwnershipVerification` explicitly documented as same-thread /
      same-token (not a foreign-owner test)
- [ ] Brittle timing assertion(s) quarantined, isolated to this issue
- [ ] Baseline table committed; suite green-or-known-red state recorded against
      unmodified `MutexRedisLock`
- [ ] No production lock-path code changed

## Blocked by

`00-redis-test-feasibility-check.md`

## Comments

### Baseline established (2026-05-18) — suite GREEN, Issue 02 unblocked

Harness assumption (from Issue 00): boots via `CacheClientTest$TestApplication`
slim `@SpringBootConfiguration`, Redis-only, `local` profile, no MySQL needed.

Classification of all 8 `MutexRedisLockTest` tests:

| Test | Baseline | Pinned invariant |
| --- | --- | --- |
| testBasicLockAcquisitionAndRelease | PASS | acquire writes the key; same-thread unlock deletes it |
| testLockTimeout | PASS | `tryLock(1)` + 2s wait: key auto-expires by TTL (lease expiry, no holder present) |
| testConcurrentLockAccess | PASS | 10 threads: increments equal successful acquisitions (mutual exclusion holds) |
| testLockOwnershipVerification | ENCODED INCORRECT GUARANTEE → REWRITTEN | now `testSameThreadDifferentInstanceSharesOwnerToken`: same JVM + same thread + same lock name share one owner token, so a different instance on the same thread releases the lock; NOT wrong-owner protection |
| testHighContentionScenario | PASS | 20 threads x 5 iters: work count equals successful acquisitions (no lost updates) |
| testLockReentrancy | PASS | lock is NOT reentrant: same thread re-acquire fails |
| testMultipleLockInstances | PASS | distinct lock names are independent |
| testLockPerformance | QUARANTINED | `@Disabled` brittle `< 10ms` avg timing assertion (matches CacheClient slice pattern) |

Result after baseline correction: `Tests run: 8, Failures: 0, Errors: 0,
Skipped: 1` (testLockPerformance disabled). BUILD SUCCESS. Production lock path
(`MutexRedisLock`, `ILock`, Redisson) unchanged.

Issue 02 is unblocked: it adds the true different-thread wrong-owner test plus
the lease-expiry-under-hold and same-token cross-instance characterizations.
