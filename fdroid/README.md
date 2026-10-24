# F-Droid submission preparation

Quiet Night is a GPL-3.0-only fork maintained by Jared Tweed, with application ID `dev.jaredtweed.quietnight`. F-Droid signing was selected by the maintainer. This directory prepares a submission; it does not claim the app has been accepted or that a submission was sent.

## Build and listing

The app builds from `android/app` with the `fdroid` Gradle flavor, SDK 36, SDK build-tools 35.0.0, OpenJDK 17, AGP 8.10.1, Kotlin 2.2.20, and checksum-verified Gradle 8.11.1. Runtime dependencies come from Google Maven and Maven Central. Debug builds have a separate `.debug` application ID. Release builds are optimized with R8 and resource shrinking and have no developer signing configuration.

English Fastlane metadata, current screenshots, the app icon, and the version-code 53 changelog live in `fastlane/metadata/android/en-US`. Inaccurate upstream listings and translations were removed; the interface is currently English. The current app keeps its unique package ID, distinct icon/name, and upstream authorship notices.

Use the [current submission recipe](https://github.com/JaredTweed/QuietNight/blob/HEAD/fdroid/metadata/dev.jaredtweed.quietnight.yml) in a public fdroiddata fork. Only the app's YAML belongs in fdroiddata; the store listing is pulled from this repository. Copy the recipe unchanged after checking its pinned full source SHA. No `Binaries`, `binary`, or `AllowedAPKSigningKeys` fields are needed for the selected F-Droid signing route.

```sh
python3 tools/check_fdroid_metadata.py
cd android
./gradlew :app:assembleFdroidDebug :app:testFdroidDebugUnitTest :app:lintFdroidDebug :app:assembleFdroidRelease
```

Release source tags use `quietnight-vVERSION` so F-Droid update checks do not pick up inherited upstream tags for a different application ID. Never move a published tag. Build from a clean checkout of the exact recipe commit. Do not submit a debug APK or an APK built from uncommitted changes.

## Maintainer feedback reviewed

| Public review | What it means here |
| --- | --- |
| [linsui: AI Tasker Bridge, note 3825287049](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/48574#note_3825287049) | Keep the listing upstream, pin a full commit SHA, use the module's build directory as `subdir`, and avoid an unnecessary `output` override. |
| [linsui: ShareDing, note 3906263134](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/50135#note_3906263134) | Use the correct module directory and enable R8. Broad application keep rules were removed. |
| [Licaon_Kter: Mafia, note 2201685562](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/16465#note_2201685562) | Disable encrypted AGP dependency information in APKs and bundles. Both `dependenciesInfo` settings remain false. |
| [Licaon_Kter: Mafia, note 2201593941](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/16465#note_2201593941) | The source tag, checkout, and artifact must correspond; verification must use the pinned clean source. |
| [Licaon_Kter: Mafia localization review](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/16465) | Listing languages must accurately describe the actual interface rather than reuse misleading upstream text. |

Two proprietary Microsoft Segoe font binaries inherited in video tooling were removed from the source checkout. They were not app dependencies. The only remaining executable build binary is the official Gradle wrapper, which F-Droid removes/replaces during preparation. Historical website/promo material is not packaged in the APK; inherited domain-verification files were removed to avoid claiming ownership of upstream's website.

## Review requirements that code cannot guarantee

The [current inclusion policy](https://f-droid.org/docs/Inclusion_Policy/) requires useful, maintained software with meaningful value and a distinct identity for forks. Quiet Night adds screen filtering, offline schedules, independent screen/audio controls, accessibility-based system-UI coverage, and a new interface, beyond the original volume controller.

The policy also normally requires three months of active development, with exceptions for established users, existing F-Droid maintainers, and event apps. Upstream history begins on 2026-06-26; Quiet Night's fork-specific development began on 2026-10-23. Whether that history or a usage exception is sufficient is the reviewers' decision. Do not claim an exception or manufacture user evidence. If reviewers require more history, continue maintaining public releases and submit later.

A real fdroiddata merge request and its official build/review pipeline are still required. Keep its source branch public and unprotected. Maintainers may request installation tests of their CI-produced APK. Do not mark a checkbox as passed until it has actually been verified. F-Droid signing is separate from a claim of cross-environment reproducibility with a developer-signed APK.

See the [submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/) and [metadata reference](https://f-droid.org/docs/Build_Metadata_Reference/) for the current process.
