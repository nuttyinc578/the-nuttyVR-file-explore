# Release validation — 1.1.0

Performed in the build workspace on 2026-10-25.

## Automated checks

- Host suites: **37 passed** (22 file-operation tests and 15 APK snapshot tests).
- Android Java/resource compilation and D8 conversion: **passed**.
- Android Lint: **0 errors**. Two advisory warnings remain: `ScopedStorage` for the file manager's broad shared-storage permission, and `OldTargetApi` for the intentional API 34 target used for Quest compatibility. The app also offers Android's folder picker.
- APK alignment: **passed**.
- APK cryptographic signature verification: **passed**, v2 and v3 signatures.
- Packaged manifest: package `com.nuttyvr.explorer`, version `1.1.0` (version code 2), minimum SDK 29, target SDK 34, exported launcher activity, resizable window, optional touchscreen and head tracking, Quest 2/Pro/3/3S device metadata.
- APK-specific install permission and private APK activity; launch visibility covers Android, TV and Quest VR launcher activities. No broad installed-package query permission is requested.
- Update uses the same signing certificate as 1.0.0.
- No internet permission and no CPU-specific native libraries.

The Gradle daemon cannot open its local server socket in this environment. The delivered release was assembled with the Android SDK tools through `build.sh`; Android Lint was run separately through `tools/lint.py`.

## Not yet verified on a headset

No usable headset, emulator or ADB connection is available here. Successful compilation and static checks do not establish headset performance, document-provider behavior or input compatibility.

Before relying on the app for important files, test the following on each intended headset with disposable files:

1. Install and launch from the headset app library. Check the default window, resize it, and verify pointer hover, focus, click, hold, scroll and keyboard entry.
2. Grant shared-storage access, then separately connect a folder through the system picker. Relaunch and verify that the folder grant persists. If the system picker is missing, test the documented storage grant using ADB.
3. Browse a large directory; switch grid/list views; search; sort; toggle hidden files. Confirm the UI remains responsive.
4. Copy and move a folder with nested files. Repeat with a conflicting destination name. Compare source and destination contents.
5. Cancel a large copy and verify source retention and partial-output cleanup. Repeat across a provider or USB drive if supported.
6. Rename and delete disposable files and folders; verify confirmation and conflict handling.
7. Preview an image and text file; open a video/document with another installed app.
8. Open a standalone APK from shared storage and from a chosen folder. Grant install permission, cancel an install, complete an install/update, and use **Open app**. Verify both standard Android launcher apps and Quest VR launcher apps. Test an invalid APK, a newer Android requirement, an absent installer and an APK with no launchable activity.
9. Confirm that unavailable folders, revoked access, a disconnected USB device and insufficient storage produce useful errors.

The checksum for the delivered APK is in `dist/SHA256SUMS`. The signing key remains local and is excluded from the source archive.
