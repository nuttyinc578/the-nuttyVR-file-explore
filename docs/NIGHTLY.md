# Nightly APK downloads

The permanent download address is:

https://github.com/nuttyinc578/the-nuttyVR-file-explore/releases/download/nightly/NuttyVR-File-Explorer-nightly.apk

The permanent link serves the latest published nightly. Failed checks do not replace it.
The **Publish verified APK** workflow verifies the included, locally signed APK against its checksum, original release signer, package/version and app-source fingerprint, then publishes it. It can update the existing nightly without uploading the private key. It refuses to move the nightly source backwards or onto a different branch.

The workflow builds changes pushed to `main`, runs daily at **08:23 UTC**, and supports **Actions → Nightly APK → Run workflow**. GitHub may delay scheduled jobs. It tests the app, builds and verifies the APK, uploads a SHA-256 checksum, and updates the `nightly` prerelease. The release notes identify the source commit and build run. The source archive attached by GitHub follows the rolling nightly tag.

## One-time setup

1. Put this app's source and the included workflows in the repository's `main` branch, preserving the existing license. **Publish verified APK** publishes the checked-in signed snapshot when `prebuilt/nightly` changes.
2. In **Settings → Secrets and variables → Actions**, create `NUTTYVR_KEYSTORE_BASE64` and `NUTTYVR_STORE_PASSWORD`. Use the existing local `signing/nuttyvr.jks` key and its password so nightlies can update the APKs already built for this project. The key alias is `nuttyvr`.
3. Enable GitHub Actions if necessary, then run **Nightly APK** once from the Actions page. Until both secrets are configured, its signing check reports the missing setup and skips building; the first download remains available.
4. Check the workflow result and test the download link. Install and test on a headset.

Private signing files are excluded from the source package and must not be committed. The workflow restores the key in the runner's temporary directory, limits the APK artifact upload to the APK and checksum, and removes the temporary key afterwards. It does not generate a new signer per build.

If using GitHub CLI from this local project directory with repository administrator access, upload the existing key directly to encrypted secrets:

```sh
base64 -w 0 signing/nuttyvr.jks | gh secret set NUTTYVR_KEYSTORE_BASE64 --repo nuttyinc578/the-nuttyVR-file-explore
gh secret set NUTTYVR_STORE_PASSWORD --repo nuttyinc578/the-nuttyVR-file-explore < signing/password
```

These commands do not print the signing material. Do not paste the private key or password into an issue, README or chat.

The normal package ID and signing certificate are retained. Nightlies use a version name such as `1.2.0-nightly.12` and the app's existing version code, so sideloaded builds with the same or lower version code can be updated without reinstalling. A build with a higher version code cannot be downgraded through the Android installer.

## Local check

The same build path can be exercised with the existing local signer:

```sh
NUTTYVR_VERSION_NAME=1.2.0-nightly.local NUTTYVR_APK_NAME=NuttyVR-File-Explorer-nightly.apk bash build.sh
```

The result is `dist/NuttyVR-File-Explorer-nightly.apk`; `dist/SHA256SUMS` covers that exact file. This does not publish anything to GitHub.

## References

- [GitHub workflow syntax and schedules](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [GitHub CLI release creation](https://cli.github.com/manual/gh_release_create)
- [GitHub CLI release asset uploads](https://cli.github.com/manual/gh_release_upload)

## Updating the signed snapshot

Run `bash test.sh` and build with the existing local release key. Copy the APK to `prebuilt/nightly/NuttyVR-File-Explorer-nightly.apk`. Update `build.json` with the APK SHA-256, `source_digest(root)` from `tools/prepare_prebuilt_nightly.py`, signer digest and the exact package version. Verify locally with `NUTTYVR_BUILD_TOOLS=34.0.0 python3 tools/prepare_prebuilt_nightly.py` before pushing. The fingerprint includes app sources, dependency pins and SDK build scripts. Neither the private key nor its password belongs in the repository.
