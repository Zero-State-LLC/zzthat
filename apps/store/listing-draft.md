# zzThat store listing (draft)

**Status: draft for Danny. Do not submit.** Task T081. Store submission, store spend, and the privacy policy URL need Danny's yes (FR-031, constitution IX, T083). Danny may change any field here before he submits.

**Finalization is held** until Danny supplies the legal name, the address, the governing-law state, and the policy host (B2, [DANNY 2026-10-05 #43]; zzThis D-2026-10-11-08). The launch is US-only, so there are no EU or UK sections; the effective date is the day the policy goes live.

## Listing text

The listing text is the everyday-use sentences from FR-031 [MICHAEL 2026-10-02], as written. The web sentence from the same paragraph is not used, because this listing is for the phone apps.

> Free for everyone. Write a code on a lost-pet flyer, a moving box, a garage-sale item, or a note, and anyone can scan it, like a QR code you can write by hand.

The listing makes no claim of measured accuracy, a pilot, a customer, an endorsement, or adoption (FR-029).

## Early release

The listing and the app call this release "early release" (zzThis D-2026-10-11-06). Never "alpha", "beta", or "test": Apple rejects public apps labeled that way. The early-release clauses are in the Terms.

## Age rating to file

| Store | Rating | Why |
|---|---|---|
| Apple App Store | Whatever the age rating questionnaire gives (13+ expected) | Michael's answer 15 (zzThis D-2026-10-11-08): the store ratings follow the questionnaires, and the Terms say 18+ (D-2026-10-11-05). |
| Google Play | Whatever the IARC questionnaire gives (Teen expected); target audience 18 and over | The same. With an 18+ audience the Families policy does not apply. |

### Questionnaire answers

The answers are grouped by topic, because the exact question wording is in each store's form. Danny confirms each one when he files.

| Topic | Answer | Why |
|---|---|---|
| User-generated content | Yes | Anyone who scans a code reads the title and message its creator wrote (FR-035). |
| Users interact or share content with each other | Yes, through those public pages only | There is no chat, messaging, profile, or comment (spec.md, Accounts and abuse). |
| Messaging or chat | No | |
| Advertising | No | No ads and no advertising ID (privacy policy draft). |
| Location sharing | No | No location (FR-013). |
| Purchases or digital goods | No | The apps are free and sell nothing. |
| Unrestricted web access | No | Links in a page are not tappable (FR-046). |
| Gambling, simulated gambling, contests, or loot boxes | No | |
| Violence, sexual content or nudity, profanity or crude humor, horror, drugs, alcohol, or tobacco, mature or suggestive themes, medical or treatment information | None in the app's own content | Only user-written pages could carry these, which is the user-generated content answer. The server refuses blocklisted terms and the operator reviews reports within one day (zzThis RM-102). |
| Parental controls or age assurance | No | |

Danny may change the rating or any answer before he submits.

## App details from the spec

| Field | Value | Source |
|---|---|---|
| App name | zzThat | spec.md, Why |
| Bundle and application id | `com.zer0state.zzthat` | ZQ24. Registering it needs Danny's yes (T083). |
| Support contact | zzsupport@zer0state.com | zzThis D-2026-10-11-04 (Michael). Danny creates the mailbox first (RM-122). |
| Privacy policy | Proposed: `zzthat.com/privacy` (zzThis D-2026-10-11-09, PROPOSED) | Drafted in [privacy-policy-draft.md](privacy-policy-draft.md). Danny publishes it. No zzThis host or name on zzThat surfaces (D-2026-10-11-07). |
| Data disclosures | [data-safety-draft.md](data-safety-draft.md) for Google Play and the App Store privacy answers; the iOS privacy manifest | runtime.md, Release step 4 |
| In-app account deletion | Account, then Delete account | FR-034 |
| Account deletion link for Google Play | https://zz.zer0state.com/account/ | B1, [DANNY 2026-10-05 #43] |
| Report, hide, block, and content check | Report from a resolved page, reviewed within one day; Hide this code and Block this creator on the device; terms accepted before the first create; the server refuses blocklisted terms | FR-035; App Store review guideline 1.2; Play user-generated content policy |

## Before submission (ZQ25)

- [ ] Michael's square app icon artwork replaces the placeholder icon.
- [ ] The `support@zer0state.com` mailbox exists.
- [ ] The privacy policy is published at the decided host (D-2026-10-11-09), and its URL is set as the Release build setting.
- [ ] The Terms of Use and content policy ([terms-draft.md](terms-draft.md)) are approved by Michael and published (RM-124).
- [ ] Danny supplies the legal name and address, the effective date, a children's section, and EU and California rights (B2).
- [ ] Danny says yes to the submission, the developer accounts, and the bundle id registration (T083).

## Not in the spec

Store screenshots, the subtitle, short description, keywords, category, and App Review notes are proposed in [review-and-metadata-draft.md](review-and-metadata-draft.md), for Michael and Danny.
