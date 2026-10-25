#!/usr/bin/env python3
"""Fetch pinned archive libraries once; verify SHA-256 on every build/test run."""
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent.parent

def ensure():
    folder = ROOT / 'app/libs'
    records = json.loads((folder / 'dependencies.json').read_text())
    for record in records:
        path = folder / record['file']
        if not path.exists():
            if not record['url'].startswith('https://repo.maven.apache.org/maven2/'):
                raise SystemExit('Archive dependencies must come from Maven Central.')
            print(f'Downloading {path.name}…', flush=True)
            with urllib.request.urlopen(record['url'], timeout=60) as response:
                data = response.read(8 * 1024 * 1024)
            if hashlib.sha256(data).hexdigest() != record['sha256']:
                raise SystemExit(f'Dependency download checksum failed: {path.name}')
            path.write_bytes(data)
        if hashlib.sha256(path.read_bytes()).hexdigest() != record['sha256']:
            raise SystemExit(f'Archive dependency checksum failed: {path.name}')
    if {p.name for p in folder.glob('*.jar')} != {r['file'] for r in records}:
        raise SystemExit('Unexpected archive libraries in app/libs.')
    return sorted(folder.glob('*.jar'))

if __name__ == '__main__':
    ensure()
    print('Archive library checksums verified.')
