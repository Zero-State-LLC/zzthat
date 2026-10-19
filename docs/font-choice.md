# Font choice for printed zz codes

Status: recommendation only. This is not a decision. The font choice is Danny's call. Nothing in this note changes a font anywhere in zzThat or zzThis.

Written 2026-10-18. Research only: public sources and the repo's own notes. No device was used.

## What the code has to survive

A zz code is lowercase words joined by hyphens, such as `zz-river-falcon-sky-zz`. The word list (`contracts/wordlists/proto-v0.txt`) is lowercase a to z only. It has no digits. A code is shown on screen, printed or copied by hand, then read back by Apple Vision on iOS or ML Kit on Android.

So the glyph pairs that matter are the ones a reader or a person can confuse inside lowercase words: l, i and 1; rn and m; z and 2 (the `zz` marks must never be read as `22`); o and 0; s and 5. Digit-only confusions matter only because a reader may return a digit where a letter was printed.

## Ranked recommendation

1. Keep IBM Plex Mono. License: SIL Open Font License 1.1, with Reserved Font Name "Plex" (a modified copy cannot use that name). It is already the mono font in the pinned zzThis design files and in both apps, and its license notice already ships. Plex's lowercase l has a tail, its capital I has a crossbar, and its zero is dotted, so the pairs above are drawn apart. Switching would cost design and license work, and no source found here shows that another font reads better on Vision or ML Kit.
2. Atkinson Hyperlegible Mono. License: SIL Open Font License 1.1. First released 20 November 2024 by Braille Institute. It was drawn to keep letterforms distinct, which makes it the strongest candidate for people reading and copying codes by hand. The legibility evidence is the designers' own testing, not a peer-reviewed study, and no OCR measurement of it was found.
3. OCR-B. Designed for machine reading (ISO 1073-2). The free version from Matthew Skala is public domain for his work plus a permissive grant from Norbert Schwarz for the Metafont source. It is not OFL, and an older note in that package raises a question about rights in the letterforms themselves, so a license check would come first. It was made for the template readers of its era. No source found here shows that it helps current neural readers, and its lowercase is less refined than the two above.

B612 Mono (OFL, from Airbus and ENAC cockpit research on glyph distinction) is a reasonable fourth option if a second hyperlegible face is wanted.

## What the synthetic bench does and does not show

The synthetic bench in open PR [#99](https://github.com/Zero-State-LLC/zzthat/pull/99) (`docs/xtech-emulated/` on that branch) rendered six handwriting-style OFL fonts, such as Caveat and Indie Flower, on a flat white field. It did not render IBM Plex Mono, Atkinson Hyperlegible Mono, OCR-B, or any other print font. Its Apple Vision reader ran on a Mac host, not a phone. Its ML Kit reader ran on an emulator, and most ML Kit misses were marker failures (no `zz` marks found) rather than misread letters. Wrong-link was 0 on both readers and in the check-word simulation.

What it cannot show: how any candidate print font reads, how paper, ink, light, focus or real handwriting change the result, or how a phone camera behaves. It is synthetic and is not a receipt. It does not stand in for T028 or ZZ-OCR-QUAL-001.

## Size guidance from outside sources

Public guidance is about size, not typeface.

- ML Kit asks for each character to be at least 16 by 16 pixels, with generally no benefit above 24 by 24 pixels.
- Apple Vision `VNRecognizeTextRequest` skips text smaller than `minimumTextHeight`, which defaults to 1/32 of the image height.
- The shipped iOS readers set `usesLanguageCorrection = false`, so Vision's `customWords` list is not in play.

## Hyphens

Vision returns no character box for a hyphen. A hyphenated line can therefore have fewer boxes than characters, and the reader failed to place the `zz` marks. PR [#96](https://github.com/Zero-State-LLC/zzthat/pull/96) is the iOS fix that maps boxes onto letters when the counts match and fails closed otherwise. Font choice does not remove this. Any test of fonts should run on a build that includes or excludes #96 on purpose, and say which.

## Cheapest real-device test

1. Take the 60 codes from the bench and print each one in all three fonts (IBM Plex Mono, Atkinson Hyperlegible Mono, OCR-B) at two sizes, on plain paper. That is 360 code lines, so it needs a few pages, not one.
2. Photograph the pages with Danny's iPhone in two kinds of light, such as daylight by a window and indoor lamp light.
3. Run `zz-qual quick` on a Mac over those photos with each code's expected text. This uses the shipped `CapturePipeline` and Vision adapter.
4. Run `scripts/qual-android.sh quick` over the same photos.

Android is only partly covered. Danny has no Android phone, so the ML Kit run is on an emulator: the photos are real, the reader is emulated, and the camera path is not tested. Like the bench, the result of this test is not a T028 receipt.

## Sources

- ML Kit text recognition, input image guidelines: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Apple Vision `minimumTextHeight`: https://apple-docs.everest.mt/docs/vision/vnrecognizetextrequest/minimumtextheight/
- Apple Vision `customWords`: https://developer.apple.com/documentation/vision/vnrecognizetextrequest/customwords
- Tesseract, improving output quality: https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html
- IBM Plex distinct zero discussion: https://github.com/IBM/plex/discussions/533
- Atkinson Hyperlegible Mono: https://github.com/googlefonts/atkinson-hyperlegible-next-mono
- Atkinson Hyperlegible Mono OFL: https://github.com/google/fonts/blob/main/ofl/atkinsonhyperlegiblemono/OFL.txt
- Notes on how Atkinson Hyperlegible was developed and tested: https://www.maxkohler.com/notes/2021-02-16-atkinson-hyperreadable/
- OCR-B free fonts: https://tsukurimashou.org/ocr.php.en
- OCR-B license detail (Debian): https://metadata.ftp-master.debian.org/changelogs/main/f/fonts-ocr-b/fonts-ocr-b_0.3.1~dfsg-1_copyright
- B612: https://lii.enac.fr/projects/definition-and-validation-of-an-aeronautical-font/
