# Specs

zzThat uses [Spec Kit](https://github.com/github/spec-kit) order: constitution, specify, clarify, plan, checklist, tasks, analyze, implement, converge. The rules for every spec are in [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) (v1.2.0, ratification pending Danny's approval).

Product behavior that is already specified lives in [zzThis](https://github.com/Zero-State-LLC/zzthis) at commit `74636f033a28be794d0f2c0959e3bec6d5afd6a0`. This repo references those specs. It does not copy them.

| zzThis artifact | What zzThat uses it for |
|---|---|
| [`docs/SPEC.md`](https://github.com/Zero-State-LLC/zzthis/blob/74636f033a28be794d0f2c0959e3bec6d5afd6a0/docs/SPEC.md) | Grammar (Section 2.2a), architecture (Section 10), questions (Section 9), v1 and v2 line (Section 12) |
| [`specs/002-resolver-core`](https://github.com/Zero-State-LLC/zzthis/tree/74636f033a28be794d0f2c0959e3bec6d5afd6a0/specs/002-resolver-core) | The server the apps call. Free public codes are v2 there. |
| [`specs/003-wordlist-checkword`](https://github.com/Zero-State-LLC/zzthis/tree/74636f033a28be794d0f2c0959e3bec6d5afd6a0/specs/003-wordlist-checkword) | Wordlist, check word, and the v1 text grammar |
| [`specs/005-v1-api`](https://github.com/Zero-State-LLC/zzthis/tree/74636f033a28be794d0f2c0959e3bec6d5afd6a0/specs/005-v1-api) | The `/v1` contract the apps call, pinned as `contracts/zzthis-v1.openapi.yaml` |
| [`design/`](https://github.com/Zero-State-LLC/zzthis/tree/74636f033a28be794d0f2c0959e3bec6d5afd6a0/design) | Tokens, fonts, logos, UX patterns, and `copy.json` |
| [`specs/004-capture`](https://github.com/Zero-State-LLC/zzthis/tree/74636f033a28be794d0f2c0959e3bec6d5afd6a0/specs/004-capture) | Camera, typing, and voice capture. Voice is not in the first app release (ZQ10). |

## Features

| ID | Feature | Built? | specify | clarify | plan | checklist | tasks | analyze |
|---|---|---|---|---|---|---|---|---|
| [001](001-zzthat-apps/spec.md) | iOS and Android apps | No | Done, deepened 2026-10-04 | ZQ1 to ZQ28 decided ([log](001-zzthat-apps/decisions.md)) | Done | [Done](001-zzthat-apps/checklists/requirements.md) | [Build order](001-zzthat-apps/tasks.md) | [Done 2026-10-04](https://github.com/Zero-State-LLC/zzthis/blob/74636f033a28be794d0f2c0959e3bec6d5afd6a0/specs/analysis-2026-10-04.md) (in zzThis) |

Web is not a feature of this repo [DANNY 2026-10-04]. zzThing is not a feature of this repo [ZZTHIS SPEC].

## Artifact map

| Artifact | Path |
|---|---|
| Constitution | `.specify/memory/constitution.md` |
| Feature spec (what and why) | `specs/NNN-name/spec.md` |
| Plan (how and stack) | `specs/NNN-name/plan.md` |
| Requirement checklist | `specs/NNN-name/checklists/requirements.md` |
| Tasks | `specs/NNN-name/tasks.md` |
| Decision log | [spec 001 decisions](001-zzthat-apps/decisions.md) (ZQ1 to ZQ21) and the zzThis Section 9 table (Q ids) |
| Runtime build contract | [spec 001 runtime](001-zzthat-apps/runtime.md) |
| Design consumption | [spec 001 design](001-zzthat-apps/design.md) |

## Issues to tasks

Question issues and task-group issues are in this repo. zzThis question numbers are cited, not re-filed there.

| Issue | Spec |
|---|---|
| [#1](https://github.com/Zero-State-LLC/zzthat/issues/1) ZQ1 web client lives in zzthis | 001 FR-001 |
| [#2](https://github.com/Zero-State-LLC/zzthat/issues/2) ZQ2 launch link is the zzThis marketing page | 001, no launch claim |
| [#3](https://github.com/Zero-State-LLC/zzthat/issues/3) ZQ3 free public scope is v2 | 001 FR-018, DEP-001 |
| [#4](https://github.com/Zero-State-LLC/zzthat/issues/4) ZQ4 duplicates and location | 001 FR-013 |
| [#5](https://github.com/Zero-State-LLC/zzthat/issues/5) ZQ5 resolved view contents | 001 DEP-005 |
| [#6](https://github.com/Zero-State-LLC/zzthat/issues/6) ZQ6 scan without an account | 001 FR-016, DEP-004 |
| [#7](https://github.com/Zero-State-LLC/zzthat/issues/7) ZQ7 what a free account is | 001 FR-015, DEP-003 |
| [#8](https://github.com/Zero-State-LLC/zzthat/issues/8) ZQ8 abuse and safety | 001 FR-035 |
| [#9](https://github.com/Zero-State-LLC/zzthat/issues/9) ZQ9 share payload | 001 FR-020, DEP-011 |
| [#10](https://github.com/Zero-State-LLC/zzthat/issues/10) ZQ10 voice (zzThis Q38) | 001 FR-014 |
| [#11](https://github.com/Zero-State-LLC/zzthat/issues/11) ZQ11 server chooses the words | 001 FR-017, DEP-006, DEP-008 |
| [#12](https://github.com/Zero-State-LLC/zzthat/issues/12) ZQ12 in-app resolved view | 001 FR-036 |
| [#13](https://github.com/Zero-State-LLC/zzthat/issues/13) ZQ13 suggestions (zzThis Q40) | 001 FR-009 |
| [#14](https://github.com/Zero-State-LLC/zzthat/issues/14) ZQ14 retry photo consent | 001 FR-005, DEP-007 |
| [#15](https://github.com/Zero-State-LLC/zzthat/issues/15) ZQ15 store listing | 001 FR-031 |
| [#16](https://github.com/Zero-State-LLC/zzthat/issues/16) ZQ16 offline draft | 001 FR-024 |
| [#17](https://github.com/Zero-State-LLC/zzthat/issues/17) ZQ17 native or cross-platform | 001 plan, [#22](https://github.com/Zero-State-LLC/zzthat/issues/22) |
| [#18](https://github.com/Zero-State-LLC/zzthat/issues/18) ZQ18 end-user auth | 001 DEP-003, [#29](https://github.com/Zero-State-LLC/zzthat/issues/29) |
| [#19](https://github.com/Zero-State-LLC/zzthat/issues/19) ZQ19 API version | 001 FR-032, DEP-009 |
| [#20](https://github.com/Zero-State-LLC/zzthat/issues/20) ZQ20 rate limits (zzThis Q26) | 001 FR-033 |
| [#21](https://github.com/Zero-State-LLC/zzthat/issues/21) ZQ21 minimum OS versions | [#22](https://github.com/Zero-State-LLC/zzthat/issues/22) |
| [#22](https://github.com/Zero-State-LLC/zzthat/issues/22) TG1 platform scaffold | T001 to T004 |
| [#23](https://github.com/Zero-State-LLC/zzthat/issues/23) TG2 API client | T010 to T014 |
| [#24](https://github.com/Zero-State-LLC/zzthat/issues/24) TG3 grammar and capture | T020 to T023 |
| [#25](https://github.com/Zero-State-LLC/zzthat/issues/25) TG4 create a code | T030 to T034 |
| [#26](https://github.com/Zero-State-LLC/zzthat/issues/26) TG5 scan and resolve | T040 to T044 |
| [#27](https://github.com/Zero-State-LLC/zzthat/issues/27) TG6 share and manage | T050 to T053 |
| [#28](https://github.com/Zero-State-LLC/zzthat/issues/28) TG7 offline | T060 to T063 |
| [#29](https://github.com/Zero-State-LLC/zzthat/issues/29) TG8 accounts, privacy, abuse | T070 to T074 |
| [#30](https://github.com/Zero-State-LLC/zzthat/issues/30) TG9 store readiness | T080 to T083 |
| [#32](https://github.com/Zero-State-LLC/zzthat/issues/32) ZQ22 sign-in by ID token (zzThis Q66, [#68](https://github.com/Zero-State-LLC/zzthis/issues/68)) | 001 FR-040, DEP-003 |
| [#33](https://github.com/Zero-State-LLC/zzthat/issues/33) ZQ23 Google only on Android (zzThis Q67, [#69](https://github.com/Zero-State-LLC/zzthis/issues/69)) | 001 FR-040 |
| [#34](https://github.com/Zero-State-LLC/zzthat/issues/34) ZQ24 bundle and application ids | runtime.md, Build configurations |
| [#35](https://github.com/Zero-State-LLC/zzthat/issues/35) ZQ25 icon, support contact, privacy policy | 001 FR-047, T083 |
| [#36](https://github.com/Zero-State-LLC/zzthat/issues/36) ZQ26 offline reading time | 001 FR-041, US6 |

## Labels

Findings and choices carry one source tag: [ZZTHIS SPEC], [MICHAEL date], [DANNY date], or [INFERRED]. OPEN marks a decision that Michael or Danny must make. Specs do not invent requirements or metrics.
