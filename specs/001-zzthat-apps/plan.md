# Plan: zzThat apps

Feature: [spec.md](spec.md). Status: proposal, not built. No application source in this change.
Decisions: [decisions.md](decisions.md).

## Workflows (copied from the spec)

anti-slop-code, production-systems, google-developer-style. CI: none in this repo. An implementation change may add `.github/workflows/apps-ci.yml` and must name that addition in this plan before the file lands. Store submission, spend, production credentials, and privacy-policy publication stay human-gated.

## Technical context

| Item | Choice | Status |
|---|---|---|
| Clients | iOS app and Android app, together | [DANNY 2026-10-04]. ZQ1. |
| Web | Not in this repo. Later promise only, from the zzThis copy. | ZQ1 [MICHAEL 2026-10-02] |
| Backend | None. Calls the zzThis central server. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.1 |
| UI stack | Swift and SwiftUI. Kotlin and Jetpack Compose. | ZQ17 |
| Minimum OS | iOS 17. Android 8.0 (API 26). | ZQ21 |
| Grammar | Each app implements zzThis Section 2.2a and fails on any G9 miss. | [ZZTHIS SPEC] |
| Check word and wordlist | Follow spec 003. Do not show a local pass before that library exists. | [ZZTHIS SPEC]. Blocked on Q30, Q31, Q32. |
| Recognition | Option A. On device first. Cloud only after the FR-005 sentence. | [ZZTHIS SPEC] Section 10.3. Q18 still open in zzThis. |
| API | Proposal in spec.md. Prefix `/v1`. Header `X-ZZ-Contract: 1`. | ZQ19 |
| Auth | OIDC with PKCE. Sign in with Apple and Sign in with Google. Access token 15 minutes. Rotating refresh in the platform secure store. | ZQ18 |
| Create | Hidden until `GET /v1` says `free_public` is true. Claim sends the person's canonical code. | ZQ3, ZQ11 |
| On-device storage | Session tokens, last canonical read, public views for 24 hours, drafts labeled "Not a code yet." | FR-041, ZQ16. 24 hours is [INFERRED]. |
| Secrets | None in the tree. No record-signing keys in the app. | Constitution IX |
| CI | Not added in this change | File name reserved above. |

## Constitution check

| Principle | How this plan holds it |
|---|---|
| I. Thin client | No server project and no resolver module. The HTTP shapes are a proposal for zzThis. |
| II. Handwriting first | Camera path is US2. The person chooses the words (ZQ11). |
| III. Exact match | No live-code suggestions (FR-009, ZQ13). |
| IV. Honest status | No accuracy number. Create is unavailable when `free_public` is false (FR-018). |
| V. Do not invent the format | G9 is the grammar oracle. Spec 003 is the check-word oracle. |
| VI. Free public gap | DEP-001 stays a server change. The app does not issue around it. |
| VII. iOS and Android only | No web target. SwiftUI and Compose (ZQ17). |
| VIII. Spec before code | This plan does not add application source. |
| IX. Human gates | T083. The listing text is chosen. Submission is not. |
| XI. Copy | T082 checks user-facing strings. |

## Build order

1. Danny accepts this spec. ZQ1 to ZQ21 are already decided.
2. Scaffold SwiftUI and Compose at the ZQ21 minimums (T001, T002).
3. Grammar vectors (T020) and API fixtures for the proposal (T011) can land before the server exists.
4. Live claim waits until the zzThis server reports `free_public: true` and implements DEP-001 and DEP-006.
5. Live public resolve waits on DEP-004 and DEP-005. The signed-out path is part of that.
6. Local wordlist snap waits on zzThis spec 003.
7. Store submission waits on Danny's yes. The listing text and the rating to file are already in FR-031.

## Risks

- Free public codes are still v2 on the resolver [ZZTHIS SPEC] spec 002 FR-016. US1 cannot complete on a live server until that scope exists. ZQ3 keeps the gate.
- The HTTP shapes are a proposal. A shipped app must not call a server that has not published `X-ZZ-Contract: 1`.
- Two native parsers can drift. FR-039 is the control.
- ZQ11 claims the person's words. Spec 002 US2 still says the server chooses words for the issuer path. The two paths are different scopes. A zzThis change has to say so before the server implements DEP-006.
- Public resolve without a session (ZQ6) disagrees with the inferred "every call is authenticated" line in Section 10.6. The not-found collapse for private records has to stay.
- `POST /v1/codes/check` can become an enumerator if response times differ. The proposal requires one timing. That has to be tested on the server.
- Rate-limit numbers and the 24-hour cache are starting choices, not measurements (Q26 is still open in zzThis).
- Edge revocation can lag by the purge window [ZZTHIS SPEC] Q26. The app cannot fix that locally.
- The age rating is a filing default. It is not a legal opinion.
