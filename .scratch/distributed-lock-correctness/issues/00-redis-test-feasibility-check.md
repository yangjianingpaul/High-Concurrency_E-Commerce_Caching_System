# 00 — Redis / test feasibility check

Status: done
Type: AFK

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## What to build

A read-only feasibility gate, run before any characterization work, confirming
the existing `MutexRedisLockTest` can run at all. No code or test changes.

- Confirm a Redis reachable under the existing test convention
  (`@SpringBootTest`, `spring.profiles.active` test/local — the same convention
  `CacheClientTest`/`MutexRedisLockTest` already use).
- Run `MutexRedisLockTest` as-is and capture the raw result verbatim (per-test
  pass/fail/error), without interpreting or fixing anything yet.
- Record the captured result into the slice's working notes for issue 01.

## Acceptance criteria

- [ ] Redis availability under the test profile is confirmed (or the blocker is
      stated explicitly with what is missing)
- [ ] `MutexRedisLockTest` has been executed unmodified and its raw per-test
      outcome captured verbatim
- [ ] No production code, no test code, no config modified
- [ ] Result handed to issue 01 as the starting point

## Blocked by

None — first issue. This is the gate; issues 01–05 do not start until this
returns a runnable suite (or an explicit infra blocker).

## Comments

### Feasibility result (2026-05-18) — runnable, GREEN-LIGHT for issue 01

**Environment discovered:**
- Redis: listening `127.0.0.1:6379`, auth password `123321` → `PONG`. Available.
- MySQL: nothing on `:3306`, no client. **Not** available — but irrelevant:
  `MutexRedisLockTest` (`spring.profiles.active=local`) booted via the slim
  `CacheClientTest$TestApplication` `@SpringBootConfiguration` (from the CacheClient
  slice), which excludes DataSource/MyBatis. Context started Redis-only in 1.4s.
- Build: Maven 3.9.16, Temurin Java 17, Spring Boot 2.5.7.

**Raw result (unmodified suite):** `Tests run: 8, Failures: 1, Errors: 0, Skipped: 0`
- FAIL `testLockOwnershipVerification` @ line 135: "Lock should still exist after
  wrong thread tries to unlock ==> expected: not <null>". `lock1`/`lock2` run on
  the *same* JUnit thread → same owner token → `lock2.unlock()` *does* release
  `lock1`'s lock → assertion fails. This is exactly the same-thread/same-token
  characterization point the PRD/issue 01–02 target: a test encoding an incorrect
  ownership guarantee.
- PASS (7): basic acquire/release, timeout-expiry, concurrent access, high
  contention, reentrancy (asserts NOT reentrant), multiple instances, performance.

No production code, test code, or config modified. Hand-off to issue 01.
