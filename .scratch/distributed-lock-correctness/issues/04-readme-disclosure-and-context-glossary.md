# 04 — README tutorial-origin disclosure + CONTEXT glossary

Status: done
Type: human-in-loop

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## What to build

The repo-wide provenance artifact and the glossary update. Narrative/judgment
work — not AFK.

- **README tutorial-origin disclosure:** one honest paragraph stating the
  hmdp/黑马点评 tutorial baseline, and framing the portfolio value as the
  **Engineering Delta** — characterization tests, lock failure-mode analysis,
  ADRs, correctness-preserving refactoring — explicitly *not* original system
  authorship. This is a one-time, repo-wide artifact established by this slice;
  later slices inherit it.
- **CONTEXT.md glossary terms:** add to the project `CONTEXT.md`, in the
  existing format (term, one-sentence definition, `_Avoid_` list), the
  failure-mode vocabulary surfaced by this slice — e.g. **Lease Expiry Under
  Hold**, **Lock Ownership Token**, **Reentrancy** — plus a Relationships line
  and a Flagged-ambiguities entry if a term displaced an old loose usage. Match
  the tone and structure of the existing cache-strategy section.

No production code change. Wording must pass the backend-first test.

## Acceptance criteria

- [ ] README contains the tutorial-origin disclosure paragraph, Engineering
      Delta framing, no original-authorship claim
- [ ] `CONTEXT.md` gains the failure-mode terms in the existing format, with
      `_Avoid_` lists and a Relationships line
- [ ] Flagged-ambiguities entry added if any term resolved a loose prior usage
- [ ] No AI-agent identity claim anywhere; defensible with AI language removed
- [ ] No production lock-path code changed

## Blocked by

`03-adr-and-failure-mode-analysis.md`

## Comments

### README + CONTEXT updated (2026-05-18) — Issue 05 unblocked; FOLLOW-UP RISK

User-approved scope change: README was not merely missing a disclosure — it
contained active laundering. User chose "full honest rewrite"; HUAWEI claim
"partially true" → removed from this repo's framing (real experience belongs in
résumé).

README changes:
- Removed "production-grade … 10,000+ concurrent … sub-millisecond" hero line.
- Added "Provenance & Portfolio Scope" section: hmdp/黑马点评 origin disclosed;
  Engineering Delta framing; links to ADR-0001, failure-mode doc,
  characterization tests.
- Removed HUAWEI-as-origin from Overview and "About the Developer".
- Reframed benchmark section as explicitly local single-machine measurements;
  deleted "Production Performance Metrics / Industry Standard", "Real-World
  Capacity", "Enterprise → National flash sale platform", and hardware/cost
  extrapolation tables. Kept the genuine zero-overselling correctness result.
- Distributed-locking section now points to the characterized limits + ADR-0001.

CONTEXT.md changes: added "### Distributed locking" terms — Lock Ownership
Token, Fixed Lease, Watchdog / Lease Renewal, Foreign-Owner Unlock, Reentrancy,
Characterization Test (each with `_Avoid_`); 4 Relationships bullets; an Example
dialogue exchange; 2 Flagged-ambiguities entries. Matches existing format.

**FOLLOW-UP RISK (out of Issue 04 scope, not actioned):** the same laundering
almost certainly persists in `ARCHITECTURE.md`, `PERFORMANCE_TEST_REPORT.md`,
`INTERVIEW_PREP.md`, `PERFORMANCE_CHEAT_SHEET.md` (HUAWEI attribution,
enterprise/national capacity, cost projections). README now points at
PERFORMANCE_TEST_REPORT as "superseded" but those files are unedited. Recommend
a dedicated honesty-sweep slice before the repo is shared publicly.

Issue 05 (resume bullet + LinkedIn draft) is unblocked.
