#!/usr/bin/env python3
"""Check the upstream F-Droid listing without requiring Fastlane or a build SDK."""
from pathlib import Path
import hashlib
import re
import ssl
import struct
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LISTING = ROOT / 'fastlane/metadata/android/en-US'


def text(name):
    value = (LISTING / name).read_text(encoding='utf-8').strip()
    assert value, f'Empty listing field: {name}'
    return value


def png_size(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', f'Not a PNG: {path}'
    return struct.unpack('>II', data[16:24])


def main():
    build = (ROOT / 'android/app/build.gradle.kts').read_text()
    code = re.search(r'versionCode\s*=\s*(\d+)', build)[1]
    app_name = ET.parse(ROOT / 'android/app/src/main/res/values/strings.xml').find("string[@name='app_name']").text
    assert text('title.txt') == app_name, 'Store title must match the app'
    summary = text('short_description.txt')
    assert len(summary) <= 80 and not summary.endswith('.'), 'Summary must be <=80 characters, without a trailing dot'
    assert len(text('full_description.txt')) <= 4000, 'Description is too long'
    assert len(text(f'changelogs/{code}.txt')) <= 500, 'Changelog must be <=500 characters'
    assert png_size(LISTING / 'images/icon.png') == (512, 512), 'Store icon must be 512x512'
    screenshots = list((LISTING / 'images/phoneScreenshots').glob('*.png'))
    assert len(screenshots) >= 2, 'Include at least two current screenshots'
    for image in screenshots:
        assert all(320 <= n <= 3840 for n in png_size(image)), f'Invalid screenshot dimensions: {image}'
    manifest = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml')
    key = '{http://schemas.android.com/apk/res/android}name'
    permissions = {p.get(key) for p in manifest.findall('uses-permission')}
    assert not permissions.intersection({
        'android.permission.INTERNET', 'android.permission.RECORD_AUDIO',
        'com.android.vending.BILLING', 'com.google.android.gms.permission.AD_ID',
    }), 'Privacy claims must be reviewed if network, microphone, billing, or advertising permissions are added'
    for path in [ROOT / 'README.md', *ROOT.glob('android/app/src/main/**/*.kt'), *ROOT.glob('android/app/src/main/**/*.xml')]:
        assert 'JaredTweed/granular-volume' not in path.read_text(), f'Old fork URL in {path}'
    recipe = (ROOT / 'fdroid/metadata/dev.jaredtweed.quietnight.yml').read_text()
    certificate = (ROOT / 'fdroid/release-signing-certificate.pem').read_text()
    fingerprint = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate)).hexdigest()
    assert re.search(r'^AllowedAPKSigningKeys:(?: ' + fingerprint + r'|\n  - ' + fingerprint + r')$',
                     recipe, re.MULTILINE), 'F-Droid must pin the public release certificate'
    reference_url = ('https://github.com/JaredTweed/QuietNight/releases/download/'
                     'quietnight-v%v/QuietNight-%v.apk')
    assert re.search(r'^Binaries:\s+' + re.escape(reference_url) + r'$', recipe,
                     re.MULTILINE), 'Signed reference APK URL is missing'
    assert re.search(r'^AutoUpdateMode: Version$', recipe, re.MULTILINE), 'Tag updates require plain Version mode'
    print(f'F-Droid listing OK: {app_name}, version code {code}, {len(screenshots)} screenshots')


if __name__ == '__main__':
    main()
