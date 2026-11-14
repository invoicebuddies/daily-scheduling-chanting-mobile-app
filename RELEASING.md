# Releasing Chanting Buddy

The release pipeline is fully automated: pushing a `vX.Y` tag builds signed APKs,
verifies their signing certificate, and opens a **draft** GitHub release with the
APKs attached. This document is the checklist for cutting a release and the
safety nets that keep a broken release from shipping.

## Checklist

1. **Bump the version** in `app/build.gradle.kts`:
   `versionCode = N+1`, `versionName = "X.Y"` (or `"X.Y.Z"`).
2. **Commit** the version bump (plus any last changes) to `main`.
3. **Verify (mandatory — no record, no release):**
   1. Build the release APK (`./gradlew test lintDebug assembleRelease`).
   2. Run `scripts/verify-release.sh app/build/outputs/release-named/ChantingBuddy-vX.Y-universal.apk vX.Y` —
      it verifies the cert, installs on the connected emulator, measures
      gfxinfo (Janky %, 90th percentile, total frames), and pre-fills
      `docs/verification/vX.Y.md` (sections A + B).
      Always **cold-boot the emulator first** — an emulator up for hours
      accumulates surfaceflinger work and measures falsely janky.
   3. Complete the manual regression rows R1–R16 and fill section E
      ("Not tested") in the verification record. Every row must be ✅.
   4. Produce the screenshot matrix in `docs/screenshots/vX.Y.Z/`
      (16 scene shots `scene-<scene>-<day|night>-<light|dark>.png`,
      6 locale shots, `settings-motion-section.png`). On a rooted emulator,
      force day/night with `adb shell date` and themes with
      `adb shell cmd uimode night yes|no`.
   5. Commit `docs/verification/vX.Y.Z.md` and the screenshots.
   The first step of the Release workflow (`scripts/check-verification.sh`)
   **refuses to build without a complete record** — any unchecked box,
   TODO, non-yes sign-off, over-budget jank number, or missing screenshot
   fails the job with "Release blocked: verification record incomplete".
4. **Tag** exactly `vX.Y` matching `versionName` — the workflow fails otherwise:
   `git tag -a vX.Y -m "Chanting Buddy vX.Y"`.
5. **Push the tag**: `git push origin vX.Y`. This fires the Release workflow.
6. **Wait for the Release workflow** to go green
   (Actions → Release). The verification gate runs first, then tests,
   builds, and signature verification.
7. **Spot-verify one APK** from the draft: download it and run
   `apksigner verify --print-certs <apk>`; the SHA-256 must be
   `e11a9c95ae12f5932295e68986389be64793e2a9719dccce1a77e8e36dcc23ab`
   (also enforced automatically against `EXPECTED_CERT_SHA256`).
8. **Install it over the previous version** on a phone or emulator
   (`adb install -r`): the upgrade must succeed and existing schedules/history
   must still be there.
9. **Publish the draft** on the GitHub releases page (edit the generated notes
   first — see previous releases for the style; link the verification record).

## Safety nets (enforced by `.github/workflows/release.yml`)

- **No secrets → no release.** The job fails up front if any of the four
  signing secrets below is missing. Unsigned APKs cannot be installed at all,
  so an unsigned release is worse than no release.
- **Wrong keystore → no release.** The decoded keystore's certificate must
  match the repo variable `EXPECTED_CERT_SHA256` *before* the build starts,
  and every built APK must verify against the same hash via `apksigner`.
- **Unsigned APKs are never silently renamed.** CI builds without secrets
  produce `ChantingBuddy-vX.Y-<abi>-UNSIGNED.apk` copies, and the in-app
  update checker's asset matcher refuses `-UNSIGNED` names, so they can never
  be offered as a download.
- Releases are created as **drafts** — a human reviews before anything goes
  public.
- Every APK asset carries a **human-readable label** (shown under its name in
  the Assets section), e.g. "Most modern phones — pick this one" for
  arm64-v8a — the download list should never require guessing.

## Required GitHub configuration

Repo → Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 keystore/chanting-buddy-release.jks` |
| `KEYSTORE_PASSWORD` | keystore (store) password |
| `KEY_ALIAS` | key alias from `keystore.properties` |
| `KEY_PASSWORD` | key password from `keystore.properties` |

Variables (same page, "Variables" tab):

| Variable | Value |
|---|---|
| `EXPECTED_CERT_SHA256` | `e11a9c95ae12f5932295e68986389be64793e2a9719dccce1a77e8e36dcc23ab` |

## ⚠️ Back up the keystore

`keystore/chanting-buddy-release.jks` + `keystore.properties` (both
gitignored, both only on the owner's machine) **must** be backed up somewhere
safe. The certificate above is baked into every published APK: losing the
keystore means future releases can't install over existing ones, and users
must **uninstall the app and lose their schedules and history** to update.
There is no recovery.

## If a release goes wrong

- **Draft is broken:** just delete the draft release and re-push the tag
  (`git push --delete origin vX.Y && git push origin vX.Y`) after fixing.
- **Already published with bad APKs:** replace the assets (upload fixed APKs
  and delete the bad ones) rather than deleting the release, so existing
  download links keep working. The update prompt in the app points users at
  `releases/latest`, so superseding broken assets quickly matters.
