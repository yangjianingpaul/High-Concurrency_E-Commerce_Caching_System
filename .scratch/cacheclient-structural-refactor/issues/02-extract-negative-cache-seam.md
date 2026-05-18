# 02 — Extract the negative-cache seam in queryWithPassThrough

Status: done
Type: AFK

## Parent

`.scratch/cacheclient-structural-refactor/PRD.md`

## What to build

Behavior-preserving structural refactor of `queryWithPassThrough` in
`CacheClient`. The empty-string absence sentinel is currently an unnamed,
inline concept. Extract it into named members so the method reads as
orchestration: read cache → branch on a named predicate → delegate.

Member names must use the `/CONTEXT.md` glossary vocabulary for the
**Negative Cache Entry** (e.g. a predicate that recognises a negative-cache
hit, and an operation that writes one). Do not introduce any glossary
_Avoid_ terms (`null cache`, `empty cache`, `blank value`).

No observable behavior change: Redis keys, TTLs, return values, and exception
behavior stay identical. The preserved known bugs listed in the PRD remain
unfixed.

## Acceptance criteria

- [x] Empty-string absence sentinel handling extracted into named member(s)
- [x] `queryWithPassThrough` reduced to orchestration-level
- [x] Member names match the `/CONTEXT.md` Negative Cache Entry vocabulary;
      no _Avoid_ terms introduced (naming audit)
- [x] Zero edits to test assertions from issue 01
- [x] Full `CacheClientTest` suite green after the change
- [x] No change to Redis keys, TTLs, return values, or exception behavior

## Blocked by

- `.scratch/cacheclient-structural-refactor/issues/01-characterization-baseline.md`
