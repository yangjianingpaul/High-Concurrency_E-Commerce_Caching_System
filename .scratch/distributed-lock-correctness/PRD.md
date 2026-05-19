# PRD: Distributed Lock Correctness — Retrospective Characterization & ADR

Status: needs-review

> Vertical slice #2 of the High-Concurrency depth project (portfolio centerpiece,
> ADR-0002). Builds on the completed CacheClient slice and uses the same loop:
> characterization test → seam → ADR → glossary. Read-only verification (done)
> confirmed `MutexRedisLock` is **dormant**: production uses Redisson `RLock`
> (`VoucherOrderServiceImpl:170–181`); the only `MutexRedisLock` references outside
> its own file are an unused import and a commented-out line. This slice is
> therefore **retrospective**: characterize the hand-rolled lock's real behavior,
> document why Redisson is the production choice. No runtime replacement.

## Why this slice

Distributed-lock correctness is a canonical senior backend interview topic. This
repo contains a hand-rolled Redis lock whose javadoc claims guarantees the
implementation does not provide. Pinning the gap between *claimed* and *actual*
behavior with characterization tests, then recording the Redisson decision in an
ADR, is exactly the AI-native signal: **characterization-test-first as a correctness
contract** applied to a real failure-mode analysis. The backend substance (lock
failure modes, lease safety, reentrancy) stands with all AI language stripped.

## Goal

Produce a defensible, public artifact set proving the candidate can (a) characterize
an unsafe distributed lock's real behavior under concurrency, (b) articulate its
failure modes precisely, and (c) justify the production lock choice — all as
correctness-preserving, test-anchored documentation.

## Non-goals

- **No production code change to the lock path.** Redisson stays the production
  lock; `MutexRedisLock` is preserved as a documented teaching/failure-mode
  artifact. No seam swap, no deletion.
- **No `VoucherOrderServiceImpl` decomposition.** Out of scope; later slice.
- **No `RedisIdWorker` work.** Out of scope.
- **No new lock implementation.** The signal is the analysis, not a "better lock."
- **No invented benchmarks or fabricated production scale.** Tutorial-derived; the
  claim is the Engineering Delta, never original-system authorship.
- **No social content written before the technical slice is complete.**

## Scope

A single contained surface: `utils/MutexRedisLock.java`, `utils/ILock.java`, and
`MutexRedisLockTest`. Work is read/test/document only.

## Output artifacts (the deliverables)

1. **Characterization baseline** — `MutexRedisLockTest` run and its real result
   pinned (which tests pass, which fail, which encode an incorrect guarantee). Add
   tests that pin the *actual* behavior of the documented-but-unverified claims:
   lease expiry while a holder is still in its critical section; non-reentrancy;
   ownership-token behavior. Tests assert what the code *does*, not what it should
   do.

   **Ownership-token characterization point (explicit):** the owner token is
   `ID_PREFIX + Thread.currentThread().getId()`, where `ID_PREFIX` is one static
   UUID per JVM/classload. Therefore, within the same JVM and the *same thread*,
   two different `MutexRedisLock` instances on the same lock name share the *same*
   owner token — so a *different lock object on the same thread can release the
   lock*. The existing `testLockOwnershipVerification` creates `lock1`/`lock2` on
   the same JUnit thread and so does **not** actually test a foreign owner; it
   characterizes same-token behavior, not wrong-owner rejection. True wrong-owner
   behavior must be exercised from a **different thread**, not merely a different
   object instance. Recycled-thread-id token aliasing is a known theoretical
   limitation and may be recorded as a research note in the failure-mode analysis;
   it is **not** a required deterministic test for this slice.
2. **Failure-mode analysis** — a section in the project's docs (or repo README
   case-study area) mapping each `MutexRedisLock` javadoc claim
   ("Auto-Expiration prevents deadlocks", "Only the thread that acquired lock can
   release it") to its characterized reality, with the concrete concurrency
   scenario that breaks it.
3. **ADR** — `docs/adr/NNNN-redis-distributed-lock-redisson-over-handrolled.md`:
   why the production path uses Redisson `RLock` over the hand-rolled lock
   (watchdog/lease renewal, reentrancy, ownership safety), considered options, and
   the explicit decision to keep `MutexRedisLock` as a documented artifact.
4. **Glossary update** — add to project `CONTEXT.md`: terms for the failure-mode
   vocabulary (e.g. **Lease Expiry Under Hold**, **Lock Ownership Token**,
   **Reentrancy**), each with an `_Avoid_` list, matching the existing format.
5. **Tutorial-origin disclosure** — one honest paragraph in the repo README:
   states the hmdp/黑马点评 tutorial baseline; frames the portfolio value as the
   Engineering Delta (characterization tests, lock failure-mode analysis, ADRs,
   correctness-preserving refactoring), not original system authorship. This is a
   one-time repo-wide artifact established by this slice.
6. **Resume bullet draft** — 1 bullet, backed by the commits/ADR, no inflated AI
   title (e.g. "Characterized a hand-rolled Redis distributed lock's failure modes
   under concurrency and documented the Redisson production decision via ADR").
7. **LinkedIn case-study post** — one post, written **at slice completion only**,
   linking the ADR/commits; backend-first framing, no AI-agent identity claim.

## Acceptance criteria

- `MutexRedisLockTest` has been run; its true pass/fail state is recorded, and any
  test that asserts a guarantee the implementation does not provide is identified
  and either corrected to characterize real behavior or annotated as such.
- At least the following are pinned by tests: (a) lock auto-expires while a holder
  is still inside a critical section longer than `timeoutSec`; (b) the lock is not
  reentrant for the same thread; (c) ownership-token behavior across same-thread
  instances and different-thread unlock attempts is characterized. Recycled-thread-id
  aliasing is at most a documented research note, not a required test.
- The ADR states the decision, the rejected alternative, and the concrete failure
  mode that justifies Redisson — and is true against the verified code.
- README contains the tutorial-origin disclosure paragraph framed as Engineering
  Delta.
- Project `CONTEXT.md` gains the failure-mode glossary terms in the existing format.
- The whole artifact set is comprehensible and defensible with every mention of AI
  removed (backend-first test).
- No production lock-path code changed; `MutexRedisLock` still present.

## Risks

- **Infra dependency.** Tests require a running Redis (`spring.profiles.active`
  test/local). Mitigation: confirm the test profile/Redis is available before
  starting; this is a feasibility gate.
- **Over-claiming.** Temptation to frame this as "I built a distributed lock."
  Mitigation: hard non-goal; the claim is failure-mode characterization + decision
  documentation only.
- **Scope creep into Redisson internals or VoucherOrder.** Mitigation: explicit
  non-goals; the slice ends at the ADR + glossary, not a lock rewrite.
- **Characterization honesty.** Some "unsafe" claims may turn out fine (lock
  *acquire* is atomic: single `SET NX EX`). Mitigation: characterize honestly —
  pin what is actually safe and what is not; the credibility is in the precision.

## Timebox

Small slice. Hard cap: **2 focused working days.** If characterization can't be
pinned and the ADR drafted within that, the slice is rescoped, not extended.

## Out of scope (explicit)

`VoucherOrderServiceImpl` decomposition; `RedisIdWorker`; any new lock
implementation; Redisson configuration tuning; performance benchmarking beyond the
existing test's own timing; issue breakdown (gated on this PRD's approval).
