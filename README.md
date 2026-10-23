# Quiet Night

A private, open-source Android wind-down companion. Softer evening light, screen dimming, and media volume below Android’s usual minimum, together on one home screen.

Quiet Night is an independent GPL-3.0 fork of [Granular Volume / Quiet Dial](https://github.com/Rzuss/granular-volume) by Rzuss. It includes the Bluetooth audible-floor fixes developed in this fork. It is not an official upstream release.

<p>
  <img src="docs/quiet-night/home.png" width="300" alt="Quiet Night home with color and dimming sliders">
  <img src="docs/quiet-night/schedule.png" width="300" alt="Custom overnight schedule with start and end handles">
</p>

## Your evening, in one place

- **Color intensity:** a fixed red-orange evening tint, adjustable from 0–100%.
- **Screen dim:** 0–85%, with optional system-wide brightness control for Quick Settings, notifications, and the interactive lock screen. Without that access, Android’s dim layer softens ordinary apps.
- **Always:** keep the selected settings active until paused.
- **Sunset → sunrise:** calculate daily sun times offline from a saved location. Use optional foreground location access or enter coordinates manually. No background location tracking, geocoding, or internet access.
- **Custom:** drag either handle on the 24-hour circular slider, including overnight windows. Start and End buttons offer precise, accessible time entry.
- **Audio button:** independently enable the media-attenuation engine. A read-only Quiet Dial indicator shows media mute, normal volume, or the 0–30 dB quiet range; use the phone’s volume keys to change it. There is no attenuation slider.
- **Screen button:** independently activate screen tint/dimming or arm the selected screen schedule. Exactly two Material extended floating action buttons stay at the bottom right.
- **Quiet steps with volume-down:** opt in to stepping down through the quiet range when hardware media volume reaches its audible floor. The option is off by default. Explicit Mute remains available; cellular and VoIP call volume is not intercepted by this feature.
- **Bluetooth calibration:** find the first audible media-volume step on Android 13+. Preview controls are capped at four steps above the lowest nonzero index. Calibrations are saved by headset name; uncalibrated users keep the original path without media-route queries.
- A persistent notification with Stop, plus a screen-only Quick Settings tile. The tile never toggles audio. The notification Stop action stops both; either home button stops its own feature.

All features are free. No ads, accounts, analytics, purchase prompts, or internet permission. System-wide dimming temporarily reduces the system brightness and pauses adaptive brightness, restoring the saved brightness and mode when paused. Manual brightness changes take precedence; use **Resume screen dimming** to apply it again. Bluetooth Absolute Volume is left unchanged. App backup is disabled so saved coordinates and settings are not uploaded through Android cloud backup.

## First run

1. Open Quiet Night and choose your preferred color intensity and dim level.
2. Use **Screen** and **Audio** at the bottom right independently. For warm tint or fallback app dimming, allow display over other apps. Audio works without screen permissions; notifications make Stop easier to reach.
3. For dimming across system screens, tap **Enable system-wide dimming** and allow **Modify system settings**. This access is optional; without it, dimming only covers ordinary apps.
4. For Custom or Sunset mode, grant **scheduled changes** access. This allows exact start/stop boundaries while the app is closed. Without this access, scheduled screen controls stay inactive and explain the missing setup instead of pretending to be armed. Always mode does not need it.
5. For Sunset, choose a location once. Update it manually when travelling. Sun times follow the phone’s current timezone; polar locations with no sunrise/sunset show a message and can use Custom instead.
6. Turn on **Audio**. If your headset is silent at the first nonzero step, play audio and open **Headphone setup** to calibrate the first audible step. For BX27 this can be step 2. Enable **Quiet steps with volume-down** to continue into the quiet range using the hardware buttons.

The schedule applies only to screen controls. Audio stays on until its own button is switched off, even during the daytime or without exact-alarm access. Screen changes do not recreate the running audio engine or overwrite Bluetooth calibration. The app never restores a previously high hardware volume automatically.

## Light and sleep

The design favors dim red-orange light, which contains less short-wavelength light. [NIOSH explains the effect of light color on circadian rhythms](https://archive.cdc.gov/www_cdc_gov/niosh/emres/longhourstraining/color.html), and [NHLBI recommends avoiding bright artificial light before bed](https://www.nhlbi.nih.gov/health/sleep-deprivation/healthy-sleep-habits). The app does not claim a particular hue induces sleep or treats insomnia. Lower brightness and putting the phone away remain the goal.

## Android behavior and limits

- Android 9+; calibration uses the actual media-route query available on Android 13+.
- The warm color overlay cannot cover every secure screen, system permission dialog, or lock screen. Some apps deliberately hide overlays. System brightness dimming reaches system screens independently of the tint. Always-on display, biometric illumination, HDR, and vendor-specific brightness overrides may behave differently.
- With system-wide access, the percentage reduces the saved brightness setting, bounded by the device minimum; it is not a calibrated percentage of perceived luminance. Slider updates never compound the reduction. Without access, the percentage uses Android’s dim amount. The tint remains translucent so ordinary app touches pass through.
- Brightness recovery is saved before global settings are changed, including across service/process restarts. Pause before force-stopping or uninstalling: Android cannot run restoration code after the app is removed or force-stopped. If necessary, adjust brightness in Quick Settings; reopening the installed app can retry a pending restoration.
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

Regression tests cover brightness restoration, interrupted writes, permission loss, manual overrides, the Android settings adapter, rendered home/schedule screens, the existing Bluetooth fixes, capped calibration, the uncalibrated fast path, optional volume-down behavior, overnight and daytime schedules, DST, solar date wrapping, polar-day fallback, permission gating, alarm scheduling, and the touch-through overlay’s window configuration. CI builds debug and optimized release APKs, runs lint, and executes the regression suite. On the Pixel 8 Pro, device checks confirmed reduced system brightness with Quick Settings open and after lock/wake; stopping Screen restored the saved level while Audio remained enabled. Other devices and always-on display behavior still require their own verification.

## Credits and license

GPL-3.0-only; see [LICENSE](LICENSE). Granular Volume’s audio engine and original work are by Rzuss and its contributors. Quiet Night’s home, screen controls, schedules, and fork extensions are by Jared Tweed and contributors. Runtime third-party notices are available inside the app under **Licenses**. Historical upstream store assets and documentation are retained as source history; they do not describe this fork’s distribution or privacy policy.
