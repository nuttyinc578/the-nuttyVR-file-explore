# NuttyVR File Explorer

[Download nightly APK](https://github.com/nuttyinc578/the-nuttyVR-file-explore/releases/download/nightly/NuttyVR-File-Explorer-nightly.apk) · [Nightly release](https://github.com/nuttyinc578/the-nuttyVR-file-explore/releases/tag/nightly) · [Build status](https://github.com/nuttyinc578/the-nuttyVR-file-explore/actions/workflows/nightly.yml)

The first nightly download is published from a verified local build by **Publish initial nightly**. Once the signing secrets are configured, **Nightly APK** builds changes to `main` and runs daily at 08:23 UTC. See [nightly setup](docs/NIGHTLY.md) for setup and manual build instructions.

A native Android file manager designed for a floating, resizable window inside a VR headset. Dark navy surfaces, mint controls, readable file cards, pointer hover/focus feedback, and large action targets.

**APK:** `dist/NuttyVR-File-Explorer-1.1.0.apk`  
**Package:** `com.nuttyvr.explorer`  
**Minimum Android:** 10 / API 29 · **Target:** Android 14 / API 34

## Headsets

The manifest declares Meta Quest 2, Quest 3, Quest 3S and Quest Pro. Android XR can run this as a standard Android panel app. It uses the headset's system window and pointer input. This version provides a single floating 2D panel; it does not contain an OpenXR 3D environment or its own hand-tracking runtime.

The APK contains Java/Dex code with no native CPU-specific libraries. No account or internet connection is required, and the app has no network permission.

**Device testing is still required:** this build environment has no usable ADB connection, headset or Android emulator. Headset launch, controller/hand interaction, provider compatibility and frame rate have not been measured on hardware.

## Included

- Adaptive sidebar and location dialog, breadcrumbs, grid and list views.
- Shortcuts to shared storage, Downloads, Movies, Pictures, Music, Documents and the app workspace.
- Asynchronous directory loading, search, filtering, sorting and file operations.
- Search within the current folder; folders stay first when sorting by name, date, size or type.
- Multiple selection with visible selection controls; Select all applies to the visible results.
- Copy, move, rename, create folders, and confirmed permanent deletion.
- Existing destination files are kept; copied items receive numbered names.
- Local copies stage their output before publishing it; cancelled copies attempt to clean up partial output.
- Images preview at bounded resolution; UTF-8 text previews are limited to 128 KB.
- Tap APK files to inspect their package/version, install or update through Android, and open the installed app.
- Request the Android permission to install unknown apps when needed; installation remains user-confirmed.
- Open other files using an installed app through temporary read-only URI grants.
- Hidden-file toggle, remembered view/sort settings, and device free-space display.
- Full shared-storage access, or one remembered folder through Android's Storage Access Framework. USB folders are available when exposed by the device's document picker.

## Install

Enable developer mode and USB debugging for your headset, connect it to your computer, and accept the debugging prompt in the headset. With Android Platform Tools installed:

```sh
adb devices
adb install -r NuttyVR-File-Explorer-1.1.0.apk
adb shell am start -n com.nuttyvr.explorer/.MainActivity
```

On Quest, you can also launch **NuttyVR File Explorer** from the app library's **Unknown Sources** filter after sideloading. Sideloading tools that accept APK files can install the same APK.

On first launch, select **Connect → Allow storage**, or **Choose folder** and grant a specific location. Until storage is connected, the app can manage its own workspace.

Some headset firmware hides Android's all-files settings or folder picker. For your own USB-debugging-enabled headset, the documented Android developer command to grant this app shared-storage access is:

```sh
adb shell appops set --uid com.nuttyvr.explorer MANAGE_EXTERNAL_STORAGE allow
```

Then reopen the app. To revoke that grant:

```sh
adb shell appops set --uid com.nuttyvr.explorer MANAGE_EXTERNAL_STORAGE default
```

Android's restrictions on private app storage still apply. This app does not bypass `Android/data`, `Android/obb`, root access or device security policies. Folder and USB availability depend on headset firmware. Other file types require an installed app that supports them. APK installation uses Android’s system installer and the headset’s installation policy; the app does not bypass blocked installations.

## Install and launch APK files

1. Install this **1.1.0** update over NuttyVR 1.0.0. It uses the same package ID and signing key, so your settings are retained.
2. Browse to a standalone `.apk` file and tap it, or choose **Install / open app** from its menu.
3. NuttyVR prepares a private snapshot and displays the app name, package, version and size. APKs in a chosen folder or USB provider work through the same screen.
4. Choose **Install APK** or **Install / update APK**. If prompted, allow NuttyVR to install unknown apps and return to NuttyVR. Confirm the installation in Android’s installer.
5. Choose **Open app** once installation succeeds. If the app is already installed, **Open app** is available immediately. Quest VR launcher activities are supported as a fallback to Android launcher activities.

If you cancel, NuttyVR reports that the installation did not complete. If the headset hides its unknown-app settings, NuttyVR tries the system installer’s own permission flow. If no system installer is exposed, installation must be done from a computer. Service-only packages have no activity to open. Split bundles such as `.apks` and `.xapk` are not supported.

Installation needs temporary device storage for a private APK snapshot. A read-only, file-specific URI grant lets the system installer read it; the original file remains untouched. Snapshots are removed when you leave the APK screen after the installer has returned. A process termination can leave cache files for Android to reclaim.

## Use

1. Point and click to open folders or preview files. Use a tile's selection control, or hold the tile, to select it.
2. Choose **Copy** or **Move**, navigate to the destination and choose **Paste here**.
3. Use the item menu for rename, details, and delete. Deletion is permanent and requires confirmation.
4. The search box searches names in the current folder. The folder options menu controls hidden files and storage access.

Keep the app open during transfers. A cancelled or failed batch may have already completed some items. A move across providers copies first, then removes the source; if source removal fails, inspect both locations before retrying. Concurrent changes by other apps and interrupted document providers can affect transfers. File size is checked after copying; cryptographic content verification is not implemented. Symbolic links are excluded from local browsing and rejected during copying.

## Build

The source includes a standard Android Gradle Plugin 8.7.3 project for Android Studio and an SDK-only builder. No app dependencies need downloading.

Requirements: Python 3, JDK 17 or newer, Android SDK Platform 35 and Build Tools 34.0.0 or newer. Set `JAVA_HOME` and `ANDROID_HOME` for your machine. JDK 22 requires a recent D8; the SDK builder uses the cached AGP 8.7.3 D8 when available, or accepts `NUTTYVR_D8_JAR`.

The original release key and password remain in the local `signing/` directory, excluded from the source archive. Keep those files to sign updates that install over this APK. To create a separate signing identity for your own build:

```sh
mkdir -p signing
keytool -genkeypair -keystore signing/nuttyvr.jks -alias nuttyvr \
  -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=NuttyVR'
export NUTTYVR_STORE_PASSWORD='your-keystore-password'
```

Use the same password for the keystore and key. Then:

```sh
./test.sh
./build.sh
```

The SDK builder compiles resources with AAPT2, compiles Java, converts it to Dex with D8, aligns the APK, signs it with v2/v3 signatures, verifies the signature and alignment, and writes a SHA-256 checksum in `dist/SHA256SUMS`.

Alternatively, with Gradle 8.9 installed, the Android SDK configured, and the signing environment variable set:

```sh
gradle :app:assembleRelease :app:lintRelease
```

This environment blocks the local sockets required by the Gradle daemon. The distributed APK was therefore built with `./build.sh`. `python3 tools/lint.py` runs the cached Android Lint CLI against the same sources after an SDK build.

## Validation

`./test.sh` runs 22 file-operation tests and 15 APK snapshot tests covering byte preservation, name collisions, nested/hidden files, cancellation, staging cleanup, source size changes, move behavior, rename conflicts, path traversal, canonical boundaries, symbolic links and recursive deletion. The APK tests cover exact snapshot bytes, source changes, storage limits, cancellation, failed reads/closes, and the private URI path boundary. Android document providers, system installation, app launching and the UI require device testing.

See `VALIDATION.md` for the checks performed on this release and a short device acceptance checklist.

## Platform references

- [Meta: Android panel app setup and manifest requirements](https://developers.meta.com/vr/documentation/android-apps/create-app/)
- [Meta: panel sizing](https://developers.meta.com/vr/essentials/horizon-os-panel-sizing/)
- [Android XR: Android apps in spatial panels](https://developer.android.com/develop/xr/jetpack-xr-sdk/add-xr-to-existing)
- [Android: all-files access and the developer ADB command](https://developer.android.com/training/data-storage/manage-all-files)
- [Android: unknown-app install permission](https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html)
- [Android: package archive inspection and app launching](https://developer.android.com/reference/android/content/pm/PackageManager)
- [Android: document and folder access](https://developer.android.com/training/data-storage/shared/documents-files)
