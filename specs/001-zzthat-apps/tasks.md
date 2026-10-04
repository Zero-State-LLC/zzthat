# Tasks: zzThat apps

Feature: [spec.md](spec.md) · Plan: [plan.md](plan.md)

Workflows for every implementing task: anti-slop-code, production-systems, google-developer-style. CI file, when added: `.github/workflows/apps-ci.yml`. Do not add that file in the spec-only change.

No task below adds a backend or a resolver. Live server calls stay blocked on the dependencies named in the spec.

## Phase 0: Decisions

These are the ZQ issues. They are not repeated as build tasks. An implementer follows the default in the spec until the owner closes the issue.

| Issue | ID | Owner |
|---|---|---|
| [#1](https://github.com/Zero-State-LLC/zzthat/issues/1) | ZQ1 | Michael |
| [#2](https://github.com/Zero-State-LLC/zzthat/issues/2) | ZQ2 | Michael |
| [#3](https://github.com/Zero-State-LLC/zzthat/issues/3) | ZQ3 | Michael and Danny |
| [#4](https://github.com/Zero-State-LLC/zzthat/issues/4) | ZQ4 | Michael |
| [#5](https://github.com/Zero-State-LLC/zzthat/issues/5) | ZQ5 | Michael |
| [#6](https://github.com/Zero-State-LLC/zzthat/issues/6) | ZQ6 | Michael |
| [#7](https://github.com/Zero-State-LLC/zzthat/issues/7) | ZQ7 | Michael |
| [#8](https://github.com/Zero-State-LLC/zzthat/issues/8) | ZQ8 | Michael |
| [#9](https://github.com/Zero-State-LLC/zzthat/issues/9) | ZQ9 | Michael |
| [#10](https://github.com/Zero-State-LLC/zzthat/issues/10) | ZQ10 | Michael |
| [#11](https://github.com/Zero-State-LLC/zzthat/issues/11) | ZQ11 | Michael |
| [#12](https://github.com/Zero-State-LLC/zzthat/issues/12) | ZQ12 | Michael |
| [#13](https://github.com/Zero-State-LLC/zzthat/issues/13) | ZQ13 | Michael |
| [#14](https://github.com/Zero-State-LLC/zzthat/issues/14) | ZQ14 | Michael |
| [#15](https://github.com/Zero-State-LLC/zzthat/issues/15) | ZQ15 | Michael |
| [#16](https://github.com/Zero-State-LLC/zzthat/issues/16) | ZQ16 | Michael |
| [#17](https://github.com/Zero-State-LLC/zzthat/issues/17) | ZQ17 | Danny |
| [#18](https://github.com/Zero-State-LLC/zzthat/issues/18) | ZQ18 | Danny |
| [#19](https://github.com/Zero-State-LLC/zzthat/issues/19) | ZQ19 | Danny |
| [#20](https://github.com/Zero-State-LLC/zzthat/issues/20) | ZQ20 | Danny |
| [#21](https://github.com/Zero-State-LLC/zzthat/issues/21) | ZQ21 | Danny |

## Phase 1: Platform scaffold

Issue: [#22](https://github.com/Zero-State-LLC/zzthat/issues/22) (TG1). Blocked on ZQ17 for a stack other than the default.

- [ ] T001 Scaffold the iOS and Android apps on the ZQ17 stack. Default: native Swift and Kotlin. No web target, no server module, no resolver.
- [ ] T002 Record minimum OS versions from ZQ21 in [plan.md](plan.md).
- [ ] T003 Add empty test targets for the grammar vectors. No product screens required.
- [ ] T004 When tests exist, add `.github/workflows/apps-ci.yml` to run them. Not part of the spec-only pull request.

## Phase 2: API client

Issue: [#23](https://github.com/Zero-State-LLC/zzthat/issues/23) (TG2). Fixture tests are not blocked. Live calls wait on ZQ3, ZQ18, ZQ19, and DEP-001 through DEP-012.

- [ ] T010 Client interface for the calls in the spec API contract: issue, resolve, revoke, record version. No audit call.
- [ ] T011 Fixture tests for each client-visible row of the spec error table: 200, 404, 400 grammar, 400 check-word mismatch, 422 bare mark, 422 reserved handle, 409, 401, 403, 429, 500. Assert the 404 fixture is one body for unknown, expired, used, and revoked.
- [ ] T012 Resolve sends the canonical string and does not attach a photo.
- [ ] T013 Send the contract version after ZQ19 names it. Until then, tests use fixtures only.
- [ ] T014 On 429, back off and surface one message that does not say whether the code exists (FR-033).

## Phase 3: Grammar and capture

Issue: [#24](https://github.com/Zero-State-LLC/zzthat/issues/24) (TG3).

- [ ] T020 Implement the v1 text grammar from zzThis `docs/SPEC.md` Section 2.2a on each platform. One shared vector list copied from G9 at the pinned zzThis commit, with the commit id in the test file header. Every vector passes on both platforms (FR-007, FR-039). Do not edit vectors to match a bug.
- [ ] T021 Snap to the wordlist and verify the check word locally, per zzThis spec 003 and spec 004. Blocked until that spec publishes the list and the algorithm. Until then the UI must not show a local check word as passed (FR-008).
- [ ] T022 Decision-band UI for accept, clarify, retry, and abstain. Thresholds are parameters (zzThis Q37). One test or snapshot per band trigger described in spec 004, without inventing numbers.
- [ ] T023 Option A camera read on device (FR-004). No accuracy percentage is a pass condition. Cloud fallback is not the default path.

## Phase 4: Create a code

Issue: [#25](https://github.com/Zero-State-LLC/zzthat/issues/25) (TG4). Live issue waits on ZQ3, ZQ11, DEP-001, and DEP-003.

- [ ] T030 Create screen: call issue, show the canonical code, tell the person to write it by hand. Server-chosen words only (FR-017).
- [ ] T031 When issue cannot be performed because the free public scope is absent, show the unavailable state and do not display a made-up code (FR-018, SC-8).
- [ ] T032 Creation check: photograph the handwriting on device and compare the parse to the issued canonical form (FR-037). A mismatch asks the person to rewrite or confirm. It does not upload the photo.
- [ ] T033 Reject any attempt to put a phone number into the code string on the client (FR-019). The server remains the enforcement point.

## Phase 5: Scan and resolve

Issue: [#26](https://github.com/Zero-State-LLC/zzthat/issues/26) (TG5). Depends on T001, T010, and T020.

- [ ] T040 Camera permission at scan start, with a purpose string. Denial leaves typed entry available (FR-011).
- [ ] T041 If the reader finds more than one candidate, list them and wait for a tap (FR-026).
- [ ] T042 Resolve and show the role-scoped view, or one not-found state (FR-010, SC-3). Do not add fields (ZQ5).
- [ ] T043 Typed entry uses the same parser and the same resolve call as the camera (US2a).
- [ ] T044 Retry upload only after an explicit confirm (FR-005, SC-4). The live upload waits on DEP-007 and on ZQ14 for the consent sentence.

## Phase 6: Share and manage

Issue: [#27](https://github.com/Zero-State-LLC/zzthat/issues/27) (TG6).

- [ ] T050 Share sheet sends the canonical code as text and does not attach a URL (FR-020).
- [ ] T051 My-codes list. Blocked on DEP-002. Do not ship a device-only ledger as the source of truth (FR-021).
- [ ] T052 Revoke and new record version through the client in T010. Refused when signed out (FR-015).
- [ ] T053 Public resolve of a revoked code uses the same not-found state as an unknown code. Do not infer the owner's revoked flag from that response.

## Phase 7: Offline

Issue: [#28](https://github.com/Zero-State-LLC/zzthat/issues/28) (TG7).

- [ ] T060 Show a cached signed reusable public view with an offline label (FR-022, SC-6).
- [ ] T061 Issue and revoke while offline are refused. They are not queued as successes.
- [ ] T062 Do not write single-use or short-expiry responses into the resolve cache (FR-023).
- [ ] T063 Keep the last canonical read on device for a later resolve. Do not keep the photo as an upload (FR-004).

ZQ16 blocks any draft UI. It does not block T060 to T063.

## Phase 8: Accounts, privacy, and abuse

Issue: [#29](https://github.com/Zero-State-LLC/zzthat/issues/29) (TG8).

- [ ] T070 Sign-in, sign-out, and session storage after ZQ18 names the mechanism. No signing keys in the binary.
- [ ] T071 Purpose strings and the system photo picker match FR-011 and FR-012.
- [ ] T072 Do not add location permission (FR-013) or microphone permission (FR-014) under the current defaults.
- [ ] T073 Account deletion in the app, blocked on DEP-012, before any submission that includes accounts (FR-034).
- [ ] T074 Do not add a report or block screen until ZQ8 is answered (FR-035).

## Phase 9: Store readiness

Issue: [#30](https://github.com/Zero-State-LLC/zzthat/issues/30) (TG9).

- [ ] T080 Draft the privacy disclosure from the spec privacy table. Do not publish it.
- [ ] T081 Listing copy and age rating. Blocked on ZQ15.
- [ ] T082 Add a check that user-facing strings contain no em dash, do not use "mock", and say "check word" where they would have said "checksum" (FR-030, SC-7).
- [ ] T083 Stop for Danny's yes before any store submission, developer-account purchase, or production credential.

## Later, only if a ZQ opens them

- Person-composed claim (ZQ11, DEP-006).
- Voice (ZQ10).
- Location and duplicate public codes (ZQ4).
- Share URL (ZQ9, DEP-011).
- Suggestion UI (ZQ13).
- Offline draft (ZQ16).
- Web client. Not this repo (ZQ1, FR-001).
