# Design consumption: zzThat apps

The apps do not define a visual system. Colors, type, spacing, logos, icons, and screen patterns come from [Zero-State-LLC/zzthis](https://github.com/Zero-State-LLC/zzthis) `design/`.

Pin: `497ea0d218ee0e7709c02ca4107846a062ff2ce2` (zzthis branch `cursor/zzthis-practices-design-api-c324`, draft PR #59). `design/README.md` at that commit is the method. It is a pinned copy. It is not a git submodule.

## Sync

From a zzThat checkout:

```sh
PIN=497ea0d218ee0e7709c02ca4107846a062ff2ce2
curl -fsSL "https://raw.githubusercontent.com/Zero-State-LLC/zzthis/${PIN}/scripts/pin-design.sh" -o /tmp/pin-design.sh
sh /tmp/pin-design.sh /tmp/zzthis-design "$PIN"
```

`scripts/pin-design.sh` writes `PIN` inside the destination. zzThat commits that sha as `apps/DESIGN-PIN`, one line, the same full sha. Task T005 does this copy. Do not track `main`.

| Copy from the pin | Commit in zzThat |
|---|---|
| `generated/Tokens.swift` | `apps/ios/zzThat/Core/Design/Tokens.swift` |
| `generated/Tokens.kt` | `apps/android/core-design/src/main/kotlin/llc/zerostate/zzthis/design/Tokens.kt` |
| `brand/logo-on-light.svg` and `brand/zzthis-logo-on-light.png` | iOS and Android logo resources |
| `brand/logo-on-dark.svg` and `brand/zzthis-logo-on-dark.webp` | dark logo resources |
| `brand/mark.svg` and `brand/mark-zz-code.webp` | the app icon source |
| the sha | `apps/DESIGN-PIN` |

`tokens.json` stays in zzthis. The apps compile the Swift and Kotlin files.

`design/UX.md` is the pattern list. Screens in [runtime.md](runtime.md) name the pattern it gives them. A screen it does not name keeps a TODO. Do not invent a local pattern or a second palette.

To move the pin, run the script with the new sha and replace those same files in one pull request.
