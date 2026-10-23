# Verification

Device checks on 2026-10-23 used a Pixel 8 Pro with BX27 connected. The app retained the headset calibration at step 2. The original Bluetooth fix branch was not modified.

With Screen enabled at 40% dimming, the saved manual brightness setting was 15 and the applied setting was 9. Quick Settings was opened and visually confirmed; the system brightness setting remained 9. It also remained 9 after sleep/wake with the keyguard showing. Turning Screen off restored 15 while Audio remained enabled and its foreground service stayed running. Turning Screen back on reapplied 9. This verifies the system-brightness path on this device; screenshots alone cannot measure physical luminance.

The color tint remains an application overlay. Always-on display, biometric illumination, HDR, and vendor-specific brightness overrides are outside the guarantee of ordinary system-brightness control.

Automated tests cover Bluetooth floor calibration and hardware-key transitions, independent activation and migration, screen scheduling, brightness recovery and manual overrides, the read-only audio indicator, and Material home-screen rendering. CI builds debug and optimized unsigned release APKs and runs tests and lint.
