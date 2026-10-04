# Runtime: zzThat apps

Feature: [spec.md](spec.md). This file is the build contract for the iOS and Android apps. It does not add application source.
Decisions: [decisions.md](decisions.md). Visuals: [design.md](design.md).
Status: specification. Danny accepts spec 001 before the trees below are created.

Every named screen uses the pattern in zzthis `design/UX.md` at pin `497ea0d218ee0e7709c02ca4107846a062ff2ce2`. A screen that document does not name keeps a TODO. Do not invent a pattern locally. See [design.md](design.md).

## Project structure

Two native apps. No web target. No server module. No Kotlin Multiplatform shared UI.

```
apps/VERSION                          # one semver line, both apps read it
apps/ios/zzThat.xcodeproj
apps/ios/zzThat/App/                  # entry, tab shell
apps/ios/zzThat/Navigation/           # AppRoute
apps/ios/zzThat/Features/Scan/
apps/ios/zzThat/Features/Create/
apps/ios/zzThat/Features/Resolve/
apps/ios/zzThat/Features/Codes/
apps/ios/zzThat/Features/Account/
apps/ios/zzThat/Features/Report/
apps/ios/zzThat/Core/API/             # generated, do not hand-edit
apps/ios/zzThat/Core/Auth/
apps/ios/zzThat/Core/Storage/
apps/ios/zzThat/Core/Grammar/
apps/ios/zzThat/Core/Design/Tokens.swift
apps/android/core-design/src/main/kotlin/llc/zerostate/zzthis/design/Tokens.kt
apps/DESIGN-PIN                       # one line, the zzthis sha from design.md
apps/ios/zzThat/Resources/Localizable.xcstrings
apps/ios/zzThatTests/
apps/android/settings.gradle.kts
apps/android/app/                     # application, navigation, feature screens
apps/android/core-api/                # generated, do not hand-edit
apps/android/core-auth/
apps/android/core-storage/
apps/android/core-grammar/
apps/android/core-design/             # synced Kotlin tokens only
contracts/zzthis-v1.openapi.yaml      # pinned copy of zzthis specs/005-v1-api/openapi.yaml
```

Minimums: iOS 17, Android 8.0 (API 26) (ZQ17, ZQ21).

## Modules

Each feature module calls core modules. Feature modules do not call each other except through navigation.

| Module | Owns | Must not own |
|---|---|---|
| App / `:app` | Process entry, tabs, dependency wiring | HTTP, grammar rules, tokens |
| Navigation | The route enum below | Screen layout |
| Scan | Camera, bands, typed entry | Resolve rendering |
| Create | Mint, re-roll, write check, unavailable state | A person-typed code string |
| Resolve | Title and body, not-found, share handoff | Issue |
| Codes | List, record version, revoke | Public resolve rules |
| Account | Sign-in, sign-out, delete | A profile form |
| Report | The four reasons in FR-035 | A block list |
| API | Generated client and fixtures | Hand-written request types |
| Auth | PKCE, token refresh, secure store | A password |
| Storage | Cache, draft, last canonical | A second source of truth |
| Grammar | Section 2.2a and G9 | A second grammar |
| Design | The pinned Swift and Kotlin token files | Values authored in this repo |

## Navigation

Three tabs: Scan, Create, My codes. Account and Report are pushed from a toolbar item on every tab, not extra tabs.

```
Scan
  several candidates -> person picks one
  -> Resolve
  camera denied, or the person chooses typing -> Type -> Resolve
Create
  signed out -> Sign in -> Create
  free_public false -> Unavailable
  -> Minted code -> Write check
My codes
  signed out -> Sign in
  empty list stays on My codes
  -> Code detail -> Edit record
                  -> Revoke confirm
                  -> Report
Toolbar
  -> Account -> Delete confirm
  -> Report
```

Back from Resolve returns to the screen that opened it. A revoked code on Code detail does not offer Edit.

## Screens

Each screen implements five states. Copy in every state says "demo" for sample data, says "check word", and uses no em dash.

| State | Meaning |
|---|---|
| Loading | Waiting on the camera, disk, or one HTTP call. The previous content stays put if there is any. |
| Empty | Nothing to show yet: no code in frame, no typed text, no owned codes, no draft. |
| Error | One message from the spec error table. No second code is offered (FR-009). |
| Offline | US6. A cached public view is labeled offline. Mint, re-roll, revoke, and record writes are refused. |
| Demo | Fixture content with the word Demo on screen. No network call. The fixture code is not issued. |

| Screen | Job | Empty | Error | Offline | Demo | UX pattern |
|---|---|---|---|---|---|---|
| Scan | Read a handwritten code on device | No code in frame. Offer Type. | Camera denied. Grammar failure. 429. | Parse locally. Resolve waits. | A scripted frame labeled Demo. | Scan |
| Type | Same parser and resolve as Scan (US2a) | `Type a zz code.` | Grammar failure. 429. | Keep the typed text. Resolve waits. | A fixture string labeled Demo. | Scan |
| Resolve | Title and body, or one not-found (US3) | Not used. Not-found is the error row. | `No match. Check the words and try again.` | Cached public view only while `Cache-Control` allows, or wait | Fixture title and body labeled Demo | Resolve |
| Create | Mint a wordlist code (US1, ZQ11) | Signed-out prompt, or Unavailable when `free_public` is false | `scope-unavailable`, `not-ready`, 401, 500, `reroll-cap` | Draft title and body only, labeled "Not a code yet." | A fixture wordlist code labeled Demo, not minted | Create |
| Minted code | Show canonical form and check word. "Get different words" while `rerolls_remaining` is above 0 | Not used after a 201 | `reroll-cap` keeps the current words | Hide re-roll. The issued words stay on screen. | Demo words, the button does not call the server | Create |
| Write check | On-device photo matches the minted canonical form (FR-037) | No photo yet | Mismatch: rewrite or confirm. No upload. | The compare still runs on device. | Demo photo, labeled Demo | Create |
| My codes | Owner list (DEP-002) | `You have no codes yet.` | 401, 500 | Last list labeled offline, if any | Fixture rows labeled Demo | Empty states |
| Code detail | One owned code, share, edit, revoke, report | Not used | 404 for a code the server dropped | Show the last owner row, writes disabled | Fixture row labeled Demo | TODO |
| Edit record | New title and body (DEP-002) | Title required. Body may be empty. | 401, 500. Nothing was saved. | Refuse the save | Demo fields, save does not call | TODO |
| Revoke confirm | One confirm, then revoke | Not used | 401, 500. The code stays active. | Refuse | Demo confirm does not call | TODO |
| Sign in | Apple or Google (FR-040) | Both buttons | Provider error. No partial session. | Refuse sign-in | Buttons visible, labeled Demo, they do not call | Account |
| Account | Id and providers. Delete. | Signed-out: go to Sign in | Delete failure leaves the account | Show the cached id. Delete refused. | Fixture id labeled Demo | Account |
| Delete confirm | `DELETE /v1/me` (FR-034) | Not used | Failure leaves the account | Refuse | Demo confirm does not call | Account |
| Report | Reasons in FR-035 | Reason not chosen yet | 429. Do not say whether the code exists. | Refuse | Demo submit does not call | TODO |
| Unavailable | Create while `free_public` is false, or the issuer returns `not-ready` (FR-018) | `Create is not available yet.` | Not a second error | Same unavailable copy | Same copy, labeled Demo | Create |

Share is the system share sheet with `share.text` only (FR-020). It is not its own screen.

## API client

The client is generated. Hand-written request types are a lint failure.

1. Generate from [`contracts/zzthis-v1.openapi.yaml`](../../contracts/zzthis-v1.openapi.yaml). That file is a byte copy of zzthis `specs/005-v1-api/openapi.yaml` at `497ea0d218ee0e7709c02ca4107846a062ff2ce2`. The sha is `contracts/ZZTHIS-API-PIN`.
2. iOS uses Swift OpenAPI Generator. Output goes to `apps/ios/zzThat/Core/API/`. Android uses OpenAPI Generator, generator `kotlin`, output in `apps/android/core-api/`. [INFERRED] toolchain. The tools are pinned in the app repos when those trees exist.
3. Every call sends `X-ZZ-Contract: 1` (FR-032).
4. When a later zzthis sha changes that OpenAPI file, replace this copy and the pin in one pull request and regenerate.
5. Live mint stays off until `free_public` is true (ZQ3). Fixture tests use the pinned file (T011).
6. The client does not call `GET /v1/audit`, a person-written claim, or a uniqueness check.
7. `POST /v1/reads` may be called only after FR-005. The v1 placeholder returns `band: abstain`. The app shows that band and does not invent a canonical code from it.

## Auth

OIDC with PKCE. Sign in with Apple and Sign in with Google (ZQ7, ZQ18). `redirect_uri` is an https URL on `ZZ_REDIRECT_ALLOWLIST`. The contract does not use `zzthat://auth`.

1. The app creates a code verifier and a challenge.
2. The platform sheet returns an authorization code.
3. `POST /v1/auth/token` exchanges the code. Access token lifetime is 900 seconds. The refresh token is stored in the platform secure store (Keychain on iOS, EncryptedSharedPreferences or the Keystore-backed store on Android).
4. Calls attach `Authorization: Bearer` except public resolve, anonymous photo retry, and report, which omit it when the person is signed out (ZQ6, ZQ8).
5. On 401, one `POST /v1/auth/refresh`. The old refresh token is discarded. A second 401 signs the person out.
6. Sign-out calls `POST /v1/auth/revoke` and deletes the local tokens.
7. Delete account calls `DELETE /v1/me`, then deletes local tokens, the draft, and the cache.

No password, phone, or profile field (ZQ7).

## Storage

| Item | Where | Lifetime |
|---|---|---|
| Refresh token | Platform secure store | Until sign-out, delete, or revoke |
| Access token | Memory | 900 seconds |
| Public reusable resolve | App cache | Only when `Cache-Control` is `public, max-age=60, stale-while-revalidate=300` (FR-041). Not after that lifetime. |
| Last canonical text | App storage | Until the person clears it, so resolve can be retried |
| Owner list snapshot | App cache | Shown offline with an offline label. Not the source of truth. |
| Draft title and body | App storage | Until mint or discard. Labeled "Not a code yet." No code words (FR-024). |
| Photo | App sandbox | Stays on device unless the person accepts FR-005 |

Honor `Cache-Control`. A `no-store` response is not written. That covers single-use codes, short-expiry codes, private records, authenticated responses, 404, and 429 (FR-023). Do not cache not-found as a permanent answer. Do not store a record-signing key.

## Privacy

The apps are analytics-free.

- No analytics SDK, no advertising identifier, no crash reporter that uploads the code, the photo, or a token.
- Logs in debug builds omit the canonical code, the photo bytes, and tokens.
- The privacy disclosure matches the table in spec.md. Danny publishes it (FR-031).
- Tracking is off in the Apple privacy manifest and in the Play Data safety form.

## Accessibility

- Every control has a name that VoiceOver and TalkBack speak. The check word is a separate label from the rest of the code.
- Text follows the system text size. The canonical code wraps. It is not truncated.
- Touch targets are at least 44 by 44 points on iOS and 48 by 48 dp on Android.
- Text contrast uses the pinned token files from [design.md](design.md). Do not invent a palette beside those files.
- Reduced motion removes transitions. State changes stay.
- Focus order matches the visual order.

## Localization

- English is the only catalog in the first release. Strings live in `Localizable.xcstrings` and `strings.xml`. Layout files do not contain user-facing sentences.
- Dates from the server are ISO-8601 instants, formatted with the platform locale.
- Do not concatenate translated sentences. The check word label is its own string.
- The spec lint, and later the app string check (T082), reject an em dash in those catalogs.

## Testing

| Check | Where it runs |
|---|---|
| Spec lint | `.github/workflows/ci.yml` now |
| G9 vectors, both platforms | App test targets (T020). Same vector file, zzThis commit id in the header. |
| API fixtures for every error row in spec.md | T011, against the generated client |
| Each screen state in the table above | One UI test or snapshot per state, labeled with the state name |
| No analytics dependency | Package manifest check in the future `apps-ci.yml` |
| Copy rules | T082 |
| Signed-out public resolve | SC-9 |
| `free_public` false mints nothing | SC-8 |

No live server is required for these checks. A live call is a separate, optional check and is not a merge gate until zzthis publishes contract `1`.

## Release

1. `apps/VERSION` is the user-visible version. Build numbers increase on each store candidate. This spec does not upload a build.
2. `.github/workflows/apps-ci.yml` is added with the first app test target. It runs tests. It does not submit to App Store Connect or Play.
3. The store listing text and the 12+ / Teen filing default stay in FR-031. Danny may change the rating. Danny submits (constitution IX).
4. Privacy manifest and Data safety answers are filled from the privacy table before he submits. They are drafts until his yes.
5. Production API base URL and client secrets are not committed. A signed-out demo build uses the Demo state and no base URL.
