# App content answers (Play Console › Policy › App content)

Answers for Perixd 1.1.0. Re-check them whenever the app starts sending new data anywhere.

## Privacy policy

- URL: `https://perixd.mildhope.net/privacy` (Portuguese: `https://perixd.mildhope.net/privacy/pt`).
- The app links to it from Settings › Privacy.

## Ads

- Does your app contain ads? **No.**

## App access

- **All functionality is available without any access restrictions.**
- Note for reviewers, if a text box is offered: "No account or login. Sync between devices is optional: Settings › Sync between devices › Set up sync on this device creates an encrypted space on the default server without credentials, and a second device joins by scanning the QR code shown under Add another device."

## Content rating (IARC questionnaire)

- Category: **All other app types** (a utility, not a game, social or reference app).
- Violence, fear, sexuality, language, gambling: **No** to every question. Perixd mentions periods and contraception only as health tracking, with no sexual content.
- Controlled substances: **No**. Contraceptive pills are tracked as medication, not drugs, alcohol or tobacco.
- Do users interact or exchange content? **No**. Sync only connects one person's own devices.
- Does the app share the user's location? **No.** Digital purchases? **No.** Unrestricted web access? **No.**
- Expected result: Everyone / PEGI 3 / USK 0.

## Target audience and content

- Target age groups: **18 and over** only.
- Could the app unintentionally appeal to children? **No.**
- No ads, so the Families ads rules don't apply.

## News app

- **No.**

## Health apps

Select these features:

- Health and fitness › **Period Tracking**: logs periods and flow, predicts the next period.
- Medical › **Medication and Treatment Management**: reminders to take the pill or change the patch, ring or injection, with a log of doses taken.
- Medical › **Reproductive and Sexual Health**: tracks the contraceptive method and pack, and shows fertile-window estimates when no hormonal method is set.

Do **not** select "Medical Device Apps". Perixd makes no diagnostic, therapeutic or contraceptive claim. Its listing and its screens say it is not a medical device and not a method of contraception, and it hides fertility estimates on hormonal methods.

## Financial features

- **My app doesn't provide any financial features.**

## Government apps

- **No.**

## Data safety

Overview:

- Does your app collect or share any of the required user data types? **Yes** (device IDs, only when sync is on).
- Is all of the user data collected by your app encrypted in transit? **Yes** (HTTPS only).
- Which of the following methods of account creation does your app support? **My app does not allow users to create an account.**
- Do you provide a way for users to request that their data is deleted? **Yes.** Deletion URL: `https://perixd.mildhope.net/delete`.

Data types:

| Category | Data type | Collected | Shared | Ephemeral | Required? | Purposes |
|---|---|---|---|---|---|---|
| Device or other IDs | Device or other IDs | Yes | No | No | Optional | App functionality |

Why the rest is not declared:

- **Health info** (periods, flow, symptoms, notes, doses) is not "collected" under Play's definition. It is processed only on the device, and when the user turns on sync it leaves the device end-to-end encrypted with a key the developer never has. Play excludes "user data that is sent off device, but that is unreadable by you or anyone other than the sender and recipient".
- **Device or other IDs** covers the random per-install device ID and space ID sent to the sync server, and the Firebase Cloud Messaging token when the server has push enabled. Google receives the token as a service provider, which Play does not count as sharing.
- No location, contacts, files, photos, messages, app activity, web history, financial or personal info is read or sent. There is no analytics or crash reporting SDK.
- The proxy keeps IP addresses in access logs for 14 days for security only, as the privacy policy says. They are not used to locate anyone.

Security practices: data encrypted in transit **Yes**; users can request deletion **Yes**; committed to the Families policy **No** (not a children's app); independent security review **No**.

## Store settings

- App category: **Health & Fitness**.
- Contact email: the address confirmed for the privacy policy. Website: `https://github.com/AallynReed/Perixd`.
