# Quiet Night

A private, open-source Android wind-down companion. Softer evening light, screen dimming, and media volume below Android’s usual minimum, together on one home screen.

Quiet Night is an independent GPL-3.0 fork of [Granular Volume / Quiet Dial](https://github.com/Rzuss/granular-volume) by Rzuss. It includes the Bluetooth audible-floor fixes developed in this fork. It is not an official upstream release.

## Your evening, in one place

- **Color intensity:** a fixed red-orange evening tint, adjustable from 0–100%.
- **Screen dim:** 0–85%, using Android’s dim layer and a translucent, touch-through tint window.
- **Always:** keep the selected settings active until paused.
- **Sunset → sunrise:** calculate daily sun times offline from a saved location. Use optional foreground location access or enter coordinates manually. No background location tracking, geocoding, or internet access.
- **Custom:** drag either handle on the 24-hour circular slider, including overnight windows. Start and End buttons offer precise, accessible time entry.
- **Quiet Dial:** independently enable the media-attenuation engine, with 0–30 dB of extra attenuation and optional compact floating controls.
- **Quiet steps with volume-down:** opt in to stepping down through the quiet range when hardware media volume reaches its audible floor. The option is off by default. Explicit Mute remains available; cellular and VoIP call volume is not intercepted by this feature.
- **Bluetooth calibration:** find the first audible media-volume step on Android 13+. Preview controls are capped at four steps above the lowest nonzero index. Calibrations are saved by headset name; uncalibrated users keep the original path without media-route queries.
- A persistent notification with Pause, plus a Quick Settings tile. Pausing removes the screen filter and releases the audio effect.

All features are free. No ads, accounts, analytics, purchase prompts, or internet permission. The app does not change the system’s brightness setting or Bluetooth Absolute Volume setting. App backup is disabled so saved coordinates and settings are not uploaded through Android cloud backup.

## First run

1. Open Quiet Night and choose your preferred color intensity and dim level.
2. Tap **Begin wind-down** and allow display over other apps. Notifications are optional but make the Pause action easier to reach.
3. For Custom or Sunset mode, grant **scheduled changes** access. This allows exact start/stop boundaries while the app is closed. Without this access, scheduled modes stay inactive and explain the missing setup instead of pretending to be armed. Always mode does not need it.
4. For Sunset, choose a location once. Update it manually when travelling. Sun times follow the phone’s current timezone; polar locations with no sunrise/sunset show a message and can use Custom instead.
5. Optionally enable Quiet Dial. If your headset is silent at the first nonzero step, begin wind-down, play audio, and open **Headphone setup** to calibrate the first audible step.

The settings are shared by the selected schedule. Turning Quiet Dial off leaves the screen controls available. Turning wind-down off removes both the visual filter and any active audio effect. The app never restores a previously high hardware volume automatically.

## Light and sleep

The design favors dim red-orange light, which contains less short-wavelength light. [NIOSH explains the effect of light color on circadian rhythms](https://archive.cdc.gov/www_cdc_gov/niosh/emres/longhourstraining/color.html), and [NHLBI recommends avoiding bright artificial light before bed](https://www.nhlbi.nih.gov/health/sleep-deprivation/healthy-sleep-habits). The app does not claim a particular hue induces sleep or treats insomnia. Lower brightness and putting the phone away remain the goal.

## Android behavior and limits

- Android 9+; calibration uses the actual media-route query available on Android 13+.
- Overlay controls cannot cover every secure screen, system permission dialog, or lock screen. Some apps deliberately hide overlays.
- The 85% setting is Android’s dim amount. The tint window remains below Android’s obscuring-opacity limit so ordinary app touches can pass through. Pause remains available in the notification.
- Audio effects and attenuation support vary by device, route, and player. Cellular-call audio cannot be attenuated through this engine. Verify your alarm and audio behavior on your own device; the app does not change the alarm-volume setting.
- Volume-down uses Android’s media-volume change broadcasts. A key press that produces no index change produces no broadcast; this is not a replacement for Android’s entire volume-key system.
- Sunset/sunrise calculations are an offline approximation including standard atmospheric refraction, not an astronomical instrument. Exact alarms still depend on Android permissions and the OS; force-stopping the app suppresses scheduled operation until it is opened again.
- The independent package is `dev.jaredtweed.quietnight` (`.debug` for test builds). It can coexist with upstream Quiet Dial. Do not run two audio-control services simultaneously. The old app’s private settings cannot be imported automatically across package IDs; calibrate the new app once.

## Build and test

JDK 17 and Android SDK 36 are required. No signing secrets are needed for debug builds.

```sh
cd android
./gradlew :app:assembleFdroidDebug :app:testFdroidDebugUnitTest
```

APK: `android/app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`.

```sh
adb install -r android/app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

Release builds use `:app:assembleFdroidRelease` and require your own signing process. Do not distribute debug keys as release keys. The retained `fdroid` build flavor contains only open-source runtime dependencies.

Tests cover the existing Bluetooth fixes, capped calibration, the uncalibrated fast path, optional volume-down behavior, overnight and daytime schedules, DST, solar date wrapping, polar-day fallback, permission gating, alarm scheduling, and the touch-through overlay’s window configuration. CI builds the APK and runs the regression suite on pushes to `main` and pull requests.

## Credits and license

GPL-3.0-only; see [LICENSE](LICENSE). Granular Volume’s audio engine and original work are by Rzuss and its contributors. Quiet Night’s home, screen controls, schedules, and fork extensions are by Jared Tweed and contributors. Runtime third-party notices are available inside the app under **Licenses**. Historical upstream store assets and documentation are retained as source history; they do not describe this fork’s distribution or privacy policy.
