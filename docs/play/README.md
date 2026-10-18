# Publishing Perixd on Google Play

Everything needed to create the app, run the required closed test and pass review, in Play Console order. Personal
developer accounts must run a closed test with at least 12 opted-in testers for 14 days in a row before they can
apply for production.

| File | What it holds |
|---|---|
| [listing-en-US.md](listing-en-US.md), [listing-pt-PT.md](listing-pt-PT.md) | App name, short and full description, release notes |
| [app-content.md](app-content.md) | Answers for every App content form, including Data safety and Health apps |
| [signing.md](signing.md) | Building the bundle, Play App Signing with the existing key via PEPK |
| [graphics/](graphics) | 512×512 icon, 1024×500 feature graphics, phone screenshots (en-US, pt-PT) |

## Before opening Play Console

- [ ] Back up `perixd-release.jks` and `keystore.properties` somewhere safe (see [signing.md](signing.md)).
- [x] The privacy policy and deletion pages are live at `https://perixd.mildhope.net/privacy` and
      `https://perixd.mildhope.net/delete`, showing `perixd@mildhope.net` (`PERIXD_CONTACT` on the server).
- [ ] Create the `perixd@mildhope.net` mailbox (or an alias to an existing one) so mail to it doesn't bounce.

## 1. Create the app

Home › Create app:

- App name: `Perixd: Period & Pill Tracker`
- Default language: English (United States) – en-US
- App or game: **App**. Free or paid: **Free**.
- Accept the Developer Program Policies and US export law declarations.

## 2. App integrity: signing

Test and release › App integrity › App signing: choose **Use a different key**, then **Export and upload a key from
Java keystore**, and follow [signing.md](signing.md) step 2. This keeps Play and GitHub builds on the same key.

## 3. App content (Policy › App content)

Fill each form from [app-content.md](app-content.md):

- [ ] Privacy policy: `https://perixd.mildhope.net/privacy`
- [ ] Ads: no ads
- [ ] App access: all functionality available without restrictions
- [ ] Content rating: IARC questionnaire, category "All other app types"
- [ ] Target audience: 18 and over
- [ ] News app: no
- [ ] Data safety: device IDs only, optional, for app functionality; deletion URL `https://perixd.mildhope.net/delete`
- [ ] Government apps: no
- [ ] Financial features: none
- [ ] Health apps: Period Tracking, Medication and Treatment Management, Reproductive and Sexual Health; not a medical device

## 4. Store presence

- [ ] Store settings: category **Health & Fitness**, contact email `perixd@mildhope.net`, website `https://github.com/AallynReed/Perixd`.
- [ ] Main store listing (en-US): text from [listing-en-US.md](listing-en-US.md); icon `graphics/icon-512.png`;
      feature graphic `graphics/feature-en-US.png`; phone screenshots `graphics/screenshots/en-US/1-today.png` to
      `6-reminder.png`, in that order.
- [ ] Translations › Add language › Portuguese (Portugal) – pt-PT: text from [listing-pt-PT.md](listing-pt-PT.md),
      feature graphic `graphics/feature-pt-PT.png`, screenshots from `graphics/screenshots/pt-PT/`.

## 5. Closed test

Test and release › Testing › Closed testing › Create track:

- [ ] Testers: an email list or Google Group with at least 12 people who will opt in and keep the app for 14 days.
      Give them the opt-in link from the Testers tab.
- [ ] Countries: at least Portugal (or all countries).
- [ ] Create release: upload `app\build\outputs\bundle\release\app-release.aab` (build it with [signing.md](signing.md)
      step 1), release name `1.1.0`, release notes from the listing files, then review and roll out.
- [ ] Keep at least 12 testers opted in for 14 consecutive days. Ask them to use it and send feedback.

## 6. Apply for production

When the dashboard allows it, apply for production access. Draft answers:

- How did you recruit testers? *Friends and family who track their periods or take the pill, invited by email.*
- How easy was it? *Easy: they opted in from the link and used the app daily for reminders and logging.*
- Feedback and what changed: *Fill in from the test; for example reminder wording, symptom list, translations.*
- Who is the app for? *Adults who want to track periods, symptoms and contraception privately, without an account.*
- How does it provide value? *Reliable daily contraceptive reminders, a visual pill pack, cycle predictions, symptom
  history, and optional end-to-end encrypted sync, with no ads or tracking.*
- Expected installs in the first year: *under 10,000.*

## Notes

- Perixd asks for Alarms & reminders (`SCHEDULE_EXACT_ALARM`) instead of declaring `USE_EXACT_ALARM`, which Play
  reserves for alarm clock and calendar apps. Without the permission reminders still arrive, possibly late, and the
  Today screen offers to turn it on.
- Push sync through Firebase is optional and off until the server has Firebase credentials. Data safety already
  declares the device IDs it would use.
- Never claim Perixd prevents pregnancy or diagnoses anything. The listing and the app say it is a tracker, not a
  medical device or a method of contraception.
