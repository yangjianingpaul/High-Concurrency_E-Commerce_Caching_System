# 01 — Establish protected characterization baseline for CacheClient

Status: ready-for-agent
Type: AFK

## Parent

`.scratch/cacheclient-structural-refactor/PRD.md`

## What to build

Establish a green characterization safety net around `CacheClient` against the
**current, unmodified** production code, so the subsequent structural refactor
can be verified to change nothing observable.

Harden the existing `CacheClientTest` (real Redis, `@SpringBootTest`, `local`
profile — the existing repo convention; local Redis must be running). Two
behaviors must be pinned as characterization tests:

- **Negative cache:** a key with no backing record, looked up twice, returns
  null both times and invokes `dbFallback` exactly once (the empty-string
  absence sentinel is served on the second lookup without a DB call).
- **Stale-on-expiry / rebuild lock:** a logically expired key returns the
  existing (stale) value immediately, and `dbFallback` is invoked **at most
  once** per rebuild window.

Assertions pin observable invariants only — never exact rebuild thread counts
or machine-tuned sleeps. Use unique key prefixes per test plus cleanup to
avoid cross-test contamination via the shared static rebuild executor.

Quarantine the brittle `testCacheHitPerformance` `< 5ms` timing assertion as
part of this baseline (it can go red for reasons unrelated to behavior). This
quarantine happens here, up front, so later slices need zero test edits.

## Acceptance criteria

- [ ] `testCacheHitPerformance` `< 5ms` timing assertion is quarantined
- [ ] Characterization test pins the negative-cache behavior (null twice,
      `dbFallback` invoked exactly once)
- [ ] Characterization test pins stale-on-expiry behavior (stale value returned
      immediately; `dbFallback` invoked at most once per rebuild window)
- [ ] No assertion depends on exact rebuild thread counts or tuned sleeps
- [ ] Tests use unique key prefixes + cleanup
- [ ] Entire `CacheClientTest` suite is green against current (pre-refactor)
      `CacheClient` and committed in that state

## Blocked by

None - can start immediately. (Prerequisite: local Redis running — an
environment requirement, not a task.)
