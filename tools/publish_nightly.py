#!/usr/bin/env python3
"""Publish a verified artifact to the one rolling 'nightly' prerelease."""
import hashlib
import os
from pathlib import Path
import re
import subprocess

repository = os.environ.get('GH_REPO', '')
commit = os.environ.get('NUTTYVR_COMMIT', '')
run_url = os.environ.get('NUTTYVR_RUN_URL', '')
if repository != 'nuttyinc578/the-nuttyVR-file-explore' or not re.fullmatch(r'[0-9a-f]{40}', commit):
    raise SystemExit('Publish requires the intended repository and a full source commit SHA.')
if not run_url.startswith(f'https://github.com/{repository}/actions/runs/'):
    raise SystemExit('Publish requires the GitHub Actions run URL.')
root = Path(__file__).resolve().parent.parent
apk = root / 'dist/NuttyVR-File-Explorer-nightly.apk'
checksum = root / 'dist/SHA256SUMS'
expected = f'{hashlib.sha256(apk.read_bytes()).hexdigest()}  {apk.name}\n'
if checksum.read_text() != expected:
    raise SystemExit('The APK checksum does not match. Nothing has been published.')
notes = root / 'dist/nightly-notes.md'
initial = os.environ.get('NUTTYVR_INITIAL_ONLY') == '1'
description = 'Initial nightly APK built locally and verified against this source in GitHub Actions.' if initial else 'Latest successful automated build of NuttyVR File Explorer.'
notes.write_text(f'''{description}

Source commit: `{commit}`  
[Build and test results]({run_url})

Download **NuttyVR-File-Explorer-nightly.apk**, install it on your headset, and grant storage access if prompted.

Includes APK installation and app launching. This is a development build; headset testing is still required.
''')

def gh(*args, **kwargs):
    return subprocess.run(['gh', *args], check=True, text=True, **kwargs)

# A full release listing distinguishes an absent nightly from an authentication or API failure.
result = gh('api', f'repos/{repository}/releases', '--paginate', '--jq', '.[] | select(.tag_name == "nightly") | .id', capture_output=True)
if result.stdout.strip():
    if initial:
        print('The nightly release already exists; keeping its current APK.')
        raise SystemExit(0)
    gh('release', 'upload', 'nightly', str(apk), str(checksum), '--clobber', '--repo', repository)
    gh('api', '--method', 'PATCH', f'repos/{repository}/git/refs/tags/nightly', '-f', f'sha={commit}', '-F', 'force=true')
    gh('release', 'edit', 'nightly', '--title', 'Nightly APK', '--notes-file', str(notes), '--prerelease', '--latest=false', '--repo', repository)
else:
    gh('release', 'create', 'nightly', str(apk), str(checksum), '--target', commit,
       '--title', 'Nightly APK', '--notes-file', str(notes), '--prerelease', '--latest=false', '--repo', repository)
url = f'https://github.com/{repository}/releases/download/nightly/{apk.name}'
print(url)
summary = os.environ.get('GITHUB_STEP_SUMMARY')
if summary:
    with open(summary, 'a') as stream:
        stream.write(f'[Download nightly APK]({url})\n\n[Source commit](https://github.com/{repository}/commit/{commit})\n')
