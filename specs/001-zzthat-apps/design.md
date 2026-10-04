# Design consumption: zzThat apps

The apps do not define a visual system. Colors, type, spacing, logos, icons, and screen patterns come from [Zero-State-LLC/zzthis](https://github.com/Zero-State-LLC/zzthis) under `design/`.

## TODO

Checked 2026-10-04: zzthis `main` at `0a5c9b93e8f8266ff9d0855f4afe2dafbe1d26d7` has no `design/` tree, and no other branch on that remote has one. A sibling change is expected to add it. Until `design/README.md` exists in zzthis, this repo does not copy tokens, generate stand-in Swift or Kotlin, or add logo or icon files.

Task: T005 in [tasks.md](tasks.md). Issue: [#22](https://github.com/Zero-State-LLC/zzthat/issues/22).

## What zzthis is expected to publish

Danny named the contents. The paths inside `design/` are whatever `design/README.md` documents. Do not guess filenames beyond that README.

- Tokens as JSON
- Generated Swift and generated Kotlin from those tokens
- Logo and icon masters
- A UX patterns document

## How this repo consumes it

Follow the method `design/README.md` documents. That is either a git submodule or a sync script. Do not pick one before that file says which.

When the README lands:

1. Pin the zzthis commit in this repo's design sync the same way the spec pin is pinned.
2. Run the documented sync. Commit only the files that README says to commit. Generated Swift lands in the iOS design module. Generated Kotlin lands in the Android design module. See [runtime.md](runtime.md).
3. Point every screen in runtime.md at a pattern name from the UX patterns document. If a screen has no pattern yet, leave the TODO on that screen. Do not draw a local substitute.
4. Delete this TODO only in the pull request that performs the first successful sync.

## Rules until then

- No color values, type ramps, or spacing scales in zzThat specs, READMEs, or app source.
- No bitmap or vector logo, app icon, or glyph committed here.
- Screens still name their job and their states in runtime.md. The visual treatment waits.
- The marketing site's CSS tokens in zzthis `src/styles/tokens.css` are the site's tokens. They are not a substitute for `design/`.
