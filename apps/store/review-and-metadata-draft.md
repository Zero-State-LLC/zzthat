# zzThat store metadata and App Review notes (draft)

**Status: draft for Michael (wording) and Danny (filing). Do not submit.** Row 7 of the zzThis v1.1 release packet (`docs/releases/v1.1.md`). Nothing here is entered in App Store Connect or Play Console until Danny's yes (T083). Every field is a proposal; Michael's choice wins.

## Metadata proposals (for Michael)

| Field | Limit | Proposal |
|---|---|---|
| App Store subtitle | 30 characters | Handwritten codes anyone scans |
| App Store promotional text | 170 characters | Early release. Write a short code by hand on anything you want found, and anyone with a phone can scan it to open its page. |
| Google Play short description | 80 characters | Write a code by hand. Anyone can scan it with a phone, like a QR code. |
| Full description | 4,000 characters | The FR-031 listing sentence, then four short paragraphs: how it works (write, scan, read the page), what you can make (lost pet, moving box, garage sale, notes), privacy (photos stay on the phone, no ads, no tracking), and early release (18 and older; features may change). No accuracy or adoption claims (FR-029). |
| App Store keywords | 100 characters | handwritten,code,scan,label,lost pet,moving box,tag,note,garage sale,qr,sign,find |
| Category | | App Store: Utilities (secondary: Productivity). Google Play: Tools. |
| Support URL and email | | `zzsupport@zer0state.com` once the mailbox exists (RM-122); support URL on the decided host (D-2026-10-11-09) |
| Privacy policy URL | | The decided host, proposed `zzthat.com/privacy` (D-2026-10-11-09) |

No competitor or brand names in keywords. No "beta", "alpha", or "test" anywhere (D-2026-10-11-06). No zzThis name or host (D-2026-10-11-07).

## Screenshots and graphics

The app is iPhone only (`TARGETED_DEVICE_FAMILY` 1), so no iPad set is needed.

| Store | Asset | Size |
|---|---|---|
| App Store | iPhone screenshots, 3 to 10 | The 6.9-inch set, 1320 x 2868 portrait, which App Store Connect scales to smaller iPhones. HexWire's 2026-10-08 submission was accepted with 1179 x 2556, so confirm the required set in App Store Connect when filing. |
| Google Play | Phone screenshots, 2 to 8 | 1080 x 1920 portrait (9:16) |
| Google Play | Feature graphic | 1024 x 500, required |
| Google Play | App icon | 512 x 512, from Michael's square artwork (RM-071) |

Proposed screenshot order, from Demo builds with demo data labeled Demo (the store shows real screens, so the Demo badge stays visible):

1. Scan: the camera with a handwritten code boxed.
2. Resolve: a public page (Lost cat).
3. Create: the minted code with its check word and "Get different words".
4. My codes, with the Saved entry.
5. Report sent, with hide and block.

Captions are Michael's. Screenshots are taken after the recognition adapter and the Terms step land, so the screens match the submitted build.

## App Review notes (App Store Connect "Notes", and Play "App access")

> zzThat reads short handwritten codes, written by people on things like flyers and boxes, and opens the page the code's owner wrote. Everything a reviewer needs is in the app:
>
> - **Scan or type without an account.** On the Scan tab, tap Type it instead and enter REVIEW_CODE (Danny mints one public code in production before submission and pastes it here). It opens its public page.
> - **Camera.** The app asks for the camera only when you tap Take a photo of the code. In this early release the app shows the code it read and asks you to confirm it before opening it. Photos are read on the phone and never uploaded.
> - **Create.** Sign in with Apple or Google (any account works; Sign in with Apple is offered at equal prominence). Before the first code, the app asks you to confirm you are 18 or older and accept the Terms. Then Get a code gives you a code; you can tap Get different words up to 5 times.
> - **User-generated content (Guideline 1.2).** On any code's page, Report sends a report that we review within one day; Hide this code and Block this creator hide content on your phone; the operator can suspend an abusive creator. Terms and content rules: Account, Terms of Use.
> - **Account deletion (Guideline 5.1.1(v)).** Account, Delete account. It deletes the account and revokes Sign in with Apple.
> - **Contact.** zzsupport@zer0state.com.

For Google Play "App access": all functionality is available without special access; creating codes needs Sign in with Google, with any Google account.

## Held

1. `REVIEW_CODE`: Danny mints one public code in production after v1.0 GO and fills it in.
2. The Terms link works only once the Terms are published (RM-124, D-2026-10-11-09).
3. Michael approves the metadata wording and the captions.
