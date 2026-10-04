# Decisions: zzThat apps

Decided 2026-10-04 from the zzThis pin `0a5c9b93e8f8266ff9d0855f4afe2dafbe1d26d7`, from zzThis issues #57 (Q33) and #58 (Q39) as those issues state their defaults, and from native thin-client practice where zzThis is silent. This repo does not change zzThis.

Create stays gated until the zzThis resolver adds free public codes (zzThis spec 002 FR-016, a v2 item). The proposed HTTP shapes in [spec.md](spec.md) are a proposal for that server. They are not implemented here.

| Issue | ID | Decision |
|---|---|---|
| [#1](https://github.com/Zero-State-LLC/zzthat/issues/1) | ZQ1 | iOS and Android together in this repo. The later web client is built in [zzthis](https://github.com/Zero-State-LLC/zzthis), which is also the central server. The current zzthis site is the marketing page for zzThis. |
| [#2](https://github.com/Zero-State-LLC/zzthat/issues/2) | ZQ2 | Launch means the first public App Store release and the first public Play release, together. "Try zzThat" links to the zzThis marketing page at `https://zero-state-llc.github.io/zzthis/`. This repo does not add the button. |
| [#3](https://github.com/Zero-State-LLC/zzthat/issues/3) | ZQ3 | Wait. Create stays unavailable until the zzThis server adds the free public scope. |
| [#4](https://github.com/Zero-State-LLC/zzthat/issues/4) | ZQ4 | No duplicate public codes and no location permission. Uniqueness is per scope. GPS priority stays v2. |
| [#5](https://github.com/Zero-State-LLC/zzthat/issues/5) | ZQ5 | The resolved page is an in-app title and body. No phone field. |
| [#6](https://github.com/Zero-State-LLC/zzthat/issues/6) | ZQ6 | A public code resolves with no account. Writes require sign-in. |
| [#7](https://github.com/Zero-State-LLC/zzthat/issues/7) | ZQ7 | A free account is Sign in with Apple or Sign in with Google. The server stores the id and the provider. |
| [#8](https://github.com/Zero-State-LLC/zzthat/issues/8) | ZQ8 | A report action is in. No on-device block list. No personal data in the code string. |
| [#9](https://github.com/Zero-State-LLC/zzthat/issues/9) | ZQ9 | Share the canonical code as text. `share.url` stays null. |
| [#10](https://github.com/Zero-State-LLC/zzthat/issues/10) | ZQ10 | No voice and no microphone in the first release. Later voice, if any, stays on the device. |
| [#11](https://github.com/Zero-State-LLC/zzthat/issues/11) | ZQ11 | The server chooses the words for every code, including free public, matching zzThis spec 002 US2. Create calls mint and shows the wordlist code and its check word. "Get different words" is capped at 3 re-rolls. The person-written claim path is dropped. |
| [#12](https://github.com/Zero-State-LLC/zzthat/issues/12) | ZQ12 | The useful page is a screen in the app. |
| [#13](https://github.com/Zero-State-LLC/zzthat/issues/13) | ZQ13 | No live-code suggestions. Exact match. |
| [#14](https://github.com/Zero-State-LLC/zzthat/issues/14) | ZQ14 | The retry consent sentence in spec.md FR-005. No training use. |
| [#15](https://github.com/Zero-State-LLC/zzthat/issues/15) | ZQ15 | Listing text is the everyday-use sentences. Apple 12+ and Play Teen. Do not submit. |
| [#16](https://github.com/Zero-State-LLC/zzthat/issues/16) | ZQ16 | A local draft is allowed if labeled "Not a code yet." It is not issued offline. |
| [#17](https://github.com/Zero-State-LLC/zzthat/issues/17) | ZQ17 | Swift and SwiftUI. Kotlin and Jetpack Compose. |
| [#18](https://github.com/Zero-State-LLC/zzthat/issues/18) | ZQ18 | Native Sign in with Apple and Sign in with Google, then the provider ID token with a server nonce (`POST /v1/auth/nonce`, then `POST /v1/auth/token`). Danny said yes to zzThis Q66 [DANNY 2026-10-04]. The PKCE authorization-code exchange is dropped. The app sends no redirect URI. Access tokens last 15 minutes. Refresh tokens rotate. |
| [#19](https://github.com/Zero-State-LLC/zzthat/issues/19) | ZQ19 | `/v1`, OpenAPI at `GET /v1/openapi.json`, header `X-ZZ-Contract: 1`. |
| [#20](https://github.com/Zero-State-LLC/zzthat/issues/20) | ZQ20 | 429 plus `Retry-After`. Limits in spec.md DEP-010. |
| [#21](https://github.com/Zero-State-LLC/zzthat/issues/21) | ZQ21 | iOS 17 and later. Android 8.0, API 26, and later. |
| [#32](https://github.com/Zero-State-LLC/zzthat/issues/32) | ZQ22 | Same answer as ZQ18. Danny said yes to zzThis Q66 [DANNY 2026-10-04] ([zzThis #68](https://github.com/Zero-State-LLC/zzthis/issues/68)): native provider sign-in, a server nonce, and the provider ID token. |
| [#33](https://github.com/Zero-State-LLC/zzthat/issues/33) | ZQ23 | Android offers Google only in v1, because Android has no Sign in with Apple library. An Apple account made on iPhone cannot sign in on Android. Owner: Michael (zzThis Q67, [zzThis #69](https://github.com/Zero-State-LLC/zzthis/issues/69)). Default: yes. |
| [#34](https://github.com/Zero-State-LLC/zzthat/issues/34) | ZQ24 | Bundle and application id `llc.zerostate.zzthat`, with `.local` and `.demo` suffixes for the other builds. An id cannot change after it is registered. Owner: Danny. Default: as written. |
| [#35](https://github.com/Zero-State-LLC/zzthat/issues/35) | ZQ25 | Before store submission Michael supplies a square app icon and a support contact, and Danny publishes the privacy policy URL. Builds use a placeholder icon. Owner: Michael and Danny. Default: no submission without them. |
| [#36](https://github.com/Zero-State-LLC/zzthat/issues/36) | ZQ26 | Offline reading lasts 360 seconds after the last online read, because the Q26 header allows 60 seconds plus 300 seconds stale. That is short for a lost-pet flyer read later offline. A longer offline time needs a server change by Danny. Owner: Michael. Default: keep 360 seconds. |

Corrected the same day, after the first close: ZQ1 and ZQ2 name zzthis as the web client, the central server, and the marketing page the launch link uses. ZQ11 is reversed to server-chosen words. ZQ18 is amended to native provider sign-in plus an ID token with a server nonce.

The app cache follows zzThis spec 005 FR-018 [DANNY 2026-10-04] (Q26) and FR-028: honor `Cache-Control`, which is `public, max-age=60, stale-while-revalidate=300` for an active reusable public unauthenticated resolve, and `no-store` on every other `/v1` response. FR-041 sets the app's 60 seconds online and 360 seconds offline (ZQ26). A photo upload, when `photo_reads` is true, is JPEG only and at most 8 MiB. While Danny chooses the image model (Q18), `GET /v1` reports `photo_reads: false`, the photo read returns `not-ready`, and the apps do not offer the upload. Sign-in follows zzThis spec 005 FR-020 and FR-021 [DANNY 2026-10-04] (Q66): the platform's own Apple or Google sign-in, a server nonce, and the provider ID token.

ZQ22 to ZQ26 were added on 2026-10-04 with the zzThis pin `484100308376bce01c88f30f871dff3e13082fe0`, from the one-shot build review (zzThis `specs/analysis-2026-10-04.md`). ZQ22 is decided with ZQ18. ZQ23 to ZQ26 still wait on the owners named, and the specs follow their defaults until then.

Low-confidence defaults, still chosen: the age rating (#15), the rate-limit numbers (#20), and the re-roll cap of 3 (#11). zzThis Q18, Q25 (no device at all), Q30 to Q32, Q36, and Q37 stay open in zzThis. This repo does not answer them.
