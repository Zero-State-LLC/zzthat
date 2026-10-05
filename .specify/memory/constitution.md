# zzThat constitution

Version 1.3.0. Ratified: pending (draft for Danny's acceptance in the pull request that adds this file). Amend through a pull request that bumps the version and records the change at the end of this file.

This file is the governing contract for every zzThat spec, plan, and task under `specs/`. When an artifact conflicts with this file, this file wins until it is amended. Product-true locks from Michael, and operator locks from Danny, win over this file.

zzThat is the iOS and Android apps portion of zzThis. The product rules live in the zzThis repository. This file adapts those rules to the apps. It does not replace them.

Source pin: zzThis at `bffcfe8e018b90c3b838cf2ebfb1b69d8ebe3e91` (2026-10-05), recorded in `contracts/ZZTHIS-API-PIN`. One pin covers the product rules, the API, design, strings, test vectors, and the wordlist. Later zzThis changes do not change this repo until a pull request moves the pin.

## Labels used in every artifact

- **[ZZTHIS SPEC]**: a claim taken from zzThis `docs/SPEC.md` or from `specs/002-resolver-core`, `specs/003-wordlist-checkword`, or `specs/004-capture` at the pin above. The original tag inside zzThis (for example [OPERATOR 2026-10-02] or [PRODUCT]) stays available in that repo. This repo does not retag those sources.
- **[MICHAEL date]**: a decision or piece of copy from Michael Chung. The date is the date used in zzThis.
- **[DANNY date]**: an operator decision from Danny. The thin-client decision and the iOS and Android scope are [DANNY 2026-10-04]. The 2026-10-04 instruction to close ZQ1 to ZQ21 from the zzThis docs and from native thin-client practice is the same date.
- **[INFERRED]**: a design or engineering choice made in a zzThat spec. An implementer may follow it without further approval. It is not a product fact.
- **OPEN**: a decision still owed. ZQ1 to ZQ21 are decided in [decisions.md](../../specs/001-zzthat-apps/decisions.md). A new gap gets the next ZQ id.

Do not invent a second grammar or a second resolver. API shapes the zzThis server does not publish yet are proposals in spec 001, tagged as proposals, and they wait on that server.

## Principles

### I. Thin client

The iOS and Android apps call the zzThis central server for resolve, issue, and manage. They do not run a backend, a resolver, or a second source of truth [DANNY 2026-10-04] [ZZTHIS SPEC]. Security for authorization, payment, revocation, single use, and expiry stays on that server [ZZTHIS SPEC].

### II. Handwriting first

A code a person writes by hand must work. Printed marks and machine-only features are optional and are never required to resolve a code [ZZTHIS SPEC].

### III. Exact match, correction on the device

The server matches the exact code. It does not suggest other live codes. Unknown, used, expired, and revoked codes look the same to a caller [ZZTHIS SPEC]. The app corrects against the closed wordlist and the check word, on the device [ZZTHIS SPEC]. The free app does not show live-code suggestions (ZQ13, decided 2026-10-04).

### IV. Honest status

zzThis has no validated codebook, no controlled comparisons, and no tested recognition or resolver yet [ZZTHIS SPEC]. The apps are not built. Public copy and store listings never claim pilots, customers, endorsement, adoption, or accuracy results. Research targets are not acceptance criteria and are not copied into this repo [ZZTHIS SPEC].

### V. Do not invent the code format

Encoding rules, the v1 text grammar, word counts, and check-word behavior come from zzThis. This repo references those specs. It does not copy them in as a second definition. ZQ1 to ZQ21 are closed in the decisions log. A new gap stays OPEN.

### VI. Free public codes are not in the resolver v1 line

zzThis spec 002 lists free public codes as v2 [ZZTHIS SPEC]. zzThat is the free everyday product and depends on that scope [MICHAEL 2026-10-02] [DANNY 2026-10-04]. Specs must say so in the open. The apps must not fake a public code by standing up their own issuer.

### VII. iOS and Android only

This repository ships iOS and Android apps, together [DANNY 2026-10-04]. It does not build a web client. The later web client, and the central server, live in the zzthis repository. The site that repository publishes today is the marketing page for zzThis (ZQ1, corrected 2026-10-04).

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
- Spec lint is `.github/workflows/ci.yml` (`python3 scripts/lint-specs.py`). App tests use `.github/workflows/apps-ci.yml`, added only when the test targets exist. Do not invent a green app check before that file exists.
- The apps are native Swift with SwiftUI, and Kotlin with Jetpack Compose. Minimums are iOS 17 and Android 8.0 (API 26). Decided in ZQ17 and ZQ21. Both apps must pass the same zzThis G9 vectors.
- Developer prose follows the Google developer documentation style guide, including principle XI.

## Governance

- Owner of product decisions: Michael Chung (GitHub zzmojo). Owner of operator decisions and approvals: Danny.
- zzThis question IDs (Q1 and up) stay in the zzThis decision log. App questions ZQ1 to ZQ21 are decided in `specs/001-zzthat-apps/decisions.md`. A new question uses the next free ZQ id. Do not renumber either series.
- A spec is accepted when Danny approves the pull request that adds or changes it.

## Amendments

| Version | Date | Change |
|---|---|---|
| 1.0.0 | 2026-10-04 | First draft. Adapted from the zzThis constitution at the pin above, plus the thin-client and iOS and Android scope from Danny. |
| 1.1.0 | 2026-10-04 | ZQ1 to ZQ21 decided. Exact match is closed for the free app. Web stays later and outside this repo. Stack is SwiftUI and Jetpack Compose. Decisions log added. |
| 1.1.1 | 2026-10-04 | Danny: zzthis is the central server and the later web client, and "Try zzThat" links to its marketing page. ZQ11 reversed: the server chooses the words for every code, including free public. |
| 1.2.0 | 2026-10-04 | Runtime build contract, design consumption TODO, spec-lint CI, and the zzThat board. |
| 1.3.0 | 2026-10-04 | One zzThis pin for everything. Deepened for a one-shot build: ZQ22 to ZQ26, sign-in by ID token, the read pipeline, strings from `copy.json`. |
