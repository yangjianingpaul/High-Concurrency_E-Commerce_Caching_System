# 04.5 — Documentation honesty sweep

Status: done
Type: human-in-loop

## Parent

`.scratch/distributed-lock-correctness/PRD.md`

## Why this issue exists

Issue 04 made `README.md` honest but discovered the laundering (HUAWEI
provenance, production/enterprise/national-scale and cost claims) is repo-wide.
Issue 05 publishes outward-facing assets that link into this repo; any remaining
laundered doc would contradict the README and the ADR-0002 guardrails the moment
a recruiter opens it. This sweep must complete before Issue 05.

## Scope

Top-level docs, especially: `ARCHITECTURE.md`, `PERFORMANCE_TEST_REPORT.md`,
`INTERVIEW_PREP.md`, `PERFORMANCE_CHEAT_SHEET.md`, plus any other top-level doc
containing HUAWEI provenance, production-grade, enterprise/national-scale, or
unsupported benchmark/cost claims.

## Rules

- Remove/neutralize any claim this repo or codebase came from, was deployed at,
  validated at, or benchmarked at HUAWEI production.
- Remove unsupported production-grade / enterprise-scale / national-scale /
  cost-projection framing.
- HUAWEI may remain only as real *background* experience, with a clear boundary:
  prior Redis/seckill/coupon experience informed the analysis, but this repo is
  not derived from employer code or production systems.
- Keep benchmark data only if framed as local single-machine experiment data
  with environment + tool + limitations; otherwise mark superseded or remove.
- Preserve honest technical value: characterization tests, ADRs, failure-mode
  analysis, zero-oversell correctness evidence, Redis/cache/concurrency reasoning.

## Constraints

- No production code changes. No test changes. No resume/LinkedIn yet.
- Issue 05 does not start until this is done.

## Acceptance criteria

- [ ] Every listed doc reviewed; offending claims removed or neutralized
- [ ] No surviving claim attributes this repo to HUAWEI production work
- [ ] No surviving unsupported enterprise/national/cost framing
- [ ] Surviving benchmark text is explicitly local-single-machine or marked
      superseded
- [ ] Honest technical value preserved
- [ ] Summary of removed / reframed / superseded recorded in Comments
- [ ] Explicit safe/not-safe recommendation for Issue 05

## Blocked by

`04-readme-disclosure-and-context-glossary.md`

## Comments

### Sweep complete (2026-05-18) — Issue 05 SAFE to proceed

Docs reviewed: ARCHITECTURE, ENVIRONMENT_SETUP_GUIDE, INTERVIEW_PREP,
JMETER_QUICK_START, PERFORMANCE_CHEAT_SHEET, PERFORMANCE_TEST_MACBOOK,
PERFORMANCE_TEST_REPORT (README/CONTEXT already done in Issue 04).

Claims REMOVED / neutralized inline:
- INTERVIEW_PREP L76 elevator pitch ("production-grade … I built") → honest
  tutorial-derived + engineering-delta pitch.
- INTERVIEW_PREP L83-84 follow-ups ("Based on my experience at HUAWEI",
  "beyond production requirements") → honest framing.
- INTERVIEW_PREP STAR story (fabricated HUAWEI 3-5s/40%-bounce incident) →
  relabelled as a structure template with an explicit "do not recite as a
  personal HUAWEI incident" caution.
- INTERVIEW_PREP closing "real production-grade thinking" → defensible-beats-
  impressive.
- PERFORMANCE_CHEAT_SHEET L132 "I validated 10,000 qps…" script → honest
  local-measurement + correctness-led answer.
- ARCHITECTURE diagram "2,000+ users / 10,000+ QPS" + "Production Deployment
  Architecture" heading → local/illustrative.
- ENVIRONMENT_SETUP_GUIDE title + 2 "production-grade environment" lines →
  "local performance-test environment".

Claims REFRAMED via top-of-file corrective banner:
- INTERVIEW_PREP — binding "read first" honesty rules overriding the doc.
- ARCHITECTURE — illustrative-local scope note.

Files MARKED SUPERSEDED (wholesale, bodies left intact but withdrawn):
- PERFORMANCE_TEST_REPORT — strong SUPERSEDED banner; Cost Analysis /
  Enterprise / National / hardware-scaling tables explicitly withdrawn.
- PERFORMANCE_TEST_MACBOOK — local-only scope banner.
- PERFORMANCE_CHEAT_SHEET — scope-correction banner.

No production code, no tests, no resume/LinkedIn touched. Post-sweep grep: every
surviving "HUAWEI" string is inside a retraction/caution banner; no surviving
"production-grade … system I built", "national flash sale", or "real-world
capacity" framing outside withdrawn bodies. JMETER_QUICK_START reviewed — its
matches are benign operational JMeter numbers, no laundering, left as-is.

Recommendation: **Issue 05 is SAFE to proceed.** Outward assets may link the
repo; README + ADR + failure-mode + glossary are the honest spine, and no
linked doc now contradicts them outside explicitly-withdrawn report bodies.
