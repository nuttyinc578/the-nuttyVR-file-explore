#!/usr/bin/env python3
"""Verify the locally signed snapshot before publishing its public download."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

def source_digest(root):
    paths = sorted((root / 'app/src').rglob('*')) + [root / 'app/build.gradle', root / 'app/libs/dependencies.json', root / 'tools/build_apk.py', root / 'tools/fetch_dependencies.py']
    sources = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
               for p in paths if p.is_file()}
    return hashlib.sha256(json.dumps(sources, sort_keys=True, separators=(',', ':')).encode()).hexdigest()

def main():
    root = Path(__file__).resolve().parent.parent
    folder = root / 'prebuilt/nightly'
    record = json.loads((folder / 'build.json').read_text())
    apk = folder / 'NuttyVR-File-Explorer-nightly.apk'
    if source_digest(root) != record['sourceSha256']:
        raise SystemExit('The signed APK does not match this app source. Use the Nightly APK workflow with the configured signing secrets.')
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if digest != record['apkSha256']:
        raise SystemExit('The signed APK checksum is invalid. Nothing has been published.')
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
    signer = sdk / 'build-tools' / os.environ.get('NUTTYVR_BUILD_TOOLS', '35.0.0') / 'apksigner'
    result = subprocess.run([str(signer), 'verify', '--print-certs', '--min-sdk-version', '24', str(apk)],
                            check=True, capture_output=True, text=True)
    certificate = re.search(r'Signer #1 certificate SHA-256 digest: (\S+)', result.stdout)
    original_signer = 'fc77cd97c2bcd052c8dc59e790b1ab11b094d783ca5584f990d355737e879cc0'
    if not certificate or certificate.group(1) != record['signerSha256'] or certificate.group(1) != original_signer:
        raise SystemExit('The signed APK signing certificate is invalid.')
    aapt = signer.parent / 'aapt2'
    manifest = subprocess.run([str(aapt), 'dump', 'badging', str(apk)], check=True, capture_output=True, text=True).stdout
    version = re.search(r"package: name='com.nuttyvr.explorer' versionCode='([0-9]+)' versionName='([^']+)'", manifest)
    if not version or int(version.group(1)) != record['versionCode'] or version.group(2) != record['versionName']:
        raise SystemExit('The APK package/version does not match its verification record.')
    dist = root / 'dist'
    dist.mkdir(exist_ok=True)
    shutil.copyfile(apk, dist / apk.name)
    (dist / 'SHA256SUMS').write_text(f'{digest}  {apk.name}\n')
    print('Signed nightly matches the checked-in app source and verified release signer.')

if __name__ == '__main__':
    main()
