# TB2Cal — TimeBlocks to Calendar

By [github.com/pantelmg](https://github.com/pantelmg)

Create TimeBlocks (and Tasks/Events) to track and improve productivity. Completed TimeBlocks are registered in-app.
Use the app's widgets (Calendar, TimeBlock manager and Task/Event manager) for efficiency. Bonus: Import events from your Google account, export TimeBlock statistics for a given time
period to PDF, set Goals, export, import or delete TB2Cal's data by date range, and export or import your
preferences.

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
manager widget's **+**, which ask: Add Task (a ticked square) or Add Event (a calendar).
- **Task:** Its name, a **date** (added from today: **No date**; From another day, in the Calendar tab or on the
  widget: That day; Tap to change it, × for No date), a **time** once it has a date, and **Repeat…** (like Google:
  Every N days/weeks/months/years, weekdays, an optional time, start, and ends never / on a date / after N times; The
  end date starts as the start date, so On + that date means only that day; Any repeat that starts and ends on the
  same day shows as **Today only**, or e.g. **02/11/26 only** once that day has passed).
  - **No date** (like Google Tasks): Listed, marked "No date", at the top of the day lists (app and widget) from the
    day it was added onward until ticked or deleted; Not in the calendars until then; Once ticked it shows, crossed
    out, on the day it was done.
  - **With a date:** On that day (calendars and lists), shown as **Today only** (or e.g. **02/11/26 only**), crossed
    out once ticked; With a time, it also gets a reminder.
  - **Repeating:** On their days, crossed out once ticked.
- **Event:** Its name, date, all day or from–to times, and Repeat…. It counts as completed once its time is over.
  Events are also saved in TB2Cal's phone calendar.
- **In the calendars:** Outlined (transparent) entries, in one color for all tasks and one for all events (Settings).
- **Day list:** Tasks have an outlined square to tick (or untick), Events a solid square of the same size (also in
  Statistics and the widget); Tap an entry to edit it, the bin deletes it. For repeating
  ones both ask: This day only, or All repeats.
- **Reminders:** A notification for timed tasks (with Done) and events: At their start (default), or some time before
  (Settings → Remind me before). A reminder whose time has already passed isn't given.
### Calendar preferences (Settings)
- **In-app Calendar: TimeBlocks/day:** 3 (default), 4 or 5. The widget's capacity grows with its size instead.
- **Gmail account:** Which account's events are shown; The one with the most events is first and picked by default.
- **Gmail import: Languages:** Calendars that publish the same events in several languages (like Google's holidays
  in Greek and in English) can be picked one by one, or none. Your other events are always shown.
- **Gmail events on the Task/Event widget** (on by default): The day's Gmail events also in the widget's Events list
  (with Import from your Gmail on).
- **Completed on the Task/Event widget:** Crossed out (default) or Removed.
- **Tapping a task/event on the widget shows it in the app** (ON by default): The Calendar tab opens on its day, with
  it at the top of the list, pulsing.
- **Allow temporary reordering** (ON by default): Touch and hold any entry of a day's list (short vibration) to move it
  up or down; Entries pulse when touched. The order also shows on the month grid, the Calendar widget and the
  Task/Event widget, and is kept (also after closing the app) until Import from your Gmail (or its account or
  languages) or TB Total is switched, or this setting is turned OFF: Then every list is back in its normal order. New
  entries take their normal place among reordered ones.
- **Allow previous day editing** (OFF by default), for honest records:
  - **OFF:** Past days are read-only: Their TimeBlocks can't be removed (Calendar tab, Statistics), their Tasks/Events
    can't be added, ticked, changed or removed (app and widget), and goals of ended periods can't be set or deleted. New items start from today (a past day's + adds them
    on today). Changing or deleting a repeat that has earlier days offers **This and following** instead of All
    repeats: Its earlier days stay as they were.
  - **ON:** All of it is allowed, and it shows: **Added in retrospect** on days before an item was added, **Ticked
    later** / **Unticked later** on tasks ticked or unticked after their day (day list, widget, Statistics and its PDF),
    **Set later** on goals saved after their period ended.
  - Data management (Export/Import/Delete) works either way, as an explicit, confirmed action.
- **Remind me before:** A scrollable bar (Start, the default, then 5m up to 24h) with **Enter** fixed on the right, for
  any time up to 48h59m before timed tasks and events.
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
- **Active goals overview** (the box next to the description): Every goal per TimeBlock, by range (Daily, Weekly, Monthly, Yearly), each with
  the periods it covers (e.g. `2–8 Nov 26`, `From Nov 26 onward, except Jan 27`, `2–8 Nov 26 to 16–22 Nov 26`); "except"
  lists the periods inside an Apply onward goal that have their own single-period goal instead. The bin deletes a goal:
  With Allow previous day editing OFF, only goals that haven't ended (a running one ends from now; Its past periods keep
  it); ON, any goal.
- Ended periods are read-only (no Tap to set) unless Allow previous day editing is ON; Goals saved after their period
  ended show **Set later**.
- Removing a TimeBlock that has goals (Edit TimeBlocks) asks first: Its goals go too; With the setting OFF, those of
  past periods stay as a record. Without goals it's removed at once.

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
- **All time** (the box after the dates): Everything at once, whole: Never-ending repeats stay never-ending, every day
  is highlighted, and the dates show the first and last day with data. The file: `TB2Cal Data All time 14 Nov
  2026.t2c` (the export day). An All time file opens in Import Preview with All time chosen. Tapping a day or a date
  goes back to a range; Turning All time off brings back the range chosen before.
- Merging never adds the same TimeBlock twice (same name and start); Different same-name TimeBlocks of a day are
  kept, and add up with TB Total and in Statistics.
- Open tasks without a date count as the day of the export (or today, for Delete).

## Export/Import preferences (Settings)
Your TimeBlock list (names, colors and countdowns), goals and settings, in one `.t2cp` file, for a backup or a new
phone: `TB2Cal Preferences 14 Nov 2026.t2cp`. **Import** asks first, since it overwrites them; Your records (TimeBlocks,
Tasks, Events) aren't changed. This phone's own settings are never in the file and stay as they are: The completion
sound, Gmail import (on/off, account, languages, hidden events) and Background reliability.

Every tab fits on one page; Only the Calendar day list and the Statistics and Goals lists scroll, when they're too
long.

## Widgets
In the widget picker: Calendar, TimeBlock manager, Task/Event manager.
- **Calendar (4×4 or larger):** The same month view as the Calendar tab. ‹ › change month, tap the month name for
  the current month, the TB icon opens TB2Cal, tap a day to open it in the app. Each day shows up to 3 entries at
  4 rows, 4 at 5 rows and 5 at 6 rows or taller, with +N next to the day number when there are more. Follows TB Total.
- **TimeBlock manager (4×1):** ⏳/⏱ in a circle (hourglass for Timer, stopwatch for Stopwatch; tap to switch), the
  TimeBlock (tap to cycle through your list), then Start, Pause and Stop, always in the same place. In Stopwatch mode
  ✓ (Complete), in a circle of the same size, appears left of Start. Buttons that can't be used do nothing.
- **Task/Event manager (4×2 or larger, 4×3 to start):** **Tasks/Events ▾** (tap to switch to Tasks or Events;
  remembered; Tasks/Events to start with; The tap area is the size of "Tasks/Events ▾", so the free space up to the
  day arrows doesn't switch it) with its icon (white versions of the Add icons: A ticked square for Tasks, a calendar for Events; For
  Tasks/Events a closed square split corner to corner, a small tick above-left and a small calendar below-right),
  then **‹ Today ›** (always in the same place; ‹ › show other days as DD/MM, back to Today at midnight) and **+** (in
  a rounded square). Below: Open tasks without a date (from the day each was added) and the day's tasks, each with an outlined square to
  tick (or untick); The day's events, with a solid square.
  **Completed on the Task/Event widget** (Settings): Ticked tasks and past events stay **Crossed out** until the day
  ends (default), or are **Removed** from the list. The widget redraws by itself when an event ends and at midnight.

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
