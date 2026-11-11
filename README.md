# TB2Cal — TimeBlocks to Calendar

By [github.com/pantelmg](https://github.com/pantelmg)

Create TimeBlocks, with your own conditions, to track and improve productivity. Completed TimeBlocks are registered in
the app and the local Calendar. Use the app's widgets (Calendar and TimeBlock manager) for efficiency. Bonus: Import
events from your Google account, export TimeBlock statistics for a given time period to PDF.

## TimeBlocks tab
- **Your list:** `study` and `workout` to start with. Add, rename and remove TimeBlocks, each with a color: 11 presets
  or any **Custom color…** (pick from the color grid or type R, G, B).
- **Two modes:** **Timer** counts down from the hours and minutes you set (or a preset); **Stopwatch** counts up until
  you tap COMPLETE. Each TimeBlock remembers its own countdown as soon as you change it, also for the widget.
- **Controls:** START locks every option; PAUSE/RESUME keeps them locked; STOP discards the TimeBlock and unlocks them.
  The buttons stay in the same place in both modes.
- **Permanent notification** while a TimeBlock runs: Name, mode and live time, with Pause/Resume, Stop (and Complete in
  Stopwatch mode).
- **Timer end alert:** Your own sound (or the default one), at the notification or the media volume, with vibration
  Off, Short, Long or Pulse until stopped.
- **Automatic registration:** Every completed TimeBlock (for example `1h20m study`) is saved the moment it finishes,
  in the app and in **TB2Cal's own calendar on the phone**, which your phone's calendar app shows. A TimeBlock can
  never be saved twice.

## Calendar tab
- Month view of your completed TimeBlocks. Tap a day to see its list.
- The bin next to a TimeBlock deletes it from the app and from TB2Cal's phone calendar (after you confirm).
- **Sync with your Gmail** (off by default): Also shows the events of the Google accounts on the phone, read-only and
  instantly, in the same colors as Google's calendar app.

## Statistics tab
- **Daily, Weekly, Monthly and Yearly** totals per TimeBlock name; ‹ › go to earlier or later periods.
- **List:** Each name with its count, total and a bar; The bin deletes that sum from Statistics (after you confirm).
- **Graph:** Stacked columns per hour (daily), day (weekly, monthly) or month (yearly), in the TimeBlocks' colors.
- **Export PDF:** Saves the graph of the selected period with a table of every TimeBlock (count, total, share). The
  file is named after the period only: `11 Nov 2026`, `09 to 15 Nov 2026`, `27 Oct to 02 Nov 2026`, `Oct 2026` or
  `2026`.

Every tab fits on one page; Only the Calendar day list and the Statistics list scroll, when they're too long.

## Widgets
- **TimeBlock manager (4×1):** T/S (tap to switch Timer/Stopwatch), the TimeBlock (tap to cycle through your list),
  then Start, Pause and Stop, always in the same place. In Stopwatch mode a boxed ✓ (Complete) appears left of Start.
  Buttons that can't be used do nothing.
- **Calendar (resizable, 4×4 by default):** The same month view as the Calendar tab. ‹ › change month, tap the month
  name for the current month, the TB icon opens TB2Cal, tap a day to open it in the app. Each day shows up to 3
  entries, with +N next to the day number when there are more.

## Staying alive on Xiaomi and other strict phones
- Foreground service with a permanent notification; An exact alarm-clock alarm for the timer end; A heartbeat alarm
  every 10 minutes; Restart after reboot, app update or being swiped away. Nothing is lost if the app is killed.
- **Settings → Background reliability** walks you through the phone settings: Notifications, calendar access,
  battery optimization, background restriction, exact alarms, Autostart and (Xiaomi) Battery saver. With Gmail events
  on, also Auto-sync and Calendar sync.

## Privacy
TB2Cal has no internet access. Your TimeBlocks, statistics and settings stay on the phone. Gmail events are only read
from the phone's own calendar storage, never changed.

## Build
**GitHub:** Every push builds the APK (`.github/workflows/build.yml`). Download it from
*Actions → latest run → Artifacts → TB2Cal-apk*.

**Signing:** The key is not in the repo. Add it once as a repository secret named `KEYSTORE_BASE64`
(Settings → Secrets and variables → Actions). With it, every build has the same signature, so updates install over
the old version. Without it, builds use a temporary key and need a reinstall each time.

**Android Studio:** Open the folder, let Gradle sync, then Run ▶. Min Android 8.0 (API 26), target API 35.

## Stack
Kotlin, Jetpack Compose (Material 3), foreground services, exact `AlarmManager` alarms, `CalendarContract`
(TB2Cal's local calendar; Gmail calendars read-only), `RemoteViews` widgets, `PdfDocument` for the PDF export.
