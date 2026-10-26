# New app: Quiet Night

Proposed release: Quiet Night 2.1.0 (54), `dev.jaredtweed.quietnight`.

The maintainer has authorized submission and selected F-Droid signing. This independent GPL-3.0-only app combines scheduled screen filtering with quiet media-volume control. It has a distinct application ID, moon-and-star icon, Material interface, Fastlane listing, privacy policy, and issue tracker. Upstream attribution is retained.

Sunset scheduling defaults to offline reference-city coordinates for the phone's time zone, with optional one-shot location/manual coordinates. The trusted accessibility overlay includes Quick Settings. While Quiet steps is on, it requests hardware-key handling for a two-second volume-down hold to mute; other keys are immediately ignored, no keys are recorded, and normal volume handling continues. It requests no content events, screen retrieval, gestures, screenshots, or floating accessibility button. Key handling switches off with Quiet steps. There is no internet permission, advertising, analytics, billing, or non-free runtime dependency.

The immutable source tag for this release is `quietnight-v2.1.0`. The final recipe must pin its full source commit, build the `fdroid` flavor from `android/app`, and contain only the latest version. R8/resource shrinking are enabled; AGP dependency information is disabled. No scanner bypass or developer-certificate preservation is requested. The maintainer chose F-Droid signing because there is no established developer-signed release certificate to preserve; debug builds use a separate application ID.

The README and upstream Fastlane listing include four current screenshots. Source: https://github.com/JaredTweed/QuietNight

The application has 141 passing regression tests and has been tested on a Pixel 8 Pro. The user confirmed the physical hold shortcut works while the app is in the background. Exact-release clean build and scanner results belong in VERIFICATION.md after they complete; do not mark official F-Droid CI or reviewer approval as passed prematurely.

History disclosure: upstream development began on 2026-06-26; this fork's development began on 2026-10-23. The maintainer uses it on their phone, but we do not claim a three-month fork history, an established public user base, or an approved policy exception. Reviewers must determine eligibility under Quality Control point 6.
