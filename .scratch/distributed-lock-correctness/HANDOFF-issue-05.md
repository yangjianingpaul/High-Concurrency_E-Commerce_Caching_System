# Handoff — Distributed Lock Correctness slice → Issue 05

## 1. Current slice and status

Slice: **distributed-lock-correctness** (vertical slice #2 of the High-Concurrency
depth project; portfolio centerpiece per job-search-system ADR-0002).
Repo: `/Users/paulyang/AI/vibecoding/High-Concurrency_E-Commerce_Caching_System`.
Issues 00–04.5 **done**. Issue 05 **not started**, unblocked, SAFE to proceed.

## 2. Completed issues 00–04.5

- **00 Feasibility:** Redis up (`localhost:6379`, pw `123321`); suite runs via
  the slim `CacheClientTest$TestApplication` (Redis-only, no MySQL). Baseline:
  8 tests, 1 failure.
- **01 Baseline:** classified all 8 tests; rewrote the mis-asserting
  `testLockOwnershipVerification` → `testSameThreadDifferentInstanceSharesOwnerToken`;
  `@Disabled` quarantined `testLockPerformance`. Green.
- **02 New characterizations:** added `testDifferentThreadCannotReleaseForeignOwnersLock`
  and `testLeaseExpiresWhileHolderStillActive`. Green.
- **03 ADR + analysis:** `docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md`
  + `docs/distributed-lock-failure-modes.md`.
- **04 Disclosure + glossary:** README delaundered + Provenance section;
  CONTEXT.md distributed-lock terms added.
- **04.5 Honesty sweep:** repo-wide laundering neutralized (details §6).

## 3. Files changed by this slice

Production code: **none**. Tests: `src/test/java/com/paulyang/ecommerce/utils/MutexRedisLockTest.java` only.
Docs/new: `docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md`,
`docs/distributed-lock-failure-modes.md`, `README.md`, `CONTEXT.md`,
`ARCHITECTURE.md`, `INTERVIEW_PREP.md`, `PERFORMANCE_CHEAT_SHEET.md`,
`PERFORMANCE_TEST_REPORT.md`, `PERFORMANCE_TEST_MACBOOK.md`,
`ENVIRONMENT_SETUP_GUIDE.md`. Tracker: `.scratch/distributed-lock-correctness/`
PRD + issues 00–05 + this handoff.

## 4. Tests / commands run and results

`mvn -Dtest=MutexRedisLockTest -DfailIfNoTests=false test`
- After 01: `Tests run: 8, Failures: 0, Skipped: 1` (perf quarantined).
- After 02 (current): **`Tests run: 10, Failures: 0, Errors: 0, Skipped: 1` —
  BUILD SUCCESS.** 9 pass, `testLockPerformance` skipped.

## 5. Key technical facts established

- `MutexRedisLock` is **dormant**; production lock = Redisson `RLock`
  (`VoucherOrderServiceImpl` ~L170–181). Only stray refs: an unused import + a
  commented line.
- Acquire = atomic `SET NX EX` (correct). Release = atomic owner-checked
  get-compare-del Lua (correct for a genuine foreign thread).
- Owner token = `ID_PREFIX + Thread.currentThread().getId()`, `ID_PREFIX` one
  static UUID/JVM → ownership unit is the **thread**: same-thread different
  instance shares the token and **can** release; a different thread **cannot**.
- Limitations that drive the decision: **fixed lease, no watchdog/renewal**
  (expires under an active holder) and **non-reentrant**. These — not any
  acquire/foreign-owner defect — are why production uses Redisson.
- Recycled-thread-id aliasing: theoretical note only, deliberately not a test.

## 6. Documentation honesty decisions

- README rewritten honest: hmdp/黑马点评 origin disclosed; value framed as the
  **Engineering Delta**; HUAWEI-as-origin removed; benchmark section reframed as
  local single-machine, enterprise/national/cost tables deleted; zero-overselling
  correctness kept.
- HUAWEI status (user-confirmed): "partially true" — real prior backend work
  exists but this repo is **not** employer-derived; HUAWEI must never be this
  repo's provenance. Belongs in résumé only, with a clear boundary.
- Sweep: INTERVIEW_PREP pitch/STAR/closing neutralized + binding read-first
  banner; CHEAT_SHEET script fixed + banner; ARCHITECTURE delabelled + banner;
  ENVIRONMENT_SETUP_GUIDE delabelled; PERFORMANCE_TEST_REPORT/MACBOOK marked
  SUPERSEDED (bodies withdrawn, not rewritten). Every surviving "HUAWEI" is
  inside a retraction banner.
- Open optional cleanup (non-blocking): withdrawn report bodies still contain
  the old fabricated tables under SUPERSEDED banners; could be deleted outright
  before public sharing.

## 7. Read first in the next context

1. `.scratch/distributed-lock-correctness/PRD.md` (+ amendments)
2. `.scratch/distributed-lock-correctness/issues/05-resume-bullet-and-linkedin-draft.md`
3. `docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md`
4. `docs/distributed-lock-failure-modes.md`
5. `README.md` "Provenance & Portfolio Scope" + "Local Measurements"
6. job-search-system `docs/adr/0002-*` + `CONTEXT.md` (positioning guardrails)
7. This handoff

## 8. Issue 05 scope

Outward assets, drafted only now that the technical slice + honesty sweep are
done:
- **1 résumé bullet** — backed by real commits/ADR; no inflated AI title;
  pattern: "Characterized a hand-rolled Redis distributed lock's failure modes
  under concurrency and documented the Redisson production decision via ADR."
- **1 LinkedIn case-study post draft** — backend-first; the lock failure modes,
  characterization-test-as-correctness-contract method, why Redisson; links
  ADR-0001/commits. Single post at completion; no interim build-log.
Both are **drafts**; publishing is the user's decision, out of scope.

## 9. Non-goals and wording guardrails

- No production code changes; no test changes (unless a doc link demands one).
- Do not start `VoucherOrderServiceImpl` or `RedisIdWorker`.
- Identity: **backend engineer transitioning into AI-native workflows** — never
  "AI Agent Engineer"; never "built a distributed lock"; never "proved Redisson
  correct".
- Disclose tutorial origin; never present this repo as production/employer work.
  No HUAWEI attribution to this repo. No production-grade/enterprise/national/
  capacity/cost claims. Throughput numbers = local single-machine only.
- Lead the signal with: characterization-test-first as a correctness contract,
  failure-mode analysis, ADR-backed decision, verified zero-overselling.
- Backend-first test: both assets must read as solid backend signal with all AI
  language stripped.
- Both assets are drafts; do not publish.
