"""Small UIAutomator/ADB inspection helper. Operates only on observed Android UI nodes."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('action', choices=['dump', 'tap', 'capture'])
p.add_argument('value', nargs='?')
p.add_argument('--adb', default=r'A:\AI\runtimes\android-sdk\platform-tools\adb.exe')
p.add_argument('--port', type=int, default=5037)
p.add_argument('--serial', default='emulator-5554')
args = p.parse_args()
def adb(*parts):
    return subprocess.check_output([args.adb, '-P', str(args.port), '-s', args.serial, *parts])
if args.action == 'capture':
    Path(args.value).write_bytes(adb('exec-out', 'screencap', '-p'))
else:
    for attempt in range(5):
        status = adb('shell', 'uiautomator', 'dump', '/data/local/tmp/chordroom-ui.xml')
        if b'dumped to' in status.lower():
            break
        time.sleep(.4)
    else:
        raise SystemExit('No fresh Android UI hierarchy; refusing to act on a stale dump.')
    root = ET.fromstring(adb('shell', 'cat', '/data/local/tmp/chordroom-ui.xml'))
    nodes = [n for n in root.iter('node') if n.get('text') or n.get('content-desc')]
    if args.action == 'dump':
        for n in nodes:
            print({k: n.get(k) for k in ['text', 'content-desc', 'resource-id', 'bounds']})
    else:
        selected = [n for n in nodes if args.value in (n.get('text'), n.get('content-desc'))]
        if not selected:
            raise SystemExit(f'No observed node matches {args.value!r}')
        n = selected[-1]
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
        print(f'Tapped {args.value!r} at observed bounds {n.get("bounds")}')
