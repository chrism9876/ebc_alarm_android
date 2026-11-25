# Alarm Clock – Android app

A native Android app for the alarm server (`../alarm_server`). It has the same editor as the web page, and it can also ring the alarms on the phone.

## What it does

- **Edit alarms and clock settings.** It uses the same fields, repeat options and DST setting as the web page. **Save** sends your changes to the server, and the clock picks them up on its next sync.
- **Offline view.** The phone keeps the last alarms it received. When the server is down (including a 502 from a reverse proxy) the app shows that copy with a "Server offline" bar. Your unsaved edits stay until you can save again.
- **Ring on the phone.** Each active alarm also rings on the phone at its time. When the phone is locked, a full-screen alarm screen laid out like an incoming call appears, with **Snooze** and **Dismiss** buttons. When the phone is in use, a coloured heads-up notification with the same two buttons appears instead.
  - To leave an alarm to the clock only, untick **Ring on this phone** on that alarm.
  - To stop the phone ringing at all, turn off **Ring on this phone** in Settings.
  - Snooze length and an auto-stop timeout are in Settings.
- **Background sync.** The app syncs with the server every 15 minutes, every time you open it, and after a reboot, so phone alarms follow changes made on the web page.

- **In step with the clock.** When the clock starts ringing, the server pushes it to the phone through Firebase and the phone rings with it. **Snooze** and **Dismiss** on the phone are passed to the clock, and the other way round.
  - This needs `app/google-services.json` from your Firebase project at build time, plus Firebase set up on the server (see `../alarm_server/README.md`).
  - Settings shows whether push is active.
- **Home HTTPS.** An `https://` address signed by your own certificate authority (such as `https://alarm.home`) works once that CA certificate is installed on the phone.

Notes:

- The "then repeat every …" recurring option only runs on the clock. The phone rings at the main time only.
- Android's built-in call-style notification always labels its buttons "Answer" and "Decline", and apps can't change them. So the app draws its own call-like alarm screen, which shows **Snooze** and **Dismiss**.

## Build and install

You need Android Studio, which provides the SDK and JDK.

- **In Android Studio:** open the `android_app` folder, wait for the sync, pick your phone and press **Run**.
- **From the command line**, from this folder:

  ```
  gradlew assembleDebug
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

The project uses Gradle 9.9.2, Android Gradle Plugin 9.5.1, Kotlin 2.4.20 and Jetpack Compose. It targets Android 10 (API 29) and newer.

## First start

1. Open **Settings** (⚙) and enter the server address, for example `http://192.168.1.100:8080` or your HTTPS proxy address. Add the API token if the server uses `ALARM_API_TOKEN`.
2. Check that every item under **Permissions for ringing** has a ✓.
3. On Samsung phones, set the app's battery mode to **Unrestricted** (App info → Battery) so the background sync isn't stopped. The alarms ring either way.
4. Tap **Test: ring now** to check the sound and the alarm screen.
