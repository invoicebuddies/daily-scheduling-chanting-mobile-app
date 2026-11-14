# Release verification record — vX.Y.Z

> Copy this file to `docs/verification/vX.Y.Z.md` for every release and fill
> every field. CI blocks the release workflow when this record is missing or
> incomplete (see RELEASING.md, "no record, no release").
> Every Result field must be ✅, ⚠️ (explained), or ❌ (blocks the release).
> Every Evidence field is a command output excerpt, screenshot path, or log
> line — never "looks fine".

## A. Build under test

| Field | Value |
|---|---|
| Tag | vX.Y.Z |
| Commit SHA | `<full sha>` |
| versionName / versionCode | X.Y.Z / N |
| APK tested | `app/build/outputs/release-named/ChantingBuddy-vX.Y.Z-universal.apk` (release build, never debug) |
| Device(s) | `<model>, API NN; lowest API tested: NN` |
| Signing cert SHA-256 | `<hash>` — must equal `EXPECTED_CERT_SHA256` (`.github/release-cert.sha256`) |

**Result:** ✅ / ⚠️ / ❌
**Evidence:** `<apksigner output excerpt>`

## B. Smoothness (gfxinfo)

Budget: **Janky frames < 5% latest API, < 10% lowest API.**

| Run | Total frames | Janky frames % | 90th percentile (ms) | Result |
|---|---|---|---|---|
| List scroll 10 s, Calm, latest API | | | | ✅ / ❌ |
| List scroll 10 s, Calm, lowest API | | | | ✅ / ❌ |
| Ringing page breathing 10 s, Calm | | | | ✅ / ❌ |

**Evidence:** `scripts/verify-release.sh` output for each run (paste the
parsed lines).

## C. Regression pass

Every row must be ✅. Any ❌ blocks the release until fixed and re-passed.

| # | Check | How | Result | Evidence |
|---|---|---|---|---|
| R1 | Alarm fires 2 min ahead, locked screen | `dumpsys alarm` shows `Alarm clock:` + `window=0`; FGS starts; audio `usage=USAGE_ALARM` | - [ ] | - [ ] |
| R2 | Ringing page full-screen on lock | screenshot | - [ ] | - [ ] |
| R3 | Timer pill: playlist elapsed / auto-stop countdown / routine "Step x of n" | three screenshots | - [ ] | - [ ] |
| R4 | "Session complete" → note saved → auto-closes after 60 s | screenshot + note visible in Stats history | - [ ] | - [ ] |
| R5 | Snooze arms +10 min | `dumpsys alarm` + `snooze_<id>` pref | - [ ] | - [ ] |
| R6 | Routine with silence-first step completes, no "missed chant" | log | - [ ] | - [ ] |
| R7 | Per-schedule volume 25% vs 100% | `dumpsys audio` stream volume / player volume log | - [ ] | - [ ] |
| R8 | Missed-chant card on **first** open after force-stop | screenshot | - [ ] | - [ ] |
| R9 | Phone call mid-gap → silent until hang-up, then resumes | `adb emu gsm call/cancel`, focus log | - [ ] | - [ ] |
| R10 | Built-in sound plays on a fresh install with no files picked | log | - [ ] | - [ ] |
| R11 | Backup export → wipe → import restores schedules + notes | screenshot | - [ ] | - [ ] |
| R12 | Widget play/stop and QS tile toggle | screenshot/log | - [ ] | - [ ] |
| R13 | Support: Ko-fi opens browser; feedback opens email with exact subject; no-email fallback copies | screenshots | - [ ] | - [ ] |
| R14 | Update card appears for an older `versionName` build | screenshot | - [ ] | - [ ] |
| R15 | Install-over: previous Latest release → this release keeps schedules, history, notes, settings | screenshot before/after | - [ ] | - [ ] |
| R16 | Motion: Calm/Minimal/Off + system "remove animations" forces Off; ambient stops in background and at Off | `MotionOffTest` result + log | - [ ] | - [ ] |

## D. Screenshots

Folder: `docs/screenshots/vX.Y.Z/`

Required set (all present):

- [ ] 16 scene shots: `scene-<monk|lotus|temple|abstract>-<day|night>-<light|dark>.png`
- [ ] 6 locale shots: `locale-<en|zh-Hans-CN|ms|th|my|si>.png`
- [ ] `settings-motion-section.png`

**Result:** ✅ / ❌
**Evidence:** `ls docs/screenshots/vX.Y.Z/ | wc -l` and the file list.

## E. Not tested

`<what could not be tested, and why. "None" is valid; blank is not.>`

## F. Sign-off

- Date: `<YYYY-MM-DD>`
- All gates passed: **yes / no**
