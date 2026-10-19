# Sources

Timestamp of this note: 2026-10-18 22:10 PDT. Numbers below were recounted from the CSVs in this folder after the runs, not copied from a harness percent line.

`/workspace/deliverables/zz-xtech-metrics/emulated/` is not writable on this machine. These files are in `/Users/appliedalchemylabs/Developer/zz-xtech-metrics/emulated/`. Images and audio stayed in `/Users/appliedalchemylabs/.hermes/cache/scratch/zz-xtech/` and are not in git.

## Shared

- zzThat pin of zzThis: `08a3c26f78004e885c0405e662135112133144ed`, file `contracts/ZZTHIS-API-PIN`.
- Apple Vision binary: worktree at that main commit, `apps/ios/Tools/ZZQual/.build/release/zz-qual`, built during this run. Rows: `track-a-ios-main-rows.csv`.
- An earlier local binary from 2026-10-18 20:09 PDT, which includes an unmerged hyphen-box change, was also run. Those rows are `track-a-ios-rows.csv`. They are not the shipped reader. Do not cite them.
- Android: `scripts/qual-android.sh quick` on `emulator-5554`, Android 16, `sdk_gphone64_arm64`. Rows: `track-a-android.txt`.
- Seed for codes and augmentations: `20261018`.
- Wilson interval uses the normal 97.5% quantile 1.959963984540054, then clamps to [0, 1].

## Track A

- Script: `/Users/appliedalchemylabs/.hermes/cache/scratch/zz-xtech/track_a_render.py`.
- Codes: first 60 distinct originals from `track-b-checkword.csv`, also `codes.csv`.
- Fonts, OFL, downloaded from `google/fonts` at `main` through the GitHub API: Indie Flower, Patrick Hand, Shadows Into Light, Reenie Beanie, Caveat, Covered By Your Grace. Homemade Apple was requested and the `ofl/homemadeapple` path returned 404, so Covered By Your Grace was the sixth font.
- Surface: flat white only. No CC0 texture was downloaded.
- Augmentations, one per code-font pair, assigned by `(code_id + font_index) mod 6`. Parameters are in `track-a-render-log.csv`.
- Joined attempts: `track-a-attempts.csv`. `passes_check_word` is 1 only when the harness result is `exact` or `FALSE-VALID`. `wrong_link` is `FALSE-VALID`. `unresolved` is an empty read column.

## Track B

- Script: `/Users/appliedalchemylabs/.hermes/cache/scratch/zz-xtech/track_b.py`.
- Algorithm copied from `apps/ios/Packages/ZZCore/Sources/ZZCore/CheckWord.swift`: check index `(d1 + 2 * d2) mod N`, issuer skips `d1 == d2`, `d2 == 0`, `(d1 + d2) % N == 0`, and a check word equal to either data word.
- List: `contracts/wordlists/proto-v0.txt`. Script counted 2663 non-empty lines and checked that the length is prime.
- Fixture self-check: fixture-7 indexes river=4, falcon=6, sky=2, `(4 + 2*6) mod 7 = 2`.
- Rows: `track-b-checkword.csv`. 12000 rows. Applied 9595. `no-neighbor` 2400. `no-target` 5. Wrong-link 0.
- The script's edit-distance-1 scan recorded 0 neighbors, so that arm did not change any code.

## Track C

- Engine: `/usr/bin/say`, voices Samantha and Fred, default rate. An earlier Alex pass produced byte-identical audio to Samantha and was discarded.
- Durations from `afinfo` "estimated duration". Rows: `track-c-airtime.csv`. 48 clips. Identical Samantha/Fred pairs: 0.
- Document strings are synthetic 14-character tokens, not issued document numbers.

## Track E

- Mac `swift test` in `apps/ios/Packages/ZZCore`, filter of three rendered tests, 3 passed, 0 failed, platform `arm64e-apple-macos14.0`, host macOS 26.3.1. This is not an iOS Simulator run.
- Android `ReadPipelineInstrumentedTest` on `suas-test` / `emulator-5554` / Android 16. XML: 3 tests, 0 failures. Times: renderedCodeYieldsThatOneCandidate 2.129 s, aCodeAcrossTwoBaselinesIsNotPaired 0.340 s, twoCodesWaitForSelection 0.224 s.
