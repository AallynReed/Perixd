# Perixd

A private period, symptom and contraception tracker for Android.

- Log flow (spotting to heavy), symptoms with their intensity (cramps, headache, back pain and more) and notes for any day.
- Predicts the next period, ovulation and the fertile window from your own cycles, as a window when cycles vary.
- Tracks the pill (any pack: 21+7, 24+4, placebo, continuous, progestin-only, custom), patch, ring, injection, IUD or implant.
- A pill pack you can see: taken pills are popped, today's pill pulses, break days sit underneath.
- Reminders at the time you choose, with a separate weekend time, that stay until you mark the dose and come back if swiped away; repeat alerts, snooze, advance notices, period and fertile-window notices, and a daily log reminder.
- Ovulation and fertile-window estimates are hidden while a hormonal method is in use. None of the estimates are contraception advice.
- English and European Portuguese, light and dark themes.

Everything stays on the device unless you turn on sync, and the data is excluded from cloud backups. There are no ads,
analytics or accounts.

## Sync between devices (optional)

Pair a phone and a tablet by scanning a QR code, and logs, doses and settings (all but the theme) follow you between
them. Data is encrypted on the device with AES-256-GCM under a key only the paired devices hold, so the
[sync server](server/README.md) stores nothing it can read. With Firebase push set up on the server, marking a pill on
one device clears the reminder on the others within seconds; without it they catch up within a few minutes while a
reminder is showing.

## Build

Requires JDK 17 and the Android SDK.

```powershell
$env:ANDROID_HOME = "C:\path\to\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Reminders and Android limits

Reminders use exact alarms and are rescheduled after a reboot, an app update, and clock or time zone changes. Since Android 14 a user can swipe away even an ongoing notification; Perixd posts it again from its delete intent until the dose is marked. Nothing can show a reminder if notifications are blocked for the app, the app is force-stopped, or a vendor battery saver kills it.

## License

GNU Affero General Public License v3.0. See [LICENSE](LICENSE).
