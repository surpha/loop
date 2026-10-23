# CloseTheLoop

A voice-first, hyper-minimal anti-procrastination app for Wear OS (built for the Galaxy Watch 7).
Speak a task and a time, watch the bezel "loop" count down, and close the loop when time's up.

- **Say it:** "Send the report in 45 minutes", "Call mom by 6 PM", "Book flights by 17th October 8pm", or just "Buy milk" (long-term).
- **See it:** up to 3 concentric rings on the bezel, green → amber → red as time runs out.
- **Close it:** at the deadline the watch wakes and buzzes every 3 minutes until you Close Loop or snooze (+5 / +15 / +30).
- **Glance:** a Tile (swipe left from the watch face) with the most urgent loop and a Mic button.

Docs: [CLAUDE.md](CLAUDE.md) (product spec) · [ARCHITECTURE.md](ARCHITECTURE.md) (code structure) · [AGENTS.md](AGENTS.md) (contributor rules).

## Requirements
- macOS/Linux with **JDK 21** and the **Android SDK** (platform 36). Android Studio provides both.
- A Wear OS 3+ watch (API 30+). Tested on Galaxy Watch 7, Wear OS 6 / API 36.
- No global Gradle needed: use `./gradlew`.

Create `local.properties` (git-ignored) pointing at your SDK:
```
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Build & test
```sh
./gradlew testDebugUnitTest   # JVM + Robolectric unit tests, no device needed
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
```

## Sideload onto the watch
1. On the watch: **Settings → About watch → Software information**, tap **Software version** 5× to unlock Developer options.
2. **Settings → Developer options**: enable **ADB debugging** and **Wireless debugging** (watch and computer on the same Wi-Fi).
3. Pair once: **Wireless debugging → Pair new device**, then
   ```sh
   ADB=~/Library/Android/sdk/platform-tools/adb
   $ADB pair <ip>:<pairing-port>        # enter the 6-digit code
   ```
4. Connect (the port is on the main Wireless debugging screen and changes after sleep/reboot):
   ```sh
   $ADB connect <ip>:<port>
   ./gradlew installDebug
   $ADB shell am start -n com.closetheloop/.MainActivity
   ```
   If the first install after reconnecting fails, just run it again.

On first launch, allow **Microphone** (voice capture) and **Notifications** (Time's-up alerts).

### Add the Tile
Long-press the watch face → Tiles → add **CloseTheLoop**, or via adb:
```sh
$ADB shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation add-tile --ecn component com.closetheloop/.tile.LoopTileService
```

### Open from the side button
Samsung: **Settings → Advanced features → Customize buttons → Double press** → CloseTheLoop.

## Troubleshooting
| Symptom | Fix |
|---|---|
| `adb: device offline` | Toggle Wireless debugging on the watch and `adb connect` with the new port. |
| "No connection" when speaking | The watch's speech recognizer needs Wi-Fi/LTE or the paired phone. |
| No Time's-up screen, only a buzz | Notifications are off: Settings → Apps → CloseTheLoop → Notifications. |
| Logs | `$ADB logcat --pid=$($ADB shell pidof com.closetheloop)` |
