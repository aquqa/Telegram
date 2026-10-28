"""Verify the built APK and collect only public build artifacts."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('build_type', choices=['debug', 'release'])
parser.add_argument('--signing-certificate', type=Path)
args = parser.parse_args()


def check(condition, message):
    if not condition:
        raise SystemExit(message)


tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '36.0.0'
args.output.mkdir(parents=True, exist_ok=True)
output_apk = args.output / f'whisper-{args.build_type}-arm64.apk'
shutil.copyfile(args.apk, output_apk)
signing = subprocess.check_output(
    [str(tools / 'apksigner'), 'verify', '--print-certs', str(output_apk)], text=True
)
(args.output / 'signing-info.txt').write_text(signing)
badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', str(output_apk)], text=True)
(args.output / 'apk-info.txt').write_text(badging)
manifest = subprocess.check_output(
    [str(tools / 'aapt2'), 'dump', 'xmltree', str(output_apk), '--file', 'AndroidManifest.xml'], text=True
)
(args.output / 'manifest-tree.txt').write_text(manifest)
properties = Path('gradle.properties').read_text()
package = re.search(r'^APP_PACKAGE=(.+)$', properties, re.MULTILINE).group(1).strip()
check(f"package: name='{package}'" in badging, 'Unexpected APK package')
check("application-label:'whisper'" in badging, 'Unexpected app name')
check(('application-debuggable' in badging) == (args.build_type == 'debug'), 'Unexpected debug flag')
if args.build_type == 'release':
    check(not re.search(r':testOnly\([^\n]*\)=(?:true|\(type 0x12\)0xffffffff)', manifest),
          'Release APK must not be test-only')
    check(args.signing_certificate is not None, 'Release signing certificate is required')
    expected = hashlib.sha256(args.signing_certificate.read_bytes()).hexdigest()
    digest = re.search(r'^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)$', signing, re.MULTILINE)
    check(digest is not None and digest.group(1) == expected, 'Release signing certificate mismatch')
with zipfile.ZipFile(output_apk) as apk:
    names = apk.namelist()
    abis = {n.split('/')[1] for n in names if n.startswith('lib/') and n.endswith('.so')}
    check(abis == {'arm64-v8a'}, f'Unexpected native architectures: {abis}')
    check('lib/arm64-v8a/libtmessages.49.so' in names, 'Missing main native library')
    # Release resource optimization can shorten paths; follow the packaged icon.
    icon = re.search(r"^application: .*\bicon='([^']+)'", badging, re.MULTILINE)
    check(icon is not None and icon.group(1) in names, 'Missing application icon')
digest = hashlib.sha256(output_apk.read_bytes()).hexdigest()
(args.output / 'SHA256SUMS.txt').write_text(f'{digest}  {output_apk.name}\n')
print(f'Verified {output_apk.name}: package, build type, signature, ARM64 libraries and icon.')
