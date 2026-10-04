# Feature spec: zzThat apps

Feature ID: 001-zzthat-apps
Status: not built. Specification only. No application source in this change.
Phase: specify (what and why). The how is in [plan.md](plan.md).
Constitution: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) v1.2.0.
Runtime: [runtime.md](runtime.md). Design consumption: [design.md](design.md).
Decisions: [decisions.md](decisions.md) (ZQ1 to ZQ21, 2026-10-04).
Source pin: zzThis `0a5c9b93e8f8266ff9d0855f4afe2dafbe1d26d7`.

## Why

zzThat is the free everyday-use app for zz codes: handwritten codes such as `zz-kathy-lost-cat-zz` that a person can write on a lost-pet flyer, a moving box, a garage-sale item, or a note, and that another person can scan [MICHAEL 2026-10-02]. It is the apps portion of zzThis. The phone apps are API clients of one central server [ZZTHIS SPEC] [DANNY 2026-10-04].

zzThis Section 12.2 still lists the apps as a product the site repo does not build [ZZTHIS SPEC]. zzThis issues #57 (Q33) and #58 (Q39) are unanswered there. This spec keeps their scope default (iOS and Android together, web later) and their launch default (the first public store release). Danny names [zzthis](https://github.com/Zero-State-LLC/zzthis) as the home of that later web client and of the central server, and points "Try zzThat" at the zzThis marketing page [DANNY 2026-10-04]. The rest of ZQ1 to ZQ21 are in [decisions.md](decisions.md). This spec is the iOS and Android product spec.

## Users

| User | Need | Source |
|---|---|---|
| Person creating a code | Get a code they can write by hand on an everyday object | [MICHAEL 2026-10-02] |
| Person scanning a code | Point a camera, or type, and see the record they are allowed to see | [ZZTHIS SPEC] spec 004 US1 and spec 002 US1 |
| Signed-in owner | See their codes, update a record, revoke a code | [ZZTHIS SPEC] spec 002 US2 and US3. The list call is proposed as DEP-002. |

Auditors, partner systems, logistics clerks, and postal flows are users of zzThis. They are not users of this app [INFERRED]. zzThing is a different product [ZZTHIS SPEC].

## User stories

### US1. Create a code (P1)

As a person, I create a zz code in the app and write it on a thing, so that someone else can scan it [MICHAEL 2026-10-02].

Acceptance:

1. The server chooses the words and the check word [ZZTHIS SPEC] spec 002 US2. Create calls mint and shows that wordlist code. The person does not send a code string (ZQ11). Issued word codes are two or three data words plus a check word [ZZTHIS SPEC] Section 2.2. Which of those counts the server uses stays zzThis Q27.
2. If the server has no free public scope, the app does not invent a code. It shows an unavailable state (FR-018, ZQ3).
3. A code string does not contain a phone number or other personal data [ZZTHIS SPEC] Section 9a D-2026-10-03-11. The free app does not mint field codes or handles.
4. After the person writes the minted code, the app can photograph it and confirm the reading matches that canonical form, on the device [ZZTHIS SPEC] spec 004 FR-017.
5. The person may tap "Get different words" up to 3 times. Each tap replaces the current code. At the cap, the current words stay (ZQ11). The cap count is [INFERRED].
6. The pitch lines `zz-kathy-lost-cat-zz` and `zz-moving-box-kitchen-3-zz` are examples in the copy [MICHAEL 2026-10-02]. Create does not claim those strings. Writing a code with no device at all stays zzThis Q25 and is not this screen.

### US2. Scan a handwritten code with the camera (P1)

As a person with a marked thing, I point the camera at a handwritten code and get that code, or a request to confirm or retry [ZZTHIS SPEC] spec 004 US1.

Acceptance:

1. Recognition runs on the device first. Only the canonical code goes to the API. The photo stays on the device unless the person confirms a retry [ZZTHIS SPEC] Section 10.3.
2. The reading passes the v1 grammar (zzThis Section 2.2a) before it is sent [ZZTHIS SPEC] spec 004 FR-008.
3. If the view holds more than one code, the person picks. The app never picks for them [ZZTHIS SPEC] spec 004 FR-013 and FR-014.
4. If the person denies the camera, typed entry still works (US2a).
5. Bands are the spec 004 bands: accept, clarify, retry, abstain. Threshold numbers stay parameters (zzThis Q37) [ZZTHIS SPEC].

The site's approved console labels (Manual, Rescan, Confirm, Resolve) are marketing microcopy [MICHAEL 2026-10-03]. They are not a second capture contract. The app follows spec 004.

### US2a. Type a code (P1)

As a person who cannot use the camera, I type the code. The same grammar accepts or rejects it [ZZTHIS SPEC] spec 004 US2.

Voice is not in the first release (ZQ10). The apps do not request the microphone.

### US3. Resolve to a message or page (P1)

As a person with a code, I see the record the server allows, or one not-found answer [ZZTHIS SPEC] spec 002 US1. The brief calls that record a useful page for a public surface such as a lost-cat flyer [ZZTHIS SPEC].

Acceptance:

1. The app calls resolve on the zzThis server with the canonical code only [DANNY 2026-10-04] [ZZTHIS SPEC].
2. Unknown, expired, used, and revoked codes show one not-found state [ZZTHIS SPEC] spec 002 FR-011.
3. The screen shows the title and the body from the server (ZQ5). It does not add a phone-number field.
4. The view is a screen in the app. This repo does not build a website (FR-036, ZQ12).
5. A signed-out person can open a public code (ZQ6). A private record stays on the one not-found state [ZZTHIS SPEC] spec 002 FR-020.

### US4. Share (P2)

As a person with a code, I share it so someone else can scan or type it [INFERRED] from "anyone can scan" [MICHAEL 2026-10-02].

Acceptance:

1. The share sheet sends the canonical code as text (FR-020, ZQ9).
2. It does not send an https URL. `share.url` in the proposed resolve body stays null.

### US5. Manage my codes (P2)

As a signed-in owner, I list my codes, add a record version, or revoke a code [ZZTHIS SPEC] spec 002 US2 and US3.

Acceptance:

1. Revoke and new record versions call the zzThis server [DANNY 2026-10-04].
2. The list screen stays unavailable until the server has a list API (DEP-002). The app does not keep a private ledger of issued codes as the source of truth.
3. A public resolve of a revoked code is not-found. The owner's own list may show a revoked state only when the server returns that state to the owner [INFERRED].

### US6. Offline behaviour (P2)

As a person without a connection, I can still read a record the app has cached, and I cannot issue or revoke [ZZTHIS SPEC] Section 10.5.

Acceptance:

1. A cached signed view of a reusable public record can be shown, labeled as offline.
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
| FR-008 | Snapping to the wordlist and local check-word verification follow zzThis specs 003 and 004. Until that library and list exist, the app must not show a local check word as passed. | [ZZTHIS SPEC]. The algorithm and list size are open there (Q30, Q31, Q32). |
| FR-009 | The app does not ask the server for nearby live codes. A miss is not-found. | [ZZTHIS SPEC] spec 002 FR-001 and FR-012. ZQ13 keeps suggestions off. |
| FR-010 | Unknown, expired, used, and revoked codes share one not-found presentation. | [ZZTHIS SPEC] spec 002 FR-011 |
| FR-011 | The camera permission is requested when the person starts a scan, with a purpose string. If they deny it, typed entry remains available. | [INFERRED] |
| FR-012 | Photo-library access uses the system picker, and only when a flow needs an existing photo. The camera path does not require the library. | [INFERRED] |
| FR-013 | The app does not request location permission. Duplicate public codes and GPS priority are out of the first release. | [ZZTHIS SPEC] Section 12.2. ZQ4. |
| FR-014 | The first release does not request microphone permission. If voice is added later, the audio stays on the device. | [ZZTHIS SPEC] spec 004 names voice. ZQ10 keeps it out of the first release. On-device audio is [INFERRED] from Section 10.3. |
| FR-015 | Issue, revoke, and record update require a signed-in owner. Unauthenticated write calls are the server's 401. | [ZZTHIS SPEC] spec 002 error states |
| FR-016 | A signed-out person can resolve a public code. The app must not require an account for that read. Writes still require sign-in. | [MICHAEL 2026-10-02] "anyone can scan". [ZZTHIS SPEC] spec 002 FR-020. ZQ6. The Section 10.6 line that every call is authenticated is an inferred sketch and does not win for this read. |
| FR-017 | Create calls mint. The server chooses the words and the check word for every code, including free public. The app shows that canonical form and the check word. It does not send a person-written code. "Get different words" calls re-roll at most 3 times for that code, and then the current words stay. | [ZZTHIS SPEC] spec 002 US2. ZQ11 [DANNY 2026-10-04]. The cap of 3 is [INFERRED]. |
| FR-018 | Until the server exposes a free public scope, create shows an honest unavailable state and does not issue. | [ZZTHIS SPEC] spec 002 FR-016 (free public is v2). ZQ3 is [#3](https://github.com/Zero-State-LLC/zzthat/issues/3). |
| FR-019 | The app never places a phone number or other personal data in the code string. | [ZZTHIS SPEC] Section 9a D-2026-10-03-11 |
| FR-020 | Share sends the canonical code as text and does not send a URL. | ZQ9. [MICHAEL 2026-10-02] "anyone can scan" is met by the words. |
| FR-021 | Revoke and new record versions use the server. A list of my codes waits on DEP-002. | [ZZTHIS SPEC] Section 10.6. DEP-002 is [INFERRED]. |
| FR-022 | The app may show a briefly cached signed record without a connection. It cannot issue, revoke, or mark a code used offline. | [ZZTHIS SPEC] Section 10.5 |
| FR-023 | The app does not cache single-use or short-expiry codes for a later resolve. | [INFERRED] from the edge rule in [ZZTHIS SPEC] Section 10.2 |
| FR-024 | The app may keep a local draft of a title and body, labeled "Not a code yet." That draft has no code words, is not issued, is not resolved, and is not shared as a live code. | [ZZTHIS SPEC] Section 10.5 forbids offline issue. ZQ16 allows the labeled draft. ZQ11: words come from mint, not from the draft. |
| FR-025 | Capture uses the spec 004 decision bands. Numeric thresholds are parameters, not constants invented here. | [ZZTHIS SPEC] spec 004. Thresholds are zzThis Q37. |
| FR-026 | When a photo or a block of text holds several code candidates, the person chooses one. | [ZZTHIS SPEC] spec 004 FR-013 [MICHAEL 2026-10-03] |
| FR-027 | A bare mark is reported as a bare mark and is not resolved in v1. The app offers typed entry. | [ZZTHIS SPEC] spec 004 FR-011 |
| FR-028 | The app displays server results for a reserved handle and for a check-word mismatch. It does not rewrite the code into a different live code. | [ZZTHIS SPEC] spec 002 FR-019 and FR-021 |
| FR-029 | No screen and no store text claims a measured accuracy, a pilot, a customer, an endorsement, or adoption. | [ZZTHIS SPEC] Section 2.1 |
| FR-030 | User-facing copy says "demo", not "mock", and "check word", not "checksum", and it contains no em dash. | [MICHAEL 2026-10-03] [DANNY 2026-10-04] [DANNY 2026-10-03] |
| FR-031 | Store submission, store spend, and a privacy-policy URL require Danny's yes. Listing text is the everyday-use sentences in ZQ15, without the web sentence. The age rating to file is Apple 12+ and Google Play Teen, and Danny may change it before submission. | [MICHAEL 2026-10-02] for the sentences. The rating is [INFERRED]. Constitution IX. |
| FR-032 | Every call uses the `/v1` prefix and sends `X-ZZ-Contract: 1`. Until the zzThis server publishes that contract, the client is tested with fixtures. | ZQ19. [ZZTHIS SPEC] Section 10.6 says the old sketch is not a contract. |
| FR-033 | On HTTP 429 the app waits for `Retry-After` and shows one wait message that does not say whether the code exists. | [ZZTHIS SPEC] spec 002 error states. Limits are ZQ20, proposed for the server. |
| FR-034 | If the app has accounts, it offers account deletion in the app before store submission. | [INFERRED] store practice. The server method is DEP-012. |
| FR-035 | The app offers a report with reason `spam`, `harassment`, `personal-data`, or `other`. It does not keep a block list. | [ZZTHIS SPEC] Section 2.3 for server-side abuse. The report action is ZQ8 [INFERRED]. |
| FR-036 | The resolved view is a screen in the app: a title and a body. | [ZZTHIS SPEC] "useful page", tagged [BRIEF]. ZQ5 and ZQ12. |
| FR-037 | The creation check (US1 acceptance 4) runs on the device. It compares the photo's parse to the minted canonical form. The app does not ask whether a person-written string is free. | [ZZTHIS SPEC] spec 004 FR-017. ZQ11. |
| FR-038 | Macros, drawn-symbol codes, object codes, the AI photo-to-action flow, and zzThing are not features of these apps. | [ZZTHIS SPEC] Section 12.2 and Section 2.6 |
| FR-039 | The same G9 input yields the same canonical form, or the same failure reason, on iOS and on Android. | [ZZTHIS SPEC] Section 2.2a. The two implementations are [INFERRED] until a shared package exists (zzThis Q29). |
| FR-040 | Sign-in is OAuth 2.0 and OpenID Connect with PKCE, through Sign in with Apple and Sign in with Google. Access tokens last 15 minutes. Refresh tokens rotate. Tokens live in the platform secure store. | ZQ7 and ZQ18. [INFERRED] thin-client practice. zzThis Q19 does not specify this. |
| FR-041 | The app caches a resolved public record only as that response's `Cache-Control` allows. An active, reusable, public, unauthenticated resolve uses `public, max-age=60, stale-while-revalidate=300`. Every other resolve sends `no-store` and is not stored. The app does not invent a longer lifetime. | zzThis spec 005 FR-018 [DANNY 2026-10-04] (Q26). Pin `497ea0d218ee0e7709c02ca4107846a062ff2ce2`. |

## API contract

The apps call the zzThis server. They do not run that server [DANNY 2026-10-04]. zzThis Section 10.6 is an inferred sketch, not a contract [ZZTHIS SPEC]. The shapes below are a proposal for the zzThis server, decided with ZQ19. Spec 002's not-found collapse still holds [ZZTHIS SPEC]. Paths use the `/v1` prefix. Every request and response carries `X-ZZ-Contract: 1`. JSON field names are snake_case. A code in a path is the canonical form, percent-encoded.

The app hides Create until discovery says the free public scope is on (ZQ3). It does not call mint, re-roll, or record writes before that.

### Discovery and version (DEP-009)

`GET /v1` requires no auth.

Response `200`:

```json
{
  "contract": "1",
  "free_public": false,
  "auth_providers": ["apple", "google"],
  "scopes": ["enterprise", "logistics"]
}
```

When the server adds the free public scope, `free_public` is true and `scopes` includes `free_public`.

`GET /v1/openapi.json` requires no auth and returns the OpenAPI document for this contract.

A request with a missing or unsupported `X-ZZ-Contract` value returns `400` and `{ "error": "contract-version" }`.

### Auth (DEP-003)

Sign-in is OAuth 2.0 and OpenID Connect with PKCE. Providers are Sign in with Apple and Sign in with Google (FR-040).

`POST /v1/auth/token` requires no session.

Request:

```json
{
  "provider": "apple",
  "code": "authorization-code",
  "code_verifier": "pkce-verifier",
  "redirect_uri": "https://example.invalid/auth"
}
```

`provider` is `apple` or `google`. `redirect_uri` is an https URL on the server allowlist `ZZ_REDIRECT_ALLOWLIST`. The contract does not use `zzthat://auth` [ZZTHIS SPEC] spec 005.

Response `200`:

```json
{
  "access_token": "access",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "refresh"
}
```

`POST /v1/auth/refresh` body is `{ "refresh_token": "refresh" }`. The response is a new pair. The previous refresh token stops working.

`POST /v1/auth/revoke` body is `{ "refresh_token": "refresh" }`. Response `204`.

`GET /v1/me` requires a bearer token.

Response `200`:

```json
{ "id": "account_id", "providers": ["apple"], "created_at": "2026-10-04T00:00:00Z" }
```

The account is that id and the provider list (ZQ7). There is no password, phone number, or profile field.

`DELETE /v1/me` (DEP-012) requires a bearer token. Response `204`. The server revokes that account's refresh tokens and revokes that account's active codes, which then resolve as not-found. Audit events stay, because the log is append-only [ZZTHIS SPEC] spec 002 FR-003. The words are not issued again. zzThis Q36 is still open there, and this follows the resolver prototype's "never reissue" default.

### Resolve a public code (DEP-004, DEP-005, DEP-011)

`GET /v1/resolve/{code}` allows no `Authorization` header when the record is public (ZQ6). A bearer token is sent when the person is signed in.

Response `200` for a public everyday code:

```json
{
  "view": "public",
  "canonical": "zz-kathy-lost-cat-zz",
  "record": {
    "title": "Lost cat",
    "body": "Answers on this page.",
    "updated_at": "2026-10-04T00:00:00Z"
  },
  "share": { "text": "zz-kathy-lost-cat-zz", "url": null }
}
```

`record.title` and `record.body` are the useful page (ZQ5). `share.text` is what the app shares. `share.url` is null in this contract (ZQ9). The app ignores `share.url` even if a later server fills it.

Response `404` is `{ "error": "not-found" }` for unknown, expired, used, and revoked, and for a private record read by a signed-out caller [ZZTHIS SPEC] spec 002 FR-011 and FR-020. The body is identical in those cases.

### Mint a wordlist code (DEP-001, DEP-006)

`POST /v1/codes` requires a bearer token. The body has no canonical code. The server chooses the words and the check word [ZZTHIS SPEC] spec 002 US2 (ZQ11).

Request:

```json
{
  "scope": "free_public",
  "record": { "title": "Lost cat", "body": "Answers on this page." }
}
```

The issued code is kind `plain`, stored in canonical form [ZZTHIS SPEC] spec 002 US2 and FR-013. It is a wordlist code: two or three data words plus a check word [ZZTHIS SPEC] Section 2.2. The server keeps it unique among active codes in the scope [ZZTHIS SPEC] spec 002 US2. It does not return a field code or a handle for this call. The words in the example below are a shape, not a code the app asks for.

Response `201`:

```json
{
  "id": "code_id",
  "canonical": "zz-copper-lantern-sky-zz",
  "check_word": "sky",
  "status": "active",
  "record_id": "record_id",
  "rerolls_remaining": 3
}
```

`check_word` is the last data-bearing word of `canonical`. The app shows both.

Until `free_public` is on, the response is `403` and `{ "error": "scope-unavailable" }`. If the plain-code issuer is not configured, the response is `503` and `{ "error": "not-ready" }`. The app treats both as the unavailable create state and shows no code (FR-018) [ZZTHIS SPEC] spec 005 FR-004.

If the server cannot store a unique code, it returns `500` and stores nothing [ZZTHIS SPEC] spec 002 FR-018. It does not return `taken` for the person to retry with different words.

### Get different words (DEP-008)

`POST /v1/codes/{id}/reroll` requires a bearer token. The body is empty. The server revokes the current code, does not issue those words again (the prototype default while zzThis Q36 is open), and returns a new wordlist code for the same record.

Response `200`:

```json
{
  "id": "code_id_2",
  "canonical": "zz-maple-harbor-stone-zz",
  "check_word": "stone",
  "status": "active",
  "record_id": "record_id",
  "rerolls_remaining": 2
}
```

The cap is 3 successful re-rolls after the first mint (ZQ11). The next call returns `403` and `{ "error": "reroll-cap" }`. The app hides "Get different words" and keeps the current words. A re-roll of a code the caller does not own is `403`. A re-roll while `free_public` is off is `403` and `{ "error": "scope-unavailable" }`.

### List, update, revoke (DEP-002)

`GET /v1/me/codes?limit=50` requires a bearer token. Optional `cursor`.

Response `200`:

```json
{
  "codes": [
    {
      "id": "code_id",
      "canonical": "zz-kathy-lost-cat-zz",
      "status": "active",
      "record_id": "record_id",
      "created_at": "2026-10-04T00:00:00Z"
    }
  ],
  "next_cursor": null
}
```

`status` for the owner may be `active` or `revoked`. A public resolve of a revoked code is still not-found.

`POST /v1/records/{id}/versions` requires a bearer token. Body: `{ "title": "Lost cat", "body": "Answers on this page." }`. Response `201` with the new version id. The server signs it. The app does not [ZZTHIS SPEC] spec 002 FR-002.

`POST /v1/codes/{id}/revoke` requires a bearer token. Response `200` and `{ "status": "revoked" }`.

The everyday app does not call `GET /audit`.

### Retry photo (DEP-007)

`POST /v1/reads` is multipart. Fields: `photo` (`image/jpeg`) and optional `canonical`. Auth is optional, because a signed-out person can scan (ZQ6). Anonymous calls use the tighter photo limit in DEP-010.

The app calls this only after the FR-005 sentence is accepted.

Response `200`:

```json
{ "canonical": "zz-kathy-lost-cat-zz", "band": "accept", "reason": null }
```

`band` is `accept`, `clarify`, `retry`, or `abstain` [ZZTHIS SPEC] spec 004. In v1 the server port is a placeholder: `POST /v1/reads` returns `abstain` until Danny chooses an image model [ZZTHIS SPEC] spec 005 Q18. The photo is jpeg, at most 8 MiB. A larger body is 413 `payload-too-large`. The server may store the photo for review of this read [ZZTHIS SPEC] Section 10.1. It is not a training set (ZQ14).

### Report (ZQ8)

`POST /v1/reports` allows no auth.

Request:

```json
{ "canonical": "zz-kathy-lost-cat-zz", "reason": "spam", "note": "" }
```

`reason` is `spam`, `harassment`, `personal-data`, or `other`.

Response `202` and `{ "id": "report_id" }` when the body parses, including when the code is unknown. The call must not be a way to learn that a code exists.

### Rate limits (DEP-010)

Limits are per client, per role, and per code in the resolver spec, and the numbers were not set [ZZTHIS SPEC] spec 002 FR-006 and Q26. This proposal sets starting numbers:

| Call | Limit |
|---|---|
| `GET /v1/resolve/{code}` | 60 per minute per IP, and 120 per minute per signed-in user |
| `POST /v1/codes` and `POST /v1/codes/{id}/reroll` | 10 per hour per user, shared |
| `POST /v1/reads` | 5 per hour per IP, and 20 per hour per signed-in user |
| `POST /v1/reports` | 10 per hour per IP |

`429` body is `{ "error": "rate-limited" }` and does not depend on whether the code exists. `Retry-After` is the wait in seconds. The app honors it (FR-033).

### Errors

| Server result | What the person sees |
|---|---|
| 200, public code | The title and body (US3) |
| 404 `not-found` | One not-found state (FR-010) |
| 400 grammar failure | Plain language, no other code offered (FR-009) |
| 400 `malformed` with reason `check-mismatch` | Confirm that code. Do not auto-correct [ZZTHIS SPEC] spec 004 FR-016 and spec 005 FR-010 |
| 400 `contract-version` | Ask the person to update the app |
| 422 bare mark | Not resolved in v1. Offer typing (FR-027) |
| 422 `reserved-handle` | Shown only if a typed handle is refused (FR-028). Create does not issue handles |
| 403 `reroll-cap` | Keep the current words. Hide "Get different words" (FR-017) |
| 403 `scope-unavailable` or 503 `not-ready` | Create is unavailable (FR-018). `not-ready` means the plain-code issuer is not configured [ZZTHIS SPEC] spec 005 FR-004 |
| 401 on a write | Sign-in (FR-015) |
| 403 otherwise | The person is not allowed to do that |
| 429 | Wait for `Retry-After` (FR-033) |
| 500 on a write | Nothing was saved [ZZTHIS SPEC] spec 002 FR-018 |
| No connection | Offline rules (US6) |

### Offline cache rules

1. Store a resolve only when its `Cache-Control` allows it (FR-041). For an active, reusable, public, unauthenticated resolve that is `public, max-age=60, stale-while-revalidate=300` [ZZTHIS SPEC] spec 005 FR-018. The server purges that key on record update, revoke, or expiry. If a purge fails, 60 seconds is the worst case on the edge. The app still stops serving its copy when those directives say so.
2. The cache is not the source of truth. The next online resolve replaces it.
3. Do not store a response that sends `no-store`. That includes single-use codes, short-expiry codes, private records, authenticated responses, 404, and 429 (FR-023) [ZZTHIS SPEC] spec 005 FR-018.
4. Do not issue, revoke, or mark used from the cache (FR-022).
5. Do not cache not-found as a permanent answer.
6. The last canonical text may be kept so resolve can be retried. The photo stays on the device unless FR-005 was accepted (FR-004).
7. A draft labeled "Not a code yet" is local only (FR-024).
8. The app does not verify record signatures until the server publishes key distribution (zzThis Q28). A cached view is not a claim that the phone checked the signature [INFERRED].

### Proposal status

The wire shapes are the pinned file [`contracts/zzthis-v1.openapi.yaml`](../../contracts/zzthis-v1.openapi.yaml), copied from zzthis `specs/005-v1-api/openapi.yaml` at `497ea0d218ee0e7709c02ca4107846a062ff2ce2` (draft PR #59). `contracts/ZZTHIS-API-PIN` holds that sha. This repository does not implement the server. Create stays gated on DEP-001. There is no person-written claim contract and no uniqueness-check contract. The everyday app does not call `GET /v1/audit`.

| ID | Proposal |
|---|---|
| DEP-001 | `free_public` on `GET /v1`, and `403` `scope-unavailable` until then |
| DEP-002 | `GET /v1/me/codes` |
| DEP-003 | `POST /v1/auth/token`, `/auth/refresh`, `/auth/revoke`, and `GET /v1/me` |
| DEP-004 | `GET /v1/resolve/{code}` with no session for a public record |
| DEP-005 | `record.title` (1 to 120 characters) and `record.body` (0 to 4000). Title is required. Body may be empty. |
| DEP-006 | `POST /v1/codes` mints a server-chosen wordlist code and returns `check_word` |
| DEP-007 | `POST /v1/reads` (`submitRead`). v1 returns `abstain`. |
| DEP-008 | `POST /v1/codes/{id}/reroll`, cap 3, previous code revoked |
| DEP-009 | `/v1`, `X-ZZ-Contract: 1`, `GET /v1/openapi.json` |
| DEP-010 | The limit table and `Retry-After` |
| DEP-011 | `share.text` plus `share.url: null` on resolve. No URL resource |
| DEP-012 | `DELETE /v1/me` |

The wordlist, the check-word algorithm, and capture thresholds stay in zzThis specs 003 and 004 (Q30, Q31, Q32, Q37). Local snap (FR-008) waits on spec 003. The grammar parser (FR-007) does not.

## On-device and cloud recognition

- Option A ships first: on device where it is good enough, cloud vision only after the FR-005 confirm [ZZTHIS SPEC] Section 10.3 and Section 10.8.
- Option B stays a zzThis benchmark, not a zzThat deliverable [ZZTHIS SPEC].
- zzThis Q18 stays open: Danny is choosing the image model. The v1 server placeholder returns `abstain` from `POST /v1/reads` [ZZTHIS SPEC] spec 005. The app does not upload a photo by default (FR-004, FR-005). An `abstain` result uses that capture band and does not invent a reading.
- The app does not train a model [INFERRED].
- No accuracy figure is a release criterion (FR-029).
- Voice is out of the first release (FR-014).

## Privacy and permissions

| Permission or data | Rule | Source |
|---|---|---|
| Camera | Asked when a scan starts. Purpose string required. | FR-011 [INFERRED] |
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
- Copied marks, replay, enumeration, unauthorized updates, and malformed input stay server-side [ZZTHIS SPEC] Section 2.3. The app's part is FR-009, FR-010, FR-033, and the report in FR-035.
- No duplicate codes and no location (FR-013) [ZZTHIS SPEC] Section 12.2.

## App store requirements

- Listing text: "Free for everyone. Write a code on a lost-pet flyer, a moving box, a garage-sale item, or a note, and anyone can scan it, like a QR code you can write by hand." [MICHAEL 2026-10-02]. The web sentence from that same paragraph is not in this listing, because this listing is the phone apps.
- Age rating to file: Apple 12+ and Google Play Teen [INFERRED], because the page is user-written text. Danny may change it before he submits.
- Privacy disclosure matches the table above.
- Account deletion is in the app before submission (FR-034), calling DEP-012.
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
| SC-1 | iOS and Android each pass every G9 vector (FR-007, FR-039). |
| SC-2 | Resolve sends the canonical code and does not send a photo. Mint sends no canonical code and no photo (FR-004, FR-006, FR-017). |
| SC-3 | Unknown, expired, used, and revoked codes show one not-found state (FR-010). |
| SC-4 | A photo upload happens only after the FR-005 sentence (FR-005). |
| SC-5 | The repo contains no resolver and no database of record (FR-002). |
| SC-6 | Offline: a cached public resolve is shown only while its `Cache-Control` lifetime allows; `no-store` responses are not kept; issue and revoke are refused (FR-022, FR-041). |
| SC-7 | User-facing strings follow FR-030. |
| SC-8 | While `free_public` is false, create stays unavailable and mints nothing (FR-018). |
| SC-9 | A signed-out person can open a public fixture code (FR-016). |

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

## Terms

zzThis spec 003 says "check word". zzThis `docs/SPEC.md` also says "checksum word" for the same extra word [ZZTHIS SPEC]. User-facing zzThat copy says "check word" only (FR-030).

"Demo" is the word for sample data and scripted behavior. Do not use "mock" in user-facing copy (FR-030).

## Out of scope

- A web client in this repo (FR-001). The later web client is a zzthis task (ZQ1). The "Try zzThat" link target is the zzThis marketing page, not a zzthat.com site (ZQ2). zzThis spec 001 FR-016 still names zzthat.com; this repo does not edit that file.
- A person-written claim, and a client uniqueness check (ZQ11).
- zzThing [ZZTHIS SPEC].
- A backend, resolver, wordlist pipeline, or check-word algorithm in this repo.
- Logistics, postal, and enterprise product surfaces, except where the shared API requires the client to tolerate those code kinds.
- Macro execution, drawn symbols, object codes, and the photo-to-action concept [ZZTHIS SPEC] Section 12.2 and Section 2.6.
- Option B model training [ZZTHIS SPEC] Section 10.8.
- Payments, partner integrations, and store submission itself (FR-031).
- Voice in the first release (FR-014).
- Duplicate public codes and location (FR-013).
- Live-code suggestions (FR-009).

## Runtime

The build contract is [runtime.md](runtime.md): project trees, modules, navigation, screen states (loading, empty, error, offline, demo), the generated API client, auth, storage, an analytics-free privacy stance, accessibility, localization, testing, and the release pipeline. Visuals follow [design.md](design.md). No screen invents a token or a UX pattern.

## Decisions

ZQ1 to ZQ21 are decided in [decisions.md](decisions.md). They are not open. zzThis Q18, Q25 for a code written with no device, Q30 to Q32, Q36, and Q37 remain open in zzThis. This spec does not close them. Design sync follows [design.md](design.md) and zzthis `scripts/pin-design.sh`. Q18's model is still Danny's. The v1 read returns `abstain`.

## Workflows

| Item | Value |
|---|---|
| Governing workflows | anti-slop-code, production-systems, google-developer-style |
| Product owner | Michael Chung (zzmojo) |
| Operator | Danny |
| CI to reuse | None exists in this repo. Do not add a workflow in the spec-only change. The plan names `.github/workflows/apps-ci.yml` as the file an implementation change may add. |
| Human gates | Store submission, spend, production credentials, privacy-policy publication (constitution IX) |
