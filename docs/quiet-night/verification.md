# Verification

Device checks on 2026-10-23 used a Pixel 8 Pro with BX27 connected. The app retained the headset calibration at step 2. The original Bluetooth fix branch was not modified.

Screen dimming is now an application overlay. It does not read or write the system brightness setting or adaptive-brightness mode, so there is no brightness transaction to restore when Screen is switched off. Display-over-other-apps access is required; without it, Screen controls stay inactive. The dim layer is touch-through, and the tint remains translucent so ordinary app input continues to work.

Android keeps some privileged system surfaces above application overlays, and some apps deliberately hide overlays. Always-on display, biometric illumination, HDR, and vendor-specific brightness overrides are outside the guarantee of an application overlay.

Automated tests cover Bluetooth floor calibration and hardware-key transitions, independent activation and migration, screen scheduling, the always-on volume-down range including media mute, the read-only audio indicator, Material home-screen rendering, and the overlay window configuration. CI builds debug and optimized unsigned release APKs and runs tests and lint.
