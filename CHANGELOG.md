# 1.3.3 (2026-11-04)

### Bug Fixes

* NFC tag activation shows SUCCESS, or FAILURE with the reason, and OK returns to Settings.
* A read-only tag says FAILURE - Tag is read-only. A tag of the wrong type, including one that is too small, says FAILURE - Incorrect Tag type. A write that does not finish says FAILURE - Write failed.
* A successful write stays on the write screen until that dialog is dismissed, so the tag does not also start or stop the session.

# 1.3.2 (2026-11-03)

### Bug Fixes

* Drop the gray card around QR code controls. Display QR Code stays indented under the switch.

# 1.3.1 (2026-11-03)

### Bug Fixes

* The printable QR code in the README is the same symbol Display QR Code draws.
* QR code controls stay locked while a session is running. Settings says to stop monitoring before changing them.
* Refresh the README settings pictures, including the locked controls and the Display QR Code screen.

# 1.3.0 (2026-11-03)

### Features

* Open TapOut from a camera scan of the printed QR code. The code encodes `tagout://TAGOUT_TOGGLE`, and Scan QR Code on the home screen reads the same payload.
* Place Display QR Code inside the Enable QR Code controls setting.
* Add a printable copy of the code to the README.

### Bug Fixes

* Publish the new session, including the attempt count and start time, before the home screen resumes after that scan.

Older printed `TAPBLOK_TOGGLE` codes no longer match. QR controls still have to be turned on in Settings.

# 1.2.1 (2026-10-30)

### Bug Fixes

* Restore NFC tag unlock on Android 17. Tag intents are delivered only to an activity that requires `android.permission.DISPATCH_NFC_MESSAGE` when the app targets API 37.

# 1.2.0 (2026-10-22)

TapOut is the display name of this fork. The Play application id stays `com.galynte.tapblok`, so this ships as an update to the existing listing. Version 1.2.0 follows the published 1.1.4 release. It remains a derivative of [TapBlok by cajdata](https://github.com/cajdata/TapBlok) under Apache 2.0.

### Features

* Rebrand the visible app as TapOut while keeping application id `com.galynte.tapblok`
* Replace the launcher and in-app mark with original ring-and-pause artwork for this fork
* Use a fixed teal brand palette instead of the default Material purple theme
* Keep the emergency hold control readable as a solid button after the Compose update
* Shorten the emergency hold from 60 seconds to 30 seconds
* Add the Play listing icon and 1024×500 feature graphic

### Maintenance

* Update Android Gradle Plugin to 9.4.1, Gradle to 9.6.1, and Kotlin to 2.4.21
* Update Compose BOM, AndroidX, Room, Coil, AppCompat, and ZXing to current stable releases

# 1.0.0 (2026-01-11)

Earlier release of this fork, before the product was renamed TapOut. The links below point at this Git repository.


### Bug Fixes

* Update build-and-release.yml ([3b00a14](https://github.com/Galynte/TapBlok/commit/3b00a145ea088f759752c34e9e31d4d658bd3a68))
* Update release flow ([4c57b93](https://github.com/Galynte/TapBlok/commit/4c57b932839545c8fbf06eec844afaa0b0bfbde4))


### Features

* Updated release flow ([f5fc1f7](https://github.com/Galynte/TapBlok/commit/f5fc1f78367ee4be9f376118cbccc39b22ae1e4a))
