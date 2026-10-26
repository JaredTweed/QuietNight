# F-Droid inclusion submission

Submitted 2026-10-26: [New app: Quiet Night (#54381)](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/54381).

The source is tagged `quietnight-v2.1.0` and pinned by full SHA in the recipe. Official publication is pending review. The following is the submitted description; live pipeline and review status are on GitLab.

## Abstract for new app inclusion

### Functions and usage

Quiet Night combines a touch-through evening screen filter with fine media-volume control. Screen tint/dimming and Quiet steps have independent controls. The screen filter supports Always, Custom, and offline sunset-to-sunrise schedules. A trusted accessibility overlay can include notifications and Quick Settings. Sunset defaults to the phone time zone's reference city, with optional one-shot location or manual coordinates for greater accuracy.

Quiet steps provide six 5 dB reductions below Android's usual audible media minimum, followed by mute, with optional Bluetooth audible-floor calibration. Holding volume-down for two seconds mutes media even with the app in the background when the trusted service is enabled. Other keys are ignored immediately; key events are not recorded, normal key handling continues, and key filtering is requested only while Quiet steps is enabled. The service requests no content events, screen retrieval, gesture injection, screenshot capability, or floating accessibility button.

The app has no internet permission, advertising, analytics, accounts, billing, or non-free runtime dependencies.

<img src="https://raw.githubusercontent.com/JaredTweed/QuietNight/quietnight-v2.1.0/docs/quiet-night/home.png" width="220" alt="Quiet Night home"> <img src="https://raw.githubusercontent.com/JaredTweed/QuietNight/quietnight-v2.1.0/docs/quiet-night/sunset.png" width="220" alt="Automatic sunset schedule"> <img src="https://raw.githubusercontent.com/JaredTweed/QuietNight/quietnight-v2.1.0/docs/quiet-night/audio.png" width="220" alt="Quiet media steps">

### Purpose and differentiation

This is maintained by Jared Tweed as an independent GPL-3.0-only application derived from Granular Volume / Quiet Dial. It addresses evening light and media volume that remains too loud at Android's lowest step. Beyond the original volume controller, it adds scheduled display tint/dimming, system-UI filtering, offline solar timing without mandatory device-location access, independent screen/audio controls, background held-key mute, and a minimal Material interface with a themed icon. It has a distinct package ID, name, icon, issue tracker, privacy policy, and current listing. Upstream attribution is retained.

Source: https://github.com/JaredTweed/QuietNight

Release: **2.1.0 (54)**, immutable tag `quietnight-v2.1.0`, source `f98bc0cf3c0ad9d2112c42ce59e14a6c96845d35`.

### Maintenance-history disclosure

The inherited source history starts on 2026-06-26, while this fork's development began on 2026-10-23. The maintainer uses and tests the application on their phone, including the background hold-to-mute behavior. We do not claim a three-month fork history, an established public user base, or an already approved policy exception. Please assess eligibility under Quality Control point 6; if additional maintenance history or usage evidence is required, please advise. The policy checkbox below is left open for that assessment.

## Checklist

### Policy

* [ ] The app complies with the inclusion criteria, including Quality Control point 6 — see the history disclosure above; reviewer assessment is requested.
* [x] The maintainer of this independent application, Jared Tweed, has explicitly authorized this submission and developer-signed reproducible builds. Original authorship is preserved in the GPL source and notices.
* [x] The upstream repository includes `en-US` Fastlane summary, description, icon, four current screenshots, and a code-54 changelog. README screenshots were updated before this release was tagged.

### Docs

* [x] Read CONTRIBUTING.md, the metadata reference, repository templates, and the Quick Start Guide.
* [x] The build recipe follows the Gradle module template: `subdir: android/app`, `gradle: [fdroid]`, no output override or scanner exemptions.

### Merge Request Setup

* [x] The title uses `New app: Quiet Night`.
* [x] The fork is public and the app-specific source branch is not protected.
* [x] This request adds only one app and only its YAML metadata file.
* [x] No existing Quiet Night fdroiddata merge request was found. No related RFP issue is being closed.

### Metadata

* [x] The file is `metadata/dev.jaredtweed.quietnight.yml`, valid YAML with LF line endings.
* [x] Descriptions, screenshots, icon, and changelog remain in the upstream app repository.
* [x] The release is tagged and automatic updates are scoped to `quietnight-v*`, excluding inherited upstream tags for another application ID.
* [x] AuthorName, source, issue tracker/contact route, and changelog links are present.
* [x] Only the latest version is included; there are no disabled builds.
* [x] The commit field contains the complete 40-character source hash.
* [x] No external source repositories or srclibs are required. Runtime dependencies are FLOSS artifacts from Google Maven and Maven Central. IANA city data is public domain, pinned, and generated offline.
* [x] R8/resource shrinking are enabled and encrypted AGP dependency information is disabled.
* [x] Developer-signed reproducible builds are enabled. `Binaries` selects the [signed 2.1.0 reference APK](https://github.com/JaredTweed/QuietNight/releases/download/quietnight-v2.1.0/QuietNight-2.1.0.apk), and `AllowedAPKSigningKeys` pins certificate SHA-256 `f90cc5f530bc227418eb1d0a016a790b052e6591a5c038a9979ab6e5544a4e2f`. The private key stays outside source and CI. Debug builds retain their separate `.debug` application ID.
* [x] ABI splitting is not needed: the optimized APK is approximately 2.3 MB and the app contains no ABI-specific native code.

### Build and pipeline evidence

* [x] F-Droid Server 2.4.5 metadata rewrite and lint passed locally.
* [x] A clean checkout of the exact public source commit passed `fdroid build --stop --test --scan-binary --refresh-scanner --no-tarball dev.jaredtweed.quietnight:54`: **one build succeeded**, with source and APK scanners enabled and no bypasses.
* [x] All 141 app regression tests passed. The [exact-source Android CI](https://github.com/JaredTweed/QuietNight/actions/runs/40346488464) and [tagged release build](https://github.com/JaredTweed/QuietNight/actions/runs/40346489138) passed. App lint has no errors; 77 existing warnings are disclosed in the verification notes.
* [ ] The updated developer-signature [pipeline](https://gitlab.com/JaredTweed/fdroiddata/-/pipelines/2991416334) is being checked. The earlier source/build pipeline passed all nine checks; the new pipeline additionally verifies the signed reference. F-Droid maintainer review remains pending.
* [ ] F-Droid maintainer review and CI-produced APK/device review remain pending.

### APK report notes

The APK report's minor findings reflect disclosed, optional features: `SYSTEM_ALERT_WINDOW` is the ordinary-app overlay fallback; coarse/fine location are requested only for an explicit one-time accuracy upgrade (the default time-zone mode needs neither); and the accessibility-service intent is for trusted system-UI filtering and the opt-in service's scoped volume-key shortcut, without screen content retrieval or recorded keys. `WRITE_SETTINGS` is retained solely to recover a pending brightness transaction from an older version; new installs never request it and overlay dimming does not write system brightness. Exact-alarm access is only the older-Android/app-overlay scheduling fallback.

The job also emits runner/scanner warnings about its own `config.yml` mode and Androguard's reference API database falling back from API 36. Those are CI-tool warnings; the APK targets API 36, the build and scanner checks passed, and no application or scanner bypass was added to hide the warnings.

Build verification: https://github.com/JaredTweed/QuietNight/blob/main/fdroid/VERIFICATION.md

The clean unsigned APK SHA-256 is `142fb341f6233206c35af3c1683bed75090b4e94f1df8d89ddcf4bc118ed52c4`. The tagged GitHub CI artifact has exactly the same unsigned SHA-256. Copying the developer signature onto this independent clean F-Droid rebuild verifies using `apksigcopier` 1.1.1 and Android `apksigner`. The signed APK SHA-256 is `429cd7ade492d83472161fae044334488e4983a09dbc0fada2d0eaeba151f92d`. Inclusion approval remains pending.