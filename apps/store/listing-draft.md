# zzThat store listing (draft)

**Status: draft for Danny. Do not submit.** Task T081. Store submission, store spend, and the privacy policy URL need Danny's yes (FR-031, constitution IX, T083). Danny may change any field here before he submits.

**Finalization is held** until Danny supplies the legal name and address, the effective date, a children's section, and EU and California rights (B2, [DANNY 2026-10-05 #43]).

## Listing text

The listing text is the everyday-use sentences from FR-031 [MICHAEL 2026-10-02], as written. The web sentence from the same paragraph is not used, because this listing is for the phone apps.

> Free for everyone. Write a code on a lost-pet flyer, a moving box, a garage-sale item, or a note, and anyone can scan it, like a QR code you can write by hand.

The listing makes no claim of measured accuracy, a pilot, a customer, an endorsement, or adoption (FR-029).

## Age rating to file

| Store | Rating | Why |
|---|---|---|
| Apple App Store | 13+ | The pages are user-written public text. Apple's age ratings go from 9+ to 13+ (FR-031; zzThis D-2026-10-10-25 item 5). |
| Google Play | Teen, through the IARC questionnaire | The same reason. The target audience is 13 and over, so the Families policy does not apply (FR-031; D-2026-10-10-25 item 5). |

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
| Support contact | support@zer0state.com | ZQ25. Danny creates the mailbox. |
| Privacy policy | The privacy page on the zzThis marketing site | ZQ25. Drafted in [privacy-policy-draft.md](privacy-policy-draft.md). Danny publishes it. |
| Data disclosures | [data-safety-draft.md](data-safety-draft.md) for Google Play and the App Store privacy answers; the iOS privacy manifest | runtime.md, Release step 4 |
| In-app account deletion | Account, then Delete account | FR-034 |
| Account deletion link for Google Play | https://zz.zer0state.com/account/ | B1, [DANNY 2026-10-05 #43] |
| Report, hide, block, and content check | Report from a resolved page, reviewed within one day; Hide this code and Block this creator on the device; terms accepted before the first create; the server refuses blocklisted terms | FR-035; App Store review guideline 1.2; Play user-generated content policy |

## Before submission (ZQ25)

- [ ] Michael's square app icon artwork replaces the placeholder icon.
- [ ] The `support@zer0state.com` mailbox exists.
- [ ] The privacy policy is published on the zzThis marketing site, and its URL is set as the Release build setting.
- [ ] Danny supplies the legal name and address, the effective date, a children's section, and EU and California rights (B2).
- [ ] Danny says yes to the submission, the developer accounts, and the bundle id registration (T083).

## Not in the spec

Store screenshots, a subtitle or short description, keywords, and the store category are not in the spec, so this draft does not write them.
