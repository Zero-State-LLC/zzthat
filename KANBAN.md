# zzThat board

Readable copy of the work state on 2026-10-18. Open issues were listed again the same evening: still 12 open, the same set. The live board is the [zzThat project](https://github.com/orgs/Zero-State-LLC/projects/25). This file does not move those cards.

Now 4. Blocked 3. Backlog 9. Done 6.

## Now

| Item | Residual |
|---|---|
| [#96](https://github.com/Zero-State-LLC/zzthat/pull/96) hyphen-box placement | Open. Not merged. Places a `zz` Vision already read when hyphen boxes are missing. Does not rewrite `22` to `zz`. |
| [#24](https://github.com/Zero-State-LLC/zzthat/issues/24) TG3 capture | Grammar and both adapters are built. The qualification receipt (T028) and the promotion decision (T029) are not. |
| [#26](https://github.com/Zero-State-LLC/zzthat/issues/26) TG5 scan | Typed resolve is built. The camera handoff (T040) and a qualification receipt are not. |
| [#48](https://github.com/Zero-State-LLC/zzthat/issues/48) Vision OCR on the macOS runner | Open bug. A green local `swift test` is not that runner. |

## Blocked

| Item | Blocker |
|---|---|
| T028 ZZ-OCR-QUAL-001 receipt | No frozen corpus manifest, no private runner, and the upstream evaluate stage does not run the camera. A `zz-qual quick` row is not that receipt. |
| Phone smoke of the rendered code | `ZZCoreTests` has no host app, so `xcodebuild test` on a paired iPhone cannot run. A Mac `swift test` is not a phone receipt. |
| Merge of [#96](https://github.com/Zero-State-LLC/zzthat/pull/96) | `main` requires `build`, `checks`, and `android`, plus one independent approval. Admins are not exempt. Head `926a53d` is rebased onto `fc18531`. The Codex count-equality finding is unanswered by geometry. |

## Synthetic bench, not a receipt

Recorded in [docs/xtech-emulated/summary.md](docs/xtech-emulated/summary.md). Font renders on a flat white field, not handwriting. Apple Vision on this Mac, `zz-qual` from `21aeba8`: exact 30/720, wrong-link 0. ML Kit on emulator-5554: exact 0/720, wrong-link 0. Check-word simulation: wrong-link 0/9595. These numbers are not T028.

## Backlog

Open issues that are not the capture residual above.

| Issue | Title |
|---|---|
| [#71](https://github.com/Zero-State-LLC/zzthat/issues/71) | RM-125 re-roll UI |
| [#70](https://github.com/Zero-State-LLC/zzthat/issues/70) | Show zzsupport@zer0state.com |
| [#69](https://github.com/Zero-State-LLC/zzthat/issues/69) | Early-release labeling |
| [#68](https://github.com/Zero-State-LLC/zzthat/issues/68) | 18+ gating and store age ratings |
| [#66](https://github.com/Zero-State-LLC/zzthat/issues/66) | Sign in with Apple beside Google |
| [#50](https://github.com/Zero-State-LLC/zzthat/issues/50) | Store compliance for v1.1 |
| [#47](https://github.com/Zero-State-LLC/zzthat/issues/47) | Privacy link points at a missing page |
| [#30](https://github.com/Zero-State-LLC/zzthat/issues/30) | TG9 store readiness |
| [#29](https://github.com/Zero-State-LLC/zzthat/issues/29) | TG8 accounts, privacy, permissions |

## Done

Merged or closed completed. Not a release.

| Item | Evidence |
|---|---|
| Qualification harness | [#94](https://github.com/Zero-State-LLC/zzthat/pull/94). `zz-qual` and `scripts/qual-android.sh`. Not a receipt. |
| End-to-end at the pin | [#95](https://github.com/Zero-State-LLC/zzthat/pull/95), on `main` before the alert-snapshot merge. |
| Alert snapshots | [#92](https://github.com/Zero-State-LLC/zzthat/pull/92), `main` at `631585d`. |
| Create | [#25](https://github.com/Zero-State-LLC/zzthat/issues/25) closed completed 2026-10-17. Live mint still waits on the server. |
| Share and manage | [#27](https://github.com/Zero-State-LLC/zzthat/issues/27) closed completed 2026-10-17. |
| Offline cache | [#28](https://github.com/Zero-State-LLC/zzthat/issues/28) closed completed 2026-10-17. |

## What not to cite

A rendered `zz-copper-lantern-sky-zz` passes as a smoke on a Mac and on emulator `emulator-5554`. Confirm-only turns that Accept into Clarify. A wrap onto a second baseline is not paired. Handwritten photos are a fail-closed stress probe. This repo does not claim an accuracy percentage.
