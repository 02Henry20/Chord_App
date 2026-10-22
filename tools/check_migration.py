"""Compare private pre/post-upgrade SQLite copies without exposing song contents in evidence."""
import argparse
import json
import sqlite3
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("--before", type=Path, required=True)
p.add_argument("--after", type=Path, required=True)
p.add_argument("--report", type=Path, required=True)
args = p.parse_args()
old = sqlite3.connect(args.before)
new = sqlite3.connect(args.after)
before = old.execute('SELECT id,title,artist,"key",body,favorite,notes,transpose,capo,lastOpened,source FROM songs ORDER BY id').fetchall()
after = new.execute('''SELECT a.id,c.title,c.artist,COALESCE(a.originalKey,''),a.body,u.favorite,u.notes,p.transpose,p.capo,u.lastOpened,a.source
FROM arrangements a JOIN catalog_songs c ON c.songUuid=a.songUuid
JOIN song_user_state u ON u.songUuid=a.songUuid JOIN arrangement_user_state p ON p.arrangementId=a.id ORDER BY a.id''').fetchall()
preserved = {r[0]: r for r in after}
song_ok = all(preserved.get(r[0]) == r for r in before)
sets_ok = old.execute('SELECT * FROM setlists ORDER BY id').fetchall() == new.execute('SELECT * FROM setlists ORDER BY id').fetchall()
entries_ok = old.execute('SELECT * FROM setlist_entries ORDER BY setlistId,songId').fetchall() == new.execute('SELECT * FROM setlist_entries ORDER BY setlistId,songId').fetchall()
docs = [json.loads(r[0]) for r in new.execute('SELECT documentJson FROM arrangements')]
report = dict(databaseVersion=new.execute('PRAGMA user_version').fetchone()[0], legacyArrangements=len(before), currentArrangements=len(after),
              allLegacyFieldsPreserved=song_ok, setlistsPreserved=sets_ok, setlistOrderAndReferencesPreserved=entries_ok,
              allCanonicalSchemasVersion1=all(d['schemaVersion'] == 1 for d in docs), foreignKeyViolations=len(new.execute('PRAGMA foreign_key_check').fetchall()))
args.report.parent.mkdir(parents=True, exist_ok=True)
args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
assert song_ok and sets_ok and entries_ok and report['foreignKeyViolations'] == 0
