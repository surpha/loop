# CloseTheLoop — Project Spec

Standalone Wear OS app (target: Samsung Galaxy Watch 7, Wear OS 5 / API 34). Voice-first, hyper-minimal anti-procrastination tool: speak a task + time, watch a glowing bezel "loop" count down, and explicitly close the loop when time's up.

## Environment (verified)
- JDK: Temurin 21.0.7 (arm64) — `/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
- Android SDK: `~/Library/Android/sdk` (platform `android-36`, build-tools 35.0.0 / 36.1.0)
- adb: `~/Library/Android/sdk/platform-tools/adb` (not on PATH)
- Test device: Galaxy Watch 7 `SM-L315F`, **Android 16 / API 36 (Wear OS 6)**, wireless adb at `192.168.0.115` (connect port changes when wireless debugging restarts).
- No global Gradle — use `./gradlew` wrapper. `local.properties` holds `sdk.dir` (git-ignored).
- Build: `./gradlew assembleDebug` → sideload: `adb connect <watch-ip>:<port>` then `adb install -r wear/build/outputs/apk/debug/wear-debug.apk`

## Tech Stack
- Kotlin 2.0+, Compose compiler plugin, AGP 8.x, Gradle version catalog
- `compileSdk 36`, `targetSdk 34`, `minSdk 30`
- Compose for Wear OS Material 3 (`androidx.wear.compose:compose-material3`)
- Room (KSP) for persistence
- `AlarmManager.setAlarmClock()` / exact alarms + `BroadcastReceiver` + `VibrationEffect`
- `androidx.wear.tiles` + ProtoLayout for the Tile
- `androidx.wear.watchface.complications.datasource` for a `RANGED_VALUE` complication
- Speech: in-app `SpeechRecognizer` (system default service; `RecognizerIntent` activity triggers a chooser on Galaxy Watch)
- Package / applicationId: `com.closetheloop`. Modules: `:shared` (data, parser, domain), `:wear`, `:mobile` (phone companion, in progress)

## Product Decisions (locked)

### 1. Launch behavior
- App opens on the **Loop Dashboard** with a large, prominent **Mic button**.
- One tap → system speech recognizer → parse → loop saved → back to dashboard.
- Deep links from Tile/Complication may go straight to Mic (see §6).

### 2. Parsing & tasks without a time
- Local, offline regex/rule-based parser. Extracts **task name** + **deadline**.
  - Relative: "in 45 minutes", "in 2 hours", "in an hour and a half", "for 30 min", "in 3 minutes 30 seconds"
  - Trailing duration (no preposition, full unit words): "Study 4 hrs", "Nap 1 hr 30 mins", "Plank 90 seconds"
  - Absolute: "by 6 PM", "at 18:30", "at noon", "tonight", "tomorrow at 9"
  - Dates: "by 17th October 8pm", "on Oct 17, 2027 at 5 pm", "at 8pm on 18 Oct", "by the 25th" (next such day of month)
  - Weekdays: "on Friday", "by Wednesday 5pm", "this Saturday 8pm", "next Monday"
- **No time detected → Long-Term list** (no deadline, no ring, no alarm).
- If a parsed absolute time is already past today → roll to tomorrow.
- Defaults: "tonight" alone → 21:00; "tomorrow" alone → 09:00; ambiguous "at 9" → next upcoming 9:00 or 21:00; "tomorrow at 1–6" → PM.
- Dates: no time → 09:00; unmarked 1–6 → PM; past date without a year → next year; "the Nth" already past → next month.
- Weekdays: "Friday"/"this Friday" = the coming one (today counts if the time is still ahead, else +7 days); "next Friday" = coming one but never today; same time defaults as dates. A weekday right before an explicit date ("Friday 23rd October") is ignored.

### 3. Dashboard layout & multiple loops
- Short-term loop = has a deadline. Long-term = no deadline.
- Outer bezel: **concentric rings, no limit**, one per loop **still counting down** (outermost = soonest deadline). Rings share a fixed band, so they get thinner as more are added (7 dp for 1–3, hairlines past ~10). No time-based loop ticking → no rings at all (long-term and expired loops never get a ring).
- Each loop has its **own stable color** (8-color palette, chosen from its id; no reds). In the **final 20%** its ring and time text turn **red and pulse**. List cards show a matching color dot.
- Center: most urgent task name + remaining time, Mic button.
- Rotary/scroll down (`ScalingLazyColumn` / `TransformingLazyColumn`): Upcoming short-term loops (sorted by deadline) → Long-Term to-dos.
- Tap an item → Close Loop / Edit by voice / Edit by typing / Delete. Editing with a time (or adding one to a long-term item) sets a new deadline; without a time only the title changes.

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
