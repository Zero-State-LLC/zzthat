# zzThat constitution

Version 1.0.0. Ratified: pending (draft for Danny's acceptance in the pull request that adds this file). Amend through a pull request that bumps the version and records the change at the end of this file.

This file is the governing contract for every zzThat spec, plan, and task under `specs/`. When an artifact conflicts with this file, this file wins until it is amended. Product-true locks from Michael, and operator locks from Danny, win over this file.

zzThat is the iOS and Android apps portion of zzThis. The product rules live in the zzThis repository. This file adapts those rules to the apps. It does not replace them.

Source pin: zzThis `main` at `0a5c9b93e8f8266ff9d0855f4afe2dafbe1d26d7` (2026-10-03). Later zzThis changes do not change this repo until a pull request says so.

## Labels used in every artifact

- **[ZZTHIS SPEC]**: a claim taken from zzThis `docs/SPEC.md` or from `specs/002-resolver-core`, `specs/003-wordlist-checkword`, or `specs/004-capture` at the pin above. The original tag inside zzThis (for example [OPERATOR 2026-10-02] or [PRODUCT]) stays available in that repo. This repo does not retag those sources.
- **[MICHAEL date]**: a decision or piece of copy from Michael Chung. The date is the date used in zzThis.
- **[DANNY date]**: an operator decision from Danny. The thin-client decision and the iOS and Android scope are [DANNY 2026-10-04], from the instruction that opened this spec.
- **[INFERRED]**: a design or engineering choice made in a zzThat spec. An implementer may follow it without further approval. It is not a product fact.
- **OPEN**: a decision still owed by Michael or Danny. Each OPEN item names its default, if one exists, and its ZQ id in [spec 001](../../specs/001-zzthat-apps/spec.md).

Do not invent product behavior, metrics, or API fields to fill a gap. Record a ZQ instead.

## Principles

### I. Thin client

The iOS and Android apps call the zzThis central server for resolve, issue, and manage. They do not run a backend, a resolver, or a second source of truth [DANNY 2026-10-04] [ZZTHIS SPEC]. Security for authorization, payment, revocation, single use, and expiry stays on that server [ZZTHIS SPEC].

### II. Handwriting first

A code a person writes by hand must work. Printed marks and machine-only features are optional and are never required to resolve a code [ZZTHIS SPEC].

### III. Exact match, correction on the device

The server matches the exact code. By default it never suggests other live codes. Unknown, used, expired, and revoked codes look the same to a caller [ZZTHIS SPEC]. The app corrects against the closed wordlist and the check word, on the device [ZZTHIS SPEC]. The free app does not show live-code suggestions unless ZQ13 is answered otherwise. The default is off.

### IV. Honest status

zzThis has no validated codebook, no controlled comparisons, and no tested recognition or resolver yet [ZZTHIS SPEC]. The apps are not built. Public copy and store listings never claim pilots, customers, endorsement, adoption, or accuracy results. Research targets are not acceptance criteria and are not copied into this repo [ZZTHIS SPEC].

### V. Do not invent the code format

Encoding rules, the v1 text grammar, word counts, and check-word behavior come from zzThis. This repo references those specs. It does not copy them in as a second definition. Gaps stay OPEN.

### VI. Free public codes are not in the resolver v1 line

zzThis spec 002 lists free public codes as v2 [ZZTHIS SPEC]. zzThat is the free everyday product and depends on that scope [MICHAEL 2026-10-02] [DANNY 2026-10-04]. Specs must say so in the open. The apps must not fake a public code by standing up their own issuer.

### VII. iOS and Android only

This repository ships iOS and Android apps. A web client is out of scope here [DANNY 2026-10-04]. Michael's earlier copy also names the web [MICHAEL 2026-10-02]. That conflict is ZQ1. It does not add a web target to this repo.

### VIII. Spec before code

Work follows Spec Kit order: constitution, specify, clarify, plan, checklist, tasks, analyze, implement, converge. Each `spec.md` has a `## Workflows` section. Plans and tasks copy that list and do not replace it. No application source is added until Danny accepts spec 001.

### IX. Human gates

A human yes from Danny is required for App Store or Play submission, developer-account spend, production API credentials, legal and privacy-policy publication, domains and DNS, and admin-override merges. Agents do not submit store builds, buy things, or deploy a backend (there is no backend in this repo).

### X. A red honest check beats a green lie

Do not weaken tests, fixtures, or CI to force green. Open a `bug` issue instead.

### XI. User-facing copy

Say "demo", not "mock" [MICHAEL 2026-10-03] [DANNY 2026-10-04]. Say "check word", not "checksum" [MICHAEL 2026-10-03]. Use no em dashes, with no exceptions [DANNY 2026-10-03].

## Engineering standards

- Default workflows: anti-slop-code, production-systems, google-developer-style.
- This repo has no CI yet. An implementation pull request may add one workflow, named first in the feature plan. Do not invent a green check before that file exists.
- The platform default is native Swift and Kotlin [INFERRED]. ZQ17 can replace it. Whatever stack is chosen, both apps must pass the same zzThis G9 vectors.
- Developer prose follows the Google developer documentation style guide, including principle XI.

## Governance

- Owner of product decisions: Michael Chung (GitHub zzmojo). Owner of operator decisions and approvals: Danny.
- zzThis question IDs (Q1 and up) stay in the zzThis decision log. New questions for these apps use the next free ZQ id in spec 001. Do not renumber either series.
- A spec is accepted when Danny approves the pull request that adds or changes it.

## Amendments

| Version | Date | Change |
|---|---|---|
| 1.0.0 | 2026-10-04 | First draft. Adapted from the zzThis constitution at the pin above, plus the thin-client and iOS and Android scope from Danny. |
