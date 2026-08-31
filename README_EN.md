<div align="center">

# ME for Android — Personal Management System (Mobile)

[简体中文](README.md) | **English**

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

### v2.4.39

- **Fixed Gitee upload failing with "sha is missing" (0/15 files)**: Gitee's API differs from GitHub's — creating a file requires POST, while PUT is strictly an update endpoint that must carry the file's sha (rejected with HTTP 400 otherwise, even when the file doesn't exist). New files previously went through a sha-less PUT, so every first-time upload failed. File creation now uses POST, with an automatic fallback to a sha-carrying update when the file already exists; fixed together with the desktop version

### v2.4.38

- **Review-screen statistics now match the desktop**: "Completed tasks" became **completed / total due**. Total due counts only tasks actually due that day — subtasks, quantitative tasks without a daily target, and recurring tasks not scheduled today (e.g. a Sat/Sun task on Monday) are excluded; completion rate = completed ÷ total due. Quantitative tasks with a daily target count as completed on days with a check-in record; a finished quantitative task counts only up to the day it reached its target
- **"vs previous period" on the Review screen**: completion rate, completed tasks and time invested (total duration) all show green-up / red-down deltas — weekly review compares last week, monthly compares last month; time statistics on "Today" compare yesterday
- **Time statistics "All" is now a 12-month monthly bar chart** — month-over-month trends at a glance instead of only the last 14 days
- **Check-in heatmap fixed**: recurring tasks previously never lit up due to a date-logic issue; they now light up based on that day's check-in records, consistent with the list view

📖 Full changelog (Chinese): [README.md](README.md) · 💻 Desktop version: [ME](https://github.com/nailao946/ME)

---

## License

MIT
