# zzThat privacy policy (draft)

**Status: draft for Danny. Not published, and not linked from the apps or any site.** Task T080 and ZQ25: Danny publishes the policy on the zzThis marketing site, and only after his yes (FR-031, constitution IX). The text below states what the zzThat apps and the zzThis server do at the pinned contract. It is not legal advice. The open items Danny decides are listed at the end.

**Finalization is held** until Danny supplies the legal name and address, the effective date, a children's section, and EU and California rights (B2, [DANNY 2026-10-05 #43]).

Sources: zzThat [spec.md](../../specs/001-zzthat-apps/spec.md) (Privacy and permissions, FR-004, FR-005, FR-011 to FR-016, FR-019, FR-034, FR-035, FR-040, FR-041, FR-045, FR-047), [runtime.md](../../specs/001-zzthat-apps/runtime.md) (Auth, Storage, Privacy), and zzThis spec 005 at the pin in `contracts/ZZTHIS-API-PIN` (FR-017, FR-020, FR-021, FR-023, FR-026, FR-027).

---

## The short version

- You can scan or type a zz code without an account.
- To create codes, you sign in with Apple or Google. We keep an account ID. We do not keep your name or email address.
- The title and message you write for a code are public. Anyone who scans the code can read them.
- Photos are read on your phone. The apps do not upload photos.
- The apps have no analytics of our own, no ads, and no tracking. On Android, Google's on-device text recognition sends Google anonymous diagnostics, such as the device model and how long a read took.
- You can delete your account in the app at any time.

## What the apps collect, and why

| Data | When | Why | Where it goes |
|---|---|---|---|
| An account ID | When you sign in with Apple or Google | To link the codes you create to you, so only you can change or revoke them | The zzThis server keeps the provider (Apple or Google) and that provider's ID for your sign-in. It does not keep your name or email address, and the apps do not ask Apple or Google for them. |
| The title and message of a code | When you create a code or edit its page | To show them to anyone who scans the code | The zzThis server. They are public. Do not include anything you do not want anyone to see. |
| A code | When you open a code by scanning or typing it | To look up its page | The zzThis server receives only the code, never the photo. Codes the apps create never contain a phone number or other personal data. When you create a code, the server chooses the words, and the app sends no code. |
| A report | When you report a code | So the operator can review it | The zzThis server keeps the code, the reason you chose, and the note you add, if any. You can report without an account. |
| Diagnostics from Google's text recognition (Android only) | When the Android app reads a code from a photo | Google's ML Kit text recognition, which reads the code on your phone, sends them to Google so Google can keep the feature working | Google receives device information (such as the manufacturer, model, and system version), the app's package name and version, performance numbers (such as how long a read took), error codes, and an identifier for this installation that is not meant to identify you or your phone. It never receives the photo or the text. Google does not pass this data to third parties. The iOS app reads codes with Apple's Vision, which sends nothing. |
| A one-way code of your network address (an HMAC of the IP address) | When you use the app without signing in, and when you send a report | To limit how often requests can be made, which prevents fraud and abuse and keeps the service secure | The zzThis server keeps it only as a rate-limit count. It is not the address itself, it is not linked to you, and it is not used to track you. |

## Photos and the camera

- The apps ask for the camera only when you start a scan. On Android, the system camera app takes the photo, so the app has no camera permission.
- You can also pick a photo with the system photo picker. The apps have no access to your photo library beyond the photo you pick.
- The code is read on your phone. The photo stays on your phone, in the app's temporary storage, and is deleted when the scan ends.
- The apps do not upload photos. If a later version offers to send a hard-to-read photo for a server read, it will ask first with this sentence: "Send this photo so we can read the code? It leaves your phone only for this retry, and it may be kept for review of this read. It is not used to train a model." A photo sent that way is deleted 30 days after upload.

## What the apps never collect

- No analytics of our own, no advertising ID, and no tracking across apps or websites. The only diagnostics are Google's text recognition diagnostics on Android, described above.
- No crash reporter that uploads data.
- No location, and no microphone.
- No contacts, and no name or email address.

## What stays on your phone

- Your sign-in: a refresh token in the iOS Keychain, or encrypted with an Android Keystore key, until you sign out or delete your account. The short-lived access token is kept in memory only.
- A copy of a public page you opened, for up to 6 minutes, so you can read it again without a connection.
- The last code you opened, so you can try again.
- A draft title and message, labeled "Not a code yet.", if you start a code while offline. It holds no code and is not sent anywhere until you create the code.

## Sign-in with Apple or Google

Sign-in runs only when you tap the Apple or Google button. Apple or Google confirms your sign-in, and the zzThis server receives a one-time sign-in token from them. The server checks the token and keeps only the account ID described above. For Sign in with Apple, the server keeps an Apple token, encrypted, only to cancel your Apple sign-in for zzThat when you delete your account.

## The zzThis server

The zzThat apps use the zzThis server, which runs on Cloudflare.

- **Server logs** hold the type of request and its route, the result, how long it took, whether a cached copy answered it, which rate limit applied, and the data center. They never hold a code, a page's text, a sign-in token, a photo, or an IP address.
- **Rate limits** count requests per account, or per network address for signed-out requests and reports. The count uses a one-way code of the address, not the address itself, only to prevent fraud and abuse and to keep the service secure. It is not linked to you and not used to track you.
- **Sharing:** we do not sell your data or share it with advertisers. Sharing a code from the app sends the code as text, to the place you pick in your phone's share sheet.

## How long we keep data

- **Your pages** stay until you change them or delete your account. Each change is kept as a new version.
- **Photos sent for a server read** (not offered today) are deleted 30 days after upload.
- **Sign-in data:** the one-time values used to sign in are deleted after use or when they expire. Refresh tokens last 30 days, and expired ones are deleted 30 days after they expire.
- **Reports**, including any note you add, are deleted one year after the operator closes them (B3, [DANNY 2026-10-05 #43]). A report is not linked to you.
- **A record of account actions** (for example, that a code was created or revoked) is kept after an account is deleted. It holds no page text.

## Deleting your account

You can delete your account in the app: Account, then Delete account. You can also ask for deletion at https://zz.zer0state.com/account/. Deleting your account:

- revokes all your codes, so anyone who scans them sees no match;
- erases the title and message of every version of your pages;
- deletes your Apple or Google sign-in link and ends every session;
- cancels your Sign in with Apple authorization for zzThat with Apple;
- deletes the sign-in, the draft, and every cached page from your phone.

If you sign in again later, you start with a new, empty account. The words of deleted codes are not used again.

## Changes to this policy

We post every change on this page and update the effective date at the top. When a change is material, the app's release notes say so too.

## Contact

Questions: support@zer0state.com

---

## Held for Danny

Decided by delegation and applied here: how policy changes are announced (S1), [DELEGATED 2026-10-05, zzThat #43].

1. Held (B2): the legal name and address of the company that publishes the apps, the effective date, a children's section (the default store ratings are Apple 13+ and Google Play Teen, FR-031), and EU and California rights. These are legal facts, so the build does not decide them.
2. Before publishing: the privacy page URL. The apps link to the zzThis marketing site's privacy page (ZQ25), which does not exist until Danny publishes this.
3. Before submission: the `support@zer0state.com` mailbox exists (ZQ25).
