# PRD: CacheClient structural refactor (first vertical slice)

Status: ready-for-agent

## Summary

Behavior-preserving structural refactor of `src/main/java/com/paulyang/ecommerce/utils/CacheClient.java`.
Name and extract two concepts that are currently buried in long methods, so
`queryWithPassThrough` and `queryWithLogicalExpire` read as short orchestration
methods. No observable behavior changes. This is a first vertical-slice exercise
chosen for low risk: self-contained class, no MySQL, existing test file.

Domain language for this work is defined in `/CONTEXT.md` (cache-strategy
glossary). Method names introduced here MUST use those terms.

## Goal

Extract two named seams:

1. **Negative-cache seam** in `queryWithPassThrough` — the empty-string absence
   sentinel becomes named operations, e.g. `isNegativeCacheHit(json)` /
   `writeNegativeCache(key)`. Glossary term: **Negative Cache Entry**.
2. **Rebuild-lock seam** in `queryWithLogicalExpire` — the
   `setIfAbsent`/`delete` pair becomes named operations, e.g.
   `acquireRebuildLock(key)` / `releaseRebuildLock(key)`. Glossary term:
   **Rebuild Lock**.

After extraction, both public methods should be orchestration-level: read
cache → branch on named predicates → delegate.

## Non-goals (explicitly deferred)

- **LOCK_SHOP_KEY decoupling.** The generic `CacheClient` hard-codes the Shop
  lock key (cross-type collision risk). Out of scope — changes Redis keyspace.
- **TTL parameterization.** `queryWithPassThrough` bakes in `CACHE_SHOP_TTL` /
  `CACHE_NULL_TTL`. Out of scope — changes cached-entry lifetime.
- **ShopServiceImpl dedup.** `ShopServiceImpl` has its own
  `queryWithLogicalExpire` / `queryWithMutex` duplicating `CacheClient`. Out of
  scope — cross-class, broader.
- **Known-bug preservation.** The background rebuild path NPEs (swallowed) if
  `dbFallback` returns null for a logically expired key. This behavior is
  **preserved, not fixed**, in this slice.

## Test strategy

- Harden the existing `src/test/java/com/paulyang/ecommerce/utils/CacheClientTest.java`.
- Keep `@SpringBootTest` + `local` profile against a real Redis. This is the
  existing repo convention — **not** an infrastructure change. Local Redis must
  be running to execute the suite (a prerequisite, not a task here).
- Add characterization tests pinning the two branches being refactored:
  - **Negative cache:** a missing key, looked up twice, returns null both times
    and invokes `dbFallback` exactly once (empty-string sentinel served on the
    second lookup).
  - **Stale-on-expiry / rebuild lock:** a logically expired key returns the
    existing (stale) value immediately, and `dbFallback` is invoked **at most
    once** per rebuild window.
- **Quarantine** the brittle `testCacheHitPerformance` `< 5ms` timing assertion
  **before** establishing the protected baseline (it can go red for reasons
  unrelated to behavior).

## Risk posture

- Characterization assertions pin **observable invariants only**: stale value
  returned immediately; `dbFallback` invoked at most once per rebuild window.
- **Never** assert exact rebuild thread counts or machine-tuned sleeps — the
  static shared `CACHE_REBUILD_EXECUTOR` makes exact-count assertions fragile.
- Use unique key prefixes per test + cleanup to avoid cross-test contamination
  via the shared executor.

## Acceptance criteria

1. Hardened characterization tests are committed **green against current
   (pre-refactor) code**, with the perf assertion already quarantined.
2. The refactor is applied with **zero edits to test assertions** — the test
   file diff after the refactor is empty (perf-assertion quarantine having been
   done up front).
3. All `CacheClientTest` tests pass after the refactor.
4. `queryWithPassThrough` and `queryWithLogicalExpire` are reduced to
   orchestration; the negative-cache and rebuild-lock concepts are extracted as
   named members.
5. **Naming audit:** extracted member names match the `/CONTEXT.md` glossary
   (Negative Cache Entry, Rebuild Lock); no glossary _Avoid_ terms introduced.
6. No change to Redis keys, TTLs, return values, or exception behavior
   (including the preserved background-rebuild NPE-on-null).

## Out of this slice / follow-ups

The four non-goals above are candidate follow-up slices, in roughly ascending
risk: ShopServiceImpl dedup, TTL parameterization, LOCK_SHOP_KEY decoupling,
then revisiting the preserved background-rebuild null bug.
