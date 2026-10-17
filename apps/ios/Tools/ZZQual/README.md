# zz-qual: the iOS qualification harness

`zz-qual` runs the iOS reader the app ships (`ZZCore` `CapturePipeline` with the Apple Vision adapter) over a folder of photos on a Mac, offline (tasks.md T027, T023c). Every complete pair in a photo is read on its own, with no selection by truth. Partial and bare marks are not read. Photos are read in place, never copied, uploaded, or written anywhere.

The photo corpus is private (zzThis `specs/004-capture/qualification.md`). Keep it out of this repo.

## Build

```sh
cd apps/ios/Tools/ZZQual && swift build -c release
```

## Quick check: photos and their codes

`truth.csv` has a header and one row per photo: the file name, then the code written in it. Leave the code empty for a photo with no code. Separate several codes with spaces.

```csv
file,code
kitchen-01.jpg,zz-copper-lantern-sky-zz
blank-02.jpg,
```

```sh
.build/release/zz-qual quick --images ~/zz-photos --truth ~/zz-photos/truth.csv \
  --wordlists ../../../../contracts/wordlists --list fixture-7 --out rows.csv
```

It prints one line per photo, then:
- exact-code accuracy;
- false-valid count: a code that parses and passes its check word but is not the one written, the primary safety metric (T028);
- the band per photo.

Camera bands are confirm-only until a PASS receipt, so a correct reading shows `clarify`. `--list` names the word list in `--wordlists` that the codes come from.

## Formal run: ZZ-OCR-QUAL-001

```sh
.build/release/zz-qual run --manifest manifest.json --images ~/zz-photos \
  --candidate-bundle candidate-bundle.json --device-matrix device-matrix.json \
  --device-entry <device_matrix_entry_id> --out adapter-results.json \
  --wordlists ../../../../contracts/wordlists --list <wordlist_version>
```

- **Manifest:** `manifest.schema.json` at the pinned zzThis commit. Images are matched to samples by `asset_sha256`, so file names don't matter.
- **Output:** `adapter-results.json`, valid against zzThis `adapter-result.schema.json`. `candidate_bundle_sha256` is the RFC 8785 hash of the bundle.
- **Scoring:** `npm run qual:ocr` in zzThis scores the output, with the other inputs that `specs/004-capture/qualification/local-runner.md` lists.
- **The manifest needs more than the code per photo:** each sample also needs its fiducial boxes, ROI polygons, case kind, stress tags, writer group, and split.

## Android

`scripts/qual-android.sh` runs the same two modes with the shipped ML Kit pipeline on a connected device or emulator, through `QualHarnessTest`. It takes the same `truth.csv`, manifest, candidate bundle, and device matrix:

```sh
scripts/qual-android.sh quick ~/zz-photos ~/zz-photos/truth.csv fixture-7
scripts/qual-android.sh run ~/zz-photos manifest.json candidate-bundle.json device-matrix.json <entry> adapter-results.json fixture-7
```

The photos go only to the demo debug app's private folder on that device, and the script deletes them when it ends.
