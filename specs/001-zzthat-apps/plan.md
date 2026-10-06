# Plan: zzThat apps

Feature: [spec.md](spec.md). Status: implementation in progress. The planned trees now exist; unchecked tasks remain requirements until verified by their named evidence.
Decisions: [decisions.md](decisions.md). Recognition integration: [recognition.md](recognition.md).

## Workflows (copied from the spec)

anti-slop-code, production-systems, google-developer-style. Spec lint: `.github/workflows/ci.yml`. App tests: `.github/workflows/apps-ci.yml`. End-to-end: `.github/workflows/apps-e2e.yml`. Store submission, spend, production credentials, and privacy-policy publication stay human-gated. Store submission, spend, production credentials, and privacy-policy publication stay human-gated.

## Technical context

| Item | Choice | Status |
|---|---|---|
| Clients | iOS app and Android app, together | [DANNY 2026-10-04]. ZQ1. |
| Web | Later, in the zzthis repo, which is also the central server. The current zzthis site is the marketing page. "Try zzThat" links there: `https://zero-state-llc.github.io/zzthis/`. | ZQ1, ZQ2 [DANNY 2026-10-04] [ZZTHIS SPEC] hosting |
| Backend | None in this repo. Calls the zzThis central server in the zzthis repo. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.1 |
| UI stack | Swift and SwiftUI. Kotlin and Jetpack Compose. | ZQ17 |
| Minimum OS | iOS 17. Android 8.0 (API 26). | ZQ21 |
| Grammar | Each app implements zzThis Section 2.2a and fails on any G9 miss. | [ZZTHIS SPEC] |
| Check word and wordlist | Follow spec 003. Build it in T020 and T021. A local pass shows only when the bundled list version equals `wordlist_version` (FR-044). | [ZZTHIS SPEC] spec 003. Q30 to Q32 were decided in zzThis on 2026-10-04 ([zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)): `(d1 + 2 * d2) mod N` with N prime, and the EFF long list. |
| Recognition | Option A. On device first. Cloud only after the FR-005 sentence. | [ZZTHIS SPEC] Section 10.3. Q18 was decided in zzThis on 2026-10-04 ([zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)): Option A on the device (Vision on iOS, ML Kit on Android), no cloud reader in v1. |
| API | zzThis spec 005 and the pinned `contracts/zzthis-v1.openapi.yaml`. Prefix `/v1`. Header `X-ZZ-Contract: 1`. | ZQ19 |
| Auth | Native Apple or Google sign-in, a server nonce, and the provider ID token (zzThis spec 005 FR-020). Apple and Google on iOS, Google on Android. Access token 15 minutes. Rotating refresh in the Keychain or Keystore-encrypted DataStore. | ZQ18 [DANNY 2026-10-04] (Q66). ZQ23. |
| Create | Shows the Unavailable state until `GET /v1` says `free_public` is true. It is never hidden. Mint returns a server-chosen wordlist code and its check word. Re-roll cap is 3. | ZQ3, ZQ11 |
| On-device storage | Session tokens, last canonical read, public resolves for 60 seconds online and 360 seconds offline (FR-041), drafts labeled "Not a code yet." | FR-041, ZQ16, ZQ26. zzThis spec 005 FR-018. |
| Secrets | None in the tree. No record-signing keys in the app. | Constitution IX |
| Structure | Trees, modules, and navigation in runtime.md | [runtime.md](runtime.md) |
| API client | Generated from the pinned `contracts/zzthis-v1.openapi.yaml`: Swift OpenAPI Generator, and OpenAPI Generator `kotlin` with Retrofit | zzThis `424395f665587de8a73dae2cbdff3dbd9593b6c3`. ZQ19. |
| Design and strings | Pinned copy via `scripts/sync-zzthis.sh`, one sha in `contracts/ZZTHIS-API-PIN`, with fonts and `copy.json` | design.md. Not a submodule. |
| Recognition engines | Engine-neutral recognition adapters. Apple Vision is the iOS baseline. Android ML Kit and PP-OCR are qualification candidates; only the ZZ-OCR-QUAL-001 winner ships enabled by default. One still photo, on device. | runtime.md, Read pipeline; zzThis PR #89 / accepted 2026-10-06 recognition intent |
| Builds | Demo, Local, and Release, with XcodeGen and Gradle flavors | runtime.md, Build configurations |
| Privacy | Analytics-free. No crash upload of codes, photos, or tokens. | runtime.md |
| CI | Spec lint and the pinned OpenAPI check in `.github/workflows/ci.yml` (`build`, the required check). The build adds `apps-ci.yml`: an `android` job and a `checks` job (pin, strings, manifests) on `ubuntu-latest` on every pull request, and an `ios` job on `macos-26` only on `workflow_dispatch` or the `ios-ci` label. It adds `apps-e2e.yml`: `workflow_dispatch`, and pull request runs gated on the `run-e2e` label, with no schedule. iOS evidence comes from the dev Mac. Neither file is a required check. | ZQ27, decided 2026-10-05 (runtime.md, Testing). Named here before they land. |

## Constitution check

| Principle | How this plan holds it |
|---|---|
| I. Thin client | No server project and no resolver module. The HTTP shapes are the pinned zzThis OpenAPI (spec 005). |
| II. Handwriting first | Camera path is US2. Create shows words the server chose, and the person writes those (ZQ11). |
| III. Exact match | No live-code suggestions (FR-009, ZQ13). |
| IV. Honest status | No accuracy number. Create is unavailable when `free_public` is false (FR-018). |
| V. Do not invent the format | G9 is the grammar oracle. Spec 003 is the check-word oracle. |
| VI. Free public gap | DEP-001 stays a server change. The app does not issue around it. |
| VII. iOS and Android only | No web target in this repo. The later web client is zzthis (ZQ1). SwiftUI and Compose (ZQ17). |
| VIII. Spec before code | This plan does not add application source. |
| IX. Human gates | T083. The listing text is chosen. Submission is not. |
| XI. Copy | T082 checks user-facing strings. |

## Build order

The one-shot order is in zzThis `docs/ONE-SHOT-BRIEF.md` and in [tasks.md](tasks.md), Phase 1 to Phase 11.

1. Done: spec 001 is merged, and ZQ22 to ZQ26 are decided (zzThis #74). Danny still accepts the zzThis build intent. ZQ27, the CI spend path, is decided.
2. The zzThis build branch exists, with the fonts, `pin-zzthis.sh`, and the wordlist files. Pin to its head.
3. Scaffold, then the core library on both platforms against `contracts/vectors.json`.
4. API client, accounts, scan and resolve, create, share and manage, offline, privacy and strings.
5. Snapshots per state and one end-to-end run per platform against the local zzThis Worker.
6. After zzThis merges, move the pin to its merge commit, then merge this repo's PR.
7. Store submission waits on Danny's yes and on the ZQ25 items: the icon artwork, the support mailbox, and the published privacy page.

## Risks

- Free public codes are still v2 on the resolver [ZZTHIS SPEC] spec 002 FR-016. US1 runs end to end on the local Worker, where the flag is on, but not on a live server until Danny turns the scope on. ZQ3 keeps the gate.
- The HTTP shapes are the pinned OpenAPI. A shipped app must not call a server that has not published `X-ZZ-Contract: 1`.
- Two native parsers can drift. FR-039 is the control.
- ZQ11 matches spec 002 US2. The server chooses the words for free public codes too. This repo does not propose a person-written claim.
- Public resolve without a session (ZQ6) disagrees with the inferred "every call is authenticated" line in Section 10.6. The not-found collapse for private records has to stay.
- The re-roll cap of 3 is in zzThis spec 005 FR-006. Replaced words are never reissued (zzThis Q36, decided in zzThis on 2026-10-04, [zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)).
- Rate-limit numbers are still the starting table (ZQ20). The cache lifetime is not: Q26 is zzThis spec 005 FR-018, and FR-041 sets the app's 60 and 360 seconds.
- The Kotlin OpenAPI generator's 3.1 support is partial. The contract avoids the constructs it handles worst. The `apps-ci.yml` `android` job generates the Kotlin client on every pull request, so a problem there is a red check. Under ZQ27, the Swift client is generated only when the `ios` job runs (`workflow_dispatch` or the `ios-ci` label) and in the dev Mac run that the PR body quotes.
- Handwriting recognition quality on each platform is unmeasured. No accuracy number is a release criterion (FR-029).
- A purge clears only the data center that handled the write. Other data centers keep their copy until `max-age` ends, so 60 seconds at the edge is the normal worst case, not only when a purge fails [ZZTHIS SPEC] spec 005 FR-019(c). The app still drops its copy when `Cache-Control` says so, and at once after the owner's own write (FR-041).
- The age rating is a filing default. It is not a legal opinion.

## Recognition implementation rule

`recognition.md` is the downstream adapter and lifecycle contract. It does not duplicate zz grammar or qualification authority. When upstream spec 004 changes, the committed zzThis pin moves through the normal sync workflow, then downstream behavior is reconciled against that exact SHA. Release camera Accept requires a qualification PASS for the pinned adapter/configuration. NO_PROMOTION keeps typed entry and does not weaken shared validation.
