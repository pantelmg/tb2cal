# TB2Cal — TimeBlocks to Calendar

By [github.com/pantelmg](https://github.com/pantelmg)

Create TimeBlocks, with your own conditions, to track and improve productivity.
Completed TimeBlocks are registered in the app and the local Calendar.
Use the app's widgets (Calendar and TimeBlock manager) for efficiency.

## Features
- **TimeBlocks:** A list (`study`, `workout` preset). Add, rename and remove them, each with a calendar color.
- **Timer** (countdown) or **Stopwatch** mode. START locks all options; PAUSE keeps them locked; STOP unlocks them
  and discards the TimeBlock.
- **Permanent notification** while running: Name, mode and live time, with Pause/Resume, Stop (and Complete for Stopwatch).
- **Timer end:** Alert with your chosen sound and vibration (Off, Short, Long, Pulse until stopped), at the
  notification or the media volume.
- **Automatic registration:** Every completed TimeBlock (for example `1h20m study`) is saved the moment it finishes,
  in the app (Calendar and Statistics tabs) and in **TB2Cal's own calendar on the phone**, which your phone's
  calendar app shows. A TimeBlock can never be saved twice.
- **Calendar tab:** Month view of your completed TimeBlocks. Tap a day to see its list.
  **Sync with your Gmail** (off by default) also shows the events of the Gmail accounts on the phone, read-only.
- **Statistics tab:** Daily, weekly and monthly totals per TimeBlock name, with a bin button to delete a sum.

## Widgets
- **TimeBlock manager (4×1):** App icon, TimeBlock choice (tap to cycle), T/S (tap to switch Timer/Stopwatch),
  Start, Pause, Stop and, in Stopwatch mode, Complete.
- **Calendar (resizable, 4×4 by default):** The same month view as the Calendar tab. ‹ › change month, tap the month
  name for the current month, + opens TB2Cal, tap a day to open it in the app.

## Staying alive on Xiaomi and other strict phones
- Foreground service with a permanent notification; An exact alarm-clock alarm for the timer end; A heartbeat alarm
  every 10 minutes; Restart after reboot, app update or being swiped away. Nothing is lost if the app is killed.
- **Settings → Background reliability** walks you through the phone settings: Notifications, calendar access,
  battery optimization, exact alarms, Autostart and (Xiaomi) Battery saver. With Gmail events on, also Auto-sync.

## Build
**GitHub:** Every push builds the APK (`.github/workflows/build.yml`). Download it from
*Actions → latest run → Artifacts → TB2Cal-apk*.

**Signing:** The key is not in the repo. Add it once as a repository secret named `KEYSTORE_BASE64`
(Settings → Secrets and variables → Actions). With it, every build has the same signature, so updates install over
the old version. Without it, builds use a temporary key and need a reinstall each time.

**Android Studio:** Open the folder, let Gradle sync, then Run ▶. Min Android 8.0 (API 26), target API 35.

## Stack
Kotlin, Jetpack Compose (Material 3), foreground services, exact `AlarmManager` alarms, `CalendarContract`
(TB2Cal's local calendar; Gmail calendars read-only), `RemoteViews` widgets. No internet access is used.
