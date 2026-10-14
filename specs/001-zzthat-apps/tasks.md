# Tasks: zzThat apps

Feature: [spec.md](spec.md) · Plan: [plan.md](plan.md) · Build contract: [runtime.md](runtime.md) · Visuals: [design.md](design.md)

Workflows for every implementing task: anti-slop-code, production-systems, google-developer-style. A named workflow skill that is not installed where the build runs is style guidance only, and the PR body says so [INFERRED]. Spec lint and the pin check: `.github/workflows/ci.yml`. App tests: `.github/workflows/apps-ci.yml`. End-to-end: `.github/workflows/apps-e2e.yml`.

The phases run in this order in one build, after the zzThis build branch exists (zzThis `docs/ONE-SHOT-BRIEF.md`, Order). iOS and Android can run in parallel from Phase 2 on. Each phase ends with a "done when" line. No task adds a backend or a resolver.

## Phase 0: Decisions

ZQ1 to ZQ21 were decided on 2026-10-04 (issues [#1](https://github.com/Zero-State-LLC/zzthat/issues/1) to [#21](https://github.com/Zero-State-LLC/zzthat/issues/21)). ZQ22 is decided with ZQ18. ZQ23 to ZQ26 (issues [#33](https://github.com/Zero-State-LLC/zzthat/issues/33) to [#36](https://github.com/Zero-State-LLC/zzthat/issues/36)) were decided by best practice with Danny's yes on zzThis #74. ZQ27, the CI spend path, was decided by established practice on 2026-10-05, as Danny asked on zzThis #75. See [decisions.md](decisions.md).

## Phase 1: Scaffold ([#22](https://github.com/Zero-State-LLC/zzthat/issues/22))

- [ ] T001 The trees in runtime.md: XcodeGen `project.yml` and the three Swift packages; the Gradle modules with a version catalog. No web target, no server module, no resolver. Keep the root `.gitignore` in sync with the generated paths in runtime.md. `scripts/lint-specs.py` lists files with `git ls-files --cached --others --exclude-standard`, so ignored build output is never linted.
- [ ] T002 Minimums iOS 17 and Android 8.0 (API 26) (ZQ21).
- [ ] T005 `scripts/sync-zzthis.sh` from design.md, Sync steps 1 to 5. Step 5 rewrites the old sha in the files that name the pin, and the date on the constitution's Source pin line when the sha changes. Run it at the pin. Commit the copied files and the generated catalogs.
- [ ] T006 The Demo, Local, and Release configurations from runtime.md, with the bundle and application ids (ZQ24).
- [ ] T007 Register the pinned fonts and wire the pinned token files, light and dark.
- [ ] T003 Test targets: `ZZCore` tests, the app tests and UI tests, `:core-grammar` tests, Paparazzi.
- [ ] T004 `.github/workflows/apps-ci.yml` with the `ios`, `android`, and `checks` jobs, on the ZQ27 triggers (runtime.md, Testing): `pull_request` with types `opened`, `synchronize`, `reopened`, and `labeled`, plus `workflow_dispatch`, so adding the `ios-ci` label starts the `ios` job. The `android` job uses JDK 21 (temurin) and the pinned Gradle wrapper, and runs `:core-api:openApiGenerate`, then every module's JVM unit tests, then Paparazzi and the ML Kit test. The `checks` job runs the pin check, the string check, and the manifest check on `ubuntu-latest` on every pull request. Every `xcodebuild` step passes `-skipPackagePluginValidation`.

Done when: both apps launch in Demo with the three tabs, the pinned fonts, and the pinned colors, and `apps-ci.yml` passes. Under ZQ27, the iOS half is the dev Mac run pasted into the PR body.

## Phase 2: Core library ([#24](https://github.com/Zero-State-LLC/zzthat/issues/24))

- [ ] T020 Grammar, classifier, matching key, scanner, and check word in `ZZCore` and `:core-grammar`. Every row of `contracts/vectors.json` passes on both. Do not edit a row to match a bug (FR-007, FR-039).
- [ ] T021 The list loader for `contracts/wordlists/`. Local check word only when the versions match (FR-044).
- [ ] T022 Band logic with the values in one constants file (FR-025). It consumes normalized recognition evidence plus grammar/check-word results, never Vision/ML Kit/PP-OCR objects. Where meaningful line confidence exists, a word takes its line's confidence and a candidate takes the lowest line among its words. Shared band rows run in `ZZCore` (`swift test`) and in the `:core-grammar` JVM tests, and both give the same band: all lines at 0.80 is accept, one line at 0.79 is clarify, and a code wrapped across lines at 0.90 and 0.45 is retry. Missing confidence stays missing and must have a qualification-backed adapter policy before promotion.
- [ ] T023 The read pipeline per runtime.md and `recognition-adapter.md`, and the platform-neutral `RecognitionResult` boundary in both app stacks: marker-first same-baseline detection/pairing, explicit selection before OCR for ambiguous candidates, safe interior ROI crop/rotation only, no perspective or projective rectification, then deterministic scanner/grammar/check-word handling. `RecognitionResult` carries engine id/version, raw candidates, confidence when meaningful, and geometry/line provenance when available. No resolver or live-code query is permitted in the adapter. Cover flat two-line payloads; test perspective, curved wrapping, and candidate overflow as fail-closed/no-OCR cases. Preserve pair IDs, permitted transform provenance, and recognizer hypothesis order.
- [ ] T023c Qualification integration: run the recognizer independently on every safe, supported complete predicted pair/ROI, keyed by `pair_id`, without ground-truth-driven selection; score and preserve hypotheses per the pinned ZZ-OCR-QUAL-001 protocol. Verify runtime ambiguity still waits for selection and does not OCR unselected pairs. Keep unsupported-geometry/overflow safety-probe results separate from supported-geometry OCR accuracy and verify no payload OCR or Accept for those probes.
- [ ] T024 iOS Apple Vision adapter, on device, behind `RecognitionResult`.
- [ ] T025 Android ML Kit Text Recognition adapter, on device, behind `RecognitionResult`.
- [ ] T026 Android PP-OCR mobile/ONNX prototype adapter for qualification only. Do not make it a shipping dependency until dependency/license/security review and ZZ-OCR-QUAL-001 promotion.
- [ ] T027 Add the ZZ-OCR-QUAL-001 fixture/result format and harness. The private image corpus is not committed. Every engine uses the same corpus manifest and expected canonical outputs.
- [ ] T028 Run qualification and report exact-code accuracy, part/word accuracy, CER, false-valid-decode rate, Accept/Clarify/Retry/Abstain distribution, wrapped/multi-code behavior, latency, and package/runtime footprint. False-valid-decode behavior is the primary safety metric.
- [ ] T029 Record the Android promotion decision (ML Kit or PP-OCR) with engine/version pin and evidence. Apple Vision must also pass the same product-level qualification; if it does not, stop rather than weaken the decoder or bands.

Done when: every vector row passes on both platforms; a rendered `zz-copper-lantern-sky-zz` is a smoke test only; recognition.md lifecycle/privacy tests pass; and no Release configuration can enable an unqualified or cloud adapter. Product promotion still requires the upstream ZZ-OCR-QUAL-001 receipt.

## Phase 3: API client ([#23](https://github.com/Zero-State-LLC/zzthat/issues/23))

- [ ] T015 Generate both clients from `contracts/zzthis-v1.openapi.yaml` (runtime.md, API client). No hand-edited types. On Android, run `:core-api:openApiGenerate`, then `:core-api:compileKotlin`, on the pinned contract right after T001, before any other Android task. Record the generator version in the PR. Generator configuration (`typeMappings`, an `openapiNormalizer` rule, or ignoring the cookie parameter) is allowed and is not hand-writing types.
- [ ] T010 The middleware and interceptor: `/v1`, exactly one `X-ZZ-Contract: 1` set by replacing, the bearer rule, the single-flight refresh on 401, no HTTP cache (FR-049). Tests on each platform: a captured request has exactly one `X-ZZ-Contract` field, with value `1`, for one bearer route and one public route (iOS: a stub `ClientTransport`; Android: the OkHttp test server). Two concurrent calls with an expired token make exactly one `/v1/auth/refresh` call, and both succeed.
- [ ] T017 The demo implementation of the generated API for Demo builds.
- [ ] T011 Fixture tests for each row of the spec error table. The 404 body is exactly `{ "error": "not-found" }` for unknown, expired, used, revoked, and a signed-out private read. The 429 body is exactly `{ "error": "rate-limited" }`. Neither has another field. A 400 `malformed` on resolve shows `type.malformed`. A 400 `malformed` on mint, a record version, and a report each shows `error.nothing_saved` and keeps the fields. Success fixtures for Code, OwnedCode, Resolve, Account, and OwnerRecord carry one timestamp with milliseconds (`.123Z`) and one without (`Z`). Both decode on both platforms.
- [ ] T012 Resolve sends the canonical string, no photo, and no `Authorization` header (FR-016).
- [ ] T013 Only a 400 `contract-version` shows `error.update_app`. Any other response without `X-ZZ-Contract`, which the Swift client throws as `ClientError`, shows `error.unavailable`, or follows the offline rules when there is no connection. Until the pin declares 500 and 503 on every operation, iOS maps an undocumented 500 on a write to `error.nothing_saved` and an undocumented 503 to `error.unavailable`, as in the spec error table.
- [ ] T014 On 429, wait for `Retry-After` and show one message that does not say whether the code exists (FR-033).
- [ ] T016 Moving the pin replaces the OpenAPI copy and regenerates both clients in one pull request.

Done when: the fixture tests cover every error row on both platforms.

## Phase 4: Accounts ([#29](https://github.com/Zero-State-LLC/zzthat/issues/29))

- [ ] T070 Sign-in per runtime.md, Auth: Apple and Google on iOS, Google on Android, developer sign-in in Local builds (FR-040, FR-048). Android draws Google's `SignInButton` in an `AndroidView`.
- [ ] T073 Delete account with `DELETE /v1/me`, then local cleanup (FR-034).
- [ ] T078 The Account screen with the support contact and privacy link from build settings (FR-047), and the Licenses screen that shows the bundled `NOTICE` and `OFL.txt` verbatim, the one FR-042 exception.

Done when: in a Local build against `npm run dev:api`, developer sign-in, sign-out, and deletion work on both platforms.

## Phase 5: Scan and resolve ([#26](https://github.com/Zero-State-LLC/zzthat/issues/26))

- [ ] T040 The camera handoff from runtime.md, Read pipeline. Typing stays available when the camera is not (FR-011).
- [ ] T041 More than one candidate: list them, draw their boxes, and wait for a tap (FR-026).
- [ ] T042 Resolve shows `record.title` and `record.body` as plain text, or one not-found state (FR-010, FR-036, FR-046). Signed-out resolve works (FR-016).
- [ ] T043 Typed entry uses the same library and the same resolve call (US2a).
- [ ] T044 Never offer or call a photo-read/upload endpoint in V1, even if `photo_reads` is true. Add request-level tests proving no captured image, crop, or OCR transcript leaves the app; only a selected canonical code may be sent for resolution (FR-004, FR-045).

Done when: the Scan, Pick, Clarify, Type, and Resolve snapshots exist for each state, and the fixture flow resolves a typed code.

## Phase 6: Create ([#25](https://github.com/Zero-State-LLC/zzthat/issues/25))

- [ ] T030 Create calls mint and shows the canonical form and the check word (FR-017). No field for code words. The title takes 1 to 120 and the body 0 to 4000 Unicode code points after trimming (DEP-005). A 400 other than `contract-version`, or a 500, shows `error.nothing_saved` and keeps the title and body.
- [ ] T031 The unavailable state while `free_public` is false or mint is `not-ready` (FR-018, SC-8).
- [ ] T032 "Get different words" until `reroll-cap` (FR-017).
- [ ] T033 Write check: the read pipeline on the person's photo, compared with the minted code (FR-037). No upload.
- [ ] T034 No phone number or person-written code string in Create (FR-019).

Done when: in a Local build, a code is minted, re-rolled to the cap, and checked against a photo of a rendered code.

## Phase 7: Share and manage ([#27](https://github.com/Zero-State-LLC/zzthat/issues/27))

- [ ] T050 Share sends `share.text` and never a URL (FR-020).
- [ ] T051 My codes with titles and paging (FR-021).
- [ ] T052 Code detail and Edit record by code id (FR-021, runtime.md, Navigation): the row from My codes, or a paged lookup in `GET /v1/me/codes` (`limit` 50, following `next_cursor`), then `GET /v1/records/{record_id}`. Revoke with the code id, and show Revoke only when the row's `status` is `active`. Versions with `record_id`, with the same field limits and 400 rule as T030. No matching row shows the one not-found state.
- [ ] T053 A public resolve of a revoked code shows the same not-found state as an unknown code.

Done when: the snapshots for these screens exist, and the Local build edits and revokes a code.

## Phase 8: Offline ([#28](https://github.com/Zero-State-LLC/zzthat/issues/28))

- [ ] T060 The FR-041 cache with a fake clock: 60 seconds online, 360 seconds offline, the offline label. The entry is dropped after a 2xx from revoke, re-roll, and a record version, and `DELETE /v1/me` clears every entry.
- [ ] T061 Issue, re-roll, and revoke are refused offline. They are not queued as successes.
- [ ] T062 `no-store` responses are never written (FR-023).
- [ ] T063 Keep the last canonical text. Delete the photo when the scan ends (FR-004).
- [ ] T064 The local draft labeled "Not a code yet." with no code words, no share, and no resolve (FR-024).

Done when: the fake-clock tests pass on both platforms.

## Phase 9: Privacy, abuse, strings ([#29](https://github.com/Zero-State-LLC/zzthat/issues/29))

- [ ] T071 The iOS camera purpose string and the system photo picker on every build (FR-011, FR-012). `NSCameraUsageDescription` comes from `scan.camera_purpose` through the generated `InfoPlist.xcstrings` and the `INFOPLIST_KEY_NSCameraUsageDescription` build setting. It is never hand-written. A unit check fails when the built Info.plist lacks the key. Android needs no camera permission, because `TakePicture` needs none.
- [ ] T072 No location and no microphone permission (FR-013, FR-014).
- [ ] T074 The Report sheet with the four reasons and every screen state.
- [ ] T075 Analytics-free manifests (runtime.md, Privacy).
- [ ] T076 Accessibility from runtime.md.
- [ ] T077 Catalogs generated from `copy.json` only: `Localizable.xcstrings`, `InfoPlist.xcstrings`, and `strings.xml`. No sentence in a layout file (FR-042).
- [ ] T082 The string check on every generated catalog, `InfoPlist.xcstrings` included: no em dash, and the demo and check word terms of FR-030 (SC-7).

Done when: `apps-ci.yml` passes with the string check and the manifest check.

## Phase 10: Whole-system check

- [ ] T085 One snapshot per screen state, light and dark, on both platforms (SC-13).
- [ ] T086 `.github/workflows/apps-e2e.yml` on the ZQ27 triggers, and the end-to-end run in runtime.md, once per platform (SC-12). The builder opens the draft PR, adds the `run-e2e` label, and links the apps-e2e.yml run in the PR body. Under ZQ27, the builder runs the iOS end-to-end test on the dev Mac, with `-skipPackagePluginValidation`, and pastes the command, the commit sha, and the log tail into the PR body. Evidence with no Actions run is listed under "could not verify".

Done when: both end-to-end runs pass against the local zzThis Worker at the pin, with the evidence T086 names.

## Phase 11: Store readiness ([#30](https://github.com/Zero-State-LLC/zzthat/issues/30))

- [ ] T080 Draft the privacy policy at `apps/store/privacy-policy-draft.md` from the spec privacy table and zzThis spec 005 FR-026 at the pin. It is a draft for Danny. Do not add it to the zzThis site, and do not publish it (ZQ25, FR-031).
- [ ] T081 Put the FR-031 listing text and the questionnaire ratings with an 18+ audience (SC9), and the questionnaire answers, into `apps/store/listing-draft.md`. Do not submit.
- [ ] T083 Stop for Danny's yes before any store submission, developer-account purchase, bundle id registration, or production credential.
- [ ] T084 The release pipeline from runtime.md: `apps/VERSION`, app CI that tests and does not upload, and drafts of the privacy manifest and Data safety form (`apps/store/data-safety-draft.md`). No production URL in the tree.
- [ ] T087 Third-party notices, on both platforms [DANNY 2026-10-05 #43]. At build time, generate a notices file of the packages that ship in the app, from the platform's pinned dependency list (iOS: the closure of the app target's package products, through their library targets, in the app's `Package.resolved`, with each package's license and NOTICE files; Android: its resolved runtime dependencies). Test-only packages and build tools are not listed (S4). The file lists each package's name, version, and full license text, with Apache 2.0 NOTICE files where they exist. Bundle it and show it as given on the existing Licenses screen, after `NOTICE` and `OFL.txt`. No new copy key and no new runtime dependency. The build fails when the app links a package the notices miss, or when a listed package has no license file it can find.

## Phase 12: Store compliance for v1.1 (zzThis D-2026-10-10-25)

Accepted by Danny on 2026-10-09 (zzThis D-2026-10-10-25). The zzThis backlog ids are in brackets.

- [x] T090 [RM-105] FR-031, the App store requirements section, runtime.md Release step 3, and the listing draft say Apple 13+ and Google Play Teen, with the questionnaire answers. No older Apple rating remains.
- [x] T091 [RM-104] The Data safety and privacy policy drafts list what the bundled ML Kit text recognition SDK sends Google, and no unqualified "no analytics" sentence remains.
- [x] T092 [RM-107] The privacy manifest adds Other Data Types for the IP-based rate-limit HMAC (not linked, not tracking, App Functionality). A Release archive's privacy report shows a manifest for every linked SDK and no undeclared required-reason API.
- [x] T093 [RM-106] A `release-android` job, run by hand only and behind a protected environment, builds `bundleProdRelease`, signs it with the upload key from Actions secrets, and uploads the `.aab` as a workflow artifact. No key, password, or keystore in the tree. Danny enrolls in Play App Signing at the first upload.
- [x] T094 [RM-101] Hide this code, the one-day review sentence in the Report sheet, and the Account screen reset, on both platforms (FR-035). Needs the new copy keys in zzThis `design/copy.json` first.
- [ ] T095 [RM-075] A contract test shows that both generated clients ignore an unknown response key and tolerate an unknown enum value.
- [x] T096 [RM-101] Block this creator, after zzThis ships the Resolve `creator` field (RM-112, merged in zzThis #161). The unknown-key half of T095 (#58) is what this field needs; the unknown-enum half (#54) stays open under T095.
- [ ] T097 [RM-101] Terms acceptance before the first create, after Michael approves the Terms of Use and content policy (zzThis RM-103, RM-124; drafted in `apps/store/terms-draft.md`), with the 18+ statement (D-2026-10-11-05).
- [x] T098 [RM-115] Saved (FR-050): Save and Remove from Saved on Resolve, the Saved list from My codes signed in or out, and Clear saved codes on Account; device only, cleared by account deletion. zzThis D-2026-10-11-01 row 17.
- [x] T099 [RM-113] The suggested-word picker on the minted code (FR-017): the pick help, Get different words up to the cap, and Keep these words, which closes the picker locally. No code string is sent. zzThis D-2026-10-11-01 option C.
- [ ] T100 [RM-121] "Early release" in the listing and the app, and no zzThis name or host on any zzThat surface (D-2026-10-11-06, -07). The store drafts are done; the Local and Demo privacy link still points at a zzThis host until the policy host is decided (D-2026-10-11-09).

## Later, not in the first release

- Voice, and only on device (ZQ10, FR-014).
- Duplicate public codes and location (ZQ4). Stays v2 in zzThis.
- A share URL (ZQ9). `share.url` stays null.
- Live-code suggestions (ZQ13).
- A web client. It is built in zzThis, not in this repo (ZQ1, FR-001).
- A person-written claim (ZQ11).
- Sign in with Apple on Android (ZQ23).
- Live video scanning and any server-side photo reading are outside V1. `photo_reads` alone never enables an app upload; a future capability requires explicit product/privacy/security promotion.

## Recognition convergence gate

Before T023/T040 are complete, advance the zzThis pin through the normal sync flow to a commit that contains merged zzThis #89. Reconcile recognition.md against that SHA, run both shared-vector suites, and attach the qualification receipt or record NO_PROMOTION. SDK presence and a rendered-image smoke test are not qualification evidence.

## Phase 13: v1.1 follow-up from the 2026-10-13 spec-depth audit

From zzThis `specs/analysis-2026-10-13-spec-depth.md`. Each task names the zzThis change it waits on.

- [ ] T101 [RM-066] After Danny's v1.0 GO, run `scripts/sync-zzthis.sh` at the v1.0 release commit, bundle the production wordlist, and pass the contract tests against production discovery. Decision A2 then falls back to that production list, not proto-v0.
- [ ] T102 [RM-064, RM-065] Confirm-only capture per platform from one setting (FR-025), on by default until a PASS receipt exists; the zzThis spec 004 confirm-only band vectors pass on both platforms. Waits on zzThis D-2026-10-13-05 and its vectors.
- [ ] T103 [D-2026-10-11-15] Idle-expired codes show `codes.status_expired` in My codes and turn back on after the owner signs in again. No new route and no new copy if zzThis accepts D-2026-10-13-01 as proposed; a public resolve of an expired code stays not-found (FR-010).
- [ ] T104 [RM-120, RM-103, RM-124] One step before the first create on a device: the person confirms they are 18 or older and accepts the Terms. The current version is `terms.version` from zzThis `design/copy.json` at the pin; the accepted version is kept on the device only and cleared by account deletion. Scanning and typing need neither. Waits on zzThis D-2026-10-13-04, the copy keys, and Michael's approval of the Terms. Replaces T097 when built.
- [ ] T105 [D-2026-10-10-23] The Retry state names one reason, chosen by signal: `scan.retry_marker_cut`, `scan.retry_blur`, `scan.retry_glare`, `scan.retry_closer` (small in the frame), or `scan.retry_angle` (skew or perspective), on both platforms, with one test per reason. Copy keys from zzThis #169. Waits on T023 (the `RecognitionResult` adapter) and on the reason-code mapping that `recognition-adapter.md` freezes in versioned qualification configuration; the signals come from the adapter's normalized evidence, never from vendor objects or the confidence number alone.
- [ ] T106 [RM-075] Unknown enum values decode to a fallback on both platforms (#54): Android `enumUnknownDefaultCase=true`, iOS a fallback case. Gate on the first store build (zzThis D-2026-10-13-09).
