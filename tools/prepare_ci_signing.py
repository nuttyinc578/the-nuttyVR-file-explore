#!/usr/bin/env python3
"""Restore a CI signer from encrypted repository secrets without logging its content."""
import base64
import binascii
import os
from pathlib import Path

encoded = os.environ.get('NUTTYVR_KEYSTORE_BASE64', '')
password = os.environ.get('NUTTYVR_STORE_PASSWORD', '')
target = os.environ.get('NUTTYVR_KEYSTORE', '')
if not encoded or not password or not target:
    raise SystemExit('Configure the NUTTYVR_KEYSTORE_BASE64 and NUTTYVR_STORE_PASSWORD repository secrets before running Nightly APK. See docs/NIGHTLY.md.')
try:
    content = base64.b64decode(''.join(encoded.split()), validate=True)
except (binascii.Error, ValueError):
    raise SystemExit('The NUTTYVR_KEYSTORE_BASE64 repository secret is not valid base64.') from None
if not content:
    raise SystemExit('The signing key is empty.')
file = Path(target)
file.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
with file.open('wb') as stream:
    os.chmod(file, 0o600)
    stream.write(content)
print('Private release signer restored for this build.')
