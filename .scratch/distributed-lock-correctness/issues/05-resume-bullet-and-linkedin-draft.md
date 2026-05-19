# 05 — Resume bullet + LinkedIn case-study draft

Status: done
Type: human-in-loop

> Drafts produced in `.scratch/distributed-lock-correctness/issue-05-drafts.md`
> (résumé bullet + LinkedIn case-study post). Drafts only; publishing is the
> user's decision and remains out of scope.

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## What to build

The outward-facing assets, drafted **only after** the technical slice
(issues 00–04) is complete. No social content is written before the technical
work lands.

- **Resume bullet (1):** backed by the commits/ADR, no inflated AI title.
  Pattern: characterized a hand-rolled Redis distributed lock's failure modes
  under concurrency and documented the Redisson production decision via ADR.
  Backend substance leads; AI-assisted/test-first method is the amplifier, not
  the headline.
- **LinkedIn case-study post (1 draft):** single post, written at slice
  completion, linking the ADR/commits. Backend-first framing: the lock failure
  modes, the characterization-test-as-correctness-contract method, why Redisson.
  Positioning: backend engineer applying AI-native workflows — never "AI Agent
  Engineer". No interim build-log posts for this slice.

Drafts only; publishing is the user's decision, outside this slice.

## Acceptance criteria

- [x] 1 resume bullet drafted, backed by real commits/ADR, no inflated AI title
- [x] 1 LinkedIn case-study post drafted, backend-first, links the ADR/commits
- [x] Both pass the backend-first test and the no-AI-agent-identity guardrail
- [x] Produced only after issues 00–04 are complete

## Blocked by

`04-readme-disclosure-and-context-glossary.md` (and, transitively, the full
technical slice 00–03)
