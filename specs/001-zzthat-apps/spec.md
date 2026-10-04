# Feature spec: zzThat apps

Feature ID: 001-zzthat-apps
Status: not built. Specification only. No application source in this change.
Phase: specify (what and why). The how is in [plan.md](plan.md).
Constitution: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) v1.0.0.
Source pin: zzThis `0a5c9b93e8f8266ff9d0855f4afe2dafbe1d26d7`.

## Why

zzThat is the free everyday-use app for zz codes: handwritten codes such as `zz-kathy-lost-cat-zz` that a person can write on a lost-pet flyer, a moving box, a garage-sale item, or a note, and that another person can scan [MICHAEL 2026-10-02]. It is the apps portion of zzThis. The phone apps are API clients of one central server [ZZTHIS SPEC] [DANNY 2026-10-04].

The apps are not specified inside zzThis. zzThis Q33 is still open there, and Section 12.2 lists the apps as an unspecified product [ZZTHIS SPEC]. This spec is that missing product spec for iOS and Android only.

## Users

| User | Need | Source |
|---|---|---|
| Person creating a code | Get a code they can write by hand on an everyday object | [MICHAEL 2026-10-02] |
| Person scanning a code | Point a camera, or type, and see the record they are allowed to see | [ZZTHIS SPEC] spec 004 US1 and spec 002 US1 |
| Signed-in owner | See their codes, update a record, revoke a code | [ZZTHIS SPEC] spec 002 US2 and US3; the list endpoint is missing (DEP-002) |

Auditors, partner systems, logistics clerks, and postal flows are users of zzThis. They are not users of this app [INFERRED]. zzThing is a different product [ZZTHIS SPEC].

## User stories

### US1. Create a code (P1)

As a person, I create a zz code in the app and write it on a thing, so that someone else can scan it [MICHAEL 2026-10-02].

Acceptance:

1. The app asks the zzThis server to issue the code. The server chooses the words and the check word [ZZTHIS SPEC] spec 002 US2. The app shows the canonical form for the person to copy by hand.
2. If the server has no free public scope, the app does not invent a code. It shows an unavailable state (FR-018).
3. A code string does not contain a phone number or other personal data [ZZTHIS SPEC] Section 9a D-2026-10-03-11.
4. After the person writes the code, the app can photograph it and confirm the reading matches that canonical form, on the device [ZZTHIS SPEC] spec 004 FR-017.
5. Claiming a code the person composed (the pitch examples `zz-kathy-lost-cat-zz` and `zz-moving-box-kitchen-3-zz`) stays closed until [#11](https://github.com/Zero-State-LLC/zzthat/issues/11) (ZQ11, zzThis Q25).

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

Voice is not in this story. See ZQ10.

### US3. Resolve to a message or page (P1)

As a person with a code, I see the record the server allows, or one not-found answer [ZZTHIS SPEC] spec 002 US1. The brief calls that record a useful page for a public surface such as a lost-cat flyer [ZZTHIS SPEC].

Acceptance:

1. The app calls resolve on the zzThis server with the canonical code only [DANNY 2026-10-04] [ZZTHIS SPEC].
2. Unknown, expired, used, and revoked codes show one not-found state [ZZTHIS SPEC] spec 002 FR-011.
3. The screen shows the role-scoped view the server returns. The app does not add fields the server did not send (ZQ5).
4. The view is a screen in the app. This repo does not build a website (FR-036).

### US4. Share (P2)

As a person with a code, I share it so someone else can scan or type it [INFERRED] from "anyone can scan" [MICHAEL 2026-10-02].

Acceptance:

1. The share sheet sends the canonical code as text (FR-020).
2. It does not send a URL, because no public URL is specified (DEP-011, ZQ9).

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
5. No labeled draft of an unissued code until ZQ16 says yes.

## Functional requirements

| ID | Requirement | Source |
|---|---|---|
| FR-001 | The products in this repo are an iOS app and an Android app. No web client is built here. | [DANNY 2026-10-04]. The public sentence that also names the web is [MICHAEL 2026-10-02] and stays open as ZQ1 ([#1](https://github.com/Zero-State-LLC/zzthat/issues/1)). |
| FR-002 | The apps are thin clients. They contain no resolver, no database of record, and no second HTTP API. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.1 |
| FR-003 | Resolve, issue, revoke, and record updates are HTTP calls to the zzThis central server. | [DANNY 2026-10-04] [ZZTHIS SPEC] Section 10.6 |
| FR-004 | Recognition runs on the device first. The photo stays on the device by default. | [ZZTHIS SPEC] Section 10.3 |
| FR-005 | A photo is uploaded only after the person confirms a retry or hard case. | [ZZTHIS SPEC] Section 10.3. Consent copy is ZQ14 ([#14](https://github.com/Zero-State-LLC/zzthat/issues/14)). |
| FR-006 | Every reading is parsed with the v1 text grammar, and the client sends only the canonical form. | [ZZTHIS SPEC] Section 2.2a and spec 004 FR-008 |
| FR-007 | The apps do not publish a second grammar. iOS and Android both pass every G9 vector in zzThis Section 2.2a. | [ZZTHIS SPEC] |
| FR-008 | Snapping to the wordlist and local check-word verification follow zzThis specs 003 and 004. Until that library and list exist, the app must not show a local check word as passed. | [ZZTHIS SPEC]. The algorithm and list size are open there (Q30, Q31, Q32). |
| FR-009 | The app does not ask the server for nearby live codes. A miss is not-found. | [ZZTHIS SPEC] spec 002 FR-001. Free-app suggestions stay off unless ZQ13 ([#13](https://github.com/Zero-State-LLC/zzthat/issues/13)) says otherwise. |
| FR-010 | Unknown, expired, used, and revoked codes share one not-found presentation. | [ZZTHIS SPEC] spec 002 FR-011 |
| FR-011 | The camera permission is requested when the person starts a scan, with a purpose string. If they deny it, typed entry remains available. | [INFERRED] |
| FR-012 | Photo-library access uses the system picker, and only when a flow needs an existing photo. The camera path does not require the library. | [INFERRED] |
| FR-013 | The app does not request location permission while ZQ4's default holds. | [ZZTHIS SPEC] Section 12.2 files local priority as v2. ZQ4 is [#4](https://github.com/Zero-State-LLC/zzthat/issues/4). |
| FR-014 | The first release does not request microphone permission. Voice waits on ZQ10. | [INFERRED] default. Voice as a zzThis input is [ZZTHIS SPEC] spec 004. ZQ10 is [#10](https://github.com/Zero-State-LLC/zzthat/issues/10). |
| FR-015 | Issue, revoke, and record update require a signed-in owner. Unauthenticated write calls are the server's 401. | [ZZTHIS SPEC] spec 002 error states |
| FR-016 | A signed-out person can resolve a public code only when the server allows that call. Otherwise the app asks them to sign in. | [INFERRED] bridge between [MICHAEL 2026-10-02] "anyone can scan" and [ZZTHIS SPEC] Section 10.6. ZQ6 is [#6](https://github.com/Zero-State-LLC/zzthat/issues/6). |
| FR-017 | Create shows a server-issued code. A person-composed claim is out of the first build until ZQ11. | [ZZTHIS SPEC] spec 002 US2. Pitch examples are [MICHAEL 2026-10-02]. ZQ11 is [#11](https://github.com/Zero-State-LLC/zzthat/issues/11). |
| FR-018 | Until the server exposes a free public scope, create shows an honest unavailable state and does not issue. | [ZZTHIS SPEC] spec 002 FR-016 (free public is v2). ZQ3 is [#3](https://github.com/Zero-State-LLC/zzthat/issues/3). |
| FR-019 | The app never places a phone number or other personal data in the code string. | [ZZTHIS SPEC] Section 9a D-2026-10-03-11 |
| FR-020 | Share sends the canonical code as text and does not send a URL. | [INFERRED]. ZQ9 is [#9](https://github.com/Zero-State-LLC/zzthat/issues/9). |
| FR-021 | Revoke and new record versions use the server. A list of my codes waits on DEP-002. | [ZZTHIS SPEC] Section 10.6. DEP-002 is [INFERRED]. |
| FR-022 | The app may show a briefly cached signed record without a connection. It cannot issue, revoke, or mark a code used offline. | [ZZTHIS SPEC] Section 10.5 |
| FR-023 | The app does not cache single-use or short-expiry codes for a later resolve. | [INFERRED] from the edge rule in [ZZTHIS SPEC] Section 10.2 |
| FR-024 | The app does not keep an offline draft that could be mistaken for an issued code, until ZQ16 says otherwise. | [ZZTHIS SPEC] Section 10.5 for the ban on offline issue. The draft idea is [INFERRED] and unapproved. ZQ16 is [#16](https://github.com/Zero-State-LLC/zzthat/issues/16). |
| FR-025 | Capture uses the spec 004 decision bands. Numeric thresholds are parameters, not constants invented here. | [ZZTHIS SPEC] spec 004. Thresholds are zzThis Q37. |
| FR-026 | When a photo or a block of text holds several code candidates, the person chooses one. | [ZZTHIS SPEC] spec 004 FR-013 [MICHAEL 2026-10-03] |
| FR-027 | A bare mark is reported as a bare mark and is not resolved in v1. The app offers typed entry. | [ZZTHIS SPEC] spec 004 FR-011 |
| FR-028 | The app displays server results for a reserved handle and for a check-word mismatch. It does not rewrite the code into a different live code. | [ZZTHIS SPEC] spec 002 FR-019 and FR-021 |
| FR-029 | No screen and no store text claims a measured accuracy, a pilot, a customer, an endorsement, or adoption. | [ZZTHIS SPEC] Section 2.1 |
| FR-030 | User-facing copy says "demo", not "mock", and "check word", not "checksum", and it contains no em dash. | [MICHAEL 2026-10-03] [DANNY 2026-10-04] [DANNY 2026-10-03] |
| FR-031 | Store submission, store spend, and a privacy-policy URL require Danny's yes. Listing copy waits on ZQ15. | [INFERRED] from constitution IX. ZQ15 is [#15](https://github.com/Zero-State-LLC/zzthat/issues/15). |
| FR-032 | Once ZQ19 names a contract version, every call sends it. Until then the client is tested with fixtures, not a live server. | [INFERRED]. ZQ19 is [#19](https://github.com/Zero-State-LLC/zzthat/issues/19). |
| FR-033 | On HTTP 429 the app backs off and shows one wait message that does not say whether the code exists. | [ZZTHIS SPEC] spec 002 error states. Values are ZQ20 ([#20](https://github.com/Zero-State-LLC/zzthat/issues/20)). |
| FR-034 | If the app has accounts, it offers account deletion in the app before store submission. | [INFERRED] store practice. The server method is DEP-012. |
| FR-035 | No report or block flow until ZQ8 is answered. | [INFERRED]. Abuse cases the server must defend are [ZZTHIS SPEC] Section 2.3. ZQ8 is [#8](https://github.com/Zero-State-LLC/zzthat/issues/8). |
| FR-036 | The resolved view is a screen in the app. | [INFERRED] default for ZQ12 ([#12](https://github.com/Zero-State-LLC/zzthat/issues/12)). "Useful page" is [ZZTHIS SPEC] tagged [BRIEF]. |
| FR-037 | The creation check (US1 acceptance 4) runs on the device. An online uniqueness check runs only when the server requires it. | [ZZTHIS SPEC] spec 004 FR-017 |
| FR-038 | Macros, drawn-symbol codes, object codes, the AI photo-to-action flow, and zzThing are not features of these apps. | [ZZTHIS SPEC] Section 12.2 and Section 2.6 |
| FR-039 | The same G9 input yields the same canonical form, or the same failure reason, on iOS and on Android. | [ZZTHIS SPEC] Section 2.2a. The two implementations are [INFERRED] until a shared package exists (zzThis Q29). |

## API contract

The apps call the zzThis server. They do not define a private API [DANNY 2026-10-04]. zzThis Section 10.6 is an INFERRED sketch, not a contract. Spec 002's error table is the prototype behavior the client must handle [ZZTHIS SPEC]. Paths below are that sketch. They become a contract only when ZQ19 publishes a version.

### Endpoints the apps need, and where zzThis stands

| App need | Sketched call | Client rule | Gap |
|---|---|---|---|
| Issue a code | `POST /codes` | Show the canonical code the server returns. Send format, expiry, single use, and the linked record only in the shape the server publishes. | Free public scope is v2 (DEP-001). Person-composed claim is missing (DEP-006). |
| Resolve | `GET /resolve/{code}` | Send the canonical form only. No photo on this call. | Signed-out access is unsettled (DEP-004). Everyday body fields are missing (DEP-005). |
| Revoke | `POST /codes/{id}/revoke` | Owner only. After success, do not present the code as active. | None in the sketch. Still not a versioned contract (DEP-009). |
| New record version | `POST /records/{id}/versions` | Owner only. The app does not sign the record. The server does. | Body schema for an everyday message is DEP-005. |
| Audit | `GET /audit` | The everyday app does not call this. | Not an app endpoint [INFERRED]. |
| List my codes | none | Do not invent a local ledger. | DEP-002 |
| End-user session | none (Q19 is partner auth) | No invented login protocol. | DEP-003 |
| Retry photo | R2 is named; no path is named | Upload only after confirm. | DEP-007 |
| Uniqueness check during creation | none | On-device read-back does not need it. The online check does. | DEP-008 |
| Delete account | none | Required before store submission if accounts exist. | DEP-012 |

### Auth

- Write calls that are not authenticated get 401. Authenticated calls that are not allowed get 403, and the server writes an audit event [ZZTHIS SPEC] spec 002.
- The app stores only the session material the server tells it to store. It does not embed record-signing keys [INFERRED]. Where those keys live is zzThis Q28, and the apps do not need the private key.
- Partner auth (zzThis Q19) is not the everyday user mechanism. Everyday auth is ZQ18 ([#18](https://github.com/Zero-State-LLC/zzthat/issues/18)).
- What the account is (fields, recovery, free versus paid) is ZQ7 ([#7](https://github.com/Zero-State-LLC/zzthat/issues/7)).

### Rate limits

Limits are per client, per role, and per code [ZZTHIS SPEC] spec 002 FR-006. The numbers are zzThis Q26, tracked here as ZQ20. A 429 body must not depend on whether the code exists [ZZTHIS SPEC]. The app obeys FR-033. It does not retry in a tight loop and it does not walk the wordlist against the server [INFERRED].

### Errors

The client maps the spec 002 prototype table as follows. Status codes may change with zzThis Q19 [ZZTHIS SPEC]. The not-found collapse must not change.

| Server result | What the person sees |
|---|---|
| 200, active code they may see | The role-scoped view (US3) |
| 404, unknown, expired, used, or revoked | One not-found state. The words do not say which of the four it was (FR-010). |
| 400, grammar failure | The failure in plain language, without a different code suggested (FR-009). |
| 400, check-word mismatch | A confirm step for that code. The app does not auto-correct to another valid code [ZZTHIS SPEC] spec 004 FR-016. |
| 422, bare mark | A bare mark is not resolved in v1. Offer typing (FR-027). |
| 422, reserved handle | The name cannot be issued. Do not reveal why beyond the server's `reserved-handle` result [ZZTHIS SPEC] spec 002 FR-019. |
| 409, handle taken | The name is taken. Do not show the owner [ZZTHIS SPEC]. |
| 401 on a write | Sign-in (FR-015). |
| 403 | The person is not allowed to do that. |
| 429 | One wait state (FR-033). |
| 500 on a write | The change did not save. Do not show it as saved [ZZTHIS SPEC] spec 002 FR-018. |
| No connection | Offline rules (US6). |

### Versioning

No version is specified [ZZTHIS SPEC] Section 10.6. ZQ19 must name one before a shipped build calls a live server (FR-032). Fixture tests in this repo do not invent a version string and then call production.

### Offline cache rules

1. The cache holds signed record views the server already returned, for a short time [ZZTHIS SPEC] Section 10.5. "Short" has no number. Do not invent a TTL. Use a placeholder the plan records, and replace it when Danny sets one [INFERRED].
2. The cache is not the source of truth. A later online resolve wins [INFERRED].
3. Do not cache single-use or short-expiry codes (FR-023).
4. Do not issue, revoke, or mark used from the cache (FR-022).
5. Do not cache a not-found result as a permanent answer [INFERRED]. A code issued later must still resolve.
6. The last canonical text the device read may be kept so resolve can be retried. The photo stays on the device (FR-004).
7. The app does not verify record signatures until the server publishes a key distribution (zzThis Q28). Showing a cached view is not a claim that the phone checked the signature [INFERRED].

### Server APIs this product needs and zzThis does not yet specify

These are dependencies on the zzThis server. This repo does not implement them.

| ID | Missing API | Why the apps need it | Issue |
|---|---|---|---|
| DEP-001 | Free public scope on issue and resolve | Spec 002 FR-016 limits v1 scopes to enterprise and logistics. Free public is v2. Everyday zzThat codes are free public. | [#3](https://github.com/Zero-State-LLC/zzthat/issues/3) |
| DEP-002 | List codes for the signed-in owner | US5. Section 10.6 has no list call. | [#27](https://github.com/Zero-State-LLC/zzthat/issues/27) |
| DEP-003 | End-user register, session, sign-out | Writes return 401 without auth. Q19 covers partner apps only. | [#7](https://github.com/Zero-State-LLC/zzthat/issues/7), [#18](https://github.com/Zero-State-LLC/zzthat/issues/18) |
| DEP-004 | A defined rule for resolve without a session | "Anyone can scan" conflicts with "every call is authenticated". | [#6](https://github.com/Zero-State-LLC/zzthat/issues/6) |
| DEP-005 | Record body for an everyday message or page | US3. Section 10.7 has a `body` column and no everyday fields. | [#5](https://github.com/Zero-State-LLC/zzthat/issues/5) |
| DEP-006 | Claim a person-written code | Pitch examples are human-composed. US2 of spec 002 says the server picks the words. Q25 is open. | [#11](https://github.com/Zero-State-LLC/zzthat/issues/11) |
| DEP-007 | Photo upload for retry and hard case | Spec 004 US3. R2 is named. No path is named. | [#14](https://github.com/Zero-State-LLC/zzthat/issues/14) |
| DEP-008 | Online uniqueness check for creation | Spec 004 FR-017, only when the scope requires it. | [#25](https://github.com/Zero-State-LLC/zzthat/issues/25) |
| DEP-009 | A published contract version | Shipped binaries outlive one server revision. | [#19](https://github.com/Zero-State-LLC/zzthat/issues/19) |
| DEP-010 | Rate-limit numbers | The 429 shape exists. The numbers do not (Q26). | [#20](https://github.com/Zero-State-LLC/zzthat/issues/20) |
| DEP-011 | Public URL, only if share grows past text | Web is out of this repo. No URL shape is specified. | [#9](https://github.com/Zero-State-LLC/zzthat/issues/9) |
| DEP-012 | Account deletion | FR-034, if accounts ship. | [#29](https://github.com/Zero-State-LLC/zzthat/issues/29) |

Other blockers that are not missing HTTP routes: the wordlist and check-word algorithm (zzThis spec 003, Q30 to Q32), capture thresholds (Q37), and where resolver code lives (Q29). Local snap (FR-008) waits on spec 003. The grammar parser (FR-007) does not.

## On-device and cloud recognition

Both paths are already specified for zzThis. This spec only binds the apps to them.

- Option A ships first: an existing vision model, on device where it is good enough, cloud vision for a retry or hard case [ZZTHIS SPEC] Section 10.8, where that recommendation is tagged [OPERATOR 2026-10-02].
- Option B, a fine-tuned model, is a benchmark in zzThis spec 004, not a zzThat deliverable [ZZTHIS SPEC].
- Whether Option A must include an on-device model, or whether US2 may fall back to cloud vision, is zzThis Q18 and is still open [ZZTHIS SPEC]. Until that answer, FR-004 and FR-005 stand: do not upload a photo by default.
- The app does not train a model and does not ship a training set [INFERRED].
- No accuracy figure is a release criterion (FR-029).

## Privacy and permissions

| Permission or data | Rule | Source |
|---|---|---|
| Camera | Used to scan. Asked when scanning starts. Purpose string required. | FR-011 [INFERRED] |
| Photos | System picker only, and only if the person selects an existing photo. Not used for the live camera path. | FR-012 [INFERRED] |
| Photo upload | Off unless the person confirms a retry. | FR-005 [ZZTHIS SPEC] |
| Microphone | Not requested in the first release. | FR-014 |
| Location | Not requested while duplicate local priority stays out of scope. | FR-013 [ZZTHIS SPEC] |
| Canonical code | Sent on resolve and issue. | FR-006 [ZZTHIS SPEC] |
| Account identifier | Sent on writes once auth exists. | FR-015 |
| Personal data inside the code | Forbidden. | FR-019 [ZZTHIS SPEC] |
| Record body | Whatever the server returns for the useful page. The app shows that view and does not add collection of its own. What the page contains is ZQ5. | [ZZTHIS SPEC] "useful page". Fields are ZQ5. |

The code on paper is a public identifier. Authorization stays on the server [ZZTHIS SPEC] Section 2.7.

## Accounts and abuse

- Creating and changing codes requires an authenticated owner (FR-015) [ZZTHIS SPEC].
- Reserved handles are refused by the server, including names such as `zzthat`, compared on the matching key [ZZTHIS SPEC] spec 002 FR-019. The app shows the refusal (FR-028).
- One-part codes and private records follow spec 002 FR-020: a private record is not opened by a one-part code for a signed-out caller [ZZTHIS SPEC].
- Server abuse cases (copied marks, replay, enumeration, unauthorized updates, malformed input) are enforced on the server [ZZTHIS SPEC] Section 2.3. The app's part is FR-009, FR-010, and FR-033.
- A human report and block path is not specified. It stays out until ZQ8 [INFERRED].
- Free public duplicates and GPS priority are v2 in zzThis and stay out of the first release unless ZQ4 pulls them in [ZZTHIS SPEC] [MICHAEL 2026-10-03].

## App store requirements

These are release obligations, not a claim that a listing exists.

- The privacy disclosure matches the table above: canonical codes, account identifiers on writes, photos only after a confirmed retry [INFERRED].
- Camera and photo purpose strings match real use (FR-011, FR-012) [INFERRED].
- If accounts exist, the app includes account deletion (FR-034) [INFERRED].
- Age rating and listing copy are ZQ15. Everyday public messages are a rating input, and the rating is not chosen here.
- No accuracy or adoption claims (FR-029) [ZZTHIS SPEC].
- Danny submits. This spec does not authorize a submission (FR-031).

## Platform choice

Danny has not chosen a UI stack. Two options:

| | Native Swift and Kotlin | A cross-platform framework |
|---|---|---|
| Camera and on-device vision | Platform APIs, which Option A needs [ZZTHIS SPEC] | The same platform APIs, through native modules |
| Grammar | Two implementations, locked by the G9 vectors [ZZTHIS SPEC] | One implementation if the framework can host it, still locked by G9 |
| Web | Not a goal of this repo [DANNY 2026-10-04] | A web target would be extra scope this repo excludes |
| Store binaries | Two | Still two |

Recommendation [INFERRED], default until [#17](https://github.com/Zero-State-LLC/zzthat/issues/17) is answered: native Swift for iOS and Kotlin for Android. The specified product path is on-device recognition with the platform camera [ZZTHIS SPEC] Section 10.3, and this repo does not ship a web client [DANNY 2026-10-04]. The grammar is a pure function [ZZTHIS SPEC] spec 003 FR-015, so each app can implement it and fail CI on any G9 mismatch (FR-039).

The zzThis plan's note that the grammar library should be TypeScript is [INFERRED] in that plan. It is not a decision that these apps use a JavaScript framework. If Danny picks a framework, FR-004, FR-007, and FR-039 still apply.

Minimum OS versions are ZQ21 ([#21](https://github.com/Zero-State-LLC/zzthat/issues/21)). None are stated in zzThis.

## Success criteria

No recognition accuracy number is a criterion [ZZTHIS SPEC] Q24.

| ID | Pass when |
|---|---|
| SC-1 | iOS and Android each pass every G9 vector (FR-007, FR-039). |
| SC-2 | Resolve and issue send the canonical code and do not send a photo on that call (FR-004, FR-006). |
| SC-3 | Unknown, expired, used, and revoked codes show one not-found state (FR-010). |
| SC-4 | A photo upload happens only after an explicit retry confirm (FR-005). |
| SC-5 | The repo contains no resolver and no database of record (FR-002). |
| SC-6 | Offline: a cached signed public record can be shown; issue and revoke are refused (FR-022). |
| SC-7 | User-facing strings follow FR-030. |
| SC-8 | With no free public scope on the server, create stays unavailable and issues nothing (FR-018). |

## Edge cases

- Camera denied: typed entry still works (FR-011).
- Two codes in one photo: the person picks (FR-026).
- A code across two lines: one code, per the grammar [ZZTHIS SPEC] Section 2.2a. The app does not restate the rule.
- Bare mark: not resolved (FR-027).
- Check-word mismatch: confirm, do not auto-correct (FR-028).
- 429: one wait state (FR-033).
- Offline issue attempt: refused, not queued as success (FR-022).
- Server has no free public scope: create is unavailable (FR-018).
- Pitch example `zz-moving-box-kitchen-3-zz` contains a digit, so under the grammar it is a field-style plain code, and field codes do not go through the check word [ZZTHIS SPEC] spec 003 edge cases. The app does not invent a check word for it. Whether the first release even accepts person-written examples is ZQ11.

## Terms

zzThis spec 003 says "check word". zzThis `docs/SPEC.md` also says "checksum word" for the same extra word [ZZTHIS SPEC]. User-facing zzThat copy says "check word" only (FR-030).

"Demo" is the word for sample data and scripted behavior. Do not use "mock" in user-facing copy (FR-030).

## Out of scope

- A web app, including zzthat.com as a site built from this repo [DANNY 2026-10-04]. Where that promise lives is ZQ1.
- zzThing [ZZTHIS SPEC].
- A backend, resolver, wordlist pipeline, or check-word algorithm in this repo (principles I and V).
- Logistics, postal, and enterprise product surfaces, except where the shared API requires the client to tolerate those code kinds.
- Macro execution, drawn symbols, object codes, and the photo-to-action concept [ZZTHIS SPEC] Section 12.2 and Section 2.6.
- Option B model training [ZZTHIS SPEC] Section 10.8.
- Payments, partner integrations, and store submission itself (FR-031).
- Voice in the first release (FR-014), unless ZQ10 pulls it in.

## Open questions

Defaults are what an implementer may follow. Owners are who can close the question. Michael's GitHub login is zzmojo.

| ID | Question | Default | Owner | Issue |
|---|---|---|---|---|
| ZQ1 | Does the public promise still include the web, and if so where is it built? This repo will not build it. | iOS and Android only here | Michael for the promise. Danny for this repo. | [#1](https://github.com/Zero-State-LLC/zzthat/issues/1) |
| ZQ2 | What event counts as the zzThat launch for the zzThis "Try zzThat" link? (zzThis Q39) | Do not declare a launch | Michael | [#2](https://github.com/Zero-State-LLC/zzthat/issues/2) |
| ZQ3 | Free public codes are v2 on the resolver. Pull them forward, or does zzThat wait? | Wait. Create stays unavailable. | Michael for the scope line. Danny for the server. | [#3](https://github.com/Zero-State-LLC/zzthat/issues/3) |
| ZQ4 | Do first-release public codes allow duplicates and location priority? (zzThis Q56, filed as v2) | No. No location permission. | Michael | [#4](https://github.com/Zero-State-LLC/zzthat/issues/4) |
| ZQ5 | What does a resolved everyday code show? | The server's role-scoped view. No invented fields. | Michael | [#5](https://github.com/Zero-State-LLC/zzthat/issues/5) |
| ZQ6 | Can someone scan a public code without an account? | Only if the server allows the call. | Michael | [#6](https://github.com/Zero-State-LLC/zzthat/issues/6) |
| ZQ7 | What is a free account? | Sign-in required for writes. Fields are not invented. | Michael | [#7](https://github.com/Zero-State-LLC/zzthat/issues/7) |
| ZQ8 | What abuse and safety rules apply to public codes, and is there a report path? | No report UI yet. No personal data in the code string. | Michael | [#8](https://github.com/Zero-State-LLC/zzthat/issues/8) |
| ZQ9 | What does share send besides the code text? | Text only. No URL. | Michael | [#9](https://github.com/Zero-State-LLC/zzthat/issues/9) |
| ZQ10 | Is voice in the first release? (zzThis Q38) | No. No microphone permission. | Michael for scope. Danny for where audio would be processed. | [#10](https://github.com/Zero-State-LLC/zzthat/issues/10) |
| ZQ11 | Does the person choose the words, or the server? (zzThis Q25) | Server-issued words. Claim path closed. | Michael | [#11](https://github.com/Zero-State-LLC/zzthat/issues/11) |
| ZQ12 | Is the resolved view a screen in the app? | Yes. No website in this repo. | Michael | [#12](https://github.com/Zero-State-LLC/zzthat/issues/12) |
| ZQ13 | May the free app suggest other codes on a miss? (zzThis Q40) | No. Exact match. | Michael | [#13](https://github.com/Zero-State-LLC/zzthat/issues/13) |
| ZQ14 | What consent is required before a retry photo upload? (zzThis Q34) | Do not upload until the person confirms. Consent sentence waits. | Michael for the sentence. Danny for the legal gate. | [#14](https://github.com/Zero-State-LLC/zzthat/issues/14) |
| ZQ15 | Store listing copy and age rating | Do not submit. | Michael for copy and rating. Danny for submission. | [#15](https://github.com/Zero-State-LLC/zzthat/issues/15) |
| ZQ16 | May the app keep an offline draft that is not a live code? | No draft UI. Offline issue stays forbidden. | Michael for the draft. The issue ban is already [ZZTHIS SPEC]. | [#16](https://github.com/Zero-State-LLC/zzthat/issues/16) |
| ZQ17 | Native Swift and Kotlin, or a cross-platform framework? | Native Swift and Kotlin | Danny | [#17](https://github.com/Zero-State-LLC/zzthat/issues/17) |
| ZQ18 | How do end users authenticate? | Use the server's published mechanism. Do not invent one. | Danny | [#18](https://github.com/Zero-State-LLC/zzthat/issues/18) |
| ZQ19 | How is the API versioned? | Fixtures only until the server names a version. | Danny | [#19](https://github.com/Zero-State-LLC/zzthat/issues/19) |
| ZQ20 | What rate limits should the apps expect? (zzThis Q26) | Honor 429 with one opaque wait message. | Danny | [#20](https://github.com/Zero-State-LLC/zzthat/issues/20) |
| ZQ21 | Which iOS and Android versions? | Record the minimums when the scaffold is created. | Danny | [#21](https://github.com/Zero-State-LLC/zzthat/issues/21) |

## Workflows

| Item | Value |
|---|---|
| Governing workflows | anti-slop-code, production-systems, google-developer-style |
| Product owner | Michael Chung (zzmojo) |
| Operator | Danny |
| CI to reuse | None exists in this repo. Do not add a workflow in the spec-only change. The plan names `.github/workflows/apps-ci.yml` as the file an implementation change may add. |
| Human gates | Store submission, spend, production credentials, privacy-policy publication (constitution IX) |
