# CloseTheLoop — Project Spec

Standalone Wear OS app (target: Samsung Galaxy Watch 7, Wear OS 5 / API 34). Voice-first, hyper-minimal anti-procrastination tool: speak a task + time, watch a glowing bezel "loop" count down, and explicitly close the loop when time's up.

## Environment (verified)
- JDK: Temurin 21.0.7 (arm64) — `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
- Android SDK: `~/Library/Android/sdk` (platform `android-36`, build-tools 35.0.0 / 36.1.0)
- adb: `~/Library/Android/sdk/platform-tools/adb` (not on PATH)
- Test device: Galaxy Watch 7 `SM-L315F`, **Android 16 / API 36 (Wear OS 6)**, wireless adb at `192.168.0.115` (connect port changes when wireless debugging restarts).
- No global Gradle — use `./gradlew` wrapper. `local.properties` holds `sdk.dir` (git-ignored).
- Build: `./gradlew assembleDebug` → sideload: `adb connect <watch-ip>:<port>` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack
- Kotlin 2.0+, Compose compiler plugin, AGP 8.x, Gradle version catalog
- `compileSdk 36`, `targetSdk 34`, `minSdk 30`
- Compose for Wear OS Material 3 (`androidx.wear.compose:compose-material3`)
- Room (KSP) for persistence
- `AlarmManager.setAlarmClock()` / exact alarms + `BroadcastReceiver` + `VibrationEffect`
- `androidx.wear.tiles` + ProtoLayout for the Tile
- `androidx.wear.watchface.complications.datasource` for a `RANGED_VALUE` complication
- Speech: in-app `SpeechRecognizer` (system default service; `RecognizerIntent` activity triggers a chooser on Galaxy Watch)
- Single package: `com.closetheloop`

## Product Decisions (locked)

### 1. Launch behavior
- App opens on the **Loop Dashboard** with a large, prominent **Mic button**.
- One tap → system speech recognizer → parse → loop saved → back to dashboard.
- Deep links from Tile/Complication may go straight to Mic (see §6).

### 2. Parsing & tasks without a time
- Local, offline regex/rule-based parser. Extracts **task name** + **deadline**.
  - Relative: "in 45 minutes", "in 2 hours", "in an hour and a half", "for 30 min"
  - Absolute: "by 6 PM", "at 18:30", "at noon", "tonight", "tomorrow at 9"
- **No time detected → Long-Term list** (no deadline, no ring, no alarm).
- If a parsed absolute time is already past today → roll to tomorrow.
- Defaults: "tonight" alone → 21:00; "tomorrow" alone → 09:00; ambiguous "at 9" → next upcoming 9:00 or 21:00; "tomorrow at 1–6" → PM.

### 3. Dashboard layout & multiple loops
- Short-term loop = has a deadline. Long-term = no deadline.
- Outer bezel: **concentric rings, max 3**, for the 3 most urgent loops **still counting down** (outermost = most urgent). No time-based loop ticking → no rings at all (long-term and expired loops never get a ring).
- Each ring's color is based on remaining fraction: **Green (>50%) → Amber (20–50%) → Red (<20%)**.
- Center: most urgent task name + remaining time, Mic button.
- Rotary/scroll down (`ScalingLazyColumn` / `TransformingLazyColumn`): Upcoming short-term loops (sorted by deadline) → Long-Term to-dos.
- Tap an item → Close Loop / Delete. Long-term items can be given a time later (stretch).

### 4. Expiry & nagging
- At deadline: exact alarm fires → wakes the screen → full-screen **Expired** activity.
- **Periodic buzz:** a strong buzz pattern at expiry, then **repeats every 3 minutes** until resolved (re-armed alarm each time, not a busy loop).
- Expired screen actions: **Close Loop** (done) or **Snooze** chips **+5 / +15 / +30 min**.
- An ongoing notification with the same actions is also posted, so the user can act from the notification shade.
- Alarms are re-registered on `BOOT_COMPLETED` and after app update.

### 5. Data model (Room)
`Loop(id, title, createdAt, deadline: Long?, originalDurationMs: Long?, status: ACTIVE|EXPIRED|CLOSED, snoozeCount, lastSnoozedAt: Long?, closedAt)`
- Ring progress = `(deadline - now) / (deadline - windowStart)` where `windowStart = lastSnoozedAt ?: createdAt`.
- Tests: `./gradlew testDebugUnitTest` (JVM + Robolectric, no device needed).

### 6. Tile & Complication (default chosen — user unsure, revisit after v1)
- **Tile:** most urgent loop's ring + title + remaining time, and a Mic button that deep-links directly into voice capture. Empty state shows "No open loops" + Mic button.
- **Complication (`RANGED_VALUE`, also `SHORT_TEXT` fallback):** progress arc + remaining minutes of the most urgent loop. Tap → opens the app directly into voice capture.
- Both refresh when loops change and on a minute-level cadence.
- Physical button double-press: the user assigns the app in Samsung settings, so it opens the dashboard.

## Build Plan (one clean commit per step)
1. **Scaffold:** Gradle wrapper, version catalog, `.gitignore`, Wear app module, manifest (`standalone=true`), empty M3 Compose activity. Verify `assembleDebug`.
2. **Data layer:** Room entity/DAO/DB, repository, simple manual DI.
3. **Parser:** `TaskParser` + JVM unit tests covering relative, absolute, and no-time phrases.
4. **Voice capture:** speech recognizer launch → parse → save, with a confirmation toast/screen.
5. **Dashboard UI:** concentric rings (max 3), color shifting, center task, scrollable upcoming + long-term lists, item actions.
6. **Alarms:** scheduler, receiver, expired full-screen activity, periodic re-buzz, snooze chips, notification actions, boot re-scheduling, permissions.
7. **Tile:** ProtoLayout tile with ring + Mic deep link.
8. **Complication:** `RANGED_VALUE` data source + tap → voice.
9. **Polish:** AOD/ambient handling, haptics on close, empty states, README sideload notes.

## Conventions
- Keep it minimal: no extra features beyond this spec without asking.
- Business logic (parser, progress/color math) stays pure Kotlin and unit-tested.
- Times are epoch millis stored in UTC and shown in local time.
