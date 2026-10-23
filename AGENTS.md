# AGENTS.md — Working Agreement for AI Agents

Read this first, then [CLAUDE.md](CLAUDE.md) (product spec) and [ARCHITECTURE.md](ARCHITECTURE.md) (code structure).

## Project
CloseTheLoop: Wear OS app (Galaxy Watch 7) for voice-captured, time-boxed tasks ("loops"), with an Android phone companion in progress. Package `com.closetheloop`. Modules: `:shared` (Room data, parser, domain, time formatting — all unit tests live here), `:wear` (watch app), `:mobile` (phone app, upcoming).

## Commands
| Task | Command |
|---|---|
| Build APK | `./gradlew assembleDebug` |
| Unit tests (JVM + Robolectric) | `./gradlew testDebugUnitTest` |
| Install on the watch only | `ANDROID_SERIAL=<watch-ip:port> ./gradlew :wear:installDebug` |
| Install on the phone only | `ANDROID_SERIAL=<phone-serial> ./gradlew :mobile:installDebug` |
| Launch on watch | `$ADB shell am start -n com.closetheloop/.MainActivity` |
| Logs | `$ADB logcat --pid=$($ADB shell pidof com.closetheloop)` |

`ADB=~/Library/Android/sdk/platform-tools/adb` (not on PATH). No global Gradle — always use `./gradlew`.

Device: Galaxy Watch 7 on **API 36** (paired once). Reconnect with `$ADB connect 192.168.0.115:<port>` — the port is on the watch's Wireless debugging screen and changes after sleep/reboot. Test exact-alarm and full-screen-intent behavior against API 36 rules.

## Toolchain (pinned — do not bump casually)
- JDK 21 (Temurin), Gradle 8.14.3, AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.4
- `compileSdk 36`, `targetSdk 34`, `minSdk 30`
- Use 2025-era stable AndroidX releases; 2026 releases may require AGP 9 / compileSdk 37.
- All versions live in [gradle/libs.versions.toml](gradle/libs.versions.toml). Never hard-code versions in build files.
- Regenerating the wrapper: `gradle wrapper --gradle-version <v> --distribution-type all --no-validate-url` (URL probe times out otherwise).

## Rules
1. **Follow the spec.** Product decisions in CLAUDE.md are locked. Don't add features without asking.
2. **One build-plan step = one commit.** Conventional messages: `feat(scope):`, `fix:`, `chore:`, `test:`, `docs:`.
3. **Every commit must pass** `./gradlew testDebugUnitTest assembleDebug`.
4. **Pure logic stays pure.** Parser, progress, color, and time math are plain Kotlin with an injectable clock (`() -> Long`) and JVM unit tests. No `System.currentTimeMillis()` inside logic.
5. **Times:** epoch millis (UTC) in storage; convert to local time only for display/parsing.
6. **No hidden state:** all persistence goes through `LoopRepository`; UI never touches the DAO.
7. **Alarms are re-armed, never busy-looped.** Periodic buzz = new exact alarm every 3 min.
8. **Keep docs current:** update ARCHITECTURE.md when adding a component; update CLAUDE.md if a spec decision changes (with user approval).
9. Don't commit `local.properties`, build outputs, or `.idea/`.

## Testing expectations
- Data layer: Robolectric + in-memory Room (`src/test/.../data`).
- Parser/logic: plain JUnit, fixed clock + fixed `ZoneId`.
- UI/alarms/tile/complication: manual verification on the watch; record steps in the commit body.
