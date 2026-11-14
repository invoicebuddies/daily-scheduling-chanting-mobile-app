# Release checklist (if this PR is for a release)

- [ ] `docs/verification/vX.Y.Z.md` exists and every section is filled (regression rows R1–R16 ✅, "Not tested" filled in, "All gates passed: **yes**")
- [ ] `docs/screenshots/vX.Y.Z/` has the full matrix (16 scene + 6 locale + settings shot)
- [ ] `scripts/verify-release.sh` was run against the connected emulator and its gfxinfo numbers are in section B
- [ ] `./gradlew test lintDebug assembleDebug assembleRelease` is green locally
- [ ] The tag will be exactly `vX.Y.Z` matching `versionName`
