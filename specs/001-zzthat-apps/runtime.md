# Runtime: zzThat apps

Feature: [spec.md](spec.md). This file is the build contract for the iOS and Android apps. It does not add application source.
Decisions: [decisions.md](decisions.md). Visuals: [design.md](design.md).
Status: specification, deepened 2026-10-04 for a one-shot build. Danny accepts spec 001 before the trees below are created.

Every named screen uses the pattern in zzThis `design/UX.md` at pin `0a65f6ef0e5c50439087cca0acbafc354aaeed7c`, and every sentence comes from `design/copy.json` at that pin. Do not invent a pattern, a palette, or a sentence.

## Toolchain

Pin exact versions in the project files and in `gradle/libs.versions.toml`, and list them in the PR. The versions below were checked on 2026-10-04 [INFERRED].

| Platform | Tools |
|---|---|
| iOS | Xcode 26 (26.6 on the build Mac), the Swift 6 toolchain, XcodeGen for the project, Swift Package Manager. Packages: swift-openapi-generator 1.x (build plugin), swift-openapi-runtime, swift-openapi-urlsession, GoogleSignIn-iOS, swift-snapshot-testing. GoogleSignIn-iOS must be 9.0.0 or later, the first release with a `nonce` parameter. Pin it exactly, at 10.0.0, and use the `GoogleSignIn` product. |
| Android | JDK 21 for Gradle, for the Kotlin toolchain (`kotlin { jvmToolchain(21) }`), and in CI (temurin 21). Android Gradle Plugin 9.4.x (9.4.1 is current), with the Gradle wrapper version that the AGP compatibility table requires. Kotlin is AGP built-in Kotlin: do not apply `org.jetbrains.kotlin.android`. Apply `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization`, and set compiler options in `kotlin { compilerOptions {} }`. `:core-grammar` and `:core-api` are plain `org.jetbrains.kotlin.jvm` modules. `compileSdk` 36, `targetSdk` 36, `minSdk` 26. Libraries: the Compose BOM, Navigation Compose with type-safe routes, `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth`, `com.google.android.libraries.identity.googleid`, `com.google.android.gms:play-services-base` (for `SignInButton`), ML Kit text recognition (bundled Latin model), DataStore, Retrofit with OkHttp and kotlinx.serialization, OpenAPI Generator 7.25.0 as the command-line jar from Maven Central (run by the `:core-api:openApiGenerate` task), and Paparazzi 2.0.0-alpha05.1. |

Pin the three credentials libraries and `play-services-base` in `libs.versions.toml`. Without `credentials-play-services-auth`, Android 13 and earlier have no Google provider.

`:core-api` is a plain Kotlin JVM module [INFERRED]. The `jvm-retrofit2` client it generates uses only Retrofit, OkHttp, and kotlinx.serialization, which are JVM libraries, so it needs no Android API and its tests run as plain JVM tests. `:app` depends on it as an ordinary JVM library. One Kotlin version in `libs.versions.toml` drives the `kotlin.jvm`, compose, and serialization plugins, so the build loads one Kotlin Gradle plugin version, the same one AGP's built-in Kotlin uses.

Paparazzi 2.0.0-alpha05.1 is the newest release on Maven Central as of 2026-10-04. The alpha is accepted because no stable Paparazzi release supports AGP 9. Re-pin to the first stable 2.x release when it ships.

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
apps/ios/zzThat/Resources/              # Localizable.xcstrings and InfoPlist.xcstrings (generated), Fonts/, Licenses/, Assets.xcassets
apps/ios/zzThat/Config/                 # Demo, Local, and Release xcconfig files, no secrets
apps/ios/Packages/ZZCore/               # grammar, classifier, check word, matching key, scanner, list loader, read pipeline
apps/ios/Packages/ZZAPI/                # openapi.yaml copy and generator config; code generated at build
apps/ios/Packages/ZZDesign/             # pinned Tokens.swift and font registration
apps/ios/zzThatTests/
apps/ios/zzThatUITests/
apps/android/settings.gradle.kts
apps/android/gradle/libs.versions.toml
apps/android/app/                       # application, navigation, feature screens; flavors demo, local, prod
apps/android/core-api/                  # plain Kotlin JVM module; the client is generated from contracts/zzthis-v1.openapi.yaml at build, not committed
apps/android/core-auth/
apps/android/core-storage/
apps/android/core-grammar/              # plain Kotlin JVM module, same scope as ZZCore
apps/android/core-design/               # pinned Tokens.kt, TTF files only in res/font, OFL.txt and NOTICE in assets/licenses
apps/store/                             # privacy-policy-draft.md, listing-draft.md, data-safety-draft.md: drafts for Danny, never uploaded
contracts/zzthis-v1.openapi.yaml        # pinned
contracts/vectors.json                  # pinned
contracts/wordlists/                    # pinned lists
contracts/ZZTHIS-API-PIN                # the one zzThis pin
scripts/sync-zzthis.sh                  # design.md, Sync
```

Minimums: iOS 17, Android 8.0 (API 26) (ZQ17, ZQ21).

The root `.gitignore` lists the generated and local paths: `.zzthis-pin/`, the generated `.xcodeproj`, Xcode and Gradle build output (the generated `:core-api` client included), the Kotlin compiler's `apps/android/.kotlin/` folder, and `local.properties`. Commits and the spec lint skip them (T001).

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
| Account | Sign-in, sign-out, delete, support and privacy links, licenses | A profile form |
| Report | The four reasons in FR-035 | A block list |
| API (`ZZAPI`, `:core-api`, a plain Kotlin JVM module) | The generated client, the request middleware, the demo implementation | Hand-written request types |
| Auth | Nonce, provider sign-in, token refresh, secure store | A password |
| Storage | Resolve cache, draft, last canonical | A second source of truth, or any `no-store` response |
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
                 -> Revoke confirm (only while the row's status is active)
Toolbar
  -> Account -> Delete confirm
             -> Licenses
```

Back from Resolve returns to the screen that opened it. Code detail shows Revoke only when the row's `status` is `active` [INFERRED]. A revoked code on Code detail does not offer Edit. Create always shows a screen: while `free_public` is false it shows Unavailable. It is never hidden.

Code detail and Edit record take a code id (`OwnedCode.id`) [INFERRED]. They use the row My codes already has. Without it, they page through `GET /v1/me/codes` (`limit` 50, following `next_cursor`) until a row's `id` matches. They then read the title and body with `GET /v1/records/{record_id}`, using that row's `record_id`. Revoke posts the code id. Save posts a version to that `record_id`. No matching row, or a 404 from the record read, shows the one not-found state. Contract 1 has no single-code read.

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
| Create | Mint a wordlist code (US1, ZQ11) | `create.signed_out`, or Unavailable | `create.content_refused`, 401. A 400 other than `contract-version`, or a 500, shows `error.nothing_saved` and keeps the title and body. | Draft only, labeled `create.draft_label` | A fixture code labeled Demo, not minted | Create |
| Minted code | The canonical form and the check word, and re-roll | Not used after a 201 | `minted.reroll_cap` keeps the words | Hide re-roll. The words stay. | Demo words. The button does not call. | Minted code |
| Write check | The photo of the handwriting matches the minted code (FR-037) | No photo yet | `writecheck.mismatch`. No upload. | Runs on the device | A fixture photo labeled Demo | Write check |
| My codes | Owner list with titles | `codes.empty`, or `codes.signed_out` | 401, 403, 500 | `error.no_connection`. No list is kept, because owner responses are `no-store`. | Fixture rows labeled Demo | My codes |
| Code detail | One owned code: share, edit, revoke. Revoke shows only when the row's `status` is `active`. | Not used | `resolve.not_found` when no row has the code id, or the record read returns 404 | `error.no_connection`. Nothing owned is kept offline. | Fixture row labeled Demo | Code detail |
| Edit record | New title and body | `create.title_required` | `create.content_refused`. A 400 other than `contract-version`, or a 500, shows `error.nothing_saved` and keeps the fields. | Refuse the save | Demo fields. Save does not call. | Edit record |
| Revoke confirm | One confirm, then revoke | Not used | `error.nothing_saved`. The code stays active. | Refuse | Demo confirm does not call | Revoke confirm |
| Unavailable | Create while `free_public` is false or the issuer is not ready (FR-018) | `create.unavailable` | Not a second error | Same copy | Same copy, labeled Demo | Create |
| Sign in | Provider buttons from discovery (FR-040) | `signin.why` with the buttons | `signin.failed`. No half session. | Refuse sign-in | Buttons labeled Demo. They do not call. | Sign in |
| Account | Provider, sign out, delete, support, privacy, licenses | Signed out: go to Sign in | `delete.failed` | Show the cached provider. Delete refused. | Fixture account labeled Demo | Account |
| Delete confirm | `DELETE /v1/me` (FR-034) | Not used | `delete.failed`. The account stays. | Refuse | Demo confirm does not call | Revoke confirm and Delete confirm |
| Licenses | The bundled `NOTICE` and `OFL.txt`, then the generated third-party notices (T087), as plain text, opened from `account.licenses` | Not used | Not used | Same text. It is bundled. | Same text, labeled Demo | Account |
| Report | The reasons in FR-035 | Reason not chosen yet | 429. Never say whether the code exists. | Refuse | Demo send does not call | Report |

Share is the system share sheet with `share.text` only (FR-020). It is not its own screen.

Create and Edit record stop input at 120 Unicode code points for the title and 4000 for the body, counted after trimming (spec.md DEP-005) [INFERRED]. Swift counts `unicodeScalars`. Kotlin uses `codePointCount`. Only a 400 `malformed` on resolve shows `type.malformed`. Any other 400 on mint, a record version, or a report, except `contract-version`, shows `error.nothing_saved` (spec.md, Errors).

The Licenses row is [INFERRED] from the zzThis license work (the EFF wordlist and IBM Plex credits in `NOTICE`). It uses the Account pattern until zzThis `design/UX.md` names one. The license text is shown verbatim, the one FR-042 exception. Its title comes from `account.licenses`. `account.licenses` is a new zzThis copy key for Michael's review, so it reaches this repo when the pin moves.

The Licenses screen also shows the third-party notices that the build generates for the packages that ship in the app, from the platform's pinned dependencies: each package's name, version, and full license text, with Apache 2.0 NOTICE files where they exist (T087) [DANNY 2026-10-05 #43]. Test-only packages and build tools are not listed (S4). The notices are shown as given, after `NOTICE` and `OFL.txt`, under the same FR-042 exception, with no copy key. The build fails when the app links a package the notices miss, or when a listed package has no license file.

## Build configurations

| | Demo | Local | Release |
|---|---|---|---|
| API | The in-memory demo implementation. No network. | `http://localhost:8787` from the iOS simulator, `http://10.0.2.2:8787` from the Android emulator | `ZZ_API_ORIGIN` build setting, injected at build, not committed |
| Sign-in | Buttons labeled Demo. They do not call. | Developer sign-in (FR-048), plus Apple and Google once client ids are set | Apple and Google only |
| Plain HTTP | None | Only to localhost or 10.0.2.2 (iOS `NSAllowsLocalNetworking`; an Android network security config in the local flavor) | None |
| Bundle and application id | `com.zer0state.zzthat.demo` | `com.zer0state.zzthat.local` | `com.zer0state.zzthat` (ZQ24) |
| Display name | zzThat Demo | zzThat Local | zzThat |

Google client ids are build settings with placeholder values until Danny creates the clients. The support contact is `support@zer0state.com`, and the privacy policy link is the marketing site's privacy page (ZQ25). They are not secrets, but Release values are still injected rather than committed.

iOS Google setup [INFERRED]: the Local and Release xcconfigs define `ZZ_GOOGLE_IOS_CLIENT_ID` and `ZZ_GOOGLE_REVERSED_CLIENT_ID`. In `project.yml`, Info.plist sets `GIDClientID` to `$(ZZ_GOOGLE_IOS_CLIENT_ID)` and registers a URL type whose scheme is `$(ZZ_GOOGLE_REVERSED_CLIENT_ID)`. The app forwards `.onOpenURL` to `GIDSignIn.sharedInstance.handle(_:)`. GoogleSignIn raises an exception when `GIDClientID` or the URL scheme is missing, so while the value is still a placeholder the Google button is hidden. Demo builds have neither key and never call GoogleSignIn.

## Pinned files

[design.md](design.md) lists every file the sync copies from zzThis and where it lands. Both platforms read `contracts/vectors.json` in their core tests and bundle `contracts/wordlists/` as resources or assets. The iOS build plugin needs its own copy of the OpenAPI file inside the `ZZAPI` target, so the sync writes it there too.

## API client

The client is generated. Hand-written request types are a lint failure.

1. iOS: Swift OpenAPI Generator as a build plugin in `ZZAPI`, with `generate: [types, client]` and `accessModifier: public`. Transport: swift-openapi-urlsession on an ephemeral session with no URL cache (FR-049). The `Client` gets `Configuration(dateTranscoder: LenientISO8601Transcoder())`, a small transcoder in `ZZAPI` that decodes with fractional seconds first, then whole seconds, and encodes with fractional seconds. Call sites pass the generated `._1` value for the required `X-ZZ-Contract` parameter. The middleware then sets the header by replacing it, `request.headerFields[HTTPField.Name("X-ZZ-Contract")!] = "1"`, never by appending. It sets `Authorization` the same way. An `actor TokenStore` holds the access token and the one shared refresh `Task` (step 8).
2. Android: OpenAPI Generator 7.25.0, the command-line jar from Maven Central, run by the `:core-api:openApiGenerate` task in `:core-api`, generator `kotlin`, library `jvm-retrofit2`, serialization `kotlinx_serialization`, coroutines on, input `contracts/zzthis-v1.openapi.yaml`, output in the build folder. Pin the generator version. OkHttp has no cache. The generated Retrofit enum for `X-ZZ-Contract` serializes as `_1`, so the interceptor replaces the header with `request.newBuilder().header("X-ZZ-Contract", "1")`. It sets `Authorization` the same way and never uses `addHeader`. A synchronized `Authenticator` runs the refresh (step 8).
3. The bearer token is added to every call except resolve, nonce, token, refresh, revoke, and a signed-out report (FR-016).
4. Demo builds use an in-memory implementation of the generated API protocol (iOS) and interfaces (Android) that returns fixtures.
5. Fixture tests cover every row of the spec error table, including `content-refused`, `wrong-length`, `contract-version`, and `rate-limited`. The 404 fixture is exactly `{ "error": "not-found" }` for unknown, expired, used, revoked, and a signed-out read of a private record. The 429 fixture is exactly `{ "error": "rate-limited" }`. Neither body has another field.
6. When either generator fails on the pinned file, open a `bug` issue in zzThis with the generator output. Do not hand-write the types. Generator configuration is allowed and does not count as hand-writing: `typeMappings`, an `openapiNormalizer` rule, or ignoring the cookie parameter. On Android the first build step is `:core-api:openApiGenerate`, then `:core-api:compileKotlin`, on the pinned contract. Record the generator version in the PR.
7. When the pin moves and the OpenAPI file changes, the sync replaces it and both clients regenerate in the same pull request.
8. Refresh is single-flight [INFERRED]. Each client runs at most one `POST /v1/auth/refresh` at a time. A request that gets a 401 while a refresh runs waits for it, then retries once with the new access token. The client signs out only when the refresh itself returns 401 (zzThis spec 005 FR-021). On iOS, `TokenStore` keeps one shared `Task` for the refresh in progress. On Android, the `Authenticator` is synchronized and compares the failed request's bearer with the current token. If they differ, another call already refreshed, so it retries with the current token and does not refresh again.
9. Times from the server are RFC 3339 UTC, with or without fractional seconds (`.123Z` or `Z`). Both platforms accept both forms.
10. Only a 400 `contract-version` shows `error.update_app`. Any other response without `X-ZZ-Contract`, which the Swift client throws as `ClientError`, shows `error.unavailable`, or follows the offline rules when there is no connection (T013).

## Auth

zzThis spec 005 FR-020 and FR-021. ZQ18 [DANNY 2026-10-04] (zzThis Q66): native Sign in with Apple or Sign in with Google, then the provider ID token with the server nonce. The PKCE authorization-code exchange is not used.

1. `POST /v1/auth/nonce` returns a nonce that works once, for 10 minutes. The app does not send a redirect URI.
2. iOS, Apple: an `ASAuthorizationAppleIDRequest` with no requested scopes, and `nonce` set to the hex SHA-256 of the server nonce. Send the identity token and the authorization code.
3. iOS, Google: `GIDSignIn.sharedInstance.signIn(withPresenting:hint:additionalScopes:nonce:)` with `hint` nil, `additionalScopes` nil, and `nonce` set to the raw server nonce, not hashed. Send `result.user.idToken.tokenString` as the ID token. Do not set `GIDServerClientID` [INFERRED].
4. Android, Google: Credential Manager with `GetSignInWithGoogleOption`, the web client id as the server client id, and the server nonce. Send the ID token. The button is `com.google.android.gms.common.SignInButton` inside an `AndroidView`, and its tap starts that Credential Manager request [INFERRED]. Android does not offer Apple (ZQ23).
5. `POST /v1/auth/token` with `provider`, `client` (`ios` or `android`), `id_token`, `nonce`, and, for Apple, `authorization_code`.
6. Keep the refresh token in the Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) on iOS. On Android, encrypt it with an AES-GCM key held in the Android Keystore and keep it in DataStore. Do not use `EncryptedSharedPreferences`, which is deprecated. The access token stays in memory. Android sets `android:allowBackup="false"`, so a backup never restores the ciphertext to a device without its Keystore key. Any decrypt failure counts as signed out, and the stored blob is deleted [INFERRED].
7. On 401, wait for or start the single in-flight refresh (API client step 8, zzThis spec 005 FR-021). The old refresh token is discarded. The person is signed out only when the refresh itself returns 401, which covers reuse of a revoked refresh token.
8. Sign-out calls `POST /v1/auth/revoke`, then deletes the tokens.
9. Delete account calls `DELETE /v1/me`, then deletes the tokens, the draft, and every cache.
10. Local builds add Developer sign-in: step 1 as usual, then step 5 with provider `dev`, ID token `dev:local-tester`, the server nonce, and no `authorization_code`. The server consumes that nonce like any other (zzThis spec 005 FR-022; FR-048).

No password, phone, or profile field (ZQ7). The app asks Apple for no name and no email.

## Storage

| Item | Where | Lifetime |
|---|---|---|
| Refresh token | Keychain, or Keystore-encrypted DataStore (no Android backup) | Until sign-out, delete, revoke, or a decrypt failure |
| Access token | Memory | 900 seconds |
| Public reusable resolve | A small JSON file in the caches folder, keyed by canonical code, at most 50 entries, oldest dropped first | FR-041: 60 seconds online, 360 seconds offline. Dropped after a 2xx from the owner's revoke, re-roll, or record version for that code. `DELETE /v1/me` clears every entry. |
| Last canonical text | App storage | Until the person clears it, so resolve can be retried |
| Draft title and body | App storage | Until mint or discard. Labeled "Not a code yet." No code words (FR-024). |
| Photo | The app's cache folder | Deleted when the scan or write check ends (FR-004) |

A `no-store` response is never written. Every `/v1` response except a cacheable resolve sends `no-store` (spec 005 FR-028), including tokens, photos, owner responses, 404, and 429. A 404 body is exactly `{ "error": "not-found" }`. A 429 body is exactly `{ "error": "rate-limited" }`. Not-found is never cached. No record-signing key is stored. A photo upload is JPEG only and at most 8 MiB (8388608 bytes).

## Read pipeline

zzThis spec 004, Client read pipeline, on each platform.

1. Camera. iOS: `UIImagePickerController` with the camera source. iOS shows `scan.camera_purpose` as `NSCameraUsageDescription` (design.md, Sync). Android: `ActivityResultContracts.TakePicture` into a `FileProvider` file in the cache folder, which needs no camera permission. Every build on both platforms also offers the system photo picker for an existing photo (FR-012): `PhotosPicker` on iOS and `PickVisualMedia` on Android. The iOS simulator has no camera, so Demo and Local runs there use the picker.
2. Reader. iOS: Vision `VNRecognizeTextRequest`, recognition level accurate, language correction off, language `en-US`. Android: ML Kit text recognition, Latin, bundled model. Each recognized line keeps its confidence: on iOS the top candidate's `VNRecognizedText.confidence`, on Android `Text.Line.getConfidence()`. Never use ML Kit Element or Symbol confidence. A word takes its line's confidence. A candidate takes the lowest confidence among the lines its words came from, so a code that wraps onto two lines takes the lower one [INFERRED]. zzThis spec 004, Client read pipeline step 1, states the same rule before either platform codes it.
3. Order the lines top to bottom, then left to right, and join them with spaces.
4. `ZZCore` and `:core-grammar` run the scanner, classify, check the word when the list versions match (FR-044), and set the band with the values in one constants file (accept 0.80, retry below 0.50).
5. The Scan screen draws a box on the photo for each candidate and lists them (design/UX.md, Scan with the camera).

## Privacy

The apps are analytics-free.

- No analytics SDK, no advertising identifier, and no crash reporter that uploads the code, the photo, or a token.
- The only third-party sign-in code is Google Sign-In on iOS, and on Android Credential Manager with the Google ID library plus the Play services `SignInButton` from `play-services-base`, which only draws the Google button. Sign-in runs only when the person taps that button.
- Logs in debug builds omit the canonical code, the photo bytes, and tokens.
- The privacy disclosure matches the table in spec.md. The build drafts it at `apps/store/privacy-policy-draft.md`, next to the listing and Data safety drafts. It never goes on the zzThis site from this build. Danny publishes it (FR-031).
- Tracking is off in the Apple privacy manifest and in the Play Data safety form.

## Accessibility

- Every control has a name that VoiceOver and TalkBack speak. The check word is a separate label from the rest of the code.
- Text follows the system text size, including the pinned fonts (scaled with the platform's font metrics). The canonical code wraps. It is not truncated.
- Touch targets are at least 44 by 44 points on iOS and 48 by 48 dp on Android.
- Colors come from the pinned token files, light and dark, following the system setting.
- Reduced motion removes transitions. State changes stay.
- Focus order matches the visual order.

## Localization

- English is the only catalog in the first release. `scripts/sync-zzthis.sh` generates `Localizable.xcstrings` (keys as written in `copy.json`), `InfoPlist.xcstrings` (`NSCameraUsageDescription` from `scan.camera_purpose`), and `strings.xml` (dots become underscores). `{provider}` and `{email}` become `%@` in `Localizable.xcstrings` and `%1$s` in `strings.xml`. Layout files hold no sentences.
- `account.signed_in_with` fills `{provider}` with the `provider.apple`, `provider.google`, or `provider.dev` string for the first entry of `providers` from `GET /v1/me` [INFERRED]. An account has one provider in v1 (spec.md, Out of scope). The `provider.*` keys are new zzThis copy keys for Michael's review.
- Dates from the server are RFC 3339 UTC instants, with or without fractional seconds. Both platforms accept both forms and format them with the platform locale.
- Do not concatenate sentences. The check word label is its own string.
- The string check (T082) applies FR-030 to every generated catalog, `InfoPlist.xcstrings` included: no em dash, and the words demo and check word.

## Testing

| Check | Where it runs |
|---|---|
| Spec lint and the pinned OpenAPI check | `.github/workflows/ci.yml` |
| Every row of `contracts/vectors.json` (grammar, classifier, matching key, scanner, check word) | `ZZCore` with `swift test`, and `:core-grammar` JVM tests |
| The read pipeline on a rendered image of `zz-copper-lantern-sky-zz` | iOS: `ZZCore` tests on macOS (Vision). Android: an instrumented test (ML Kit) on the `apps-ci.yml` android job's emulator. |
| Band rows: line confidences in, one band out (T022) | `ZZCore` with `swift test`, and `:core-grammar` JVM tests, from one shared table |
| API fixtures for every error row | iOS: a stub transport. Android: the OkHttp test server. |
| FR-041 with a fake clock | Both |
| One snapshot per screen state in the table above, light and dark | iOS: swift-snapshot-testing in the Demo configuration, on the destination below. Android: Paparazzi 2.0.0-alpha05.1 on the demo flavor with `DeviceConfig.PIXEL_5`. |
| Kotlin client generation, `:core-api:openApiGenerate`, before any other Android step | `apps-ci.yml`, `android` job |
| Copy rules on the generated catalogs (T082) | `apps-ci.yml`, `checks` job |
| No analytics dependency | `apps-ci.yml`, `checks` job, a check of the package manifests |
| The pin check: run the sync at the committed pin and fail on any difference | `apps-ci.yml`, `checks` job |
| End-to-end against the local zzThis Worker | `apps-e2e.yml` |

### Triggers and runners (ZQ27)

zzThat is a private repo on GitHub Team, so hosted minutes come out of the organization's included minutes, and a macOS minute costs about ten Linux minutes. ZQ27 sets this path, decided 2026-10-05 and accepted by Danny on #39 (decisions.md, #40):

- `apps-ci.yml` triggers on `pull_request` with types `opened`, `synchronize`, `reopened`, and `labeled`, and on `workflow_dispatch`. The `labeled` type is what lets adding the `ios-ci` label start the `ios` job. Adding any label also reruns the other jobs.
- Both `apps-ci.yml` and `apps-e2e.yml` set workflow-level `concurrency` with the group `${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}` and `cancel-in-progress: true`, so a new push cancels the run it replaces (ZQ27).
- `apps-ci.yml`, `android` job: `ubuntu-latest`, on every pull request. It uses JDK 21 (temurin) and the pinned Gradle wrapper. It runs `:core-api:openApiGenerate`, then every module's JVM unit tests (`:core-grammar`, `:core-api`, and the local unit tests of the Android modules), then Paparazzi. It then enables KVM and runs only the ML Kit read-pipeline instrumented test, on an x86_64 `google_apis` API 35 emulator, with `reactivecircus/android-emulator-runner@v2`.
- `apps-ci.yml`, `checks` job: `ubuntu-latest`, on every pull request, with no label gate. It runs the pin check, the string check (T082), and the manifest check (T075). It does not wait for the `android` job.
- `apps-ci.yml`, `ios` job: runs only on `workflow_dispatch` or when the PR has the `ios-ci` label. A skipped `ios` job does not fail a check. It uses `macos-26` and selects Xcode 26.6. App tests and snapshots run on `-destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'`. Snapshot references are recorded on that same destination. On the dev Mac, use the stock iPhone 17 simulator, not a renamed or custom one.
- Every `xcodebuild` call, in CI and on the dev Mac, passes `-skipPackagePluginValidation`. Without it, the `ZZAPI` build plugin stops a headless build at plug-in validation.
- No job needs a live server. Neither `apps-ci.yml` nor `apps-e2e.yml` is a required check. The required check is `ci.yml` (`build`).
- The builder runs the iOS tests on the dev Mac and pastes the command, the commit sha, and the log tail into the PR body.

## End-to-end run

`apps-e2e.yml` triggers on `workflow_dispatch` and on `pull_request` with types `labeled` and `synchronize`. On a `pull_request` event, its jobs run only when the PR has the `run-e2e` label. A `workflow_dispatch` run has no PR, so the label gate does not apply to it. It has no schedule and is not a merge gate (ZQ27). A manual run can start only once the file is on `main`, so on the build PR the label is how the run happens. The builder opens the draft PR, adds `run-e2e`, and links the run in the PR body. On a `pull_request` event, the iOS job also needs the `ios-ci` label, like the `ios` job above. Under ZQ27, the builder runs the iOS end-to-end test on the dev Mac and pastes the command, the commit sha, and the log tail into the PR body. Evidence with no Actions run is listed under "could not verify".

1. Check out zzThis at the pin, run `npm ci`, and start `npm run dev:api` in the background.
2. iOS: build the Local configuration and run the UI test on `platform=iOS Simulator,name=iPhone 17,OS=26.5`, with `-skipPackagePluginValidation`. Android: on `ubuntu-latest` with KVM, start an x86_64 `google_apis` API 35 emulator with `reactivecircus/android-emulator-runner@v2`, and run the instrumented test on the local flavor.
3. The test signs in with developer sign-in, creates a code titled `E2E`, re-rolls until `reroll-cap`, signs out, types the code, sees `E2E`, signs in, revokes the code, types it again, sees not-found, and deletes the account.

## Release

1. `apps/VERSION` is the user-visible version. Build numbers come from the CI run number. This spec does not upload a build.
2. The build adds `.github/workflows/apps-ci.yml` with the first test targets. It tests. It does not submit to App Store Connect or Play.
3. The store listing text and the 12+ and Teen filing default stay in FR-031, drafted in `apps/store/listing-draft.md`. Danny may change the rating. Danny submits (constitution IX).
4. The privacy manifest and the Data safety answers (`apps/store/data-safety-draft.md`) are filled from the privacy table before he submits. They are drafts until his yes.
5. The production API origin, the client ids, and signing credentials are not committed. A Demo build needs none of them.
