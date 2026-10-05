# Design consumption: zzThat apps

The apps do not define a visual system or their own sentences. Colors, type, spacing, fonts, the zz code mark, strings, and screen patterns come from [Zero-State-LLC/zzthis](https://github.com/Zero-State-LLC/zzthis) at one pin: `14039ed168da3a851900566ace64c05f34e821a6`, recorded in `contracts/ZZTHIS-API-PIN`. The same pin covers the OpenAPI file, the shared test vectors, and the wordlist. It is a pinned copy, not a git submodule. Do not track `main`.

## Sync

`scripts/sync-zzthis.sh <sha>` (task T005) does the whole sync:

1. Download zzThis `scripts/pin-zzthis.sh` at that sha and run it into `.zzthis-pin/`, a relative folder in the checkout that `.gitignore` lists. The pin scripts accept only a relative destination and a full 40-character sha. If the pinned commit has no `pin-zzthis.sh` yet, run `scripts/pin-design.sh` at that sha and download the other paths below directly from `https://raw.githubusercontent.com/Zero-State-LLC/zzthis/<sha>/`.
2. Copy each file in the table into place.
3. Generate the string catalogs from `copy.json` (runtime.md, Localization).
4. Write the sha to `contracts/ZZTHIS-API-PIN` and to `apps/DESIGN-PIN`, one line each, the same full sha.
5. Replace the old sha, which the script reads from `contracts/ZZTHIS-API-PIN` before step 4, with the new sha in every file that states the current pin: `specs/001-zzthat-apps/spec.md`, `runtime.md`, `design.md`, `plan.md`, `README.md`, `specs/README.md`, and the Source pin line of `.specify/memory/constitution.md`. On that line, when the new sha differs from the old one, it also replaces the date in parentheses after the sha with the day of the sync (UTC, `YYYY-MM-DD`) [INFERRED]. Do not rewrite `decisions.md` or `checklists/requirements.md`, which record the pin used at the time. `lint-specs.py` checks spec.md, runtime.md, and design.md. At the committed pin this step replaces the sha with itself and leaves the date alone, so the SC-10 check still finds no difference.

`pin-design.sh` at this pin takes a relative destination inside the checkout. It rejects an absolute path and any `..` segment, and it writes `PIN` inside that directory.

CI runs the sync at the committed pin and fails on any difference (SC-10). The check compares copied files byte for byte. It compares generated catalogs by running the generator again. The placeholder icon is outside the check (see the placeholder icon paragraph below). To move the pin, run the script with the new sha, run `python3 scripts/lint-specs.py`, and commit everything it changed in one pull request.

| From zzThis at the pin | Into zzThat |
|---|---|
| `design/generated/Tokens.swift` | `apps/ios/Packages/ZZDesign/Sources/ZZDesign/Tokens.swift` |
| `design/generated/Tokens.kt` | `apps/android/core-design/src/main/kotlin/llc/zerostate/zzthis/design/Tokens.kt` |
| `design/fonts/*.ttf` | `apps/ios/zzThat/Resources/Fonts/`, names unchanged, each listed under `UIAppFonts`. `apps/android/core-design/src/main/res/font/`, renamed to lowercase with each hyphen as an underscore (`IBMPlexSans-Regular.ttf` becomes `ibmplexsans_regular.ttf`) [INFERRED]. Only TTF files go in `res/font`. |
| `design/fonts/OFL.txt` and the zzThis root `NOTICE` | `apps/ios/zzThat/Resources/Licenses/` and `apps/android/core-design/src/main/assets/licenses/`. Never `res/font`, which accepts only font files. The Licenses screen shows both (runtime.md, Screens). |
| `design/brand/mark-zz-code.webp` | `apps/android/app/src/main/res/drawable/mark_zz_code.webp`, copied. The placeholder app icon (design/UX.md, App icon; ZQ25) is built from it once (see the placeholder icon paragraph below). |
| `design/copy.json` | `apps/ios/zzThat/Resources/Localizable.xcstrings`, `apps/ios/zzThat/Resources/InfoPlist.xcstrings`, and `apps/android/app/src/main/res/values/strings.xml`, generated. `InfoPlist.xcstrings` maps `NSCameraUsageDescription` to `scan.camera_purpose`. The sync also writes that key's English text as the `INFOPLIST_KEY_NSCameraUsageDescription` value in `project.yml`. |
| `specs/005-v1-api/openapi.yaml` | `contracts/zzthis-v1.openapi.yaml`, and `apps/ios/Packages/ZZAPI/Sources/ZZAPI/openapi.yaml` for the build plugin |
| `specs/003-wordlist-checkword/vectors.json` | `contracts/vectors.json` |
| `packages/zz-core/wordlists/*.txt` | `contracts/wordlists/`, bundled in both apps |

`tokens.json` stays in zzThis. The apps compile the Swift and Kotlin files, not the JSON.

The apps do not copy the zzThis logos (`design/brand/logo-on-*.svg`, `zzthis-logo-on-*`), because no screen shows the wordmark.

The placeholder icon is composed once, committed, and kept out of the pin check until Michael's square artwork replaces it (ZQ25) [INFERRED]. Android: a hand-written adaptive icon at `apps/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`. Its background is #F4EFE4, the pinned light paper color (`ColorPaperLight` in Tokens.kt). Its foreground is `@drawable/mark_zz_code` in an `<inset>` of 22% on the left and right and 40.7% on the top and bottom. The 480 by 160 mark then keeps its 3:1 shape, at about 60 by 20 dp of the 108 dp layer, inside the 66 dp safe zone. iOS: one opaque 1024 by 1024 PNG in `Assets.xcassets/AppIcon.appiconset`, made once on macOS: the mark, 720 pixels wide, centered on #F4EFE4. The mark's own background is #fefefe, so a faint band around it is expected.

The fonts come into zzThis `design/fonts/` from the npm packages `@ibm/plex-sans` 1.1.0, `@ibm/plex-sans-condensed` 2.0.0, and `@ibm/plex-mono` 2.5.0, with `OFL.txt` (zzThis spec 005 T030). zzThat copies them from the pin and never downloads fonts itself.

The fonts, `NOTICE`, `pin-zzthis.sh`, and the wordlist files arrive with the zzThis build (zzThis spec 005 T029, T030, and T033). So do the new copy keys `scan.camera_purpose`, `provider.apple`, `provider.google`, `provider.dev`, and `account.licenses`, each marked INFERRED for Michael's review. In the one-shot build, zzThat pins the zzThis build branch head, then moves the pin to the zzThis merge commit before it merges (zzThis `docs/ONE-SHOT-BRIEF.md`).

## Patterns

zzThis `design/UX.md` is the pattern list. Every screen in [runtime.md](runtime.md) names its pattern, and none is a TODO. Do not invent a local pattern, a second palette, or a sentence. If a screen needs something UX.md does not name, open an issue in zzThis and leave the screen on its closest named pattern.
