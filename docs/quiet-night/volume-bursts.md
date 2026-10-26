# Background volume bursts — 2026-10-26

Android's [audio service](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/AudioService.java) groups stream-volume broadcasts using `DELIVERY_GROUP_POLICY_MOST_RECENT` and `DEFERRAL_POLICY_UNTIL_ACTIVE`. The last notification can describe only one transition even though the hardware index has moved several steps while the app is in the background.

Previously, Quiet Night eased gain by 5 dB per delivered notification, then restored the hardware floor. A burst could therefore become one quiet step. The old heuristic also treated larger movements as something to defend against and could require a second attempt.

The coordinator now keeps the last hardware index accounted for by its quiet ladder. It reads the current hardware level on a notification and reconciles the accumulated distance in one operation. The pure `QuietVolumeSteps` mapper advances the quiet range, carries surplus steps into normal hardware volume, and handles calibrated-headset audibility and mute. Old notifications cannot replay an already-accounted-for burst. A route change establishes a new anchor instead of pretending its initial volume is a key press.

All external media-volume changes follow this same relative-step model while in the quiet range; the app does not guess whether a large movement came from keys, Android's slider, or another app. Hardware is lowered before easing gain. Raising hardware is reserved for restoring a calibrated headset from its silent indices. No-op hardware writes are skipped.

No continuous polling, wake lock, fake playback, key interception, new permission, or dependency was added. Android can still delay notification delivery. Keys pressed after hardware volume has saturated, and opposing changes completely lost through coalescing, cannot be reconstructed from stream indices; this is not a promise of exact physical-key counting in those cases.

## Validation

- Four background-burst regressions failed against the previous implementation and passed with the fix.
- All 106 unit/platform tests passed. Tests cover coalesced rises, older queued events, successive bursts with late acknowledgements, quiet-to-normal overflow, mute recovery, coalesced down presses, and missed mute notifications.
- Pure tests compare batched and sequential increases across all six quiet depths and hardware floors 1–5, through each observable hardware position.
- Debug and optimized unsigned release builds succeeded; lint reported zero errors and 77 existing warnings.
- Home, schedule, audio, and compact-screen renders matched the saved baseline byte for byte.
- F-Droid Server 2.4.5's source scanner reported zero problems on an exported copy of the working source, with no scan bypasses. Its binary scanner reported zero problems in the new unsigned release APK, including its extra-signing-block check. This is separate from the earlier clean F-Droid CLI build of the published 2.0.0 source.
- The fixed debug build was installed on the Pixel with app data retained, and its foreground audio service was confirmed active with the UI in the background. Android audio hardening rejected shell-generated media-volume changes; physical-button timing was not measured by ADB.

## F-Droid release preparation

The application ID, FLOSS dependencies, manifests, capabilities, toolchain, and unsigned release configuration are unchanged. The metadata checker passes. The existing recipe remains pinned to the immutable 2.0.0 release, not this development fix. Before submitting a release containing this fix, bump the version, create a new immutable source tag, pin the recipe to that full source commit, and run the clean F-Droid build for that exact source. Do not move the old tag or present this development APK as the old release artifact. Official review and inclusion-policy eligibility are still F-Droid's decision.
