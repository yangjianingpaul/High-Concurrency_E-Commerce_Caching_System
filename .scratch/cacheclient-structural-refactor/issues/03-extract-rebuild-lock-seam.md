# 03 — Extract the rebuild-lock seam in queryWithLogicalExpire

Status: done
Type: AFK

## Parent

`.scratch/cacheclient-structural-refactor/PRD.md`

## What to build

Behavior-preserving structural refactor of `queryWithLogicalExpire` in
`CacheClient`. The `setIfAbsent` / `delete` pair guarding background
reconstruction is currently inline and unnamed. Extract it into named members
(acquire / release) so the method reads as orchestration: read cache → check
logical expiry → on expiry, acquire the lock and refresh in the background,
otherwise serve the existing entry.

Member names must use the `/CONTEXT.md` glossary vocabulary for the
**Rebuild Lock**. Do not introduce any glossary _Avoid_ terms (`mutex`,
`refresh lock`, `cache lock`).

No observable behavior change: Redis keys, lock key/TTL, the stale-on-expiry
read, return values, and exception behavior stay identical. The preserved
known bug (swallowed background-rebuild NPE when `dbFallback` returns null for
an expired key) remains unfixed.

This slice is independent of issue 02 (different method) and may proceed in
parallel once the baseline exists.

## Acceptance criteria

- [x] `setIfAbsent`/`delete` rebuild guard extracted into named acquire/release
      member(s)
- [x] `queryWithLogicalExpire` reduced to orchestration-level
- [x] Member names match the `/CONTEXT.md` Rebuild Lock vocabulary; no _Avoid_
      terms introduced (naming audit)
- [x] Stale-on-expiry read behavior preserved (stale value returned; at most
      one rebuild per window)
- [x] Zero edits to test assertions from issue 01
- [x] Full `CacheClientTest` suite green after the change
- [x] No change to Redis keys, lock key/TTL, return values, or exception
      behavior (including the preserved background-rebuild NPE-on-null)

## Blocked by

- `.scratch/cacheclient-structural-refactor/issues/01-characterization-baseline.md`
