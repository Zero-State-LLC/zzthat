# Plan: zzThat apps

Feature: [spec.md](spec.md). Status: proposal, not built. No application source in this change.
Decisions: [decisions.md](decisions.md).

## Workflows (copied from the spec)

anti-slop-code, production-systems, google-developer-style. Spec lint: `.github/workflows/ci.yml`. An implementation change may add `.github/workflows/apps-ci.yml` for app tests and must name that addition in this plan before the file lands. Store submission, spend, production credentials, and privacy-policy publication stay human-gated.

## Technical context

| Item | Choice | Status |
|---|---|---|
| Clients | iOS app and Android app, together | [DANNY 2026-10-04]. ZQ1. |
| Web | Later, in the zzthis repo, which is also the central server. The current zzthis site is the marketing page. "Try zzThat" links there: `https://zero-state-llc.github.io/zzthis/`. | ZQ1, ZQ2 [DANNY 2026-10-04] [ZZTHIS SPEC] hosting |
| Backend | None in this repo. Calls the zzThis central server in the zzthis repo. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.1 |
| UI stack | Swift and SwiftUI. Kotlin and Jetpack Compose. | ZQ17 |
| Minimum OS | iOS 17. Android 8.0 (API 26). | ZQ21 |
| Grammar | Each app implements zzThis Section 2.2a and fails on any G9 miss. | [ZZTHIS SPEC] |
| Check word and wordlist | Follow spec 003. Do not show a local pass before that library exists. | [ZZTHIS SPEC]. Blocked on Q30, Q31, Q32. |
| Recognition | Option A. On device first. Cloud only after the FR-005 sentence. | [ZZTHIS SPEC] Section 10.3. Q18 still open in zzThis. |
| API | Proposal in spec.md. Prefix `/v1`. Header `X-ZZ-Contract: 1`. | ZQ19 |
| Auth | Native Apple or Google sign-in, a server nonce, and the provider ID token (zzThis spec 005 FR-020). Apple and Google on iOS, Google on Android. Access token 15 minutes. Rotating refresh in the Keychain or Keystore-encrypted DataStore. | ZQ18 [DANNY 2026-10-04] (Q66). ZQ23. |
| Create | Hidden until `GET /v1` says `free_public` is true. Mint returns a server-chosen wordlist code and its check word. Re-roll cap is 3. | ZQ3, ZQ11 |
| On-device storage | Session tokens, last canonical read, public resolves for 60 seconds online and 360 seconds offline (FR-041), drafts labeled "Not a code yet." | FR-041, ZQ16, ZQ26. zzThis spec 005 FR-018. |
| Secrets | None in the tree. No record-signing keys in the app. | Constitution IX |
| Structure | Trees, modules, and navigation in runtime.md | [runtime.md](runtime.md) |
| API client | Generated from the pinned `contracts/zzthis-v1.openapi.yaml`: Swift OpenAPI Generator, and OpenAPI Generator `kotlin` with Retrofit | zzThis `484100308376bce01c88f30f871dff3e13082fe0`. ZQ19. |
| Design and strings | Pinned copy via `scripts/sync-zzthis.sh`, one sha in `contracts/ZZTHIS-API-PIN`, with fonts and `copy.json` | design.md. Not a submodule. |
| Recognition engines | Apple Vision on iOS, ML Kit text recognition on Android, one still photo | runtime.md, Read pipeline [INFERRED] |
| Builds | Demo, Local, and Release, with XcodeGen and Gradle flavors | runtime.md, Build configurations |
| Privacy | Analytics-free. No crash upload of codes, photos, or tokens. | runtime.md |
| CI | Spec lint and the pinned OpenAPI check in `.github/workflows/ci.yml`. The build adds `apps-ci.yml` and `apps-e2e.yml`. | Named here before they land. |

## Constitution check

| Principle | How this plan holds it |
|---|---|
| I. Thin client | No server project and no resolver module. The HTTP shapes are a proposal for zzThis. |
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

1. Done: spec 001 is merged, and ZQ22 to ZQ26 are decided (zzThis #74). Danny still accepts the zzThis build intent.
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
- The re-roll cap of 3 is in zzThis spec 005 FR-006. Replaced words follow the never-reissue default while Q36 is open.
- Rate-limit numbers are still the starting table (ZQ20). The cache lifetime is not: Q26 is zzThis spec 005 FR-018, and FR-041 sets the app's 60 and 360 seconds.
- The Kotlin OpenAPI generator's 3.1 support is partial. The contract avoids the constructs it handles worst, and `apps-ci.yml` generates both clients on every run, so a problem is a red check.
- Handwriting recognition quality on each platform is unmeasured. No accuracy number is a release criterion (FR-029).
- If an edge purge fails, 60 seconds is the worst case [ZZTHIS SPEC] spec 005 FR-018. The app still drops its copy when `Cache-Control` says so.
- The age rating is a filing default. It is not a legal opinion.
