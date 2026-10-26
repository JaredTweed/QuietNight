# Permission audit — 2026-10-26

The full-screen accessibility overlay is preserved, including notifications and Quick Settings. No screen-reading, accessibility event, gesture, key-filtering, or screenshot capability was added.

## Required access by feature

| Feature | Access |
| --- | --- |
| Full-screen filtering, including Quick Settings | Enable Quiet Night screen dimming in Accessibility. Display-over-other-apps access is not needed for this path. |
| Scheduled full-screen filtering on Android 14+ or Android 9–11 | No additional grant. A timer belongs to the already-bound accessibility service. |
| Scheduling on Android 12/13, or app-only scheduling on Android 12+ | Optional Alarms & reminders fallback, preserving the earlier operating mode. |
| App-only filtering without the accessibility service | Optional Display over other apps. |
| Quiet steps | Automatic Modify audio settings permission. |
| Notification with Stop | Optional notification grant on Android 13+. |
| Sunset calculation | No location permission for manually entered/saved coordinates. Foreground location access is requested only for Use current location; precise access preserves GPS-only operation. |
| Restore pre-overlay brightness | Optional Modify system settings only when an unfinished legacy brightness record exists. This permission does not implement the current dimmer. |
| Restart after boot; maintain active controls | Android's normal boot and foreground-service permissions. Removing them would remove those capabilities. |

Optional manifest declarations remain to preserve older-device support, app-only operation, and recovery of earlier brightness changes. Their presence does not require a full-screen-filter user to grant them. On new setup the Filter button guides users to full-screen filtering first, avoiding an unnecessary application-overlay grant.

## Timer design and limits

Android's [AlarmManager listener API](https://developer.android.com/reference/android/app/AlarmManager) supports a permission-free exact listener alarm on Android 14+; Android 12/13 required exact-alarm access for this overload. Listener alarms belong to a live component and cannot replace persistent alarms for arbitrary dead processes. Quiet Night uses them only while its existing accessibility service is connected, cancels them when it disconnects, and re-arms from saved settings when Android reconnects it.

The filter view is removed outside scheduled windows. A transparent full-screen window, permanent foreground notification during inactive windows, continuous polling, silent playback, battery-optimization exemption, and additional permissions are not needed. The normal foreground notification remains during active filter/audio sessions.

A listener alarm may be deferred while the device is in Doze. The bound service re-evaluates the wall-clock schedule immediately on screen-on/unlock and on time/date/timezone changes, and applies the trusted filter before waiting for asynchronous foreground-service startup. This preserves correct filtering when the display becomes usable; it is not a claim of waking a sleeping device at an exact moment without exact-while-idle alarm access. That persistent path remains available for the compatibility mode.

Late listener callbacks are ignored after cancellation or replacement, without removing AlarmManager's queued delivery wrapper: Android must be allowed to acknowledge delivery and release its own wake lock.

The app does not revoke grants on an offline phone or silently disable fallback capabilities. Existing grants may still appear in Android settings until the user turns them off after installing this version.

## Verification

- Debug and optimized unsigned release builds passed with the pinned JDK/Gradle/Android toolchain.
- All 116 regression tests passed. New tests cover denied exact-alarm access, listener ownership, Android 9/12/13/14 eligibility, wake-time schedule reconciliation, disconnection fallback, and stale callback delivery after replacement/cancellation.
- Lint reported zero errors and 77 existing warnings. The home, schedule, audio, and compact-screen renders matched the saved baseline byte for byte.
- The F-Droid metadata checker and F-Droid Server 2.4.5 source/APK scanners passed with zero scanner problems. No new dependency, permission declaration, or scan bypass was added. The published release recipe and tags were not moved.
- No phone was connected for installation or real-device denied-permission/Doze verification. Existing grants on the phone were not changed.
