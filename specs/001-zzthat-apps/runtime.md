# Runtime: zzThat apps

Feature: [spec.md](spec.md). This file is the build contract for the iOS and Android apps. It does not add application source.
Decisions: [decisions.md](decisions.md). Visuals: [design.md](design.md).
Status: specification, deepened 2026-10-04 for a one-shot build. Danny accepts spec 001 before the trees below are created.

Every named screen uses the pattern in zzThis `design/UX.md` at pin `484100308376bce01c88f30f871dff3e13082fe0`, and every sentence comes from `design/copy.json` at that pin. Do not invent a pattern, a palette, or a sentence.

## Toolchain

Pin exact versions in the project files and in `gradle/libs.versions.toml`, and list them in the PR.

| Platform | Tools |
|---|---|
| iOS | Xcode 26 (26.6 on the build Mac), the Swift 6 toolchain, XcodeGen for the project, Swift Package Manager. Packages: swift-openapi-generator 1.x (build plugin), swift-openapi-runtime, swift-openapi-urlsession, GoogleSignIn-iOS 7.1 or later (for its nonce parameter), swift-snapshot-testing. |
| Android | JDK 17, the Gradle wrapper, the current stable Android Gradle Plugin, Kotlin 2, the Compose BOM, Navigation Compose with type-safe routes, `androidx.credentials` with the Google ID library, ML Kit text recognition (bundled Latin model), DataStore, Retrofit with OkHttp and kotlinx.serialization, the OpenAPI Generator Gradle plugin, Paparazzi. `compileSdk` and `targetSdk` are the level Google Play requires at build time. |

## Project structure

Two native apps. No web target. No server module. No Kotlin Multiplatform shared UI.

```
apps/VERSION                            # one semver line, both apps read it
apps/ios/project.yml                    # XcodeGen; the .xcodeproj is generated, not committed
apps/ios/zzThat/App/                    # entry, tab shell, dependency wiring
apps/ios/zzThat/Features/Scan/
apps/ios/zzThat/Features/Create/
apps/ios/zzThat/Features/Resolve/
apps/ios/zzThat/Features/Codes/
apps/ios/zzThat/Features/Account/
apps/ios/zzThat/Features/Report/
apps/ios/zzThat/Core/Auth/
apps/ios/zzThat/Core/Storage/
apps/ios/zzThat/Resources/              # Localizable.xcstrings (generated), Fonts/, Assets.xcassets
apps/ios/zzThat/Config/                 # Demo, Local, and Release xcconfig files, no secrets
apps/ios/Packages/ZZCore/               # grammar, classifier, check word, matching key, scanner, list loader, read pipeline
apps/ios/Packages/ZZAPI/                # openapi.yaml copy and generator config; code generated at build
apps/ios/Packages/ZZDesign/             # pinned Tokens.swift and font registration
apps/ios/zzThatTests/
apps/ios/zzThatUITests/
apps/android/settings.gradle.kts
apps/android/gradle/libs.versions.toml
apps/android/app/                       # application, navigation, feature screens; flavors demo, local, prod
apps/android/core-api/                  # generated from contracts/zzthis-v1.openapi.yaml at build, not committed
apps/android/core-auth/
apps/android/core-storage/
apps/android/core-grammar/              # plain Kotlin JVM module, same scope as ZZCore
apps/android/core-design/               # pinned Tokens.kt and the fonts in res/font
contracts/zzthis-v1.openapi.yaml        # pinned
contracts/vectors.json                  # pinned
contracts/wordlists/                    # pinned lists
contracts/ZZTHIS-API-PIN                # the one zzThis pin
scripts/sync-zzthis.sh                  # design.md, Sync
```

Minimums: iOS 17, Android 8.0 (API 26) (ZQ17, ZQ21).

## Modules

Each feature calls core modules. Features do not call each other except through navigation.

| Module | Owns | Must not own |
|---|---|---|
| App / `:app` | Process entry, tabs, dependency wiring, build configuration | HTTP, grammar rules, tokens |
| Navigation | The route list below | Screen layout |
| Scan | Camera handoff, candidate list, clarify, typed entry | Resolve rendering |
| Create | Mint, re-roll, write check, unavailable state, draft | A person-typed code string |
| Resolve | Title and body, not-found, share, report entry | Issue |
| Codes | My codes, Code detail, Edit record, revoke | Public resolve rules |
| Account | Sign-in, sign-out, delete, support and privacy links | A profile form |
| Report | The four reasons in FR-035 | A block list |
| API (`ZZAPI`, `:core-api`) | The generated client, the request middleware, the demo implementation | Hand-written request types |
| Auth | Nonce, provider sign-in, token refresh, secure store | A password |
| Storage | Resolve cache, owner list snapshot, draft, last canonical | A second source of truth |
| Core (`ZZCore`, `:core-grammar`) | zzThis Section 2.2a, the spec 003 check word, the spec 004 scanner and bands, the list loader | A second grammar |
| Design (`ZZDesign`, `:core-design`) | The pinned token files and fonts | Values authored in this repo |

## Navigation

Three tabs: Scan, Create, My codes. Account opens from a toolbar item on every tab. Report opens from Resolve.

```
Scan
  take photo -> candidates
    one in Accept -> Resolve
    several -> person picks one -> Resolve
    clarify -> Clarify -> Resolve
    retry -> take photo again
    abstain -> reason, then Type
  camera off, or the person chooses typing -> Type -> Resolve
Resolve
  -> Share (system sheet)
  -> Report
Create
  signed out -> Sign in -> Create
  free_public false, or not-ready -> Unavailable
  offline -> draft, labeled "Not a code yet."
  -> Minted code -> Write check
My codes
  signed out -> Sign in
  -> Code detail -> Edit record
                 -> Revoke confirm
Toolbar
  -> Account -> Delete confirm
```

Back from Resolve returns to the screen that opened it. A revoked code on Code detail does not offer Edit.

## Screens

Each screen implements five states. Strings come from `copy.json`.

| State | Meaning |
|---|---|
| Loading | Waiting on the camera, the reader, disk, or one HTTP call. The previous content stays put if there is any. |
| Empty | Nothing to show yet: no code in the photo, no typed text, no owned codes, no draft. |
| Error | One sentence from the spec error table. No second code is offered (FR-009). |
| Offline | US6. A cached public view is labeled offline. Mint, re-roll, revoke, and record writes are refused. |
| Demo | Fixture content with the demo badge on screen. No network call. The fixture code is not issued. |

| Screen | Job | Empty | Error | Offline | Demo | UX pattern |
|---|---|---|---|---|---|---|
| Scan | Take a photo and list candidates | `scan.no_code`. Offer Type. | `scan.camera_off`, `scan.partial`, `scan.bare`, 429 | Read on the device. Resolve waits. | A fixture photo labeled Demo | Scan with the camera |
| Pick | Choose among several candidates | Not used | Not used | Same list | Fixture candidates | Scan with the camera, step 3 |
| Clarify | Confirm one uncertain word or a check-word mismatch | Not used | `scan.check_mismatch` | Same | Fixture word | Scan with the camera, step 4 |
| Type | Same parser and resolve as Scan (US2a) | `type.empty` | `type.malformed`, 429 | Keep the typed text. Resolve waits. | A fixture string labeled Demo | Scan |
| Resolve | Title and body, or one not-found (US3) | Not used. Not-found is the error row. | `resolve.not_found` | FR-041, then `resolve.not_on_device` | Fixture title and body labeled Demo | Resolve |
| Create | Mint a wordlist code (US1, ZQ11) | `create.signed_out`, or Unavailable | `create.content_refused`, 401, 500 | Draft only, labeled `create.draft_label` | A fixture code labeled Demo, not minted | Create |
| Minted code | The canonical form and the check word, and re-roll | Not used after a 201 | `minted.reroll_cap` keeps the words | Hide re-roll. The words stay. | Demo words. The button does not call. | Minted code |
| Write check | The photo of the handwriting matches the minted code (FR-037) | No photo yet | `writecheck.mismatch`. No upload. | Runs on the device | A fixture photo labeled Demo | Write check |
| My codes | Owner list with titles | `codes.empty`, or `codes.signed_out` | 401, 403, 500 | Last list labeled offline, if any | Fixture rows labeled Demo | My codes |
| Code detail | One owned code: share, edit, revoke | Not used | 404 for a code the server dropped | Last owner row. Writes disabled. | Fixture row labeled Demo | Code detail |
| Edit record | New title and body | `create.title_required` | `error.nothing_saved`, `create.content_refused` | Refuse the save | Demo fields. Save does not call. | Edit record |
| Revoke confirm | One confirm, then revoke | Not used | `error.nothing_saved`. The code stays active. | Refuse | Demo confirm does not call | Revoke confirm |
| Unavailable | Create while `free_public` is false or the issuer is not ready (FR-018) | `create.unavailable` | Not a second error | Same copy | Same copy, labeled Demo | Create |
| Sign in | Provider buttons from discovery (FR-040) | `signin.why` with the buttons | `signin.failed`. No half session. | Refuse sign-in | Buttons labeled Demo. They do not call. | Sign in |
| Account | Provider, sign out, delete, support, privacy | Signed out: go to Sign in | `delete.failed` | Show the cached provider. Delete refused. | Fixture account labeled Demo | Account |
| Delete confirm | `DELETE /v1/me` (FR-034) | Not used | `delete.failed`. The account stays. | Refuse | Demo confirm does not call | Revoke confirm and Delete confirm |
| Report | The reasons in FR-035 | Reason not chosen yet | 429. Never say whether the code exists. | Refuse | Demo send does not call | Report |

Share is the system share sheet with `share.text` only (FR-020). It is not its own screen.

## Build configurations

| | Demo | Local | Release |
|---|---|---|---|
| API | The in-memory demo implementation. No network. | `http://localhost:8787` from the iOS simulator, `http://10.0.2.2:8787` from the Android emulator | `ZZ_API_ORIGIN` build setting, injected at build, not committed |
| Sign-in | Buttons labeled Demo. They do not call. | Developer sign-in (FR-048), plus Apple and Google once client ids are set | Apple and Google only |
| Plain HTTP | None | Only to localhost or 10.0.2.2 (iOS `NSAllowsLocalNetworking`; an Android network security config in the local flavor) | None |
| Bundle and application id | `llc.zerostate.zzthat.demo` | `llc.zerostate.zzthat.local` | `llc.zerostate.zzthat` (ZQ24) |
| Display name | zzThat Demo | zzThat Local | zzThat |

Google client ids and the support contact are build settings with placeholder values until Danny creates the clients and Michael supplies the contact. They are not secrets, but Release values are still injected rather than committed.

## Pinned files

[design.md](design.md) lists every file the sync copies from zzThis and where it lands. Both platforms read `contracts/vectors.json` in their core tests and bundle `contracts/wordlists/` as resources or assets. The iOS build plugin needs its own copy of the OpenAPI file inside the `ZZAPI` target, so the sync writes it there too.

## API client

The client is generated. Hand-written request types are a lint failure.

1. iOS: Swift OpenAPI Generator as a build plugin in `ZZAPI`, with `generate: [types, client]` and `accessModifier: public`. Transport: swift-openapi-urlsession on an ephemeral session with no URL cache (FR-049). A middleware adds `X-ZZ-Contract: 1` and the bearer token, and refreshes once on 401.
2. Android: the OpenAPI Generator Gradle plugin in `:core-api`, generator `kotlin`, library `jvm-retrofit2`, serialization `kotlinx_serialization`, coroutines on, input `contracts/zzthis-v1.openapi.yaml`, output in the build folder. OkHttp has no cache. An interceptor adds the headers. An authenticator refreshes once on 401.
3. The bearer token is added to every call except resolve, nonce, token, refresh, revoke, and a signed-out report (FR-016).
4. Demo builds use an in-memory implementation of the generated API protocol (iOS) and interfaces (Android) that returns fixtures.
5. Fixture tests cover every row of the spec error table, including `content-refused`, `wrong-length`, `contract-version`, and `rate-limited`. The 404 fixture is exactly `{ "error": "not-found" }` for unknown, expired, used, revoked, and a signed-out read of a private record. The 429 fixture is exactly `{ "error": "rate-limited" }`. Neither body has another field.
6. When either generator fails on the pinned file, open a `bug` issue in zzThis with the generator output. Do not hand-write the types.
7. When the pin moves and the OpenAPI file changes, the sync replaces it and both clients regenerate in the same pull request.

## Auth

zzThis spec 005 FR-020 and FR-021. ZQ18 [DANNY 2026-10-04] (zzThis Q66): native Sign in with Apple or Sign in with Google, then the provider ID token with the server nonce. The PKCE authorization-code exchange is not used.

1. `POST /v1/auth/nonce` returns a nonce that works once, for 10 minutes. The app does not send a redirect URI.
2. iOS, Apple: an `ASAuthorizationAppleIDRequest` with no requested scopes, and `nonce` set to the hex SHA-256 of the server nonce. Send the identity token and the authorization code.
3. iOS, Google: GoogleSignIn with the server nonce. Send the ID token.
4. Android, Google: Credential Manager with `GetSignInWithGoogleOption`, the web client id as the server client id, and the server nonce. Send the ID token. Android does not offer Apple (ZQ23).
5. `POST /v1/auth/token` with `provider`, `client` (`ios` or `android`), `id_token`, `nonce`, and, for Apple, `authorization_code`.
6. Keep the refresh token in the Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) on iOS. On Android, encrypt it with an AES-GCM key held in the Android Keystore and keep it in DataStore. Do not use `EncryptedSharedPreferences`, which is deprecated. The access token stays in memory.
7. On 401, one `POST /v1/auth/refresh`. The old refresh token is discarded. A second 401, or reuse of a revoked refresh token, signs the person out.
8. Sign-out calls `POST /v1/auth/revoke`, then deletes the tokens and the owner list snapshot.
9. Delete account calls `DELETE /v1/me`, then deletes the tokens, the draft, and every cache.
10. Local builds add Developer sign-in: provider `dev`, ID token `dev:local-tester` (FR-048).

No password, phone, or profile field (ZQ7). The app asks Apple for no name and no email.

## Storage

| Item | Where | Lifetime |
|---|---|---|
| Refresh token | Keychain, or Keystore-encrypted DataStore | Until sign-out, delete, or revoke |
| Access token | Memory | 900 seconds |
| Public reusable resolve | A small JSON file in the caches folder, keyed by canonical code, at most 50 entries, oldest dropped first | FR-041: 60 seconds online, 360 seconds offline |
| Last canonical text | App storage | Until the person clears it, so resolve can be retried |
| Owner list snapshot | App cache | Shown offline with an offline label. Deleted at sign-out. |
| Draft title and body | App storage | Until mint or discard. Labeled "Not a code yet." No code words (FR-024). |
| Photo | The app's cache folder | Deleted when the scan or write check ends (FR-004) |

A `no-store` response is never written. Every `/v1` response except a cacheable resolve sends `no-store` (spec 005 FR-028), including tokens, photos, owner responses, 404, and 429. A 404 body is exactly `{ "error": "not-found" }`. A 429 body is exactly `{ "error": "rate-limited" }`. Not-found is never cached. No record-signing key is stored. A photo upload is JPEG only and at most 8 MiB (8388608 bytes).

## Read pipeline

zzThis spec 004, Client read pipeline, on each platform.

1. Camera. iOS: `UIImagePickerController` with the camera source. The simulator has no camera, so Demo and Local builds also offer the system photo picker. Android: `ActivityResultContracts.TakePicture` into a `FileProvider` file in the cache folder. Both platforms offer the system photo picker for an existing photo (FR-012).
2. Reader. iOS: Vision `VNRecognizeTextRequest`, recognition level accurate, language correction off, language `en-US`. Android: ML Kit text recognition, Latin, bundled model. Each recognized line keeps its confidence.
3. Order the lines top to bottom, then left to right, and join them with spaces.
4. `ZZCore` and `:core-grammar` run the scanner, classify, check the word when the list versions match (FR-044), and set the band with the values in one constants file (accept 0.80, retry below 0.50).
5. The Scan screen draws a box on the photo for each candidate and lists them (design/UX.md, Scan with the camera).

## Privacy

The apps are analytics-free.

- No analytics SDK, no advertising identifier, and no crash reporter that uploads the code, the photo, or a token.
- The only third-party sign-in code is Google Sign-In on iOS and Credential Manager with the Google ID library on Android. It runs only when the person taps the Google button.
- Logs in debug builds omit the canonical code, the photo bytes, and tokens.
- The privacy disclosure matches the table in spec.md. Danny publishes it (FR-031).
- Tracking is off in the Apple privacy manifest and in the Play Data safety form.

## Accessibility

- Every control has a name that VoiceOver and TalkBack speak. The check word is a separate label from the rest of the code.
- Text follows the system text size, including the pinned fonts (scaled with the platform's font metrics). The canonical code wraps. It is not truncated.
- Touch targets are at least 44 by 44 points on iOS and 48 by 48 dp on Android.
- Colors come from the pinned token files, light and dark, following the system setting.
- Reduced motion removes transitions. State changes stay.
- Focus order matches the visual order.

## Localization

- English is the only catalog in the first release. `scripts/sync-zzthis.sh` generates `Localizable.xcstrings` (keys as written in `copy.json`) and `strings.xml` (dots become underscores, `{provider}` and `{email}` become `%1$s`). Layout files hold no sentences.
- Dates from the server are ISO-8601 instants, formatted with the platform locale.
- Do not concatenate sentences. The check word label is its own string.
- The string check (T082) applies FR-030 to the generated catalogs: no em dash, and the words demo and check word.

## Testing

| Check | Where it runs |
|---|---|
| Spec lint and the pinned OpenAPI check | `.github/workflows/ci.yml` |
| Every row of `contracts/vectors.json` (grammar, classifier, matching key, scanner, check word) | `ZZCore` with `swift test`, and `:core-grammar` JVM tests |
| The read pipeline on a rendered image of `zz-copper-lantern-sky-zz` | iOS: `ZZCore` tests on macOS (Vision). Android: an instrumented test (ML Kit). |
| API fixtures for every error row | iOS: a stub transport. Android: the OkHttp test server. |
| FR-041 with a fake clock | Both |
| One snapshot per screen state in the table above, light and dark | iOS: swift-snapshot-testing in the Demo configuration. Android: Paparazzi on the demo flavor. |
| Copy rules on the generated catalogs (T082) | `apps-ci.yml` |
| No analytics dependency | `apps-ci.yml`, a check of the package manifests |
| The pin check: run the sync at the committed pin and fail on any difference | `apps-ci.yml` |
| End-to-end against the local zzThis Worker | `apps-e2e.yml` |

`apps-ci.yml` runs an `ios` job on macOS and an `android` job on Ubuntu. Neither job needs a live server.

## End-to-end run

`apps-e2e.yml` runs on demand and nightly. It is not a merge gate. The builder runs it once and links the run in the PR.

1. Check out zzThis at the pin, run `npm ci`, and start `npm run dev:api` in the background.
2. iOS: build the Local configuration and run the UI test on a simulator. Android: start an emulator and run the instrumented test on the local flavor.
3. The test signs in with developer sign-in, creates a code titled `E2E`, re-rolls until `reroll-cap`, signs out, types the code, sees `E2E`, signs in, revokes the code, types it again, sees not-found, and deletes the account.

## Release

1. `apps/VERSION` is the user-visible version. Build numbers come from the CI run number. This spec does not upload a build.
2. The build adds `.github/workflows/apps-ci.yml` with the first test targets. It tests. It does not submit to App Store Connect or Play.
3. The store listing text and the 12+ and Teen filing default stay in FR-031. Danny may change the rating. Danny submits (constitution IX).
4. The privacy manifest and the Data safety answers are filled from the privacy table before he submits. They are drafts until his yes.
5. The production API origin, the client ids, and signing credentials are not committed. A Demo build needs none of them.
