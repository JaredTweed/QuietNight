# Verification of Quiet Night 2.2.1 for F-Droid

- Application ID: `dev.jaredtweed.quietnight`.
- Version: 2.2.1 / code 56.
- Immutable source tag: `quietnight-v2.2.1`.
- Source commit: `0860f3a757bd0c1216f56df10a4447089a51983c`.
- Signing route: developer-signed reproducible builds, using the signed GitHub reference APK and the pinned public certificate.
- License: GPL-3.0-only, with upstream attribution and public-domain IANA data notices retained.

## Exact-source build

A separate clean checkout of the public source commit was built using F-Droid Server 2.4.5 and its official `gradlew-fdroid` helper, with OpenJDK 17, SDK 36, build-tools 35.0.0, AGP 8.10.1, Kotlin 2.2.20, and checksum-verified Gradle 8.11.1:

```sh
fdroid build --stop --test --scan-binary --refresh-scanner --no-tarball dev.jaredtweed.quietnight:56
```

Result: **one build succeeded**. Source scanning and APK scanning were enabled, including non-free class and extra-signing-block checks. The official Gradle wrapper was removed by F-Droid's normal preparation. There were no `scanignore`, `scandelete`, forced scanner bypasses, source patches, or developer signing configuration.

Unsigned APK SHA-256: `7c1a3bc0dc226014c6e78fc741fa4d5b84ecb86ad820fafed4f805f2f2977502`.

The tagged GitHub CI unsigned APK has this same SHA-256. Copying the developer signature onto the independent F-Droid rebuild verifies with `apksigcopier` 1.1.1 and Android `apksigner`. This does not constitute F-Droid inclusion approval.

## Additional checks

- F-Droid metadata rewrite and lint passed locally. Tag-based automatic updates use `AutoUpdateMode: Version`, as required by the current fdroiddata schema; the `UpdateCheckMode` regex restricts accepted release tags. The recipe contains only the latest build and pins the complete source SHA.
- The upstream metadata checker passed, including the code-56 changelog and four current screenshots. The README screenshots were updated before the source tag and submission.
- The generated offline time-zone city lookup matches the pinned public-domain IANA tzdb 2026e source tables. Builds and runtime do not download city data.
- All 154 regression tests passed, including scheduled fades and displayed solar windows. Debug/release builds and debug lint passed, with no lint errors and 78 warnings.
- GitHub [Android CI](https://github.com/JaredTweed/QuietNight/actions/runs/43805894874) and the [tagged release build](https://github.com/JaredTweed/QuietNight/actions/runs/43805899354) passed for this exact source commit.
- The matching runtime implementation was installed on a Pixel 8 Pro before the version-only release bump, preserving settings and calibration. The notification icon was visually verified after clearing Android’s cached previous app icon. The maintainer previously confirmed that a physical two-second volume-down hold muted media with the app in the background.

## Ordinary-overlay review fix

Version 2.2.1 removes the ordinary overlay’s system dim layer and composites warmth/dimming in one window capped at Android’s touch-through opacity limit. Regression coverage includes the reviewer’s 45% warmth / 25% dim reproduction, maximum opacity, and fades. The matching debug update was installed on a Pixel 8 Pro with every existing preference preserved. A retest on the reviewer’s GrapheneOS device has been requested; it is not claimed here.

## Submission review

The application was submitted in [fdroiddata merge request #54381](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/54381) on 2026-10-26. Publication remains pending; the developer-signed reference APK is published on GitHub.

The public fdroiddata branch is based on upstream master and is not protected. Only `metadata/dev.jaredtweed.quietnight.yml` was added. Its [pipeline](https://gitlab.com/JaredTweed/fdroiddata/-/pipelines/3072065154) passed all nine checks for version 2.2.1, including the independent build, source scanning, recipe formatting, metadata lint, update detection, and final APK inspection. This is separate from the upstream app's GitHub CI and does not constitute maintainer approval.

The fork's development began on 2026-10-23; the inherited source history began on 2026-06-26. The maintainer uses the app, but no established public user base, three-month fork history, policy exception, or inclusion approval is claimed. F-Droid reviewers must assess the Quality Control point 6 criterion and the final submission.

The APK report flags the expected overlay/location permissions and accessibility intent as minor findings. These are disclosed optional features, explained in the merge request. Runner config-file mode and Androguard API-database fallback warnings originate in CI tooling; no app source or scanner bypass was used to suppress them.

## Developer-signed reference

The [2.2.1 production APK](https://github.com/JaredTweed/QuietNight/releases/download/quietnight-v2.2.1/QuietNight-2.2.1.apk) uses certificate SHA-256 `f90cc5f530bc227418eb1d0a016a790b052e6591a5c038a9979ab6e5544a4e2f`. Its signed APK SHA-256 is `af17b568fd744d9d0d1000d0990bf3020e688a9d7454a62ed861c43bbf5ed2e9`; the public download matches the verified local artifact. The source tag and commit are unchanged.

The signer preserves existing ZIP alignment for build-tools 35 compatibility. Android signature verification and signature-copy reproduction passed. The helper also correctly refused mismatched rebuilds, a debug package, and overwriting an existing release. The encrypted key stays outside the repository and CI; the public certificate and reproducibility-aware signer are committed. See [SIGNING.md](SIGNING.md).

The [updated F-Droid pipeline](https://gitlab.com/JaredTweed/fdroiddata/-/pipelines/3072065154) passed all nine checks for version 2.2.1, including rebuilding the pinned source and successfully verifying the published developer-signed APK. Its [build job](https://gitlab.com/JaredTweed/fdroiddata/-/jobs/18053859539) performs the independent reference verification. Inclusion approval remains pending.
