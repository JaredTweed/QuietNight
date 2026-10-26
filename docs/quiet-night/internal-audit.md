# Internal reliability and performance review — 2026-10-26

This pass preserves the interface, resources, settings, calibration keys, and application ID.

## Changes

- Centralized native audio-effect ownership. Partially initialized effects are released, enable-status failures trigger fallback, and disable failures cannot skip release. Android's [AudioEffect contract](https://developer.android.com/reference/android/media/audiofx/AudioEffect) includes non-success return codes from `setEnabled`, in addition to exceptions.
- Serialized controller gain writes, initialization, reattachment, and terminal teardown. Initialization is idempotent; calls after teardown cannot revive effects or change saved gain. Failed native writes mark the effect unavailable while retaining the requested gain for recovery.
- Rejected non-finite gain requests, sanitized saved gain, and avoided redundant preference writes.
- Used the API 33+ [prospective media route](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)) for volume curves, with conservative fallback on older devices or unavailable routing queries. Multiple simultaneous outputs and invalid audible gain tables cannot manufacture a single misleading curve.
- Cached schedule plans until a boundary, settings edit, clock rollback outside the cached interval, or timezone change. Repeated refreshes do not repeat solar calculations.
- Avoided unchanged filter-window and notification updates. A failed trusted-overlay operation no longer claims filter ownership, allowing the existing application-overlay fallback to run.
- Cancelled replaced location requests and timeouts. Results from cancelled requests cannot overwrite manual coordinates or a newer request; permission revocation also cancels the pending request.

## Verification

Using JDK 17, SDK 36, and the repository's pinned toolchain:

```
bash android/gradlew -p android \
  :app:assembleFdroidDebug :app:testFdroidDebugUnitTest \
  :app:lintFdroidDebug :app:assembleFdroidRelease \
  --no-daemon --max-workers=1
python3 tools/check_fdroid_metadata.py
```

- Debug and optimized unsigned release APKs built successfully.
- All 92 tests passed, including 24 added regressions covering lifecycle failures, routing and invalid curves, cache boundaries/DST, overlay ownership, and stale location results.
- Lint: zero errors, 77 remaining warnings. Dependency updates and visual-resource cleanup were not bundled into this pass.
- Home, custom schedule, audio, and compact-screen PNG renders matched the saved pre-change baseline byte for byte.
- The F-Droid metadata checker passed. Published release tags and the pinned F-Droid recipe were not moved.

Native failures are simulated in regression tests. This is not a claim of universal OEM audio-effect support, a measured battery improvement, or a physical-device endurance/leak test. No phone was connected for this pass.
