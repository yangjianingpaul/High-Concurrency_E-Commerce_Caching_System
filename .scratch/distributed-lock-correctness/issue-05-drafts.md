# Issue 05 — Outward Asset Drafts (distributed-lock-correctness)

Status: ready-for-human
Type: human-in-loop (drafts only — publishing is the user's decision, out of scope)

Source of truth for every claim below:
- `docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md`
- `docs/distributed-lock-failure-modes.md`
- `src/test/java/com/paulyang/ecommerce/utils/MutexRedisLockTest.java`
  (`Tests run: 10, Failures: 0, Errors: 0, Skipped: 1` — perf test quarantined)
- `README.md` → "Provenance & Portfolio Scope" + "Local Measurements"

No production code or tests were modified to produce these drafts.

---

## 1. Résumé bullet (draft)

**Primary (canonical):**

> Characterized a hand-rolled Redis distributed lock's real failure modes under
> concurrency — fixed-lease expiry while a holder is still in its critical
> section, non-reentrancy, and per-thread ownership-token release — using
> characterization tests as a correctness contract, then recorded the production
> decision to use Redisson `RLock` (and the rejected alternatives) in an ADR.

**Compact variant (single line, for space-constrained layouts):**

> Used characterization tests as a correctness contract to pin a hand-rolled
> Redis lock's failure modes (fixed-lease expiry under hold, non-reentrancy,
> per-thread ownership token) and documented the Redisson-over-hand-rolled
> production decision and trade-offs in an ADR.

**Honesty boundary (not part of the bullet; for the candidate's own reference
when this appears on a résumé):** the codebase is a hmdp / 黑马点评 tutorial
baseline. The contribution being claimed is the failure-mode characterization
and decision record — the *Engineering Delta* — not authorship of the original
system, not a "distributed lock I built," and not employer-derived work. Do not
attach this repo to HUAWEI provenance.

---

## 2. LinkedIn case-study post (single draft)

> **What a hand-rolled Redis lock actually guarantees — pinned with tests, not opinions**
>
> I picked up a Spring Boot + Redis e-commerce codebase (built on the open
> hmdp / 黑马点评 tutorial) and went after one canonical senior-backend
> question: does the hand-rolled distributed lock in it actually do what its
> Javadoc claims?
>
> Instead of arguing about it, I pinned the real behaviour with characterization
> tests — tests that assert what the code *does*, not what it should do — and
> used them as a correctness contract.
>
> What held up:
> • Acquire is a single atomic `SET NX EX` — no check-then-set race.
> • Release is an atomic owner-checked get-compare-del Lua script, and it
>   correctly rejects a genuinely different thread.
>
> What did not:
> • The lease is fixed with no watchdog/renewal — it can expire while the
>   holder is still inside the critical section, letting a second acquirer in.
> • The lock is non-reentrant — the same thread re-acquiring the same key fails.
> • Ownership is keyed on a per-thread token, so any code on the *same thread*
>   (even via a different lock object) can release it.
>
> Those two real limitations — fixed lease and non-reentrancy — not any
> acquire-path or foreign-owner defect, are exactly why the production path uses
> Redisson `RLock`. I wrote that up as an ADR: the decision, the rejected
> alternatives (keep it / delete it), and the concrete failure mode behind the
> call. The unsafe lock is kept on purpose, fully tested, as a documented
> failure-mode artifact.
>
> The method I keep coming back to: characterization-test-first as a correctness
> contract before any claim about a concurrency primitive. AI-assisted tooling
> sped up the loop, but the signal is the analysis and the decision record, not
> the tooling — and it reads the same with every mention of AI removed.
>
> Write-up + ADR + tests are in the repo (links in comments). Origin is
> disclosed up front: the value here is the engineering delta on a tutorial
> baseline, not original-system authorship.

**Comment-1 (links, drafted separately so the post body has no bare URLs):**
ADR-0001 `docs/adr/0001-redis-distributed-lock-redisson-over-handrolled.md` ·
failure-mode map `docs/distributed-lock-failure-modes.md` ·
characterization suite `MutexRedisLockTest`. (Fill the public GitHub URLs at
publish time — the technical slice is committed before the post goes out.)

---

## 3. Backend-first test (acceptance gate)

With every word about AI / agents / tooling deleted, both assets still read as a
complete, defensible backend story:

> Characterized a Redis distributed lock's failure modes with characterization
> tests (atomic acquire and foreign-thread rejection hold; fixed-lease expiry
> under an active holder, non-reentrancy, and per-thread-token release do not),
> and recorded the Redisson-over-hand-rolled production decision and rejected
> alternatives in an ADR.

That sentence contains zero AI language and loses none of the signal. **Pass.**

---

## 4. Guardrail compliance checklist

- [x] No production code changed; no tests changed (drafts only).
- [x] No "AI Agent Engineer" identity; framed as backend engineer applying
      AI-native workflows, with AI as amplifier not headline.
- [x] No "built a distributed lock" / "proved Redisson correct" claims.
- [x] Tutorial origin (hmdp / 黑马点评) disclosed in both assets.
- [x] No production / employer / HUAWEI provenance for this repo.
- [x] No enterprise / national / capacity / cost claims; no throughput numbers
      cited (and any that exist elsewhere are local single-machine only).
- [x] Signal leads with: characterization-test-first as correctness contract,
      failure-mode analysis, ADR-backed decision.
- [x] Backend-first test passes (§3).
- [x] Both assets are drafts; not published.

## 5. Acceptance criteria (from issue 05)

- [x] 1 résumé bullet drafted, backed by ADR/characterization tests, no inflated
      AI title (one canonical + one compact variant, both within the boundary).
- [x] 1 LinkedIn case-study post drafted, backend-first, links the ADR / suite.
- [x] Both pass the backend-first test and the no-AI-agent-identity guardrail.
- [x] Produced only after issues 00–04.5 were complete.

## 6. Open item for the user before publishing (non-blocking)

The technical slice is in the working tree but **not yet committed** (`git log`
shows no ADR/failure-mode/MutexRedisLockTest commit). Both assets reference the
ADR/tests/commits; commit and push the slice so the public links resolve before
either asset is used externally. This is a publish-time prerequisite, owned by
the user — not part of this drafts-only issue.
