# Quiet Night privacy

Quiet Night is an offline Android app. It has no internet permission, accounts, advertising, analytics, crash-reporting service, or automatic updater. It does not send settings, device information, or usage data anywhere. Source and license links open in your chosen browser only when you tap them.

Settings, screen schedules, and headphone calibration are stored locally. Android app backup and data extraction are disabled. Optional location access is foreground-only: a selected coordinate is saved to calculate sunrise and sunset on the device. You can enter coordinates manually. There is no background location tracking or geocoding service.

## Special access

- Display over other apps permits the app-only screen filter.
- The optional accessibility service permits the touch-through filter above notifications and Quick Settings. It requests no accessibility event types, screen-content retrieval, gesture injection, key filtering, or screenshot capability. It draws the filter and maintains its local schedule.
- Modify system settings is retained only to restore an outstanding brightness transaction made by a pre-overlay version. Fresh installs do not request it, and the current dimmer never starts a brightness transaction.
- Modify audio settings allows media-volume control and a global audio-effect attenuation chain. Audio support varies by device and player; other mixed audio may also be affected. Test alarms and call behavior on your device.
- Full-screen filtering on Android 14+ (and Android 9–11) uses a timer owned by the already-bound accessibility service, with a schedule recheck on wake. It needs no Alarms & reminders grant. Optional exact-alarm access remains for Android 12/13 and app-only scheduling on Android 12+. It is not replaced with a battery-optimization exemption, wake-lock permission, or background polling.
- The foreground notification keeps active controls running; its Stop action stops both screen and audio. The Quick Settings tile changes the screen filter only.

Issues: https://github.com/JaredTweed/QuietNight/issues

Source: https://github.com/JaredTweed/QuietNight
