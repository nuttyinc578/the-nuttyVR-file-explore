#!/usr/bin/env python3
"""Run the cached Android Lint CLI against a standalone SDK project descriptor."""
from pathlib import Path
import os
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
os.chdir(root)
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
jars = list(cache.rglob('*.jar'))
if not list(cache.glob('com.android.tools.lint/lint/*/*/*.jar')):
    raise SystemExit('Android Lint is not cached. Run gradle :app:lintRelease on a configured Android workstation.')
project = root / 'build/lint-project'
project.mkdir(parents=True, exist_ok=True)
shutil.copyfile(root / 'build/sdk/AndroidManifest.xml', project / 'AndroidManifest.xml')
(project / 'project.properties').write_text('target=android-35\n')
java = Path(os.environ.get('JAVA_HOME', str(Path.home() / 'dev-tools/java-22'))) / 'bin/java'
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
command = [str(java), '-Xmx768m', '-cp', os.pathsep.join(map(str, jars)), 'com.android.tools.lint.Main',
    '--offline', '--exitcode', '--sdk-home', str(sdk), '--compile-sdk-version', '35', '--java-language-level', '1.8',
    '--sources', str(root / 'app/src/main/java'), '--resources', str(root / 'app/src/main/res'),
    '--classpath', str(root / 'build/sdk/classes'), '--xml', str(root / 'build/lint-results.xml'),
    '--text', str(root / 'build/lint-results.txt'), str(project)]
result = subprocess.run(command)
print((root / 'build/lint-results.txt').read_text())
raise SystemExit(result.returncode)
