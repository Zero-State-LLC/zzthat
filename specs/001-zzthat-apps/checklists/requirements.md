# Requirements checklist: 001-zzthat-apps

Checked 2026-10-04 against this spec package, again after ZQ1 to ZQ21 were decided, and again after the one-shot deepening (ZQ22 to ZQ26, zzThis pin `484100308376bce01c88f30f871dff3e13082fe0`). The apps are not built. Rows below check the spec, not a binary.

## Completeness

- [x] Spec has a `## Workflows` section naming workflows and the CI file to add later.
- [x] Plan and tasks copy that workflow list and do not replace it.
- [x] Each user story US1 to US6 has acceptance.
- [x] Functional requirements have IDs FR-001 to FR-049, and each row has a source tag.
- [x] Out of scope is stated, including web, zzThing, and a backend in this repo.
- [x] ZQ1 to ZQ21 are decided in `decisions.md`, with a GitHub issue each (#1 to #21).
- [x] ZQ22 is decided with ZQ18, and ZQ23 to ZQ26 are decided with Danny's yes on zzThis #74. Each has a GitHub issue (#32 to #36).
- [x] Every task group TG1 to TG9 has a GitHub issue (#22 to #30) and tasks in `tasks.md`.
- [x] The API section points at the pinned OpenAPI file instead of restating it, and maps DEP-001 to DEP-012 to zzThis spec 005.
- [x] Create stays gated on free public codes (DEP-001, FR-018, SC-8). The local Worker turns the flag on for tests.
- [x] Free public codes are called out as v2 in zzThis spec 002 (FR-018, DEP-001, ZQ3).
- [x] v1 recognition is on-device only. The recognizer is an evidence adapter, not semantic authority. Apple Vision is the iOS baseline; Android ML Kit and PP-OCR are qualification candidates under ZZ-OCR-QUAL-001.
- [x] Privacy and permissions cover camera and photos, and say location and microphone are off under the defaults.
- [x] Accounts, abuse, and store obligations are present, with human gates.
- [x] Platform is decided: SwiftUI and Jetpack Compose, iOS 17, Android API 26 (ZQ17, ZQ21). The tradeoff table remains.
- [x] Success criteria SC-1 to SC-13 set no invented accuracy number.
- [x] runtime.md names structure, modules, navigation, screen states, the generated client, auth, storage, privacy, accessibility, localization, testing, and release.
- [x] design.md pins zzThis at one sha with `scripts/sync-zzthis.sh`, including fonts and `copy.json`. No token values or sentences are invented here.
- [x] Every screen in runtime.md names a pattern in zzThis `design/UX.md`. None is a TODO.
- [x] runtime.md names the toolchain, build configurations, the read pipeline, and an end-to-end run.
- [x] Spec lint is `.github/workflows/ci.yml`.

## Source and copy rules

- [x] Product claims are tagged [ZZTHIS SPEC], [MICHAEL date], [DANNY date], or [INFERRED].
- [x] The grammar is referenced (zzThis Section 2.2a, G9), not redefined.
- [x] Resolver, wordlist, and capture requirements are referenced, not copied as a second spec.
- [x] User-facing rules require "demo", "check word", and no em dash (FR-030).
- [x] The words checksum and mock appear only inside the copy rule that forbids them, beside check word and demo, or in the terms note that zzThis also says "checksum word". They are not used as zzThat product terms.

## Consistency

- [x] Thin client (FR-002, FR-003) matches Danny's 2026-10-04 decision and zzThis Section 10.1.
- [x] Offline issue is forbidden (FR-022), matching zzThis Section 10.5.
- [x] Exact match (FR-009) matches zzThis spec 002 FR-001. ZQ13 records Michael's open suggestion policy.
- [x] Server-issued words (FR-017) match zzThis spec 002 US2, including free public codes. There is no person-written claim contract (ZQ11).
- [x] Capture bands follow spec 004, and the marketing labels Manual, Rescan, Confirm, Resolve are not treated as the product contract.
- [x] Raw scan photos never leave the device in v1.
- [x] Engine-specific recognizer objects do not enter the shared grammar libraries.
- [x] Missing recognizer confidence is explicit and cannot be fabricated by an adapter.
- [x] Android engine promotion requires ZZ-OCR-QUAL-001 evidence; false-valid-decode behavior is the primary safety metric.

## Not verified, because nothing is built

- [ ] SC-1 to SC-8 on a binary.
- [ ] T011 fixture tests.
- [ ] T020 `contracts/vectors.json` runs on iOS and Android.
- [ ] T082 string check in CI.
- [ ] A live call to the zzThis server. No such server is in this repo, and the sketch is not a versioned contract (DEP-009).
