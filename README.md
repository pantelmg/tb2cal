# TB2Cal — TimeBlocks to Calendar

By [github.com/pantelmg](https://github.com/pantelmg)

Create TimeBlocks (and Tasks/Events), with your own conditions, to track and improve productivity. Completed TimeBlocks
are registered in the app and the local Calendar. Use the app's widgets (Calendar, TimeBlock manager and Task/Event
manager) for efficiency. Bonus: Import events from your Google account, export TimeBlock statistics for a given time
period to PDF, set Goals, and export, import or delete TB2Cal's data by date range.

## TimeBlocks tab
- **Your list:** `study` and `workout` to start with. Add, rename and remove TimeBlocks, each with a color: 11 presets
  or any **Custom color…** (slide your finger on the color map, or type R, G, B
  (0–255) or a hex code).
- **Two modes:** **Timer** counts down from the hours and minutes you set (or a preset); **Stopwatch** counts up until
  you tap COMPLETE. Each TimeBlock remembers its own countdown as soon as you change it, also for the widget.
- **Presets:** Tap one under the countdown to set it. **Edit** (at the end of the row) adds, removes or resets them.
- **Controls:** START locks every option; PAUSE/RESUME keeps them locked; STOP discards the TimeBlock and unlocks them.
  The buttons stay in the same place in both modes.
- **Permanent notification** while a TimeBlock runs: Name, mode and live time, with Pause/Resume, Stop (and Complete in
  Stopwatch mode).
- **Timer end alert:** Your own sound (or the default one), at the notification or the media volume, with vibration
  Off, Short, Long or Pulse until stopped. **Test alert** in Settings; It stops from its notification or when you leave
  Settings. While the sound plays, other apps' music and videos pause, and they resume after it.
- **Completion screen:** After a timer ends, it shows when you tap its notification, or first thing whenever you open
  the app (icon, widget, a calendar day… which then opens after it), until you tap DONE, which also stops the alert.
  Stopwatch: It shows right after Complete (app, notification or widget).
- **Live time** in the app and the notification, updated exactly when each second changes.
- **Secondary color** (Settings → Appearance): The phone's colors (default), or Purple, Blue, Teal, Green, Yellow,
  Orange, Red or Pink for buttons, switches and highlights.
- **Automatic registration:** Every completed TimeBlock (for example `1h20m study`) is saved the moment it finishes,
  in the app and in **TB2Cal's own calendar on the phone**, which your phone's calendar app shows. A TimeBlock can
  never be saved twice.

## Calendar tab
- Month view of your completed TimeBlocks, 3, 4 or 5 per day (Settings). Tap a day to see its list, under its short
  date (e.g. `Mon 15/12/26`).
- **TB Total ON/OFF** (OFF by default, remembered): Merges same-name TimeBlocks of a day into one
  total, in the order of the first of each name, in the grid, the list (marked *TimeBlock Total*) and the widget.
- The bin next to a TimeBlock deletes it from the app and from TB2Cal's phone calendar (after you confirm). On a
  TB Total entry it deletes every TimeBlock of that name on that day.
- **Import from your Gmail** (off by default): Also shows the events of a Google account on the phone, read-only and
  instantly, in the same colors as Google's calendar app.

## Tasks and Events
A side feature next to TimeBlocks, added with **+Task/Event** (Calendar tab, left of TB Total) or the Task/Event
manager widget's **+**, which ask: Add Task or Add Event.
- **Task:** Its name, and **Repeat…** (like Google: Every N days/weeks/months/years, weekdays, an optional time,
  start, and ends never / on a date / after N times). Without a repeat it's a one-time task with no date: It waits on
  the widget (and at the top of today's list) until ticked, then shows, crossed out, on the day it was done.
  Repeating tasks show on their days, crossed out once ticked.
- **Event:** Its name, date, all day or from–to times, and Repeat…. It counts as completed once its time is over.
  Events are also saved in TB2Cal's phone calendar.
- **In the calendars:** Outlined (transparent) entries, in one color for all tasks and one for all events (Settings).
- **Day list:** Tasks have a circle to tick (or untick); Tap an entry to edit it, the bin deletes it. For repeating
  ones both ask: This day only, or All repeats.
- **Reminders:** A notification at the time of timed tasks (with Done) and events.
### Calendar preferences (Settings)
- **In-app Calendar: TimeBlocks/day:** 3 (default), 4 or 5. The widget's capacity grows with its size instead.
- **Gmail account:** Which account's events are shown; The one with the most events is first and picked by default.
- **Gmail import: Languages:** Calendars that publish the same events in several languages (like Google's holidays
  in Greek and in English) can be picked one by one, or none. Your other events are always shown.
- **Gmail events on the Task/Event widget** (off by default): Today's Gmail events also in the widget's Events list.
- **Visible to other calendar apps** (on by default): Completed TimeBlocks and Events also show in the phone's other
  calendar apps; Off hides TB2Cal's phone calendar there (and the completion screen's Open in another calendar app).
- **Task and Event colors:** One preset color for all tasks and one for all events; Changing it recolors past and
  future ones.
- **Allow hiding Gmail events** (off by default): A Hide button next to Gmail events in the Calendar tab hides the
  event (here and on the widget; Your Google account isn't changed). For a repeating event it then asks: All repeating
  events, or On this day only. Turning Import from your Gmail off
  and on brings them back; Turning Allow hiding off shows them while it's off.

## Statistics tab
- **Daily, Weekly, Monthly and Yearly** totals per TimeBlock name; ‹ › go to earlier or later periods. The period and
  List/Graph you picked are remembered.
- **List:** Each name with its count, total and a bar; The bin deletes that sum from Statistics (after you confirm).
- **Graph:** Stacked columns per hour (daily), day (weekly, monthly) or month (yearly), in the TimeBlocks' colors.
- **TimeBlocks / Tasks/Events** (remembered): In Tasks/Events, the card shows how many are completed, with
  **Tasks / Events / Both** (remembered) in place of List/Graph, and the period's list below, completed ones crossed
  out.
- **Export PDF:** Asks what to include: TimeBlocks (the graph and a table of every TimeBlock: count, total, share),
  Tasks and/or Events (the completed ones of the period). The file is named `TB2Cal Statistics` + the period: `TB2Cal Statistics 11 Nov 2026`,
  `TB2Cal Statistics 09 to 15 Nov 2026`, `TB2Cal Statistics 27 Oct to 02 Nov 2026`, `TB2Cal Statistics Oct 2026` or
  `TB2Cal Statistics 2026`.

## Goals tab
- Laid out like Statistics: **Daily, Weekly, Monthly and Yearly**, ‹ › for other periods (also later ones).
- Every TimeBlock of your list: **Tap to set** opens hours and minutes; **Retap to save** saves the goal (0h 0m
  removes it). The bar shows how much of the goal is done, with **Z% of Goal** next to the name.
- **N goals set in this time period:** The TimeBlocks with a goal of the period type shown.
- **Apply onward ON/OFF** (one per period type, ON by default, remembered; When ON: Applied on retap to save): ON, a goal you
  save repeats in every later period of that type; OFF, it's only for the period shown.

## Data management (Settings)
Only TB2Cal's own TimeBlocks, Tasks and Events. Events imported from your Gmail stay in your Google account; They're
never exported, imported or deleted here.
- **Export:** Opens **Export Preview**, the Calendar tab's month view (same rules) with only TB2Cal's data. Tap the
  first and last day (or pick them in **Between DD/MM/YY and DD/MM/YY** below), tick **TimeBlocks, Tasks, Events**
  (all ticked to start), then **Export** (bottom-right). The file is named like the PDFs: `TB2Cal Data 09 to 15 Nov
  2026.t2c`, `TB2Cal Data Oct 2026.t2c`… Repeating tasks and events keep their rule, limited to the chosen days.
- **Import:** Pick a `.t2c` file; **Import Preview** shows its data with its days already chosen and highlighted
  (change them the same way). If TB2Cal already has data on those days, it asks: **Merge** (keeps both, nothing added
  twice) or **Overwrite** (deletes TB2Cal's data there first).
- **Delete:** **Delete Preview**, the same screen; Asks before deleting, and can't be undone. Repeating tasks and
  events lose only their days in the chosen range.
- Open tasks without a date count as the day of the export (or today, for Delete).

Every tab fits on one page; Only the Calendar day list and the Statistics and Goals lists scroll, when they're too
long.

## Widgets
- **TimeBlock manager (4×1):** ⏳/⏱ (hourglass for Timer, stopwatch for Stopwatch; tap to switch), the TimeBlock
  (tap to cycle through your list), then Start, Pause and Stop, always in the same place. In Stopwatch mode a boxed ✓
  (Complete) appears left of Start. Buttons that can't be used do nothing.
- **Task/Event manager (4×2 or larger, 4×3 to start):** **Tasks/Events ▾** (tap to switch to Tasks or Events;
  remembered) with its icon (a target with an arrow for Tasks, a wine glass for Events, both split by a line for
  Tasks/Events), then **‹ Today ›** (always in the same place; ‹ › show other days as DD/MM, back to Today at
  midnight) and **+** (the same circle as the TimeBlock manager's). Below: Open tasks without a date (on Today) and
  the day's tasks, each with a circle to tick off; The day's events (past ones crossed out).
- **Calendar (4×4 or larger):** The same month view as the Calendar tab. ‹ › change month, tap the month name for
  the current month, the TB icon opens TB2Cal, tap a day to open it in the app. Each day shows up to 3 entries at
  4 rows, 4 at 5 rows and 5 at 6 rows or taller, with +N next to the day number when there are more. Follows TB Total.

## Staying alive when Android stops apps
- Foreground service with a permanent notification; An exact alarm-clock alarm for the timer end; A heartbeat alarm
  every 10 minutes; Restart after reboot, app update or being swiped away. Nothing is lost if the app is killed.
- **Settings → Background reliability** walks you through the phone settings: Notifications, calendar access,
  battery optimization, background restriction, exact alarms, pause app activity if unused / remove permissions if
  app is unused, Autostart and (Xiaomi) pop-up and lock screen notifications and Battery saver. With Gmail events on,
  also Auto-sync and Calendar sync.

## Privacy
TB2Cal has no internet access. Your TimeBlocks, statistics and settings stay on the phone; Exported `.t2c` files and
PDFs go only where you save them. Gmail events are only read
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
