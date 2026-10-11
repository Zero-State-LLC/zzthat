# Feature spec: zzThat apps

Feature ID: 001-zzthat-apps
Status: not built. Specification only. No application source in this change.
Phase: specify (what and why). The how is in [plan.md](plan.md).
Constitution: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) v1.3.0.
Runtime: [runtime.md](runtime.md). Design consumption: [design.md](design.md).
Decisions: [decisions.md](decisions.md) (ZQ1 to ZQ28 decided by 2026-10-05).
zzThis pin: `6c9a9ba98953d125b9a1f1120ca0929988287b6a`, in `contracts/ZZTHIS-API-PIN`. One pin covers the product rules, the API, design, strings, test vectors, and the wordlist.
Deepened 2026-10-04 so both apps can be built in one pass with the zzThis server and web client: zzThis [analysis](https://github.com/Zero-State-LLC/zzthis/blob/6c9a9ba98953d125b9a1f1120ca0929988287b6a/specs/analysis-2026-10-04.md) and [build brief](https://github.com/Zero-State-LLC/zzthis/blob/6c9a9ba98953d125b9a1f1120ca0929988287b6a/docs/ONE-SHOT-BRIEF.md).

## Why

zzThat is the free everyday-use app for zz codes: handwritten codes such as `zz-kathy-lost-cat-zz` that a person can write on a lost-pet flyer, a moving box, a garage-sale item, or a note, and that another person can scan [MICHAEL 2026-10-02]. It is the apps portion of zzThis. The phone apps are API clients of one central server [ZZTHIS SPEC] [DANNY 2026-10-04].

zzThis Section 12.2 still lists the apps as a product the site repo does not build [ZZTHIS SPEC]. zzThis issues #57 (Q33) and #58 (Q39) were resolved in zzThis on 2026-10-04 ([zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)) with the defaults this spec keeps: iOS and Android together, web later, and the first public store release as the launch. Danny names [zzthis](https://github.com/Zero-State-LLC/zzthis) as the home of that later web client and of the central server, and points "Try zzThat" at the zzThis marketing page [DANNY 2026-10-04]. The rest of ZQ1 to ZQ21 are in [decisions.md](decisions.md). This spec is the iOS and Android product spec.

## Users

| User | Need | Source |
|---|---|---|
| Person creating a code | Get a code they can write by hand on an everyday object | [MICHAEL 2026-10-02] |
| Person scanning a code | Point a camera, or type, and see the record they are allowed to see | [ZZTHIS SPEC] spec 004 US1 and spec 002 US1 |
| Signed-in owner | See their codes, update a record, revoke a code | [ZZTHIS SPEC] spec 002 US2 and US3. The list is zzThis spec 005 `GET /v1/me/codes`. |

Auditors, partner systems, logistics clerks, and postal flows are users of zzThis. They are not users of this app [INFERRED]. zzThing is a different product [ZZTHIS SPEC].

## User stories

### US1. Create a code (P1)

As a person, I create a zz code in the app and write it on a thing, so that someone else can scan it [MICHAEL 2026-10-02].

Acceptance:

1. The server chooses the words and the check word [ZZTHIS SPEC] spec 002 US2. Create calls mint and shows that wordlist code. The person does not send a code string (ZQ11). Issued word codes are two or three data words plus a check word [ZZTHIS SPEC] Section 2.2. The prototype issues two data words and a check word [ZZTHIS SPEC] spec 003 Prototype defaults (Q27, decided 2026-10-04).
2. If the server has no free public scope, the app does not invent a code. It shows an unavailable state (FR-018, ZQ3).
3. A code string does not contain a phone number or other personal data [ZZTHIS SPEC] Section 9a D-2026-10-03-11. The free app does not mint field codes or handles.
4. After the person writes the minted code, the app can photograph it and confirm the reading matches that canonical form, on the device [ZZTHIS SPEC] spec 004 FR-017, with the spec 004 Client read pipeline.
5. The person may tap "Get different words" up to 3 times. Each tap replaces the current code. At the cap, or once the code has been resolved, the current words stay (ZQ11) [ZZTHIS SPEC] spec 005 FR-006. The Create UI may present server-chosen candidates in a suggested-word picker so the person feels they chose a code; the app still never sends a person-written code string (zzThis D-2026-10-11-01 option C, RM-113).
6. The pitch lines `zz-kathy-lost-cat-zz` and `zz-moving-box-kitchen-3-zz` are examples in the copy [MICHAEL 2026-10-02]. Create does not claim those strings. Writing a code with no device at all stays zzThis Q25 and is not this screen.

### US2. Scan a handwritten code with the camera (P1)

As a person with a marked thing, I point the camera at a handwritten code and get that code, or a request to confirm or retry [ZZTHIS SPEC] spec 004 US1.

Acceptance:

1. The person takes one still photo with the system camera. Recognition runs on the device. Only the canonical code goes to the API. The photo stays on the device unless the person confirms a retry [ZZTHIS SPEC] Section 10.3, and v1 offers no retry upload (FR-045).
2. The reading passes the v1 grammar (zzThis Section 2.2a) before it is sent [ZZTHIS SPEC] spec 004 FR-008.
3. If the view holds more than one code, the person picks. The app never picks for them [ZZTHIS SPEC] spec 004 FR-013 and FR-014.
4. If the person denies the camera, typed entry still works (US2a).
5. Bands are the spec 004 bands: accept, clarify, retry, abstain. The prototype values are zzThis spec 004, Client read pipeline. They stay parameters (zzThis Q37) [ZZTHIS SPEC].
6. Candidates come from the zzThis spec 004 scanner rules, and every platform passes the pinned scanner vectors.

The site's approved console labels (Manual, Rescan, Confirm, Resolve) are marketing microcopy [MICHAEL 2026-10-03]. They are not a second capture contract. The app follows spec 004.

### US2a. Type a code (P1)

As a person who cannot use the camera, I type the code. The same grammar accepts or rejects it [ZZTHIS SPEC] spec 004 US2.

Voice is not in the first release (ZQ10). The apps do not request the microphone.

### US3. Resolve to a message or page (P1)

As a person with a code, I see the record the server allows, or one not-found answer [ZZTHIS SPEC] spec 002 US1. The brief calls that record a useful page for a public surface such as a lost-cat flyer [ZZTHIS SPEC].

Acceptance:

1. The app calls resolve on the zzThis server with the canonical code only, and without an `Authorization` header (FR-016) [DANNY 2026-10-04] [ZZTHIS SPEC].
2. Unknown, expired, used, and revoked codes show one not-found state [ZZTHIS SPEC] spec 002 FR-011.
3. The screen shows the title and the body from the server (ZQ5). It does not add a phone-number field.
4. The view is a screen in the app. This repo does not build a website (FR-036, ZQ12).
5. A signed-out person can open a public code (ZQ6). A private record stays on the one not-found state [ZZTHIS SPEC] spec 002 FR-020.

### US4. Share (P2)

As a person with a code, I share it so someone else can scan or type it [INFERRED] from "anyone can scan" [MICHAEL 2026-10-02].

Acceptance:

1. The share sheet sends the canonical code as text (FR-020, ZQ9).
2. It does not send an https URL. `share.url` in the contract 1 resolve body stays null (zzThis spec 005 FR-015).

### US5. Manage my codes (P2)

As a signed-in owner, I list my codes, add a record version, or revoke a code [ZZTHIS SPEC] spec 002 US2 and US3.

Acceptance:

1. Revoke and new record versions call the zzThis server [DANNY 2026-10-04].
2. My codes uses `GET /v1/me/codes`, which carries each record's title. Code detail and Edit record find the code's row by code id, then read the current title and body with `GET /v1/records/{record_id}`, using that row's `record_id` [ZZTHIS SPEC] spec 005 FR-030 (runtime.md, Navigation). The app does not keep a private ledger of issued codes as the source of truth.
3. A public resolve of a revoked code is not-found. The owner's own list may show a revoked state only when the server returns that state to the owner [INFERRED].

### US6. Offline behaviour (P2)

As a person without a connection, I can still read a record the app has cached, and I cannot issue or revoke [ZZTHIS SPEC] Section 10.5.

Acceptance:

1. A cached view of a reusable public record can be shown, labeled as offline, until 360 seconds after it was fetched (FR-041, ZQ26).
2. Issue, revoke, and mark-used are refused offline. They are not queued as successes.
3. Single-use and short-expiry codes are not served from the app cache (FR-023).
4. On-device recognition may still produce a canonical code. Resolve of a code that is not cached waits for a connection.
5. A local draft may be kept only with the label "Not a code yet." It cannot be shared as a live code, and it is not issued until the server accepts it (ZQ16).

## Functional requirements

| ID | Requirement | Source |
|---|---|---|
| FR-001 | The products in this repo are an iOS app and an Android app, shipped together. The later web client is built in the zzthis repo, which is also the central server. The site that repo publishes today is the marketing page for zzThis. | [DANNY 2026-10-04]. ZQ1. The both-platforms default is the one stated on zzThis issue #57. |
| FR-002 | The apps are thin clients. They contain no resolver, no database of record, and no second HTTP API. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.1 |
| FR-003 | Resolve, issue, revoke, and record updates are HTTP calls to the zzThis central server. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.6 |
| FR-004 | Recognition runs on the device first. The photo stays on the device by default. | [ZZTHIS SPEC] Section 10.3 |
| FR-005 | A photo is uploaded only after the person confirms a retry or hard case. The confirm sentence is: "Send this photo so we can read the code? It leaves your phone only for this retry, and it may be kept for review of this read. It is not used to train a model." | [ZZTHIS SPEC] Section 10.3 and the object-storage label "photos for retries and review". The sentence is ZQ14. |
| FR-006 | Every reading is parsed with the v1 text grammar, and the client sends only the canonical form. | [ZZTHIS SPEC] Section 2.2a and spec 004 FR-008 |
| FR-007 | The apps do not publish a second grammar. iOS and Android both pass every G9 vector in zzThis Section 2.2a. | [ZZTHIS SPEC] |
| FR-008 | Snapping to the wordlist and local check-word verification follow zzThis specs 003 and 004. Until that library and list exist, the app must not show a local check word as passed. | [ZZTHIS SPEC]. The algorithm, list size, and word source were decided in zzThis on 2026-10-04 (Q30 to Q32, [zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)). A local pass still needs the library and a matching list version (FR-044). |
| FR-009 | The app does not ask the server for nearby live codes. A miss is not-found. | [ZZTHIS SPEC] spec 002 FR-001 and FR-012. ZQ13 keeps suggestions off. |
| FR-010 | Unknown, expired, used, and revoked codes share one not-found presentation. | [ZZTHIS SPEC] spec 002 FR-011 |
| FR-011 | On iOS the camera permission is requested when the person starts a scan, with a purpose string. On Android the app opens the system camera app and declares no camera permission. Whenever the camera is not available, typed entry remains available. | [INFERRED] |
| FR-012 | Photo-library access uses the system picker, and only when a flow needs an existing photo. The camera path does not require the library. | [INFERRED] |
| FR-013 | The app does not request location permission. Duplicate public codes and GPS priority are out of the first release. | [ZZTHIS SPEC] Section 12.2. ZQ4. |
| FR-014 | The first release does not request microphone permission. If voice is added later, the audio stays on the device. | [ZZTHIS SPEC] spec 004 names voice. ZQ10 keeps it out of the first release. On-device audio is [INFERRED] from Section 10.3. |
| FR-015 | Issue, revoke, and record update require a signed-in owner. Unauthenticated write calls are the server's 401. | [ZZTHIS SPEC] spec 002 error states |
| FR-016 | A signed-out person can resolve a public code. The app must not require an account for that read. Writes still require sign-in. The app sends no `Authorization` header on resolve, so a signed-in read stays cacheable [ZZTHIS SPEC] spec 005 FR-008. | [MICHAEL 2026-10-02] "anyone can scan". [ZZTHIS SPEC] spec 002 FR-020. ZQ6. The Section 10.6 line that every call is authenticated is an inferred sketch and does not win for this read. |
| FR-017 | Create calls mint. The server chooses the words and the check word for every code, including free public. The app shows that canonical form and the check word. It does not send a person-written code. "Get different words" calls re-roll at most 3 times for that code, and then the current words stay. A suggested-word picker may show server-chosen candidates; it does not accept arbitrary user-typed code words (zzThis RM-113, D-2026-10-11-01). | [ZZTHIS SPEC] spec 002 US2. ZQ11 [DANNY 2026-10-04]. The cap of 3 is [ZZTHIS SPEC] spec 005 FR-006. D-2026-10-11-01 option C. |
| FR-018 | Until the server exposes a free public scope, create shows an honest unavailable state and does not issue. | [ZZTHIS SPEC] spec 002 FR-016 (free public is v2). ZQ3 is [#3](https://github.com/Zero-State-LLC/zzthat/issues/3). |
| FR-019 | The app never places a phone number or other personal data in the code string. | [ZZTHIS SPEC] Section 9a D-2026-10-03-11 |
| FR-020 | Share sends the canonical code as text and does not send a URL. | ZQ9. [MICHAEL 2026-10-02] "anyone can scan" is met by the words. |
| FR-021 | Revoke and new record versions use the server. My codes uses `GET /v1/me/codes`. Code detail and Edit record take a code id. They find that code's row in `GET /v1/me/codes` (`limit` 50, following `next_cursor`), then read `GET /v1/records/{record_id}`. Revoke uses the code id. Versions use `record_id`. No matching row shows the one not-found state. | [ZZTHIS SPEC] spec 005 US3 and FR-030. Contract 1 has no single-code read, so the lookup by code id is [INFERRED]. |
| FR-050 | The app offers a device-only Saved list of codes the person scanned and chose to save (canonical form, and an optional local thumbnail). Saved does not sync to the server and does not upload photos. The list is cleared on account deletion and by a Settings reset. Cloud sync of Saved is out of this release (zzThis RM-117). | zzThis D-2026-10-11-01 row 17; RM-115. |
| FR-022 | The app may show a briefly cached signed record without a connection. It cannot issue, revoke, or mark a code used offline. | [ZZTHIS SPEC] Section 10.5 |
| FR-023 | The app does not cache single-use or short-expiry codes for a later resolve. | [INFERRED] from the edge rule in [ZZTHIS SPEC] Section 10.2 |
| FR-024 | The app may keep a local draft of a title and body, labeled "Not a code yet." That draft has no code words, is not issued, is not resolved, and is not shared as a live code. | [ZZTHIS SPEC] Section 10.5 forbids offline issue. ZQ16 allows the labeled draft. ZQ11: words come from mint, not from the draft. |
| FR-025 | Capture uses the spec 004 decision bands with the prototype values in zzThis spec 004, Client read pipeline (accept at 0.80, retry below 0.50). They are parameters in one file per platform, not constants spread through the code. | [ZZTHIS SPEC] spec 004. The thresholds are zzThis Q37, decided as parameters in zzThis on 2026-10-04 ([zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)). |
| FR-026 | When a photo or a block of text holds several code candidates, the person chooses one. | [ZZTHIS SPEC] spec 004 FR-013 [MICHAEL 2026-10-03] |
| FR-027 | A bare mark is reported as a bare mark and is not resolved in v1. The app offers typed entry. | [ZZTHIS SPEC] spec 004 FR-011 |
| FR-028 | The app displays server results for a reserved handle and for a check-word mismatch. It does not rewrite the code into a different live code. | [ZZTHIS SPEC] spec 002 FR-019 and FR-021 |
| FR-029 | No screen and no store text claims a measured accuracy, a pilot, a customer, an endorsement, or adoption. | [ZZTHIS SPEC] Section 2.1 |
| FR-030 | User-facing copy says "demo", not "mock", and "check word", not "checksum", and it contains no em dash. | [MICHAEL 2026-10-03] [DANNY 2026-10-04] [DANNY 2026-10-03] |
| FR-031 | Store submission, store spend, and a privacy-policy URL require Danny's yes. Listing text is the everyday-use sentences in ZQ15, without the web sentence. The age rating to file is Apple 13+ (the age rating questionnaire, with user-generated content answered yes) and Google Play Teen (IARC), and Danny may change it before submission. | [MICHAEL 2026-10-02] for the sentences. The rating is [ZZTHIS D-2026-10-10-25, DANNY 2026-10-09] item 5: Apple's age ratings go from 9+ to 13+, and unmoderated public text points to 13+ and Teen. Constitution IX. |
| FR-032 | Every call uses the `/v1` prefix and sends `X-ZZ-Contract: 1`. Tests use fixtures and the local zzThis Worker (`npm run dev:api` at the pin), not a deployed server. | ZQ19. [ZZTHIS SPEC] spec 005 FR-001. |
| FR-033 | On HTTP 429 the app waits for `Retry-After` and shows one wait message that does not say whether the code exists. | [ZZTHIS SPEC] spec 002 error states. Limits are zzThis spec 005 FR-011, which adopted the ZQ20 numbers. |
| FR-034 | The app offers account deletion in the app before store submission. It calls `DELETE /v1/me`, which also revokes the Sign in with Apple token on the server. | [INFERRED] store practice. [ZZTHIS SPEC] spec 005 FR-023. |
| FR-035 | The app offers a report with reason `spam`, `harassment`, `personal-data`, or `other`. The Report sheet's sent state says the creator is reviewed within one day and can be suspended. Resolve and the Report sheet offer "Hide this code", and, once zzThis adds the optional Resolve `creator` field (zzThis RM-112, after the RM-075 tolerant-reader check), "Block this creator". Hidden codes and blocked creator references are kept in a list on the device only, and hiding, blocking, unhiding, and unblocking make no server call. A hidden code, or a code whose `creator` is blocked, resolves to a hidden state without its title or body, with Unhide or Unblock. Account deletion (FR-034) and a reset on the Account screen clear both lists. Create asks the person to accept the current terms of use and content policy before the first create on that device, and again when the terms version changes (zzThis RM-103). New copy keys come from zzThis `design/copy.json` (FR-042). | [ZZTHIS SPEC] Section 2.3 for server-side abuse. The report action is ZQ8 [INFERRED]. Hide, block, the one-day review sentence, and terms acceptance are [ZZTHIS D-2026-10-10-25, DANNY 2026-10-09] item 1 and item 2, for App Review Guideline 1.2 and the Google Play user-generated content policy; they replace ZQ8's "no block list". The Account screen reset is the decision's "Settings reset", because the app has no Settings screen. |
| FR-036 | The resolved view is a screen in the app: a title and a body. | [ZZTHIS SPEC] "useful page", tagged [BRIEF]. ZQ5 and ZQ12. |
| FR-037 | The creation check (US1 acceptance 4) runs on the device. It compares the photo's parse to the minted canonical form. The app does not ask whether a person-written string is free. | [ZZTHIS SPEC] spec 004 FR-017. ZQ11. |
| FR-038 | Macros, drawn-symbol codes, object codes, the AI photo-to-action flow, and zzThing are not features of these apps. | [ZZTHIS SPEC] Section 12.2 and Section 2.6 |
| FR-039 | The same G9 input yields the same canonical form, or the same failure reason, on iOS and on Android. | [ZZTHIS SPEC] Section 2.2a. The two implementations are [INFERRED] until a shared package exists (zzThis Q29). |
| FR-040 | Sign-in follows zzThis spec 005 FR-020 and FR-021: the app gets a nonce from `POST /v1/auth/nonce`, signs in with the platform's own provider sign-in using that nonce, and sends the ID token to `POST /v1/auth/token`. iOS offers Sign in with Apple and Google. Android offers Google through Credential Manager (ZQ23). Access tokens last exactly 900 seconds. Refresh tokens rotate. The refresh token lives in the Keychain on iOS, and on Android it is encrypted with an Android Keystore key and kept in DataStore. The app sends no redirect URI. | ZQ7 and ZQ18 [DANNY 2026-10-04] (zzThis Q66). ZQ22 is the same yes. [INFERRED] storage. |
| FR-041 | The app keeps a resolve only when its `Cache-Control` is `public, max-age=60, stale-while-revalidate=300`. Online, it uses that copy for 60 seconds after the fetch and then fetches again before showing. Offline, it shows the copy, labeled offline, until 360 seconds after the fetch, and then the not-on-this-device state. Every other `/v1` response sends `no-store` and is never kept. The app does not invent a longer lifetime. After a 2xx from `POST /v1/codes/{id}/revoke`, `POST /v1/codes/{id}/reroll`, or `POST /v1/records/{id}/versions`, the app deletes the cache entry for that code's canonical form. `DELETE /v1/me` clears every entry. | zzThis spec 005 FR-018, FR-019, and FR-028 [DANNY 2026-10-04] (Q26). ZQ26. Dropping the entry on the owner's own writes is [INFERRED]. |
| FR-042 | Every sentence a person reads comes from zzThis `design/copy.json` at the pin, through generated string catalogs. The app writes no other user-facing sentence. The one exception is verbatim third-party license text: the Licenses screen shows the bundled `NOTICE` and `OFL.txt` exactly as pinned, and the copy rules (FR-030) do not apply to it. | [ZZTHIS SPEC] design/UX.md. The license text exception is [INFERRED], because a license must be shown as written. |
| FR-043 | Fonts are the IBM Plex files from zzThis `design/fonts/` at the pin. Colors and type come from the pinned token files. | [ZZTHIS SPEC] design/README.md |
| FR-044 | The app checks a check word locally only when its bundled list version equals `wordlist_version` from `GET /v1`. Otherwise the server's answer decides. | [ZZTHIS SPEC] spec 004, Client read pipeline step 4 |
| FR-045 | The app offers the photo upload (FR-005) only when `GET /v1` reports `photo_reads: true`. It is false in v1, so v1 never uploads a photo. When it is offered, the file is JPEG only and at most 8388608 bytes (8 MiB). The server checks the JPEG signature (bytes FF D8 FF). Another type is 400 `malformed`. A larger body is 413 `payload-too-large` and is not stored. | [ZZTHIS SPEC] spec 005 US5 and FR-013 |
| FR-046 | The record body is shown as plain text. Links in it are not tappable. | [ZZTHIS SPEC] spec 005 FR-007 |
| FR-047 | The Account screen shows the support contact (`support@zer0state.com`) and the privacy policy link from build settings (ZQ25). A store submission waits until the mailbox exists and the policy is published. | [INFERRED] store practice. App Store review guideline 1.2 asks for contact details. |
| FR-048 | Local builds offer developer sign-in (zzThis spec 005 FR-022) and call the local Worker. Release builds compile developer sign-in out. | [INFERRED] |
| FR-049 | The app turns off the HTTP library's own cache for `/v1` calls. FR-041 is the only resolve cache. | [INFERRED], so one rule owns caching |

## API contract

The contract is zzThis spec 005 and its OpenAPI file, pinned at `6c9a9ba98953d125b9a1f1120ca0929988287b6a` as [`contracts/zzthis-v1.openapi.yaml`](../../contracts/zzthis-v1.openapi.yaml). Wire shapes live in that file, and this section does not restate them. It lists what the apps call and the rules on the app side.

| Call | When the app calls it | App rule |
|---|---|---|
| `GET /v1` | Launch, return to foreground, and before Create | Show the Unavailable state on Create unless `free_public` is true (FR-018). Create is never hidden. Use `wordlist_version` (FR-044) and `photo_reads` (FR-045). `auth_providers` decides the sign-in buttons. |
| `POST /v1/auth/nonce`, `POST /v1/auth/token` | Sign in | FR-040 |
| `POST /v1/auth/refresh` | After a 401, at most one at a time (runtime.md, API client step 8) | A request that gets a 401 during a refresh waits for it, then retries once. The person is signed out only when the refresh itself returns 401. |
| `POST /v1/auth/revoke` | Sign out | Then delete the local tokens |
| `GET /v1/me`, `DELETE /v1/me` | Account, Delete confirm | FR-034 |
| `GET /v1/resolve/{code}` | Scan, Type | No `Authorization` (FR-016). Cache per FR-041. |
| `POST /v1/codes` | Create | `scope` `free_public`, `kind` `plain`, title and body. No code string. |
| `POST /v1/codes/{id}/reroll` | Get different words | Hide the button at `reroll-cap` |
| `GET /v1/me/codes` | My codes | Cursor paging, 50 per page |
| `GET /v1/records/{id}` | Code detail, Edit record | Owner only. `{id}` is the `record_id` from the code's `GET /v1/me/codes` row (FR-021). |
| `POST /v1/records/{id}/versions` | Save in Edit record | `{id}` is that `record_id` |
| `POST /v1/codes/{id}/revoke` | Revoke confirm | `{id}` is the code id |
| `POST /v1/reads` | Not in v1 | Only after FR-005, and only when `photo_reads` is true (FR-045). JPEG only, at most 8 MiB |
| `POST /v1/reports` | Report | Allowed signed out. The answer never says whether the code exists. |

The apps never call `GET /v1/audit`. There is no person-written claim call and no uniqueness-check call.

### Dependencies on the zzThis server

The DEP ids were zzThat proposals. zzThis spec 005 adopted them on 2026-10-04.

| ID | What | zzThis spec 005 |
|---|---|---|
| DEP-001 | `free_public` in discovery, and 403 `scope-unavailable` until it is on | FR-005 |
| DEP-002 | `GET /v1/me/codes` | US3, FR-030 |
| DEP-003 | `POST /v1/auth/nonce`, then `POST /v1/auth/token` with the provider ID token, plus refresh, revoke, and `GET /v1/me` | FR-020, FR-021. ZQ18 [DANNY 2026-10-04] (zzThis Q66). |
| DEP-004 | Signed-out resolve of a public record | FR-008 |
| DEP-005 | `title` 1 to 120 and `body` 0 to 4000 Unicode code points, counted after trimming | FR-007 |
| DEP-006 | Server-chosen mint that returns `check_word` | US2, FR-004 |
| DEP-007 | `POST /v1/reads` | US5, FR-013 |
| DEP-008 | Re-roll, cap 3 | FR-006 |
| DEP-009 | `/v1`, `X-ZZ-Contract: 1`, `GET /v1/openapi.json` | FR-001, FR-002 |
| DEP-010 | Rate limits and `Retry-After` | FR-011 |
| DEP-011 | `share.text`, and `share.url` null | FR-015 |
| DEP-012 | `DELETE /v1/me` | FR-023 |

The wordlist, the check word, and the capture values are zzThis specs 003 and 004 (prototype defaults, decided in zzThis on 2026-10-04, [zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)). Local snap (FR-008) uses the bundled list from the pin.

### Errors

| Server result | What the person sees (key in `copy.json`) |
|---|---|
| 200, public code | The title and body (US3) |
| 404 `not-found` | `resolve.not_found` (FR-010). The JSON is exactly `{ "error": "not-found" }`, with no other field, and `Cache-Control: no-store` |
| 400 `malformed` on resolve, from Scan or Type | `type.malformed`. No other code offered (FR-009) |
| 400 `malformed`, reason `check-mismatch` | `scan.check_mismatch`. Confirm or retype. Do not auto-correct [ZZTHIS SPEC] spec 004 FR-016 |
| 400 `malformed`, reason `wrong-length` | `scan.wrong_length` |
| 400 `contract-version` | `error.update_app`. Only this result shows it (T013) |
| A 400 other than `contract-version` on mint, a record version, or a report | `error.nothing_saved`. The fields the person filled in stay (DEP-005) |
| Any other response without `X-ZZ-Contract` | `error.unavailable`, or the offline rules when there is no connection |
| 422 `unsupported` (bare mark) | `scan.bare`. Offer typing (FR-027) |
| 422 `reserved-handle` | `error.reserved_handle` (FR-028). Create does not issue handles |
| 422 `content-refused` | `create.content_refused` |
| 403 `reroll-cap` | `minted.reroll_cap`. Hide "Get different words" (FR-017) |
| 403 `scope-unavailable`, or 503 `not-ready` on mint | `create.unavailable` (FR-018) |
| 401 on a write | Sign in (FR-015) |
| 403 otherwise | `error.forbidden` |
| 413 `payload-too-large` | The photo is over 8 MiB. Nothing was stored (FR-045) |
| 429 | `resolve.rate_limited`, then wait for `Retry-After` (FR-033). The JSON is exactly `{ "error": "rate-limited" }`, with no other field, and `Cache-Control: no-store` |
| 500 on a write | `error.nothing_saved` [ZZTHIS SPEC] spec 002 FR-018 |
| 503 otherwise | `error.unavailable` |
| No connection | Offline rules (US6). `error.no_connection` for writes |

### Offline cache rules

1. Keep a resolve only when its `Cache-Control` is `public, max-age=60, stale-while-revalidate=300` (FR-041). Store the body and the fetch time.
2. Online: use the copy for 60 seconds after the fetch. After that, fetch before showing. Do not show a stale copy while online.
3. Offline: show the copy, labeled offline, until 360 seconds after the fetch. Then show `resolve.not_on_device`.
4. Never keep a `no-store` response. Every `/v1` response except a cacheable resolve sends it [ZZTHIS SPEC] spec 005 FR-028. That covers tokens, photos, owner responses, single-use codes, short-expiry codes, private records, authenticated responses, 404, and 429 (FR-023, FR-041).
5. Do not issue, revoke, re-roll, or mark used from the cache (FR-022).
6. The last canonical text may be kept so resolve can be retried. The photo stays on the device and is deleted when the scan ends (FR-004).
7. A draft labeled "Not a code yet" is local only (FR-024).
8. The app does not verify record signatures until zzThis publishes keys (Q28). A cached view is not a claim that the phone checked a signature [INFERRED].
9. After a 2xx from revoke, re-roll, or a record version, delete the cache entry for that code's canonical form. `DELETE /v1/me` clears every entry (runtime.md, Auth step 9) [INFERRED].

The edge purge clears only the data center that handled the write. Other data centers can serve a revoked record for up to 60 seconds after the revoke [ZZTHIS SPEC] spec 005 FR-019(c). On the owner's device the change shows at once only because of both halves: rule 9 drops the app's own copy, and FR-019(c) purges the data center that handled the owner's write, which is the one the owner's next resolve normally reaches. Other devices online may see the old record for about 2 minutes (rule 2). Offline, they may show it for at most about 7 minutes (rule 3).

## On-device and cloud recognition

- Option A ships first: on device where it is good enough, cloud vision only after the FR-005 confirm [ZZTHIS SPEC] Section 10.3 and Section 10.8.
- Option B stays a zzThis benchmark, not a zzThat deliverable [ZZTHIS SPEC].
- zzThis Q18 was decided in zzThis on 2026-10-04 ([zzThis #74](https://github.com/Zero-State-LLC/zzthis/issues/74)): on-device reading only in v1, with no cloud reader. `GET /v1` reports `photo_reads: false` and `POST /v1/reads` returns `not-ready` [ZZTHIS SPEC] spec 005 US5, so the apps do not offer the upload (FR-045).
- The on-device readers are Apple Vision on iOS and ML Kit text recognition on Android (runtime.md, Read pipeline).
- The app does not train a model [INFERRED].
- No accuracy figure is a release criterion (FR-029).
- Voice is out of the first release (FR-014).

## Privacy and permissions

| Permission or data | Rule | Source |
|---|---|---|
| Camera | iOS asks when a scan starts, with a purpose string. Android uses the system camera app and declares no camera permission. | FR-011 [INFERRED] |
| Photos | System picker only, and only to choose an existing photo. | FR-012 [INFERRED] |
| Photo upload | Only after the FR-005 sentence. | FR-005 [ZZTHIS SPEC] |
| Microphone | Not requested. | FR-014 |
| Location | Not requested. | FR-013 |
| Canonical code | Sent on resolve. Mint does not send one. | FR-006, FR-017 |
| Account id | Sent on writes, from the bearer token. | FR-040 |
| Code string | No phone number or other personal data. | FR-019 [ZZTHIS SPEC] |
| Page | Title and body the person wrote. | ZQ5 |

The code on paper is a public identifier. Authorization stays on the server [ZZTHIS SPEC] Section 2.7.

## Accounts and abuse

- Mint, re-roll, revoke, and record updates require a signed-in free account (FR-015, FR-040).
- This app does not issue handles. The server still refuses a reserved handle if one is submitted [ZZTHIS SPEC] spec 002 FR-019.
- A private record is not opened by a signed-out caller [ZZTHIS SPEC] spec 002 FR-020.
- Copied marks, replay, enumeration, unauthorized updates, and malformed input stay server-side [ZZTHIS SPEC] Section 2.3. The app's part is FR-009, FR-010, FR-033, and the report, hide, block, and terms acceptance in FR-035.
- The server refuses record text with blocklisted terms, and the operator can suspend an account [ZZTHIS SPEC] spec 005 FR-024 and FR-025. The app shows `create.content_refused` and `error.forbidden` for those.
- No duplicate codes and no location (FR-013) [ZZTHIS SPEC] Section 12.2.

## App store requirements

- Listing text: "Free for everyone. Write a code on a lost-pet flyer, a moving box, a garage-sale item, or a note, and anyone can scan it, like a QR code you can write by hand." [MICHAEL 2026-10-02]. The web sentence from that same paragraph is not in this listing, because this listing is the phone apps.
- Age rating to file: Apple 13+ and Google Play Teen [ZZTHIS D-2026-10-10-25, DANNY 2026-10-09] item 5, because the page is user-written public text. The questionnaire answers are in `apps/store/listing-draft.md`. Danny may change them before he submits.
- Privacy disclosure matches the table above.
- Account deletion is in the app before submission (FR-034), calling DEP-012. The server revokes the Sign in with Apple token, as Apple requires.
- App Store review guideline 1.2 (user-written pages) and the Google Play user-generated content policy: the report action, hide and block, terms acceptance before the first create (FR-035), the server's content check, report review within one day, and suspension (zzThis RM-102), and a support contact in the app (FR-047).
- Before submission (ZQ25): Michael's square icon artwork, the `support@zer0state.com` mailbox, and the published privacy page. Builds use a placeholder icon.
- No accuracy or adoption claims (FR-029).
- Danny submits (constitution IX). This spec does not authorize a submission.

## Platform

Decided in ZQ17 and ZQ21. Native Swift with SwiftUI on iOS 17 and later. Kotlin with Jetpack Compose on Android 8.0 (API 26) and later. Both pass the same G9 vectors (FR-039).

The comparison that led there:

| | Native Swift and Kotlin | A cross-platform framework |
|---|---|---|
| Camera and on-device vision | Platform APIs, which Option A needs [ZZTHIS SPEC] | The same APIs through native modules |
| Grammar | Two implementations, locked by G9 [ZZTHIS SPEC] | One implementation if the framework can host it |
| Web | Built later in zzthis, not in this repo [DANNY 2026-10-04] | A web target here would be scope this repo excludes |

The zzThis plan's TypeScript note for the grammar library is [INFERRED] there. It is not a decision to use a JavaScript UI.

## Success criteria

No recognition accuracy number is a criterion [ZZTHIS SPEC] Q24.

| ID | Pass when |
|---|---|
| SC-1 | iOS and Android each pass every row of the pinned `contracts/vectors.json`: grammar (G9), classifier, matching key, scanner, and check word (FR-007, FR-039). |
| SC-2 | Resolve sends the canonical code and does not send a photo. Mint sends no canonical code and no photo (FR-004, FR-006, FR-017). |
| SC-3 | Unknown, expired, used, and revoked codes show one not-found state (FR-010). |
| SC-4 | A photo upload happens only after the FR-005 sentence (FR-005). |
| SC-5 | The repo contains no resolver and no database of record (FR-002). |
| SC-6 | A cached public resolve follows FR-041 under a fake clock (60 seconds online, 360 seconds offline). `no-store` responses are not kept. Issue and revoke are refused offline (FR-022). |
| SC-7 | User-facing strings follow FR-030. |
| SC-8 | While `free_public` is false, create stays unavailable and mints nothing (FR-018). |
| SC-9 | A signed-out person can open a public fixture code (FR-016). |
| SC-10 | The pin check passes: every pinned file equals the zzThis file at `contracts/ZZTHIS-API-PIN`. |
| SC-11 | No user-facing sentence exists outside the generated catalogs (FR-042), except the verbatim license text FR-042 names, and the catalogs pass the copy rules (FR-030). |
| SC-12 | On each platform, an end-to-end run against the local zzThis Worker signs in with developer sign-in, creates a code, re-rolls to the cap, resolves it signed out, revokes it, sees not-found, and deletes the account. |
| SC-13 | Each screen state in runtime.md has one snapshot, light and dark. |

## Edge cases

- Camera denied: typed entry still works (FR-011).
- Two codes in one photo: the person picks (FR-026).
- A code across two lines: one code, per the grammar [ZZTHIS SPEC] Section 2.2a.
- Bare mark: not resolved (FR-027).
- Check-word mismatch on a word code: confirm, do not auto-correct (FR-028).
- `zz-moving-box-kitchen-3-zz` is a field code because it contains a digit. No check word is required [ZZTHIS SPEC] spec 003.
- 429: wait for `Retry-After` (FR-033).
- Offline mint: refused, not queued as success (FR-022). A title and body may remain, labeled "Not a code yet" (FR-024). The app does not invent words for that draft.
- Re-roll at the cap: the current words stay (FR-017).
- `free_public` false: create is unavailable (FR-018).
- Signed-out resolve of a public code: the title and body (FR-016).
- A stray `zz` in running text can pair with a real marker. Both candidates show and the person picks [ZZTHIS SPEC] spec 004 scanner rules.
- The bundled list version differs from the server's: no local check word, and the server decides (FR-044).

## Terms

zzThis spec 003 says "check word". zzThis `docs/SPEC.md` also says "checksum word" for the same extra word [ZZTHIS SPEC]. User-facing zzThat copy says "check word" only (FR-030).

"Demo" is the word for sample data and scripted behavior. Do not use "mock" in user-facing copy (FR-030).

## Out of scope

- A web client in this repo (FR-001). The later web client is a zzthis task (ZQ1). The "Try zzThat" link target is the zzThis marketing page, not a zzthat.com site (ZQ2). zzThis spec 001 FR-016 still names zzthat.com; this repo does not edit that file.
- A person-written claim, and a client uniqueness check (ZQ11). Real person-made claim is zzThis v2.0 / RM-116 (D-2026-10-11-01 option A), not this release.
- Cloud sync of the Saved list or upload of Saved thumbnails (zzThis RM-117 / v2.2).
- zzThing [ZZTHIS SPEC].
- A backend, resolver, wordlist pipeline, or check-word algorithm in this repo.
- Logistics, postal, and enterprise product surfaces, except where the shared API requires the client to tolerate those code kinds.
- Macro execution, drawn symbols, object codes, and the photo-to-action concept [ZZTHIS SPEC] Section 12.2 and Section 2.6.
- Option B model training [ZZTHIS SPEC] Section 10.8.
- Payments, partner integrations, and store submission itself (FR-031).
- Voice in the first release (FR-014).
- Duplicate public codes and location (FR-013).
- Live-code suggestions (FR-009).
- Sign in with Apple on Android (ZQ23), and linking two sign-in providers to one account.
- Live video scanning. v1 reads one still photo.

## Runtime

The build contract is [runtime.md](runtime.md): project trees, modules, navigation, screen states (loading, empty, error, offline, demo), the generated API client, auth, storage, an analytics-free privacy stance, accessibility, localization, testing, and the release pipeline. Visuals follow [design.md](design.md). No screen invents a token or a UX pattern.

## Decisions

ZQ1 to ZQ21 are decided in [decisions.md](decisions.md). ZQ22 is decided with ZQ18 [DANNY 2026-10-04] (zzThis Q66). ZQ23 to ZQ26 (issues [#33](https://github.com/Zero-State-LLC/zzthat/issues/33) to [#36](https://github.com/Zero-State-LLC/zzthat/issues/36)) were decided the same day by best practice, with Danny's yes on zzThis #74. The zzThis questions this spec relies on (Q18, Q25 for a code written with no device, Q27, Q30 to Q32, Q36, and Q37) were decided in zzThis on 2026-10-04 too. Sync follows [design.md](design.md). Q18 keeps v1 reading on the device, so v1 does not upload photos. ZQ27, the CI spend path, and ZQ28, the toolchain pins, were decided on 2026-10-05 by established practice (decisions.md).

## Workflows

| Item | Value |
|---|---|
| Governing workflows | anti-slop-code, production-systems, google-developer-style |
| Product owner | Michael Chung (zzmojo) |
| Operator | Danny |
| CI to reuse | `.github/workflows/ci.yml` (spec lint and the pin check, the required check). The build adds `.github/workflows/apps-ci.yml` (both platforms' tests, snapshots, strings, pin) and `.github/workflows/apps-e2e.yml` (manual, and on PRs with the `run-e2e` label, not a merge gate). Triggers, runners, and required checks follow ZQ27 (runtime.md, Testing). Both are named in plan.md. |
| Human gates | Store submission, spend, production credentials, privacy-policy publication (constitution IX) |
