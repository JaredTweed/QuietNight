# Automatic sunset and held-key mute

Sunset mode defaults to the representative city for the system time zone. The lookup is generated from pinned, public-domain IANA tzdb tables; the app performs no network lookup and requests no location access for this path. Current-location and manual-coordinate options remain available for greater accuracy. Switching back to automatic retains saved coordinates without using them. Fixed-offset zones without a representative city explain the limitation instead of inventing a location. Polar conditions retain the existing Custom schedule fallback.

Solar calculations, schedule-window selection, city lookup, hardware-key detection, and media-volume reconciliation have distinct responsibilities. Generated city data has a reproducible generator and CI consistency check. A held volume-down key uses Android uptime and one cancellable deadline, rather than volume-broadcast timing or polling. Repeat events cannot postpone the deadline. Short presses do not accumulate into a hold.

The existing accessibility service requests key filtering only while Quiet steps is enabled. Other keys are ignored immediately, normal key handling continues, and key events are never recorded. Disabling Quiet steps, disconnecting the service, releasing the key early, or pressing volume-up cancels the pending hold. Calls are excluded. The mute action is idempotent and checks that media actually reached zero; subsequent volume-up uses the normal quiet-step reconciliation without restoring a previously loud index.

Another accessibility service can own key filtering, and Android scheduling can delay callbacks under load. The shortcut therefore depends on the trusted service being connected. It adds no Android uses-permission declaration and does not request screen contents, gestures, screenshots, or an accessibility button.

The development changes require a new version/tag and an updated F-Droid recipe before submission. Passing local scans does not constitute F-Droid acceptance, and the existing release tag remains unchanged.

## Namespace and upgrade check

Active app code and tests now live under `dev/jaredtweed/quietnight`, with namespace `dev.jaredtweed.quietnight`. The Quick Settings service is `ScreenFilterTileService`, and the theme is `Theme.QuietNight`. Upstream authorship remains in legal notices. Persisted preference keys are intentionally retained for upgrade compatibility.

On the connected Pixel 8 Pro, the debug APK installed successfully over the existing app. A comparison confirmed every existing preference value was retained. The previously enabled accessibility service and existing Quick Settings tile were migrated to their renamed components while preserving other entries. Android reported the trusted service bound with key-filter capability and no crashed-service entry or accessibility button. The foreground audio service remained active in the background. The user confirmed that a physical two-second volume-down hold muted media while the app was in the background; ADB-injected events were not treated as equivalent verification.

All 141 unit tests passed after the namespace migration. Debug lint reported no errors and 77 existing warnings. The metadata checker, generated-data check, and F-Droid source scan passed; the source scan reported zero problems without exemptions.

Both the debug and optimized unsigned release APKs built successfully. The final release APK scan reported zero problems. These checks used fdroidserver 2.4.5 without source or binary scan exemptions.
