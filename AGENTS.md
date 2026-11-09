# Chanting Buddy — Project Guide & Current State

> Living document for continuing development across sessions. Update this file whenever features, architecture, or setup change.

## What this is

**Chanting Buddy** — a native Android app that schedules chanting sounds to play automatically at set times (fixed clock time, sunrise, sunset, full moon, new moon), with no manual play needed. Built with Kotlin + Jetpack Compose.

- Package: `com.chantingscheduler.app` (kept despite rebrand; user-facing name is "Chanting Buddy")
- minSdk 26 · targetSdk/compileSdk 36
- Toolchain: Gradle 8.14.3 (wrapper), AGP 8.11.1, Kotlin 2.2.0 + Compose plugin, KSP 2.2.0-2.0.2, Compose BOM **2025.11.01** (material3 1.4.x no longer brings `material-icons-core`, so `material-icons-core:1.7.8` is an explicit dep; the BOM no longer needs to track Rive — kept pinned anyway, downgrading buys nothing), Room 2.7.2 (schema **v6**, real migrations only, `exportSchema = true` → `core/data/schemas/*.json` committed), commons-suncalc 3.11 (sun + moon phases), Media3 1.8.0, Hilt 2.57.2 (KSP), Glance 1.1.1 (widget), Robolectric 4.15.1 (migration/codec tests), JUnit 4
- **Rive removed** (was ~10 MB native + a mismatched sample asset); the hand-drawn MonkArt is the mascot. Release builds are minified + ABI-split: **~3.7 MB per ABI** (was 37 MB monolithic).
- Build on this machine with JDK 24 — verified working.

## Environment (already set up on this PC)

- Android SDK: `<ANDROID_SDK>` (cmdline-tools/latest, platform-tools, platforms;android-36, build-tools;36.0.0). `local.properties` points here.
- Emulator: AVD `chanting_test` (pixel_6, Android 36, google_apis x86_64, WHPX hardware acceleration works).
  - Start: `<ANDROID_SDK>/emulator/emulator.exe -avd chanting_test`
  - adb: `<ANDROID_SDK>/platform-tools/adb.exe`
- Build: `./gradlew assembleDebug` → split APKs under `app/build/outputs/apk/debug/app-<abi>-debug.apk` (install the x86_64 or universal one on the emulator). CI runs `test lintDebug assembleDebug` on JDK 17 via `.github/workflows/android.yml`.
- Tests: `./gradlew test` — 70 JVM tests (TriggerCalculator 14, StreakCalculator 11+6, RoutinePlan 5, SunTimes 4, VolumeFade 4, BackupCodec 8, PlaylistFailurePolicy 6, AudioFocusRetryPolicy 6, SnoozeRestore 4, Room migrations 2) · Lint: `./gradlew lintDebug` (0 errors).
- Release builds are minified + resource-shrunk (R8) and **verified working on the emulator** (v1.2 universal APK, API 36): launch + onboarding, schedule create/edit, alarm armed `window=0` + `Alarm clock:` in dumpsys, alarm fire → receiver → FGS → audio (`dumpsys audio` shows `usage=USAGE_ALARM`), **locked-screen full-screen ringing page auto-launch** (verified twice; a third fire didn't surface it — possibly the API 34+ FSI rate limit), **120 s gap pauses and resumes on time with the screen off** (`event:paused` → `event:started` exactly 120 s later), snooze arm at +10 min (setAlarmClock) + persisted `snooze_1` pref, stats screen, session logging (`session_logs` read back via root). NOT re-verified on release: widget/tile clicks and SAF backup picker (unchanged from last round's debug verification; their start path goes through the now-guarded `PlaybackService.tryStart`). Found & fixed during this pass: the inter-track gap never actually paused playback.
- Release APKs are additionally **copied** (never renamed in place) to `app/build/outputs/release-named/ChantingBuddy-v<version>-<abi|universal>.apk` by a `copyReleaseApks` task finalized by every `package*Release` task (`VariantOutput.outputFileName` was removed in AGP 8). The originals stay in `build/outputs/apk/release/` for adb/Android Studio; rebuilds can't leave a stale APK under the release name. Release builds keep all four WAVs in `res/raw` at full size (keep.xml + R.raw references).
- CI (`.github/workflows/android.yml`): `test lintDebug assembleDebug` on JDK 17 for every push/PR; README badge reflects it.
- **Windows quirk:** `classes.jar` file locks occasionally fail builds with "used by another process" — `taskkill //F //IM java.exe` then retry fixes it.

## Core features (all working & verified on emulator)

1. **Timed playback** — alarms armed via `AlarmManager.setAlarmClock` (status-bar alarm chip whose tap opens MainActivity; most Doze/OEM-resistant API; allows bg FGS starts). `window=0 exactAllowReason=permission` verified in dumpsys. Inexact wake-up fallback only when exact permission is missing. **Alarm-sourced sessions** post their initial notification on `chant_alarm` (IMPORTANCE_HIGH, silent) with `setFullScreenIntent` + CATEGORY_ALARM, so the ringing page auto-opens on a locked screen (verified on emulator, API 36); every later re-post uses `setOnlyAlertOnce` and never re-attaches the FSI. Manual/tile sessions stay on the quiet `playback` channel, no FSI. Verified firing → receiver → service → audio under the minified release build.
2. **Re-arm everywhere** — `RescheduleReceiver` (boot, package-replaced, time/timezone change, exact-permission change; LOCKED_BOOT_COMPLETED deliberately skipped — credential-protected storage isn't readable that early) + app-start re-arm in `ChantingApp` (self-heals force-stop). `rearmAll` also restores persisted snoozes still in the future (`SnoozeRestore`, unit-tested) and drops stale ones. Verified: timezone switch to Asia/Tokyo re-armed to 06:00 local.
3. **Sunrise/sunset triggers** — per-schedule offset −60..+60 min. `SunTimesCalculator` pins the commons-suncalc timezone explicitly (JVM-default would break tests) and `TriggerCalculator` rejects sun instants that fall outside the queried day (suncalc returns the *next* event even months later, e.g. after polar night).
4. **Lunar triggers (Uposatha)** — `TriggerType.FULL_MOON / NEW_MOON` fire at the schedule's hour:minute on the local full/new-moon date (commons-suncalc `MoonPhase`), ignore days-of-week, need no location. Unit-tested against 2026 moon dates.
5. **Playlists** — ordered sound list per schedule (SAF picker with persisted grants, or built-in chimes), configurable gap seconds (notification shows "Pausing between chants…"; pressing play explicitly skips the gap; a partial wake lock is held through the gap so uptime-based timers can't stall in deep sleep), repeat count 1–108 (quick chips 1/3/7/21/108), optional auto-stop (5–60 min; playlist loops, 5 s fade-out at the deadline). Session logs record `audioSeconds` (actual audio time, gaps/silence excluded; per-row fallback to wall-clock for pre-v5 rows).
6. **Routine mode** — ordered steps (sound looped with REPEAT_MODE_ONE, or silence holding a partial wake lock with timeout), handler-timer advance, "Step x of n" in notification/PlaybackState/ringing page. A broken step sound skips to the next step (its stale `STEP_TOKEN` is cleared in `startStep`); if every playable step fails, the session falls back to the bundled chime instead of running silently. Edit screen has the Playlist/Routine switch and full step editor.
7. **Built-in sounds** — three synthesized chimes (morning/temple bell, singing bowl; WAVs generated in code, see LICENSES.md) stored as `android.resource://` URIs; new schedules default to the morning bell so a fresh install rings with zero setup. If every user URI fails (revoked SAF grant), the service loops `fallback_chime` — capped at 2 minutes (or the schedule's auto-stop deadline, whichever comes first) — and posts a repair notification.
8. **Snooze** — separate one-shot alarm (request code = scheduleId + 1_000_000_000) through the same AlarmReceiver path. Pending snoozes are persisted in `PlaybackSettingsStore` (scheduleId → triggerAt) and restored by `rearmAll` via the pure `SnoozeRestore` (future ones re-armed, past ones dropped and cleared). Verified armed in dumpsys.
9. **Pre-session reminders** — per-schedule 5/10 min before; `ReminderReceiver` posts a quiet notification on its own request-code range (+2_000_000_000). Reminders arm with `setExactAndAllowWhileIdle` (inexact fallback) — NOT `setAlarmClock`, so they never show up as the device's "next alarm". Room v6 added `reminderMinutes`.
10. **Session history & stats** — Room v3→v6, no FK (survives schedule deletion). Stats: current + longest streak cards, this-week/all-time mindful minutes (audio-time based), month calendar, 20-row history list. `StreakCalculator` pure + unit-tested.
11. **Undo delete** — 5 s snackbar; the deleted `ScheduleWithSounds` is held in memory and re-inserted (fresh ids) + re-armed on undo.
12. **Backup/restore** — versioned JSON via `BackupCodec` (unit-tested): SAF export/import in Settings → About. Built-in URIs survive; phone-file URIs are marked "⚠ re-pick" only when the app no longer holds the grant (`persistedUriPermissions` predicate, same-device imports re-attach cleanly). Importing a backup whose schedules already exist is skipped as duplicates ("Imported X, skipped Y duplicates"). Cloud backup + device transfer include the Room DB; dead SAF grants on a restored device are flagged at runtime with a "Re-pick" chip on the list screen.
13. **Widget + QS tile** — Glance widget (next session + play/stop) refreshed via `WidgetBridge` (package-targeted APPWIDGET_UPDATE broadcast from core:player; the receiver lives in app and is referenced by name). `ChantTileService` QS tile chants the first enabled schedule / stops.
14. **Update prompt** — anonymous check of the GitHub latest-release API at most once per 24 h on app open (`UpdateStore` throttle, silent on every failure; a generic `ChantingBuddy/<version>` user agent, no identifiers). A calm card on the list offers Download (ABI-matched asset → universal → release page via `ACTION_VIEW`; no install permission), Later, and Skip this version. `VersionComparator` (numeric, part-by-part, unit-tested) compares the `vX.Y` tag against `versionName`; malformed tags never prompt. Settings → About has the auto-check toggle (default on) and a "Check for updates now" button. Everything is gated on `BuildConfig.GITHUB_UPDATE_CHECK` so a future Play build can switch it off.
14. **i18n** — all user-facing strings in resources, translated to Simplified Chinese (`zh-rCN`) and Malay (`ms`), including core module strings. Verified zh UI on emulator via per-app locales.
15. **Reliability UX** — Settings → Alarms & reliability: exact-alarm status, battery optimization, full-screen-intent permission (Android 14+), test sound. FGS-start refusals (tile/widget on Android 12+) route through `PlaybackService.tryStart`, which falls back to a high-priority "Time to chant" tap-to-start notification (channel `playback_fallback`, `getForegroundService` PendingIntent).

## UI/UX (current design system)

- **Theme**: Summit-inspired. Dark: bg `#0E1B1A`, cards `#182826`, accent sage `#6E9A92`, text `#F4F8F7`. Light: bg `#F4F3EF`, white cards, accent deep teal `#2E5E57`. Font: **Urbanist** variable (bundled, OFL). Theme pref Auto/Light/Dark.
- **List screen**: LivingHeader (dawn/dusk backdrop from computed sun times, breathing MonkArt — static when reduced motion is on); "Next Session" hero; schedule cards; undo snackbar; ringing card.
- **Edit screen**: 5-way trigger selector (Fixed/Sunrise/Sunset/Full moon/New moon), TimeDialPicker (drag dials + AM/PM + **Type time** fallback dialog; dials are TalkBack-adjustable progress controls), Playlist/Routine mode switch, grouped rows (Repeat/Sounds|Steps/Label/Options), auto-stop & repeat & reminder chips.
- **Stats screen**: streak cards, minutes cards, month calendar, history list.
- **Settings sheet**: theme cards + **Reduce motion** toggle, location (device/manual/search), playback (fade-in, snooze, **Play scheduled chants on alarm volume** — default ON; alarm sessions render on the alarm stream via `USAGE_ALARM` set before prepare, so they sound at media volume 0 / under DND, with a one-time heads-up if the alarm stream itself is muted), reliability rows, about (version, replay onboarding, export/import).
- **Onboarding**: 3 pages, first launch only, replayable.
- **Ringing page**: per-trigger animated backdrop, Track/Step status, SNOOZE + STOP; full-screen intent with Android 14 permission gate.

## Modules

Multi-module. **Packages were deliberately NOT renamed** (`com.chantingscheduler.app.data` etc.) — module membership ≠ package. Namespaces differ per module and matter only for each module's R class (relevant now with i18n: library strings are referenced via `com.chantingscheduler.app.core.<module>.R` from app code).

```
app            – activities, ui/, ui/widget/, tile/, di/, ChantingApp (channels, re-arm)
core:data      – Room v6 + migrations + schemas, repositories, stores (Location/Theme/
                 Onboarding/PlaybackSettings/Motion), BackupCodec, StreakCalculator,
                 BuiltinSounds live in core:player
core:scheduling– TriggerCalculator (pure), AlarmScheduler (setAlarmClock for alarms/snooze +
                 setExactAndAllowWhileIdle for reminders + snooze persistence/restore),
                 AlarmReceiver, RescheduleReceiver, ReminderReceiver, SnoozeRestore
                 (pure, unit-tested), SunTimesCalculator, SessionStarter seam
core:player    – PlaybackService (playlist + routine + auto-stop + fade + fallback chime +
                 alarm-stream attributes + audio-focus retry + gap wake lock),
                 PlaybackState, RoutinePlan, PlaylistFailurePolicy (per-URI failure
                 decisions, unit-tested), AudioFocusRetryPolicy (60 s/30 s/5 min
                 retry-then-give-up, unit-tested), SoundPreviewPlayer, VolumeFade,
                 WidgetBridge, BuiltinSounds, raw/ chime WAVs, notification
                 strings/drawables
```

Dependency direction: `app → core:player → core:scheduling → core:data` (app also depends on the other two). Boundary seams:
- **SessionStarter** (`core:scheduling`): AlarmReceiver → Hilt-injected `PlaybackSessionStarter` (app/di/AppModule.kt) → `PlaybackService.playIntent(...)` with routine/repeat/auto-stop extras.
- App components referenced from library code by **class name** (MainActivity from AlarmScheduler/ReminderReceiver/notifications; RingingActivity from PlaybackService; the Glance widget receiver from WidgetBridge).

## Hilt

`@HiltAndroidApp` ChantingApp; `@AndroidEntryPoint` on MainActivity, RingingActivity, AlarmReceiver, RescheduleReceiver, ReminderReceiver; `@HiltViewModel` ScheduleViewModel (now also `@ApplicationContext`-injected for widget refresh) + StatsViewModel. `ScheduleViewModel.save()` passes steps; PlaybackService/ChantingApp stay manual-construction by design.

## Architecture map

```
app/src/main/java/com/chantingscheduler/app/
  ChantingApp.kt            – channels (playback/fallback/reminders), rearmAll on start
  MainActivity.kt           – nav, theme, SAF export/import launchers, permission prompts
  RingingActivity.kt        – ringing page (Track/Step status)
  di/AppModule.kt           – PlaybackSessionStarter → SessionStarter (FGS guard)
  tile/ChantTileService.kt  – QS tile toggle
  ui/                       – list/edit/settings/stats/onboarding/dial/Components/Motion…
  ui/widget/NextSessionWidget.kt – Glance widget + PlayToggleCallback
core/data/.../data/         – Entities (v6), AppDatabase + MIGRATIONS 1..5, schemas JSONs,
                              Daos, ScheduleRepository (save/import/snapshot), stores,
                              BackupCodec, StreakCalculator, MotionStore
core/scheduling/.../        – TriggerCalculator, AlarmScheduler, AlarmReceiver,
                              RescheduleReceiver, ReminderReceiver, SunTimesCalculator
core/player/.../playback/   – PlaybackService, PlaybackState, RoutinePlan, BuiltinSounds,
                              SoundPreviewPlayer, VolumeFade, WidgetBridge
```

## Continuity notes for the next session

- **Current release**: versionCode 4 / v1.3 — audit round 3 (focus-retry vs silence routines, raw-sound keep, release-named APK copies, import dedup for sessions, broken-last-track fix, chant volume slider, CI release job, localized built-in names, FSI cancel-first, GitHub update prompt).
- **Release discipline**: every release MUST be tagged `vX.Y` matching `versionName` exactly (`release.yml` fails otherwise), APKs are uploaded from `app/build/outputs/release-named/`, and the APK must be **signed with the same keystore** as the previous release — Android refuses to install over an app signed with a different key.
- **Quota**: the 5-hour usage limit has interrupted work repeatedly; commit early and often (this doc now reflects a fully committed state).
- **Design verification loop**: install (`adb install -r .../app-x86_64-debug.apk`), screenshot (`adb exec-out screencap -p > x.png`), inspect, fix. Per-app locales: `adb shell cmd locale set-app-locales <pkg> --locales zh-Hans-CN`. Toggle dark/light: `adb shell cmd uimode night yes|no`.
- **Alarm test recipe**: schedule ~2–3 min ahead of the EMULATOR clock (`adb shell date`), verify in `adb shell dumpsys alarm` (expect `window=0`, `Alarm clock:` block), wait, confirm FGS + notification; `adb shell am startservice -n .../.playback.PlaybackService -a ...action.SNOOZE` (as adb root) exercises snooze. Clean up test schedules afterwards.
- **Migration test pattern**: Robolectric builds a raw v1 file, applies each `Migration` stepwise, then opens via Room (which validates the schema against current entities). Update it whenever the version bumps. `4.json/5.json/6.json` are committed; historical 1–3 schemas predate `exportSchema=true` and are intentionally absent.

## Caveats / known limits

- Real-device reliability needs exact-alarm permission + battery exemption (settings has fix buttons); aggressive OEM savers untestable on emulator.
- Full-screen intent shows as heads-up when the device is unlocked (standard Android behavior).
- Built-in chime names are proper-noun product names — intentionally not translated.
- Commons-suncalc polar-day/night returns the *next* event outside the queried day; `TriggerCalculator` guards with an on-date check, so polar schedules return -1 within the search window (documented, unit-tested).
- Pre-session reminder notification depends on POST_NOTIFICATIONS being granted.
- Imported phone-file URIs are marked "⚠ re-pick" only when the grant is dead; the list screen shows a "Re-pick" chip for schedules with missing `content://` URIs, and playback falls back to the bundled chime meanwhile.
- Widget updates rely on explicit `WidgetBridge` broadcasts + the 30-min system period (no content-observer).
- Session-log `audioSeconds` counts rendered-audio time via `onIsPlayingChanged`; a crash mid-session can still drop one record (fire-and-forget insert).
- Ringing-page quirks (pre-existing, cosmetic): the timer pill shows a nonsense value for playlist sessions (it formats a step-duration source that doesn't exist), and RingingActivity doesn't finish itself when the session ends on its own — it sits there with a stale timer until closed.
- Snooze alarms skip playback if the schedule was disabled in the meantime; fresh playback cancels pending snoozes.
- Geocoder address search needs network + a backend (google_apis image OK).
- Variable-font weights may synthesize on API 26–27.
- Release signing: `keystore/chanting-buddy-release.jks` + gitignored `keystore.properties` (both must be backed up by the owner — losing them blocks updates). Minify + resource shrinking are ON (verified with an emulator alarm fire); Play Store listing not started.

## Ideas not yet implemented (candidates for future sessions)

- AAB / Play upload instead of raw APK splits
- Widget: multiple sizes / direct schedule picking
- Wear OS tile / complication
- Silence-step ambient tone option
- Per-schedule volume
- Multi-language expansion (Burmese, Thai, Pali terms)
