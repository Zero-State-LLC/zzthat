# Synthetic render bench (emulated, not handwriting)

Input label: SYNTHETIC. These are font renders on a flat white field. They are not handwriting, not field photos, and not a live camera.

The requested photographed surfaces were not run. See GAPS.md.

Reader notes:

- Apple Vision is `zz-qual` built from `origin/main` `21aeba859eaf3ce49e3797ca4a7d46d424d9ff9e` on this Mac (macOS 26.3.1). That is a Mac host, not a phone and not an emulator.
- ML Kit is `scripts/qual-android.sh quick` on emulator `emulator-5554`, product `sdk_gphone64_arm64`, Android 16. Reader label: EMULATED.
- Word list: `contracts/wordlists/proto-v0.txt`, 2663 words. Seed `20261018`.
- Design: 60 issued codes, 6 OFL fonts, one surface (`flat-white`), a clean image plus one augmentation per code-font pair. 720 images per reader. Augmentations are not balanced across fonts, so condition rows are not a font-controlled comparison. The clean-by-font table is.

## Apple Vision, macOS host, SYNTHETIC input

| Surface | Condition | Exact | Percent | Wilson 95% low | Wilson 95% high | Wrong-link | Unresolved |
|---|---|---|---|---|---|---|---|
| flat-white | clean | 15/360 | 4.17 | 0.0254 | 0.0676 | 0 | 326 |
| flat-white | blur | 2/60 | 3.33 | 0.0092 | 0.1136 | 0 | 58 |
| flat-white | gamma | 1/60 | 1.67 | 0.0029 | 0.0886 | 0 | 56 |
| flat-white | glare | 5/60 | 8.33 | 0.0361 | 0.1807 | 0 | 54 |
| flat-white | jpeg | 2/60 | 3.33 | 0.0092 | 0.1136 | 0 | 54 |
| flat-white | perspective | 5/60 | 8.33 | 0.0361 | 0.1807 | 0 | 50 |
| flat-white | smudge | 0/60 | 0.00 | 0.0000 | 0.0602 | 0 | 59 |
| flat-white | all | 30/720 | 4.17 | 0.0293 | 0.0589 | 0 | 657 |

Clean by font, Apple Vision, 60 images each:

| Font | Exact |
|---|---|
| Caveat | 0/60 |
| Covered By Your Grace | 0/60 |
| Indie Flower | 6/60 |
| Patrick Hand | 6/60 |
| Reenie Beanie | 1/60 |
| Shadows Into Light | 2/60 |

Sum of those font rows is 15/360, which matches the clean row. Unresolved on the all row is the sum of the condition unresolved counts, 657.

## ML Kit, emulator-5554, EMULATED reader, SYNTHETIC input

Exact 0/720. Wrong-link 0. Unresolved 687. Non-empty wrong reads 33. Wilson 95% for 0/720 is 0.0000 to 0.0053. Bands: no-fiducial 449, missing-opening-fiducial 220, clarify 33, missing-closing-fiducial 12, unsupported-geometry 3, abstain 2, partial-fiducial 1. Sum 720.

## Check word, SYNTHETIC

12,000 issued codes, seed 20261018. 9,595 perturbations applied. Wrong-link 0. Fail-closed 9,595. Wilson 95% for 0/9595 is 0.00000000 to 0.00040020. Analytic single on-list word substitution wrong-link is 0, because `(d1 + 2*d2) mod 2663` is one-to-one in each argument and 2663 is prime. A random triple passes the check with probability 1/2663 = 0.00037552. This mix did not sample random triples, so that figure is not the simulation rate.

## TTS airtime, EMULATED, not human timing

Engine `macOS say`, default rate. Voices Samantha and Fred. 12 word codes and 12 synthetic 14-character strings, each voice. Speech-to-text was not run.

| Kind | Voice | n | Median seconds | Min | Max |
|---|---|---|---|---|---|
| word-code | Samantha | 12 | 2.468798 | 2.143719 | 2.573288 |
| word-code | Fred | 12 | 2.496871 | 2.212426 | 2.665215 |
| document-number | Samantha | 12 | 5.022676 | 4.736599 | 5.113061 |
| document-number | Fred | 12 | 5.716667 | 5.296961 | 5.916009 |

## What did not run

Staging lookups. Photographed surfaces. Tesseract. Whisper round-trip. iOS Simulator app suite. See GAPS.md.
