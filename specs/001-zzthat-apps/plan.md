# Plan: zzThat apps

Feature: [spec.md](spec.md). Status: proposal, not built. No application source in this change.

## Workflows (copied from the spec)

anti-slop-code, production-systems, google-developer-style. CI: none in this repo. An implementation change may add `.github/workflows/apps-ci.yml` and must name that addition in this plan before the file lands. Store submission, spend, production credentials, and privacy-policy publication stay human-gated.

## Technical context

| Item | Choice | Status |
|---|---|---|
| Clients | iOS app and Android app | [DANNY 2026-10-04] |
| Web | Not in this repo | [DANNY 2026-10-04]. Public copy still names the web [MICHAEL 2026-10-02]. ZQ1. |
| Backend | None. Calls the zzThis central server. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.1 |
| UI stack | Native Swift (iOS) and Kotlin (Android) | [INFERRED] default. OPEN ZQ17 ([#17](https://github.com/Zero-State-LLC/zzthat/issues/17)). |
| Minimum OS | Not chosen | OPEN ZQ21 ([#21](https://github.com/Zero-State-LLC/zzthat/issues/21)). Record the numbers in this table when the scaffold starts. |
| Grammar | Each app implements zzThis Section 2.2a and fails on any G9 miss. Replace with a shared package if zzThis Q29 publishes one. | [ZZTHIS SPEC]. Two copies until a package exists is [INFERRED]. |
| Check word and wordlist | Call the spec 003 rules. Do not show a local pass before that library exists. | [ZZTHIS SPEC] spec 003. Blocked on Q30, Q31, Q32. |
| Recognition | Option A: on device first, cloud only after the person confirms a retry. | [ZZTHIS SPEC] Section 10.3 and Section 10.8. Q18 still open. |
| API | Endpoints and errors in spec.md API contract. Fixtures until DEP-009. | [ZZTHIS SPEC] Section 10.6 is a sketch. |
| On-device storage | Session material, last canonical read, short cache of signed reusable public views. Not a source of truth. | [ZZTHIS SPEC] Section 10.5. TTL number is not set [INFERRED] placeholder, recorded here when Danny sets it. |
| Secrets | None in the tree. No record-signing keys in the app. | [INFERRED] from constitution VI and IX |
| CI | Not added in this change | Constitution. File name reserved above. |

## Constitution check

| Principle | How this plan holds it |
|---|---|
| I. Thin client | No server project, no database of record, no resolver module. |
| II. Handwriting first | Camera path is US2. Printed-only features are not required. |
| III. Exact match | Client never requests live-code suggestions (FR-009). |
| IV. Honest status | SC list has no accuracy number. Create is unavailable when the scope is missing (FR-018). |
| V. Do not invent the format | G9 is the grammar oracle. Spec 003 is the check-word oracle. |
| VI. Free public gap | DEP-001 and FR-018 stay visible. The app does not issue around them. |
| VII. iOS and Android only | No web target in the scaffold task. |
| VIII. Spec before code | This plan does not add application source. |
| IX. Human gates | T083. No store action in this plan. |
| XI. Copy | T082 checks user-facing strings. |

## Build order

1. Accept this spec (Danny).
2. Close ZQ17 before writing the scaffold. The default lets an implementer start on native Swift and Kotlin if Danny does not object.
3. Grammar vectors (T020) can land with the scaffold. They do not need the server.
4. API client fixture tests (T011) can land before the server exists.
5. Live issue and resolve wait on DEP-001, DEP-003, and DEP-009.
6. Local wordlist snap waits on zzThis spec 003.
7. Store submission waits on ZQ15 and Danny's yes.

## Risks

- Free public codes are v2 on the resolver [ZZTHIS SPEC] spec 002 FR-016. The everyday product cannot complete US1 until that moves. ZQ3.
- Section 10.6 is not a contract [ZZTHIS SPEC]. A shipped app that guesses paths will break. ZQ19.
- Two native parsers can drift. FR-039 and one shared vector file are the control [INFERRED].
- The pitch examples look person-composed [MICHAEL 2026-10-02], and the issuer story says the server picks the words [ZZTHIS SPEC]. Building only the server-issued path is the default and may not match what Michael wants. ZQ11.
- "Anyone can scan" [MICHAEL 2026-10-02] conflicts with "every call is authenticated" [ZZTHIS SPEC] Section 10.6. ZQ6.
- Edge revocation can lag by the purge window [ZZTHIS SPEC] Q26. The app cannot fix that locally.
