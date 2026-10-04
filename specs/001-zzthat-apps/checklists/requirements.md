# Requirements checklist: 001-zzthat-apps

Checked 2026-10-04 against this spec package, then again the same day after ZQ1 to ZQ21 were decided. The apps are not built. Rows below check the spec, not a binary.

## Completeness

- [x] Spec has a `## Workflows` section naming workflows and the CI file to add later.
- [x] Plan and tasks copy that workflow list and do not replace it.
- [x] Each user story US1 to US6 has acceptance.
- [x] Functional requirements have IDs FR-001 to FR-039, and each row has a source tag.
- [x] Out of scope is stated, including web, zzThing, and a backend in this repo.
- [x] ZQ1 to ZQ21 are decided in `decisions.md`, with a GitHub issue each (#1 to #21).
- [x] Every task group TG1 to TG9 has a GitHub issue (#22 to #30) and tasks in `tasks.md`.
- [x] The API contract proposes endpoints, auth, rate limits, errors, versioning, and offline cache rules for DEP-001 to DEP-012.
- [x] Those proposals are for the zzThis server. Create stays gated on free public codes (DEP-001, FR-018, SC-8).
- [x] Free public codes are called out as v2 in zzThis spec 002 (FR-018, DEP-001, ZQ3).
- [x] On-device versus cloud recognition references zzThis Section 10.3 and Section 10.8 and does not add a third approach.
- [x] Privacy and permissions cover camera and photos, and say location and microphone are off under the defaults.
- [x] Accounts, abuse, and store obligations are present, with human gates.
- [x] Platform is decided: SwiftUI and Jetpack Compose, iOS 17, Android API 26 (ZQ17, ZQ21). The tradeoff table remains.
- [x] Success criteria SC-1 to SC-9 set no invented accuracy number.

## Source and copy rules

- [x] Product claims are tagged [ZZTHIS SPEC], [MICHAEL date], [DANNY date], or [INFERRED].
- [x] The grammar is referenced (zzThis Section 2.2a, G9), not redefined.
- [x] Resolver, wordlist, and capture requirements are referenced, not copied as a second spec.
- [x] User-facing rules require "demo", "check word", and no em dash (FR-030).
- [x] "checksum" and "mock" appear only inside the copy rule that forbids them, or in the terms note that zzThis also says "checksum word". They are not used as zzThat product terms.

## Consistency

- [x] Thin client (FR-002, FR-003) matches Danny's 2026-10-04 decision and zzThis Section 10.1.
- [x] Offline issue is forbidden (FR-022), matching zzThis Section 10.5.
- [x] Exact match (FR-009) matches zzThis spec 002 FR-001. ZQ13 records Michael's open suggestion policy.
- [x] Server-issued words (FR-017) match zzThis spec 002 US2. ZQ11 records the conflict with the handwritten examples.
- [x] Capture bands follow spec 004, and the marketing labels Manual, Rescan, Confirm, Resolve are not treated as the product contract.

## Not verified, because nothing is built

- [ ] SC-1 to SC-8 on a binary.
- [ ] T011 fixture tests.
- [ ] T020 G9 runs on iOS and Android.
- [ ] T082 string check in CI.
- [ ] A live call to the zzThis server. No such server is in this repo, and the sketch is not a versioned contract (DEP-009).
