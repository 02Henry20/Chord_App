"""Deterministic original content for real Android SAF acceptance tests."""
from pathlib import Path
from uuid import uuid5, NAMESPACE_URL
import json, zipfile, zlib, struct

root = Path(__file__).resolve().parents[1] / 'qa-artifacts' / 'picker-fixtures'
root.mkdir(parents=True, exist_ok=True)
def uid(value): return str(uuid5(NAMESPACE_URL, 'chordroom-release:' + value))
def document(title, art=False):
    d = dict(schemaVersion=1, songId=uid(title), title=title, artist='Release original fixtures', originalKey='C', tonalMode='major',
             sections=[dict(id=uid(title+'verse'), type='verse', label='Verse', lines=[dict(id=uid(title+'line'), segments=[
                 dict(id=uid(title+'seg0'),text='Un'),dict(id=uid(title+'seg1'), chord='C',text='der écho '),dict(id=uid(title+'seg2'),chord='G',text='日本')])])])
    if art: d['artwork'] = dict(path='assets/cover.png', credit='Original generated QA gradient')
    return json.dumps(d, ensure_ascii=False, indent=2).encode('utf-8')
def chunk(kind, data): return struct.pack('>I', len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
pixels = b''.join(b'\0'+b''.join(bytes((30+x//2,75+y//3,105+(x+y)//8)) for x in range(128)) for y in range(128))
png = b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',128,128,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b'')
(root/'Release-single.json').write_bytes(document('Release import single'))
(root/'Release-multiple.cho').write_text('{title: Release import multiple}\n{artist: Release original fixtures}\n{key: G}\n{start_of_bridge: Bridge}\n[G]Only original [D/F#]words\n[Em7]Écho 東京 [C]glows\n', encoding='utf-8')
(root/'Release-second.pro').write_text('{title: Release second original}\n{artist: Release original fixtures}\n{key: Am}\n{start_of_solo: Solo}\n[Am][Dm][E]\n', encoding='utf-8')
folder=root/'ReleaseFolder'
(folder/'songs'/'Nested').mkdir(parents=True,exist_ok=True)
(folder/'assets').mkdir(exist_ok=True)
(folder/'songs'/'Nested'/'song.json').write_bytes(document('Release folder original',True))
(folder/'assets'/'cover.png').write_bytes(png)
with zipfile.ZipFile(root/'Release-pack.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('songs/Title/song.json',document('Release zip original',True))
    z.writestr('assets/cover.png',png)
    z.writestr('malformed.json','{broken')
with zipfile.ZipFile(root/'Release-unsafe.zip','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('../escape.cho','{title: Not allowed}\n[C]Unsafe')
print(root)
