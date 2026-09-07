"""Collect resolved runtime POM licenses and embedded notices; no network access.

First run Gradle :app:exportRuntimeInventory. Paths remain in ignored build output.
"""
from pathlib import Path
from zipfile import ZipFile, BadZipFile
import io
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
inventory = json.loads((ROOT / 'apk/android/app/build/reports/runtime-inventory.json').read_text())
output = ['Runtime dependencies and upstream embedded notices',
          'Generated from releaseRuntimeClasspath. See THIRD_PARTY_NOTICES.md for native upstream notices.', '']

def embedded_notices(data, label):
    try:
        with ZipFile(data) as archive:
            for name in sorted(archive.namelist()):
                leaf = name.rsplit('/', 1)[-1].lower()
                if leaf.startswith(('license', 'notice', 'copyright')) and not name.endswith('/'):
                    if archive.getinfo(name).file_size < 2_000_000:
                        output.extend([f'--- {label}: {name} ---', archive.read(name).decode('utf-8', errors='replace'), ''])
                elif name == 'classes.jar':
                    embedded_notices(io.BytesIO(archive.read(name)), label + '/classes.jar')
    except BadZipFile:
        pass

missing = []
for artifact in inventory:
    coordinate = artifact['coordinate']
    path = Path(artifact['file'])
    output.extend(['=' * 72, coordinate])
    poms = list(path.parents[1].glob('*/*.pom'))
    if poms:
        pom = ET.parse(poms[0]).getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        licenses = pom.findall('m:licenses/m:license', ns)
        for license in licenses:
            output.append('License: ' + license.findtext('m:name', '', ns))
            output.append('License URL: ' + license.findtext('m:url', '', ns))
        output.append('Upstream: ' + (pom.findtext('m:scm/m:url', '', ns) or pom.findtext('m:url', '', ns)))
        if not licenses:
            missing.append(coordinate)
    elif 'capacitor' in coordinate:
        output.append('Local Capacitor module: MIT; generated Cordova bridge: Apache-2.0. See THIRD_PARTY_NOTICES.md.')
    else:
        missing.append(coordinate)
    embedded_notices(path, coordinate)

(ROOT / 'licenses').mkdir(exist_ok=True)
normalized = '\n'.join(line.rstrip() for line in '\n'.join(output).splitlines()).rstrip() + '\n'
(ROOT / 'licenses/dependency-notices.txt').write_text(normalized, encoding='utf-8', newline='\n')
print(f'{len(inventory)} resolved runtime artifacts. POM license metadata absent: {missing}')
