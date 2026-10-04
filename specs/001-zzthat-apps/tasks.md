# Tasks: zzThat apps

Feature: [spec.md](spec.md) · Plan: [plan.md](plan.md) · Build contract: [runtime.md](runtime.md) · Visuals: [design.md](design.md)

Workflows for every implementing task: anti-slop-code, production-systems, google-developer-style. Spec lint and the pin check: `.github/workflows/ci.yml`. App tests: `.github/workflows/apps-ci.yml`. End-to-end: `.github/workflows/apps-e2e.yml`.

The phases run in this order in one build, after the zzThis build branch exists (zzThis `docs/ONE-SHOT-BRIEF.md`, Order). iOS and Android can run in parallel from Phase 2 on. Each phase ends with a "done when" line. No task adds a backend or a resolver.

## Phase 0: Decisions

ZQ1 to ZQ21 were decided on 2026-10-04 (issues [#1](https://github.com/Zero-State-LLC/zzthat/issues/1) to [#21](https://github.com/Zero-State-LLC/zzthat/issues/21)). ZQ22 to ZQ26 (issues [#32](https://github.com/Zero-State-LLC/zzthat/issues/32) to [#36](https://github.com/Zero-State-LLC/zzthat/issues/36)) wait on Danny and Michael, and the build follows their defaults. See [decisions.md](decisions.md).

## Phase 1: Scaffold ([#22](https://github.com/Zero-State-LLC/zzthat/issues/22))

- [ ] T001 The trees in runtime.md: XcodeGen `project.yml` and the three Swift packages; the Gradle modules with a version catalog. No web target, no server module, no resolver.
- [ ] T002 Minimums iOS 17 and Android 8.0 (API 26) (ZQ21).
- [ ] T005 `scripts/sync-zzthis.sh` from design.md. Run it at the pin. Commit the copied files and the generated catalogs.
- [ ] T006 The Demo, Local, and Release configurations from runtime.md, with the bundle and application ids (ZQ24).
- [ ] T007 Register the pinned fonts and wire the pinned token files, light and dark.
- [ ] T003 Test targets: `ZZCore` tests, the app tests and UI tests, `:core-grammar` tests, Paparazzi.
- [ ] T004 `.github/workflows/apps-ci.yml` with the `ios` and `android` jobs, the pin check, and the string check.

Done when: both apps launch in Demo with the three tabs, the pinned fonts, and the pinned colors, and `apps-ci.yml` passes.

## Phase 2: Core library ([#24](https://github.com/Zero-State-LLC/zzthat/issues/24))

- [ ] T020 Grammar, classifier, matching key, scanner, and check word in `ZZCore` and `:core-grammar`. Every row of `contracts/vectors.json` passes on both. Do not edit a row to match a bug (FR-007, FR-039).
- [ ] T021 The list loader for `contracts/wordlists/`. Local check word only when the versions match (FR-044).
- [ ] T022 Band logic with the values in one constants file (FR-025). One test per band trigger in zzThis spec 004.
- [ ] T023 The read pipeline: Vision on iOS, ML Kit on Android (runtime.md, Read pipeline).

Done when: every vector row passes on both platforms, and a rendered image of `zz-copper-lantern-sky-zz` yields that one candidate on both.

## Phase 3: API client ([#23](https://github.com/Zero-State-LLC/zzthat/issues/23))

- [ ] T015 Generate both clients from `contracts/zzthis-v1.openapi.yaml` (runtime.md, API client). No hand-edited types.
- [ ] T010 The middleware: `/v1`, `X-ZZ-Contract: 1`, the bearer rule, one refresh on 401, no HTTP cache (FR-049).
- [ ] T017 The demo implementation of the generated API for Demo builds.
- [ ] T011 Fixture tests for each row of the spec error table, with one 404 body for unknown, expired, used, revoked, and a signed-out private read.
- [ ] T012 Resolve sends the canonical string, no photo, and no `Authorization` header (FR-016).
- [ ] T013 A response without `X-ZZ-Contract`, or a 400 `contract-version`, shows `error.update_app`.
- [ ] T014 On 429, wait for `Retry-After` and show one message that does not say whether the code exists (FR-033).
- [ ] T016 Moving the pin replaces the OpenAPI copy and regenerates both clients in one pull request.

Done when: the fixture tests cover every error row on both platforms.

## Phase 4: Accounts ([#29](https://github.com/Zero-State-LLC/zzthat/issues/29))

- [ ] T070 Sign-in per runtime.md, Auth: Apple and Google on iOS, Google on Android, developer sign-in in Local builds (FR-040, FR-048).
- [ ] T073 Delete account with `DELETE /v1/me`, then local cleanup (FR-034).
- [ ] T078 The Account screen with the support contact and privacy link from build settings (FR-047).

Done when: in a Local build against `npm run dev:api`, developer sign-in, sign-out, and deletion work on both platforms.

## Phase 5: Scan and resolve ([#26](https://github.com/Zero-State-LLC/zzthat/issues/26))

- [ ] T040 The camera handoff from runtime.md, Read pipeline. Typing stays available when the camera is not (FR-011).
- [ ] T041 More than one candidate: list them, draw their boxes, and wait for a tap (FR-026).
- [ ] T042 Resolve shows `record.title` and `record.body` as plain text, or one not-found state (FR-010, FR-036, FR-046). Signed-out resolve works (FR-016).
- [ ] T043 Typed entry uses the same library and the same resolve call (US2a).
- [ ] T044 No photo upload offer while `photo_reads` is false (FR-045).

Done when: the Scan, Pick, Clarify, Type, and Resolve snapshots exist for each state, and the fixture flow resolves a typed code.

## Phase 6: Create ([#25](https://github.com/Zero-State-LLC/zzthat/issues/25))

- [ ] T030 Create calls mint and shows the canonical form and the check word (FR-017). No field for code words.
- [ ] T031 The unavailable state while `free_public` is false or mint is `not-ready` (FR-018, SC-8).
- [ ] T032 "Get different words" until `reroll-cap` (FR-017).
- [ ] T033 Write check: the read pipeline on the person's photo, compared with the minted code (FR-037). No upload.
- [ ] T034 No phone number or person-written code string in Create (FR-019).

Done when: in a Local build, a code is minted, re-rolled to the cap, and checked against a photo of a rendered code.

## Phase 7: Share and manage ([#27](https://github.com/Zero-State-LLC/zzthat/issues/27))

- [ ] T050 Share sends `share.text` and never a URL (FR-020).
- [ ] T051 My codes with titles and paging (FR-021).
- [ ] T052 Code detail and Edit record from `GET /v1/records/{id}`; revoke; versions (FR-021).
- [ ] T053 A public resolve of a revoked code shows the same not-found state as an unknown code.

Done when: the snapshots for these screens exist, and the Local build edits and revokes a code.

## Phase 8: Offline ([#28](https://github.com/Zero-State-LLC/zzthat/issues/28))

- [ ] T060 The FR-041 cache with a fake clock: 60 seconds online, 360 seconds offline, the offline label.
- [ ] T061 Issue, re-roll, and revoke are refused offline. They are not queued as successes.
- [ ] T062 `no-store` responses are never written (FR-023).
- [ ] T063 Keep the last canonical text. Delete the photo when the scan ends (FR-004).
- [ ] T064 The local draft labeled "Not a code yet." with no code words, no share, and no resolve (FR-024).

Done when: the fake-clock tests pass on both platforms.

## Phase 9: Privacy, abuse, strings ([#29](https://github.com/Zero-State-LLC/zzthat/issues/29))

- [ ] T071 The iOS camera purpose string and the system photo picker (FR-011, FR-012).
- [ ] T072 No location and no microphone permission (FR-013, FR-014).
- [ ] T074 The Report sheet with the four reasons and every screen state.
- [ ] T075 Analytics-free manifests (runtime.md, Privacy).
- [ ] T076 Accessibility from runtime.md.
- [ ] T077 Catalogs generated from `copy.json` only. No sentence in a layout file (FR-042).
- [ ] T082 The string check on the catalogs: no em dash, and the demo and check word terms of FR-030 (SC-7).

Done when: `apps-ci.yml` passes with the string check and the manifest check.

## Phase 10: Whole-system check

- [ ] T085 One snapshot per screen state, light and dark, on both platforms (SC-13).
- [ ] T086 `.github/workflows/apps-e2e.yml` and the end-to-end run in runtime.md, once per platform, linked in the PR (SC-12).

Done when: both end-to-end runs pass against the local zzThis Worker at the pin.

## Phase 11: Store readiness ([#30](https://github.com/Zero-State-LLC/zzthat/issues/30))

- [ ] T080 Draft the privacy disclosure from the spec privacy table. Do not publish it.
- [ ] T081 Put the FR-031 listing text and the 12+ and Teen rating into the store draft. Do not submit.
- [ ] T083 Stop for Danny's yes before any store submission, developer-account purchase, bundle id registration, or production credential.
- [ ] T084 The release pipeline from runtime.md: `apps/VERSION`, app CI that tests and does not upload, and drafts of the privacy manifest and Data safety form. No production URL in the tree.

## Later, not in the first release

- Voice, and only on device (ZQ10, FR-014).
- Duplicate public codes and location (ZQ4). Stays v2 in zzThis.
- A share URL (ZQ9). `share.url` stays null.
- Live-code suggestions (ZQ13).
- A web client. It is built in zzThis, not in this repo (ZQ1, FR-001).
- A person-written claim (ZQ11).
- Sign in with Apple on Android (ZQ23).
- Live video scanning and the photo upload, after zzThis Q18.
