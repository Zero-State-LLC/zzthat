# zzThat data disclosures (draft)

**Status: draft for Danny. Not entered in Play Console or App Store Connect.** Task T084 and runtime.md, Release step 4: the Google Play Data safety answers and the App Store privacy answers are filled from the spec privacy table before Danny submits, and they stay drafts until his yes (FR-031, constitution IX).

Sources: zzThat [spec.md](../../specs/001-zzthat-apps/spec.md) (Privacy and permissions), [runtime.md](../../specs/001-zzthat-apps/runtime.md) (Privacy, Storage), the iOS privacy manifest at `apps/ios/zzThat/Resources/PrivacyInfo.xcprivacy`, and zzThis spec 005 at the pin (FR-017, FR-020, FR-023, FR-026, FR-027) with the server schema at the pin.

## What the server stores

| Item | Stored | Linked to the account | Source |
|---|---|---|---|
| Account | An account ID and its creation time | Yes | spec 005 FR-020 |
| Sign-in identity | The provider (Apple or Google) and that provider's subject ID. No name and no email address. For Apple, an encrypted Apple token, kept only to revoke it at deletion. | Yes | FR-020, FR-023 |
| Sessions | Refresh tokens as hashes, with the client type (iOS or Android) | Yes | FR-021 |
| Pages | The title and message the person writes, as versions | Yes. Public to anyone who scans the code. | spec.md, Page |
| Codes | The codes the server issued to the account | Yes | FR-017 |
| Reports | The reported code, the reason, and the optional note. No reporter account and no IP address. | No | FR-017, FR-035 |
| Audit events | The action, its target, the result, and the time. No page text. Kept after account deletion. | Yes | FR-012, FR-023 |
| Logs | Request type, route, result, timing, cache result, rate-limit rule, and data center. Never a code, page text, token, nonce, photo, or IP address. | No | FR-027 |
| Rate-limit counters | A count per account, or per HMAC of the IP address for signed-out calls | Per account when signed in | FR-011, FR-027 |
| Photos | None in v1. The apps read photos on the phone and do not upload them (FR-045). | Not applicable | FR-004, FR-045 |

## Google Play Data safety

### Data collection and security

| Question | Draft answer | Note |
|---|---|---|
| Does the app collect or share any of the required user data types? | Yes, it collects. It shares none. | Cloudflare hosts the server as a service provider. |
| Is all collected data encrypted in transit? | Yes | Release builds call the API over HTTPS only. Only the Local test build uses plain HTTP, to `10.0.2.2` on the emulator. |
| Account creation methods | OAuth: Sign in with Google | Android offers Google only (ZQ23). |
| Can people ask for their data to be deleted? | Yes, in the app: Account, then Delete account | FR-034. See open item 1. |

### Data types

| Data type | Collected | Shared | Ephemeral | Required or optional | Purposes |
|---|---|---|---|---|---|
| Personal info: User IDs (the account ID and the Google subject ID) | Yes | No | No | Optional. Scanning and typing work without an account; creating codes needs sign-in. | App functionality, Account management |
| App activity: Other user-generated content (page titles and messages, and report notes) | Yes | No | No | Optional | App functionality. Reports: Fraud prevention, security, and compliance |

Not collected: name, email address, phone number, address, location, photos and videos (read on the phone only), audio, files, contacts, calendar, messages, financial info, health and fitness, web browsing, crash logs, diagnostics, and device or other IDs (no advertising ID).

## App Store privacy (App Store Connect)

These match the privacy manifest, which declares no tracking and no tracking domains.

| Category | Data type | Linked to the user | Used for tracking | Purpose |
|---|---|---|---|---|
| Identifiers | User ID | Yes | No | App Functionality |
| User Content | Other User Content (page titles and messages, and report notes) | Yes for pages. Reports are stored without the reporter. | No | App Functionality |

Data Used to Track You: none. The apps contain no analytics, no advertising identifier, and no crash reporter that uploads (runtime.md, Privacy).

## Open items for Danny

1. **Account deletion web link.** Google Play asks apps that create accounts for a web link where a person can ask for account deletion, in addition to in-app deletion. The spec has in-app deletion only (FR-034). Danny decides the link, for example the zzThis web client or a support mail route.
2. **Codes sent to open a page.** Opening a code sends the code to the server, which looks it up and keeps nothing from the request (FR-027). Danny decides whether the forms list this as ephemeral processing.
3. **Rate limits.** Signed-out calls are counted under an HMAC of the IP address (FR-011, FR-027). Danny decides whether this counts as collected data for either store.
4. **Report notes.** Reports are stored without the reporter. Danny confirms that the User Content row should still mention report notes.
