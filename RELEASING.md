# Releasing Chanting Buddy

The release pipeline is fully automated: pushing a `vX.Y` tag builds signed APKs,
verifies their signing certificate, and opens a **draft** GitHub release with the
APKs attached. This document is the checklist for cutting a release and the
safety nets that keep a broken release from shipping.

## Checklist

1. **Bump the version** in `app/build.gradle.kts`:
   `versionCode = N+1`, `versionName = "X.Y"` (or `"X.Y.Z"`).
2. **Commit** the version bump (plus any last changes) to `main`.
3. **Tag** exactly `vX.Y` matching `versionName` — the workflow fails otherwise:
   `git tag -a vX.Y -m "Chanting Buddy vX.Y"`.
4. **Push the tag**: `git push origin vX.Y`. This fires the Release workflow.
5. **Wait for the Release workflow** to go green
   (Actions → Release). It runs tests, builds, and verifies signatures.
6. **Spot-verify one APK** from the draft: download it and run
   `apksigner verify --print-certs <apk>`; the SHA-256 must be
   `e11a9c95ae12f5932295e68986389be64793e2a9719dccce1a77e8e36dcc23ab`
   (also enforced automatically against `EXPECTED_CERT_SHA256`).
7. **Install it over the previous version** on a phone or emulator
   (`adb install -r`): the upgrade must succeed and existing schedules/history
   must still be there.
8. **Publish the draft** on the GitHub releases page (edit the generated notes
   first — see previous releases for the style).

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
