# Quiet Night privacy

Quiet Night is an offline Android app. It has no internet permission, accounts, advertising, analytics, crash-reporting service, or automatic updater. It does not send settings, device information, or usage data anywhere. Source and license links open in your chosen browser only when you tap them.

Settings, screen schedules, and headphone calibration are stored locally. Android app backup and data extraction are disabled. Sunset timing defaults to an offline estimate for the representative city of the phone’s time zone; it does not query device location. Optional foreground location access or manually entered coordinates can improve accuracy. A chosen coordinate is saved locally and remains available when switching back to automatic. There is no background location tracking, geocoding service, or network lookup.

## Special access

- Display over other apps permits the app-only screen filter.
- The optional accessibility service permits the touch-through filter above notifications and Quick Settings. It requests no accessibility content event types, screen-content retrieval, gesture injection, or screenshot capability. It draws the filter and maintains its local schedule. With Quiet steps on it additionally requests hardware-key events to detect a two-second volume-down hold. Non-volume keys are immediately ignored without inspecting characters, logging, or storage. Normal key handling continues. The current volume-button press is held only transiently in memory; key handling and pending holds are disabled when Quiet steps is off.
- Modify system settings is retained only to restore an outstanding brightness transaction made by a pre-overlay version. Fresh installs do not request it, and the current dimmer never starts a brightness transaction.
- Modify audio settings allows media-volume control and a global audio-effect attenuation chain. Audio support varies by device and player; other mixed audio may also be affected. Test alarms and call behavior on your device.
- Full-screen filtering on Android 14+ (and Android 9–11) uses a timer owned by the already-bound accessibility service, with a schedule recheck on wake. It needs no Alarms & reminders grant. Optional exact-alarm access remains for Android 12/13 and app-only scheduling on Android 12+. It is not replaced with a battery-optimization exemption, wake-lock permission, or background polling.
- The foreground notification keeps active controls running; its Stop action stops both screen and audio. The Quick Settings tile changes the screen filter only.

Issues: https://github.com/JaredTweed/QuietNight/issues

Source: https://github.com/JaredTweed/QuietNight
