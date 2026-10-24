# Suggested merge request: New app — Quiet Night

Adds `dev.jaredtweed.quietnight`, Quiet Night 2.0.0 (53).

Quiet Night is an independent GPL-3.0-only fork of Granular Volume / Quiet Dial, maintained by Jared Tweed. The maintainer has requested F-Droid inclusion preparation and selected F-Droid signing. The fork has its own package ID, name, moon-and-star icon, current English listing, privacy policy, issue tracker, and security-reporting route.

Beyond the original media-volume controller, it adds scheduled screen tint/dimming, offline solar schedules, optional accessibility-based filtering over Quick Settings, independent Screen/Audio controls, and a minimal Material interface. The accessibility service requests no events, content retrieval, gesture injection, screenshot capability, or key filtering. There is no internet permission, advertising, analytics, billing, or non-free runtime dependency.

The recipe contains one Gradle build, pinned to source commit `39dfd82afe32995994df6b382e2d0eab0d780145` (tag `quietnight-v2.0.0`). It builds the `fdroid` flavor from `android/app`, without an output override or scanner suppression. R8/resource shrinking are enabled and encrypted AGP dependency information is disabled. F-Droid will sign the result; the recipe does not request preservation of a developer certificate.

English descriptions, screenshots, icon, and changelog are upstream in Fastlane format. Code, listing, and build details: https://github.com/JaredTweed/QuietNight

Verification evidence: https://github.com/JaredTweed/QuietNight/blob/HEAD/fdroid/VERIFICATION.md

- [x] Public GPL source and meaningful fork differentiation.
- [x] Unique application ID and distinct name/icon.
- [x] Author contact through the issue tracker; maintainer consents to inclusion preparation.
- [x] Accurate upstream English Fastlane metadata and code-53 changelog.
- [x] Tagged source and scoped automatic tag-update checks.
- [x] Local F-Droid recipe lint, source scanner, `fdroid build`, and binary scanner pass without bypasses.
- [x] 68 regression tests, debug/release builds, lint, and upstream CI pass.
- [ ] Official fdroiddata merge-request CI and maintainer/device review.
- [ ] Reviewer determination of the maintenance-history/usage criterion for this new fork.

History disclosure: the original code history starts on 2026-06-26, while Quiet Night-specific fork development began on 2026-10-23. We are not asserting that a policy exception has already been granted. If further public maintenance history or usage evidence is needed, please advise; no users or acceptance claims are being fabricated.

Before posting: fork fdroiddata publicly, copy only `fdroid/metadata/dev.jaredtweed.quietnight.yml` to its `metadata/` directory, commit it on an unprotected app-specific branch, run its pipeline, and replace this draft's remaining unchecked items only when verified.
