"""Generate original, deterministic offline import fixtures using only Python's standard library."""
import json
import struct
import uuid
import zlib
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1] / 'docs' / 'demo-packs'
ROOT.mkdir(parents=True, exist_ok=True)

def png():
    width, height = 400, 300
    pixels = b''.join(b'\0' + b''.join(bytes((30 + x * 70 // width, 70 + y * 90 // height, 80 + (x + y) * 80 // (width + height))) for x in range(width)) for y in range(height))
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')

def document(index, prefix='Pack'):
    name = f'{prefix} original {index:03d}'
    return dict(schemaVersion=1, songId=str(uuid.uuid5(uuid.NAMESPACE_URL, f'chordroom/demo/{prefix}/{index}')), title=name,
                artist='Chordroom Originals', album='Local Sessions', year=2026, language='en', genre='acoustic', tags=['demo', 'original'],
                artwork={'path': 'assets/cover.png', 'credit': 'Generated original gradient · Chordroom'},
                musicLinks={'spotifyTrackUrl': None, 'youtubeVideoUrl': None}, arrangements=[dict(
                    arrangementId=str(uuid.uuid5(uuid.NAMESPACE_URL, f'chordroom/demo/{prefix}/{index}/main')), name='Acoustic', originalKey='G', tonalMode='major', tempoBpm=96, timeSignature='4/4',
                    sourceId=f'chordroom-original:{prefix}:{index}', sourceFormat='json', provenance='Original test pack', licenseNotes='Original demonstration material, free to use',
                    sections=[dict(id=f'{prefix}-{index}-v1', type='verse', label='Verse 1', lines=[
                        {'segments': [{'text': 'Under the '}, {'chord': 'G', 'text': 'evening lights, '}, {'chord': 'D/F#', 'text': 'we sing'}]},
                        {'segments': [{'chord': 'Em7', 'text': 'é 👩🏽‍🎤 東京  '}, {'chord': 'C', 'text': 'stay here\u00a0'}]}, {'segments': []},
                        {'segments': [{'chord': 'G', 'text': ''}, {'chord': 'D', 'text': ''}]}]),
                              dict(id=f'{prefix}-{index}-c1', type='chorus', label='Chorus', lines=[{'segments': [{'chord': 'G', 'text': 'Stay here '}, {'chord': 'D', 'text': 'with me'}]}])])])

cover = png()
folder = ROOT / 'ChordroomFolder'
(folder / 'songs' / 'Evening' / 'assets').mkdir(parents=True, exist_ok=True)
(folder / 'nested' / 'session').mkdir(parents=True, exist_ok=True)
(folder / 'songs' / 'Evening' / 'song.json').write_text(json.dumps(document(1, 'Folder'), ensure_ascii=False, indent=2), encoding='utf-8')
(folder / 'songs' / 'Evening' / 'assets' / 'cover.png').write_bytes(cover)
(folder / 'nested' / 'session' / 'bridge.pro').write_text('{title: Folder bridge original}\n{artist: Chordroom Originals}\n{key: F#m}\n{tempo: 88}\n{time: 6/8}\n{start_of_intro}\n[F#m][D]\n{end_of_intro}\n{start_of_bridge: Bridge}\n[F#m]Quiet roads [D]lead us home  \n', encoding='utf-8')
(ROOT / 'Evening-single.json').write_text(json.dumps(document(2, 'Files'), ensure_ascii=False, indent=2), encoding='utf-8')
(ROOT / 'River-single.cho').write_text('{title: River file original}\n{artist: Chordroom Originals}\n{key: Bb}\n{start_of_verse: Verse 1}\n[Bb]Paper boats [F]sail away  \n\n{start_of_chorus: Chorus}\n[Eb]Carry the [Bb]day\n', encoding='utf-8')
(ROOT / 'Review-positions.txt').write_text('C     G\nHello world\n\nAm    F\nQuiet roads', encoding='utf-8')
(ROOT / 'assets').mkdir(exist_ok=True)
(ROOT / 'assets' / 'cover.png').write_bytes(cover)

with ZipFile(ROOT / 'Chordroom-320.zip', 'w', ZIP_DEFLATED) as pack:
    for i in range(320):
        pack.writestr(f'songs/Track{i:03d}/song.json', json.dumps(document(i), ensure_ascii=False))
    pack.writestr('assets/cover.png', cover)
    pack.writestr('errors/unsupported-future.json', '{"schemaVersion":99}')
    pack.writestr('errors/malformed-utf8.cho', bytes([0xc3, 0x28]))

with ZipFile(ROOT / 'Traversal-rejected.zip', 'w', ZIP_DEFLATED) as pack:
    pack.writestr('../escape.json', json.dumps(document(999, 'Unsafe')))

print(f'Generated 320-song ZIP, recursive folder, file pair, review TXT and traversal fixture in {ROOT}')
