#!/usr/bin/env python3
"""Publish a verified artifact to the rolling nightly release without moving it backwards."""
import hashlib
import json
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
snapshot = os.environ.get('NUTTYVR_VERIFIED_SNAPSHOT') == '1'
description = ('Locally signed NuttyVR APK, with source fingerprint, signing certificate and archive tests verified in GitHub Actions.'
               if snapshot else 'Latest successful automated build of NuttyVR File Explorer.')
notes.write_text(f'''{description}

Source commit: `{commit}`  
[Build and test results]({run_url})

Download **NuttyVR-File-Explorer-nightly.apk** and install it over your existing NuttyVR APK. The package and release signer are unchanged.

Includes APK installation and app launching, plus **ZIP, TAR, TAR.GZ and RAR4/RAR5 extraction**. Tap an archive and choose **Extract** to create a new folder beside it. Extraction supports progress and cancellation and keeps existing files.

Password protected archives, multipart RAR, archive links and sparse TAR files are not supported. Extraction is limited to 8 GiB of file data, 100,000 entries and a 64 MiB RAR dictionary; chosen-folder archive snapshots are limited to 2 GiB.

This is a development build. Physical headset and document-provider testing is still required.
''')

def gh(*args, **kwargs):
    return subprocess.run(['gh', *args], check=True, text=True, **kwargs)

# A failed API request must not be mistaken for an absent release.
result = gh('api', f'repos/{repository}/releases', '--paginate', '--jq', '.[] | select(.tag_name == "nightly") | .id', capture_output=True)
if result.stdout.strip():
    comparison = json.loads(gh('api', f'repos/{repository}/compare/nightly...{commit}', capture_output=True).stdout)
    if comparison['status'] not in ('ahead', 'identical'):
        raise SystemExit('Keeping the current nightly: this workflow would move its source backwards or onto another branch.')
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
