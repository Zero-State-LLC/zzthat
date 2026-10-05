# zzThat data disclosures (draft)

**Status: draft for Danny. Not entered in Play Console or App Store Connect.** Task T084 and runtime.md, Release step 4: the Google Play Data safety answers and the App Store privacy answers are filled from the spec privacy table before Danny submits, and they stay drafts until his yes (FR-031, constitution IX).

**Finalization is held** until Danny supplies the legal name and address, the effective date, a children's section, and EU and California rights (B2, [DANNY 2026-10-05 #43]).

Sources: zzThat [spec.md](../../specs/001-zzthat-apps/spec.md) (Privacy and permissions), [runtime.md](../../specs/001-zzthat-apps/runtime.md) (Privacy, Storage), the iOS privacy manifest at `apps/ios/zzThat/Resources/PrivacyInfo.xcprivacy`, and zzThis spec 005 at the pin (FR-017, FR-020, FR-023, FR-026, FR-027) with the server schema at the pin.

## What the server stores

| Item | Stored | Linked to the account | Source |
|---|---|---|---|
| Account | An account ID and its creation time | Yes | spec 005 FR-020 |
| Sign-in identity | The provider (Apple or Google) and that provider's subject ID. No name and no email address. For Apple, an encrypted Apple token, kept only to revoke it at deletion. | Yes | FR-020, FR-023 |
| Sessions | Refresh tokens as hashes, with the client type (iOS or Android) | Yes | FR-021 |
| Pages | The title and message the person writes, as versions | Yes. Public to anyone who scans the code. | spec.md, Page |
| Codes | The codes the server issued to the account | Yes | FR-017 |
| Reports | The reported code, the reason, and the optional note. No reporter account and no IP address. Deleted one year after the operator closes the report (B3). | No | FR-017, FR-035; B3 |
| Audit events | The action, its target, the result, and the time. No page text. Kept after account deletion. | Yes | FR-012, FR-023 |
| Logs | Request type, route, result, timing, cache result, rate-limit rule, and data center. Never a code, page text, token, nonce, photo, or IP address. | No | FR-027 |
| Rate-limit counters | A count per account, or per HMAC of the IP address for signed-out calls and reports | The HMAC is not linked to the person (B4b). Account counts are per account. | FR-011, FR-027 |
| Photos | None in v1. The apps read photos on the phone and do not upload them (FR-045). | Not applicable | FR-004, FR-045 |

## Google Play Data safety

### Data collection and security

| Question | Draft answer | Note |
|---|---|---|
| Does the app collect or share any of the required user data types? | Yes, it collects. It shares none. | Cloudflare hosts the server as a service provider. |
| Is all collected data encrypted in transit? | Yes | Release builds call the API over HTTPS only. Only the Local test build uses plain HTTP, to `10.0.2.2` on the emulator. |
| Account creation methods | OAuth: Sign in with Google | Android offers Google only (ZQ23). |
| Can people ask for their data to be deleted? | Yes, in the app (Account, then Delete account) and at https://zz.zer0state.com/account/ | FR-034; the web link is B1, [DANNY 2026-10-05 #43] |

### Data types

| Data type | Collected | Shared | Ephemeral | Required or optional | Purposes |
|---|---|---|---|---|---|
| Personal info: User IDs (the account ID and the Google subject ID) | Yes | No | No | Optional. Scanning and typing work without an account; creating codes needs sign-in. | App functionality, Account management |
| App activity: Other user-generated content (page titles and messages, and report notes) | Yes | No | No | Optional | App functionality. Reports: Fraud prevention, security, and compliance |
| The IP-based rate-limit HMAC (B4b). The form has no IP-specific type; Device or other IDs is the closest listed type, which Danny confirms when he enters the form. | Yes | No | No | Required for signed-out use and reports | Fraud prevention, security, and compliance |

Not collected: name, email address, phone number, address, location, photos and videos (read on the phone only), audio, files, contacts, calendar, messages, financial info, health and fitness, web browsing, crash logs, diagnostics, and the advertising ID. Codes a person looks up are not listed as collected (B4a, [DANNY 2026-10-05 #43]): the server keeps nothing from a lookup (FR-027).

## App Store privacy (App Store Connect)

These match the privacy manifest, which declares no tracking and no tracking domains.

| Category | Data type | Linked to the user | Used for tracking | Purpose |
|---|---|---|---|---|
| Identifiers | User ID | Yes | No | App Functionality |
| User Content | Other User Content (page titles and messages, and report notes) | Yes for pages. Reports are stored without the reporter. | No | App Functionality |
| Other Data | Other Data Types: the IP-based rate-limit HMAC (B4b) | No | No | App Functionality (Apple counts preventing fraud and keeping the service secure here) |

Data Used to Track You: none. The apps contain no analytics, no advertising identifier, and no crash reporter that uploads (runtime.md, Privacy).

## Open items for Danny

Decided on #43 and applied here: the account deletion link (B1), report retention (B3), codes looked up are not collected (B4a), and the rate-limit HMAC disclosure (B4b).

1. **Held (B2).** Store finalization waits for the legal name and address, the effective date, a children's section, and EU and California rights.
2. **Report notes.** Reports are stored without the reporter. Danny confirms that the User Content rows should still mention report notes.
3. **Form categories.** Neither form has an IP-specific type for the rate-limit HMAC. Danny confirms Device or other IDs on Google Play and Other Data Types on the App Store when he enters them.
