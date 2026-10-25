# Release validation — 1.2.0

Performed in the build workspace on 2026-10-25.

## Automated checks

- Host suites: **71 passed** (22 file-operation tests, 15 APK snapshot tests and 34 archive tests).
- Android Java/resource compilation and D8 conversion: **passed**.
- Clean Gradle Java/resource compilation: **passed**, starting in a temporary checkout without archive JARs. The dependency task downloads and verifies the pinned libraries before compilation.
- Android Lint: **0 errors**. Two advisory warnings remain: `ScopedStorage` for the file manager's broad shared-storage permission, and `OldTargetApi` for the intentional API 34 target used for Quest compatibility. The app also offers Android's folder picker.
- APK alignment: **passed**.
- APK cryptographic signature verification: **passed**, v2 and v3 signatures.
- Packaged manifest: package `com.nuttyvr.explorer`, version `1.2.0` (version code 3), minimum SDK 29, target SDK 34, exported launcher activity, resizable window, optional touchscreen and head tracking, Quest 2/Pro/3/3S device metadata.
- APK-specific install permission and private APK activity; launch visibility covers Android, TV and Quest VR launcher activities. No broad installed-package query permission is requested.
- Update uses the same signing certificate as 1.0.0 and 1.1.0.
- ZIP, TAR/PAX/GZIP, RAR4/RAR5 and solid archive extraction tested with exact file contents and checksums. Host tests cover unsafe paths, links, duplicate names, output conflicts, corrupt/truncated archives, resource limits and cancellation.
- APK contains third-party license/notice assets; archive library SHA-256 hashes are verified before building.
- Nightly publication guard checked with five mocked GitHub CLI scenarios: initial creation, forward update, same-source update, stale source and diverged source. Stale/diverged source scenarios issue no publication commands.
- No internet permission and no CPU-specific native libraries.

The delivered release was assembled and signed with the Android SDK tools through `build.sh`; Android Lint was run separately through `tools/lint.py`. A separate clean Gradle compile verified the Android Studio build configuration without using the private signing key.

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
9. Extract ZIP, TAR, TAR.GZ, RAR4, RAR5 and solid RAR samples from shared storage and from a chosen folder/USB provider. Verify nested file bytes, output name collisions, cancellation cleanup, read-only destinations, corrupt archives and free-space errors. Confirm encrypted or multipart archives report their limitation.
10. Confirm that unavailable folders, revoked access, a disconnected USB device and insufficient storage produce useful errors.

The checksum for the delivered APK is in `dist/SHA256SUMS`. The signing key remains local and is excluded from the source archive.
