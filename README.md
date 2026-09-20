<div align="center">

# ME for Android — Personal Management System (Mobile)

[简体中文](README_CN.md) | **English**

The Android version of **ME** — a personal management system (goals · tasks · time · health). All data is stored locally on your device and is fully compatible with the Windows desktop app ([ME](https://github.com/nailao946/ME)), with cloud sync between the two.

[![Release](https://img.shields.io/github/v/release/nailao946/ME-PE)](https://github.com/nailao946/ME-PE/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/nailao946/ME-PE/total)](https://github.com/nailao946/ME-PE/releases/latest)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-orange)
![License](https://img.shields.io/badge/License-MIT-green)
[![Stars](https://img.shields.io/github/stars/nailao946/ME-PE?style=social)](https://github.com/nailao946/ME-PE/stargazers)

**⬇️ [Download the latest APK](https://github.com/nailao946/ME-PE/releases/latest)** · 💻 [Windows desktop version](https://github.com/nailao946/ME)

</div>

> **Note:** the app's UI is currently Chinese-only. The project is fully usable if you can read basic Chinese; issues and PRs in English are always welcome.

---

## Features (aligned with the desktop version)

| Module | Features |
|--------|----------|
| 📋 Tasks | One-time / recurring / quantitative tasks, date bar filtering, tag filters, check-ins (multiple per day), **▲▼ manual sorting**, subtasks |
| 🎯 Goals | Short-term / long-term / idea categories, tag system, colors, parent-child hierarchy, quantitative goals, automatic progress |
| 📅 Calendar | Monthly completion-rate heat blocks, day details, monthly check-in rate / pending / perfect-streak stats |
| ⏱️ Time | One-tap tag timers, today timeline, weekly / monthly distribution rings, tag management |
| 💚 Health | Sleep / weight-BMI / water containers / mood / uric acid (normal ranges by sex) / exercise items / sedentary counter / medication records |
| 📊 Compare | Two-parameter overlay trends + AI correlation analysis (OpenAI-compatible API) |
| 🗺️ Map | Goal tree overview, progress rings, overall progress |
| 📝 Review | Weekly / monthly completion-rate trends, goal progress, review notes |
| ⚙️ Settings | Light / dark / follow-system theme, 6 accent colors, water & activity goals, **backup export/import**, AI providers |

Also included: **cloud sync** (GitHub / Gitee / WebDAV, device-flow account login, token stored locally), **Pomodoro timer** with status-bar notification timing, **custom modules** (any record type with trend charts), **medication reminders** via AlarmManager, **check-in heatmaps** per task.

---

## Data compatibility with the desktop version

- Storage format: `files/JsonData/*.json` — field names / enum values / time formats are identical to the desktop app's `%LocalAppData%\ME\JsonData`.
- Desktop backups are directories (`me_backup_*.db` containing `*.json`) → zip the directory and import it on the phone via "Settings → Import backup".
- The phone's "Export backup" produces `me_backup_<timestamp>.zip` → unzip it over the desktop `JsonData` folder to sync back to the PC.
- Or simply use the built-in cloud sync (GitHub / Gitee / WebDAV) on both ends — no manual file shuffling needed.

---

## Build

Requires JDK 17 and Android SDK 34:

```bash
./gradlew assembleDebug     # output: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # install directly on a connected device
```

- Minimum Android 8.0 (API 26), target Android 14 (API 34).
- Stack: Kotlin + Jetpack Compose (Material 3) + kotlinx.serialization + OkHttp; no third-party database.
- Medication reminders use AlarmManager with daily repeating notifications and boot-time rescheduling (`BootReceiver`).

---

## Technical notes

- JSON serialization uses `@SerialName("PascalCase")` to align with C# property names; enums are stored as numbers (e.g. GoalColor: red=0 … yellow=5).
- `LocalDateTime` is serialized as `yyyy-MM-ddTHH:mm:ss` (no timezone), compatible with C# `System.Text.Json` defaults and TimeSpan's `c` format.
- `DataBus.rev` is a global revision counter: every repository write increments it and Compose recomposes on change.

---

## Project structure

```
app/src/main/java/com/joe/mepe/
├── MEApp.kt / MainActivity.kt      # app entry, notification channels
├── data/
│   ├── Models.kt                   # all data models (identical fields to desktop)
│   ├── Serializers.kt              # DateTime/TimeSpan-compatible serializers
│   ├── JsonStore.kt / Repos.kt     # JSON storage + repository layer
│   ├── TaskLogic.kt                # task occurrence / completion / progress rules
│   └── BackupManager.kt            # zip backup export & import
├── notify/                         # medication reminder alarms + boot rescheduling
├── ai/LlmService.kt                # OpenAI-compatible chat client
└── ui/
    ├── theme/ Charts / Components  # theme, Canvas charts (line / bar / ring / progress), shared components
    ├── AppNav.kt                   # bottom 5 tabs + top map / review / settings entries
    ├── tasks/ goals/ calendar/ timetrack/
    ├── health/                     # 8 health sub-tabs + compare + AI analysis
    └── map/ review/ settings/
```

---

## Recent Updates

### v2.4.45

- Quantitative tasks and goals at 100% now remain completed on every date.

### v2.4.44

- Added bilingual UI preferences and synchronized goal completion history with today/past-completed sections.

### v2.4.41

- **Fixed misaligned taps on the Health page**: mid-animation page positions were synced back to the tab bar, which interrupted the pending scroll — tapping "Weight" landed on "Sleep", "Water" landed on "Body", "Mood" landed on "Water" (top tabs affected too). Page position is now synced only after the pager settles
- **Pinned health overview**: the overview is no longer a tab — it now stays pinned above the tab bar (collapsible by tapping its title), so today's summary is visible on every sub-page and metric/quick-record tiles jump straight to the right tab; everything else scrolls as usual
- **Sort & collapse for record lists**: all record lists (water today, uric acid history, time-tracking daily records, etc.) gained a sort icon (oldest-first / newest-first, icon only) and a collapse chevron in the title row, with expand/collapse animations
- **"Today" page in Review**: period selector is now Today / Week / Month; the Today page shows today's completion rate, done tasks, remaining tasks and a 7-day rate chart, with time stats focused on today; writing reviews & history stay on Week/Month
- **Review time stats as line charts that follow the period**: the 14-day daily and 12-month monthly duration charts are now line charts; tapping the Today / This week / This month / All labels switches the chart to that range
- **Equal stat-card heights**: cards without a "vs previous period" line now reserve the same row, so all cards in a row share the same height
- **Calendar day detail follows task order**: the day's task list now sorts like the Tasks page (priority descending, then manual order), plus a daily progress row (done / total for that day, same counting as Review)
- **Multi-cloud sync (redundant backups)**: any of GitHub / Gitee / WebDAV with credentials filled is enabled; uploads are pushed to ALL enabled clouds at once — one failing doesn't affect the others and is caught up on the next upload; downloads prefer the cloud with the most recent successful upload and fall back to the next one on failure; branches are remembered per cloud (GitHub=main, Gitee=master), old configs migrate automatically

### v2.4.40

- **Fixed WebDAV (Jianguoyun) uploads failing with HTTP 409**: Jianguoyun and other WebDAV services never create parent folders implicitly — uploading into a missing folder always returns 409 (AncestorsNotFound). The old code mistook the folder-creation request's 409 for "folder already exists, go ahead", so nothing was created and every file failed. The app now creates the sync folder level by level before uploading, and a 409 during upload triggers an automatic folder re-creation plus one retry; the server address defaults to Jianguoyun (https://dav.jianguoyun.com/dav/) and is pre-filled when switching to WebDAV (desktop version fixed in sync)

### v2.4.51

- **Long-press app icon shortcuts**: like WeChat, long-pressing the ME icon now shows a quick menu with Start Timer (starts time tracking with the most recently used tag via a transparent trampoline — toast plus the running chronometer notification, no app UI), Stop Timer (stops the running timer), and Add Widget (opens a short guide on placing the home-screen widget); fully localized in Chinese and English

### v2.4.50

- **Home screen widget rebuilt**: light/dark colours now follow the system night mode (dark launchers get a dark card instead of the fixed white one), the widget picker gains a preview image so older launchers no longer show a blank entry, the row count adapts to the widget's resized height (2–10 rows), the renderer is exception-proof (a data failure can no longer leave the widget permanently blank — it falls back to "open the app to view"), the widget gets a fallback refresh on app start, and widget strings are localized (English added)

### v2.4.49

- **Module CSV export**: the module history dialog can now export all records as a CSV file
- **Number field constraints**: module fields can define min / max / step; the record dialog blocks out-of-range input
- **Sync health log**: the last 30 upload/download results with duration are kept and shown on the sync page

### v2.4.48

- **Module knowledge library**: custom modules can now hold offline HTML "library pages" with companion CSV — AI can generate one from the module's records, and pages open in an in-app WebView; data lives in `html_library.json` and syncs with the desktop app
- Conflict resolution gains a third option "keep both" (the cloud copy is saved as `*.from-cloud.json` and keeps syncing)

### v2.4.47

- **Calendar screen performance**: the whole month's status is now precomputed once per month instead of recomputing per cell on every frame, which removed the lag when opening the calendar; day cells also get an iOS-like ripple on press
- **Home screen widget**: new "今日任务" AppWidget (standard Android widget protocol, works on stock Android / Xiaomi HyperOS / ColorOS / OriginOS) showing today's tasks and completion count, refreshed automatically when data changes
- **Custom modules on the surface**: a modules shortcut was added to the quick links of every main screen; the module editor no longer overlaps the icon with the record badge, and "recent records" is now strictly the latest by date, time and id
- Removed the redundant "custom modules" entry from settings (the module page is the single entry point)

### v2.4.46

- **Task completion history**: one-off tasks and quantitative tasks that reach 100% now appear under "Done today" only on the day they were completed, then move to the "Completed earlier" group showing the actual completion date; the new "Today's goals" card on the tasks screen follows the same rule and no longer lists historically completed items
- **Calendar view**: removed the check-in rate — the first stat chip now reads "Today's tasks: Done / Not done", and month cells are coloured by done / partially done instead of a rate gradient; the check-in streak counts a day as checked in as soon as any single task is completed that day
- **Cloud sync fixed and hardened**: Gitee and WebDAV uploads no longer fail — Gitee now recovers a missing response sha, falls back between main/master when the branch doesn't exist and no longer hides auth errors; the "latest version" check uses content hashes plus per-cloud baselines instead of file timestamps, so downloading never overwrites local data that hasn't been uploaded yet; upload/download now report per cloud which platforms succeeded and which failed with the reason
- **Cloud sync diagnostics and conflict resolution**: new "诊断连接" tests connect → list → read on every cloud with per-step timings; files changed on both sides can now be resolved one by one (keep local or take cloud) from the sync page
- **Custom modules editor enriched**: icon picker with a visible selected state, quick-start templates, a colour swatch palette, field reordering and a live card preview; quantitative task cards now show "still needs +N today" for the daily target

### v2.4.45

- **Glass theme**: new "毛玻璃" (frosted-glass) option in Appearance — gradient backdrop with translucent frosted cards, dark/light follows the system
- **Custom modules redesigned**: module cards now follow a Feishu-style layout — large-radius cards with a hairline border, tinted rounded icon block, title plus meta line and a record-count badge, a latest-record summary, a hairline divider and an icon+text action row

### v2.4.39

- **Fixed Gitee upload failing with "sha is missing" (0/15 files)**: Gitee's API differs from GitHub's — creating a file requires POST, while PUT is strictly an update endpoint that must carry the file's sha (rejected with HTTP 400 otherwise, even when the file doesn't exist). New files previously went through a sha-less PUT, so every first-time upload failed. File creation now uses POST, with an automatic fallback to a sha-carrying update when the file already exists; fixed together with the desktop version

### v2.4.38

- **Review-screen statistics now match the desktop**: "Completed tasks" became **completed / total due**. Total due counts only tasks actually due that day — subtasks, quantitative tasks without a daily target, and recurring tasks not scheduled today (e.g. a Sat/Sun task on Monday) are excluded; completion rate = completed ÷ total due. Quantitative tasks with a daily target count as completed on days with a check-in record; a finished quantitative task counts only up to the day it reached its target
- **"vs previous period" on the Review screen**: completion rate, completed tasks and time invested (total duration) all show green-up / red-down deltas — weekly review compares last week, monthly compares last month; time statistics on "Today" compare yesterday
- **Time statistics "All" is now a 12-month monthly bar chart** — month-over-month trends at a glance instead of only the last 14 days
- **Check-in heatmap fixed**: recurring tasks previously never lit up due to a date-logic issue; they now light up based on that day's check-in records, consistent with the list view

📖 Full changelog (Chinese): [README_CN.md](README_CN.md) · 💻 Desktop version: [ME](https://github.com/nailao946/ME)

---

## License

MIT
