# Design consumption: zzThat apps

The apps do not define a visual system or their own sentences. Colors, type, spacing, fonts, logos, strings, and screen patterns come from [Zero-State-LLC/zzthis](https://github.com/Zero-State-LLC/zzthis) at one pin: `6b9c37973a463064b448a585153fdaf72d230c9b`, recorded in `contracts/ZZTHIS-API-PIN`. The same pin covers the OpenAPI file, the shared test vectors, and the wordlist. It is a pinned copy, not a git submodule. Do not track `main`.

## Sync

`scripts/sync-zzthis.sh <sha>` (task T005) does the whole sync:

1. Download zzThis `scripts/pin-zzthis.sh` at that sha and run it into a temporary folder. If the pinned commit has no `pin-zzthis.sh` yet, run `scripts/pin-design.sh` at that sha and download the other paths below directly from `https://raw.githubusercontent.com/Zero-State-LLC/zzthis/<sha>/`.
2. Copy each file in the table into place.
3. Generate the string catalogs from `copy.json` (runtime.md, Localization).
4. Write the sha to `contracts/ZZTHIS-API-PIN` and to `apps/DESIGN-PIN`, one line each, the same full sha.

`pin-design.sh` at this pin takes a relative destination inside the checkout. It rejects an absolute path and any `..` segment, and it writes `PIN` inside that directory.

CI runs the sync at the committed pin and fails on any difference (SC-10). To move the pin, run the script with the new sha and commit everything it changed in one pull request.

| From zzThis at the pin | Into zzThat |
|---|---|
| `design/generated/Tokens.swift` | `apps/ios/Packages/ZZDesign/Sources/ZZDesign/Tokens.swift` |
| `design/generated/Tokens.kt` | `apps/android/core-design/src/main/kotlin/llc/zerostate/zzthis/design/Tokens.kt` |
| `design/fonts/` (IBM Plex TTF files and `OFL.txt`) | `apps/ios/zzThat/Resources/Fonts/`, and `apps/android/core-design/src/main/res/font/` with lowercase names and underscores |
| `design/brand/logo-on-light.svg`, `zzthis-logo-on-light.png` | iOS asset catalog and Android drawables, light |
| `design/brand/logo-on-dark.svg`, `zzthis-logo-on-dark.webp` | iOS asset catalog and Android drawables, dark |
| `design/brand/mark-zz-code.webp` | The placeholder app icon: the mark centered on the paper color (design/UX.md, App icon; ZQ25) |
| `design/copy.json` | `apps/ios/zzThat/Resources/Localizable.xcstrings` and `apps/android/app/src/main/res/values/strings.xml`, generated |
| `specs/005-v1-api/openapi.yaml` | `contracts/zzthis-v1.openapi.yaml`, and `apps/ios/Packages/ZZAPI/Sources/ZZAPI/openapi.yaml` for the build plugin |
| `specs/003-wordlist-checkword/vectors.json` | `contracts/vectors.json` |
| `packages/zz-core/wordlists/*.txt` | `contracts/wordlists/`, bundled in both apps |

`tokens.json` stays in zzThis. The apps compile the Swift and Kotlin files, not the JSON.

The fonts, `pin-zzthis.sh`, and the wordlist files arrive with the zzThis build (zzThis spec 005 T029, T030, and T033). In the one-shot build, zzThat pins the zzThis build branch head, then moves the pin to the zzThis merge commit before it merges (zzThis `docs/ONE-SHOT-BRIEF.md`).

## Patterns

zzThis `design/UX.md` is the pattern list. Every screen in [runtime.md](runtime.md) names its pattern, and none is a TODO. Do not invent a local pattern, a second palette, or a sentence. If a screen needs something UX.md does not name, open an issue in zzThis and leave the screen on its closest named pattern.
