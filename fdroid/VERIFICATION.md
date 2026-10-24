# Verification of the proposed F-Droid source

- App: Quiet Night, `dev.jaredtweed.quietnight`, version 2.0.0 / code 53.
- Source commit: `39dfd82afe32995994df6b382e2d0eab0d780145`.
- Immutable source tag: `quietnight-v2.0.0`.
- License: GPL-3.0-only; upstream attribution retained.
- Signing route: F-Droid signing, selected by the maintainer. No developer key or reference binary is required by this recipe.

## Checks performed

1. Local debug assembly, release assembly, and debug lint succeeded with AGP 8.10.1, Kotlin 2.2.20, Gradle 8.11.1, OpenJDK 17, SDK 36, and SDK build-tools 35.0.0. All 68 regression tests passed. Lint had zero errors; pre-existing warnings remain.
2. The Gradle distribution SHA-256 and wrapper JAR were checked against Gradle's official checksums. The wrapper JAR is `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
3. `python3 tools/check_fdroid_metadata.py` passed: matching app/store title, summary/description limits, version-code 53 changelog, 512x512 icon, three current PNG screenshots, no stale fork URLs, and no unexpected network/microphone/billing/advertising permissions.
4. F-Droid Server 2.4.5's source scanner reported zero problems in a clean prepared source tree. The scanner removed the official Gradle wrapper as expected. No broad `scanignore`, `scandelete`, or forced scan bypass was used.
5. F-Droid Server 2.4.5 metadata rewrite and lint passed against the current fdroiddata category configuration. `fdroid checkupdates` recognized the release and application name using the `quietnight-v*` tag filter.
6. A separate clean clone of the published commit was built by `fdroid build --stop --test --scan-binary --refresh-scanner --no-tarball dev.jaredtweed.quietnight:53`, using the official version-matched `gradlew-fdroid` helper. Source and binary scans were enabled. Result: **one build succeeded**. The local metadata workspace was bootstrapped with the clean clone before invoking the CLI; no source or scanner patches were applied.
7. The resulting unsigned APK passed scanning for known non-free classes and extra signing blocks. Package/version/SDK checks: `dev.jaredtweed.quietnight`, 2.0.0, 53, min SDK 28, target SDK 36.
8. The clean F-Droid CLI build and local release APK have the same SHA-256: `23c66228bb471e2f139f8524dc494756b2a6e5ccd0927b1a10955c4dbb60c2b5`. This is a same-environment reproducibility check, not a claim of independent F-Droid server reproduction or a developer-signature comparison.
9. GitHub's Android CI passed on both published branches for this source commit, and the source-tag release-build workflow passed. The updated debug APK was installed on the connected Pixel without clearing data.

## Submission boundary

The recipe and this evidence are prepared for a new-app fdroiddata merge request. No GitLab submission, official F-Droid build-server run, inclusion approval, or signing/publication by F-Droid has happened. The new-fork maintenance-history/usage question in [README.md](README.md) remains for reviewers. The separate Bluetooth-fix branch was not changed.
