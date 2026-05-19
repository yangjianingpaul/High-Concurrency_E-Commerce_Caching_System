# 03 — ADR + failure-mode analysis

Status: done
Type: human-in-loop

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## What to build

Turn the pinned characterization (issues 01–02) into the two documentation
artifacts. Judgment/narrative work — not AFK.

- **Failure-mode analysis** (in the project docs / repo case-study area): a
  table mapping each `MutexRedisLock` javadoc claim ("Auto-Expiration prevents
  deadlocks", "Only the thread that acquired lock can release it") to its
  characterized reality, each row naming the concrete concurrency scenario that
  breaks it. Be honest about what is actually safe: lock *acquire* is atomic
  (single `SET NX EX`). Recycled-thread-id aliasing may appear here as a
  one-line research note, clearly labelled theoretical.
- **ADR** `docs/adr/NNNN-redis-distributed-lock-redisson-over-handrolled.md`
  (scan `docs/adr/` for the next number): why production uses Redisson `RLock`
  over the hand-rolled lock — watchdog/lease renewal, reentrancy, ownership
  safety — the rejected alternative, and the explicit decision to **keep
  `MutexRedisLock` as a documented teaching artifact** (not delete, not swap).

The ADR must be true against the verified code (Redisson is the live path;
`MutexRedisLock` is dormant). No production code change.

## Acceptance criteria

- [ ] Failure-mode analysis table: every javadoc claim → characterized reality
      → breaking scenario, citing the issue 01/02 tests as evidence
- [ ] Honest "what is actually safe" row included (atomic acquire)
- [ ] ADR created with next sequential number; states decision, rejected
      alternative, and the keep-as-artifact decision
- [ ] ADR is consistent with verified code; backend-first (defensible with all
      AI language removed)
- [ ] No production lock-path code changed

## Blocked by

`02-ownership-and-lease-expiry-tests.md`

## Comments

### ADR + failure-mode analysis written (2026-05-18) — Issue 04 unblocked

Artifacts created (docs only; no production/test code touched):

- `docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md` — first ADR
  in this repo (no prior `docs/adr/`). Decision: production uses Redisson
  `RLock`; `MutexRedisLock` kept as a documented teaching artifact, not deleted,
  not re-wired. Rationale narrowed to the two properties tests prove the
  hand-rolled lock lacks: watchdog/lease-renewal and reentrancy — explicitly
  NOT acquisition atomicity or foreign-owner rejection (those are already
  correct). Backend-first: contains no AI/agentic language.
- `docs/distributed-lock-failure-modes.md` — claim-vs-characterized table, every
  row cites the Issue 01–02 test that pins it. Recycled-thread-id kept as a
  labelled theoretical note, not a test.

Precision honoured: `SET NX EX` atomic; `unlock.lua` correctly rejects a real
foreign thread; same-thread cross-instance release framed as owner-token-design
consequence (not object identity); fixed-lease-without-watchdog and
non-reentrancy named as the deciding limitations. No overstatement: does not
claim a lock was authored or that Redisson was proven correct. Tutorial-origin /
portfolio framing deliberately deferred to Issue 04.

Issue 04 (README tutorial-origin disclosure + CONTEXT glossary) is unblocked.
