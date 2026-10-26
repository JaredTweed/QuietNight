# Quiet Night architecture

The Android project uses namespace and release application ID `dev.jaredtweed.quietnight`. Debug builds append `.debug` to the application ID. Source and test folders mirror the namespace under `java/dev/jaredtweed/quietnight/`.

## Responsibilities

- `MainActivity` owns the Material home screen, explicit permission requests, and user edits.
- `audio/` contains audio-effect strategies, hardware-volume access, quiet-step reconciliation, and volume-down hold detection. Quiet-step calculations remain independent of Android event delivery.
- `service/VolumeControlService` owns the foreground audio session and its resource lifecycle. `ScreenFilterTileService` toggles only the screen filter.
- `sleep/` contains screen-filter windows, the trusted accessibility service, screen scheduling, and legacy brightness recovery. `SleepConfig` describes configuration; `SleepSettings` persists it. `SunTimes` calculates solar events; `SleepSchedule` selects schedule windows; `TimeZoneCities` provides generated offline reference coordinates.
- `receiver/BootReceiver` reconciles the user's enabled controls after supported system events.
- `util/` contains permission helpers, audio preferences, and the observable running state.

Sunset defaults to the system time zone's reference city. A foreground location request or manual coordinates can improve accuracy. The app does not track background location or use the internet. Generated public-domain city data is pinned under `tools/tzdb/`; `tools/generate_time_zone_cities.py --check` verifies it.

The trusted overlay covers Quick Settings without reading screen contents. Its key-filter capability is requested only while Quiet steps is enabled, ignores non-volume keys, and records no key events. A two-second volume-down hold issues an idempotent media-mute command without consuming normal key handling.

## Compatibility and validation

The application ID, persisted settings keys, and headset calibration values remain stable across source refactors. Android component class renames can require re-enabling the accessibility service or re-adding an existing Quick Settings tile; package updates preserve application data.

Run the F-Droid debug unit tests, debug lint, debug APK build, optimized release APK build, metadata checker, generated-data check, and source/APK scans before release. Publish a new version and immutable source tag before updating the F-Droid build recipe. Historical implementations remain available in Git history; this document describes the current code.
