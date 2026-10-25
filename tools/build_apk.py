#!/usr/bin/env python3
"""Android SDK build without a Gradle daemon; archive libraries are pinned."""
import hashlib
from fetch_dependencies import ensure
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
JAVA = Path(os.environ.get('JAVA_HOME', str(Path.home() / 'dev-tools/java-22')))
BT = SDK / 'build-tools' / os.environ.get('NUTTYVR_BUILD_TOOLS', '34.0.0')
PLATFORM = SDK / 'platforms/android-35/android.jar'
OUT = ROOT / 'build/sdk'
DIST = ROOT / 'dist'
VERSION_NAME = os.environ.get('NUTTYVR_VERSION_NAME', '1.2.0')
VERSION_CODE = int(os.environ.get('NUTTYVR_VERSION_CODE', '3'))
APK_NAME = os.environ.get('NUTTYVR_APK_NAME', f'NuttyVR-File-Explorer-{VERSION_NAME}.apk')
KEYSTORE = Path(os.environ.get('NUTTYVR_KEYSTORE', str(ROOT / 'signing/nuttyvr.jks')))
KEY_ALIAS = os.environ.get('NUTTYVR_KEY_ALIAS', 'nuttyvr')
if VERSION_CODE < 1 or VERSION_CODE > 2100000000:
    raise SystemExit('NUTTYVR_VERSION_CODE must be between 1 and 2100000000.')
if Path(APK_NAME).name != APK_NAME or not APK_NAME.endswith('.apk'):
    raise SystemExit('NUTTYVR_APK_NAME must be an APK filename without directories.')
env = dict(os.environ, JAVA_HOME=str(JAVA))
env['PATH'] = str(JAVA / 'bin') + os.pathsep + env.get('PATH', '')
if 'NUTTYVR_STORE_PASSWORD' not in env and (ROOT / 'signing/password').exists():
    env['NUTTYVR_STORE_PASSWORD'] = (ROOT / 'signing/password').read_text().strip()
if not KEYSTORE.is_file() or not env.get('NUTTYVR_STORE_PASSWORD'):
    raise SystemExit('Release signing key is required. See README.md for setup.')

def run(*args):
    subprocess.run([str(a) for a in args], env=env, check=True)

if OUT.exists():
    shutil.rmtree(OUT)
for folder in [OUT / 'generated', OUT / 'classes', OUT / 'dex', DIST]:
    folder.mkdir(parents=True, exist_ok=True)

libraries = ensure()

print('Compiling Android resources…', flush=True)
run(BT / 'aapt2', 'compile', '--dir', ROOT / 'app/src/main/res', '-o', OUT / 'resources.zip')
android = 'http://schemas.android.com/apk/res/android'
tools = 'http://schemas.android.com/tools'
ET.register_namespace('android', android)
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
manifest.getroot().set('package', 'com.nuttyvr.explorer')
manifest.getroot().set(f'{{{android}}}versionCode', str(VERSION_CODE))
manifest.getroot().set(f'{{{android}}}versionName', VERSION_NAME)
manifest.getroot().insert(0, ET.Element('uses-sdk', {f'{{{android}}}minSdkVersion': '29', f'{{{android}}}targetSdkVersion': '34'}))
for node in manifest.iter():
    for key in list(node.attrib):
        if key.startswith('{' + tools + '}'):
            del node.attrib[key]
manifest.write(OUT / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
run(BT / 'aapt2', 'link', '-o', OUT / 'resources.apk', '--manifest', OUT / 'AndroidManifest.xml',
    '-I', PLATFORM, '--java', OUT / 'generated', '--min-sdk-version', '29', '--target-sdk-version', '34',
    '--version-code', str(VERSION_CODE), '--version-name', VERSION_NAME, OUT / 'resources.zip', '-A', ROOT / 'app/src/main/assets')

print('Compiling native Android app…', flush=True)
sources = sorted((ROOT / 'app/src/main/java').rglob('*.java')) + sorted((OUT / 'generated').rglob('*.java'))
run(JAVA / 'bin/javac', '-g', '-encoding', 'UTF-8', '--release', '8', '-classpath', os.pathsep.join(map(str, [PLATFORM] + libraries)), '-d', OUT / 'classes', *sources)
run(JAVA / 'bin/jar', 'cf', OUT / 'classes.jar', '-C', OUT / 'classes', '.')
d8_args = ['--release', '--min-api', '29', '--lib', PLATFORM, '--output', OUT / 'dex', OUT / 'classes.jar', *libraries]
# AGP's D8 handles the class metadata emitted by recent javac versions; Build Tools 34's older D8 does not.
cached_d8 = sorted((Path.home() / '.gradle/caches/modules-2/files-2.1/com.android.tools.build/builder/8.7.3').glob('*/*.jar'))
d8_jar = os.environ.get('NUTTYVR_D8_JAR') or (str(cached_d8[0]) if cached_d8 else None)
if d8_jar:
    run(JAVA / 'bin/java', '-cp', d8_jar, 'com.android.tools.r8.D8', *d8_args)
else:
    run(BT / 'd8', *d8_args)

shutil.copyfile(OUT / 'resources.apk', OUT / 'unsigned.apk')
with zipfile.ZipFile(OUT / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as archive:
    for dex in sorted((OUT / 'dex').glob('*.dex')):
        archive.write(dex, dex.name)
print('Aligning and signing release APK…', flush=True)
run(BT / 'zipalign', '-f', '4', OUT / 'unsigned.apk', OUT / 'aligned.apk')
apk = DIST / APK_NAME
run(BT / 'apksigner', 'sign', '--ks', KEYSTORE, '--ks-key-alias', KEY_ALIAS,
    '--ks-pass', 'env:NUTTYVR_STORE_PASSWORD', '--key-pass', 'env:NUTTYVR_STORE_PASSWORD',
    '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
    '--out', apk, OUT / 'aligned.apk')
run(BT / 'apksigner', 'verify', '--verbose', '--min-sdk-version', '24', apk)
run(BT / 'zipalign', '-c', '4', apk)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
(DIST / 'SHA256SUMS').write_text(f'{digest}  {apk.name}\n')
print(f'Built {apk.name}: {apk.stat().st_size:,} bytes')
