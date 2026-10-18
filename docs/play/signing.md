# Signing and uploading

Perixd releases are signed with `perixd-release.jks` (alias `perixd`). The keystore and its passwords in
`keystore.properties` stay on this PC and are not in git. Back both up somewhere safe: losing them means no update
can ever install over an existing Perixd.

Play App Signing is set up with this same key, so a Play install and a GitHub APK are signed identically and can update
each other.

## 1. Build the bundle

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "S:\AndroidAvd\Sdk"
$env:GRADLE_USER_HOME = "S:\AndroidAvd\gradle"
Set-Location S:\Desktop\Projects\Perixd
.\gradlew.bat :app:testDebugUnitTest :app:bundleRelease
```

The signed bundle is `app\build\outputs\bundle\release\app-release.aab`.

## 2. Give Play the app signing key (once, when creating the app)

In Play Console, under Test and release › App integrity › App signing, choose **Use a different key** and then
**Export and upload a key from Java keystore**. Download `pepk.jar` and `encryption_public_key.pem` from that page
into Downloads, then run:

```powershell
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" -jar "$HOME\Downloads\pepk.jar" --keystore="S:\Desktop\Projects\Perixd\perixd-release.jks" --alias=perixd --output="S:\Desktop\Projects\Perixd\secrets\perixd-signing-key.zip" --include-cert --rsa-aes-encryption --encryption-key-path="$HOME\Downloads\encryption_public_key.pem"
```

It asks for the keystore password and then the key password. Both are the same and are in `keystore.properties`:

```powershell
Get-Content S:\Desktop\Projects\Perixd\keystore.properties
```

Upload `secrets\perixd-signing-key.zip` on the same page. If Play shows its own pepk command with different flags,
use that one. The `secrets` folder is gitignored.

## 3. Upload key

Play needs an upload key to check the bundles you upload. Using the same key is simplest. If Play asks for a separate
upload key, create one and register its certificate:

```powershell
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\keytool.exe" -genkeypair -keystore S:\Desktop\Projects\Perixd\perixd-upload.jks -storetype PKCS12 -alias upload -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Aallyn, O=Perixd"
```

```powershell
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\keytool.exe" -export -rfc -keystore S:\Desktop\Projects\Perixd\perixd-upload.jks -alias upload -file S:\Desktop\Projects\Perixd\secrets\upload_certificate.pem
```

Then sign bundles with it by pointing a separate properties file at it; ask Claude to wire that into the build.

## 4. Checks already done for 1.1.0

- `bundleRelease` produces a signed AAB (`jarsigner -verify` passes, signer `CN=Aallyn, O=Perixd`).
- 16 KB pages: the only native libraries (`libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`, for all
  four ABIs) have 16 KB-aligned LOAD segments, and the APK passes `zipalign -c -P 16 -v 4`.
- No `USE_EXACT_ALARM`: Perixd asks for `SCHEDULE_EXACT_ALARM` (Alarms & reminders) and falls back to inexact alarms.
