"""Audit private app copies across schema 3->4 without exposing song or note content."""
import argparse, hashlib, json, sqlite3, math
from pathlib import Path
from preferences_snapshot import decode,logical

p=argparse.ArgumentParser(); p.add_argument('before',type=Path); p.add_argument('after',type=Path); a=p.parse_args()
def connect(root): return sqlite3.connect((root/'databases/chord.db').resolve().as_uri()+'?mode=ro',uri=True)
b,c=connect(a.before),connect(a.after)
def rows(db,table,fields='*'): return sorted(db.execute(f'SELECT {fields} FROM {table}').fetchall())
def artwork(root,db):
    paths={r[0] for r in db.execute('SELECT artworkPath FROM arrangements WHERE artworkPath IS NOT NULL')}
    return {path:hashlib.sha256((root/'files'/path).read_bytes()).hexdigest() for path in paths}
report=dict(
    before_arrangements=b.execute('SELECT count(*) FROM arrangements').fetchone()[0],
    after_arrangements=c.execute('SELECT count(*) FROM arrangements').fetchone()[0],
    catalog_exact=rows(b,'catalog_songs')==rows(c,'catalog_songs'),
    canonical_documents_fingerprints_ids_exact=rows(b,'arrangements')==rows(c,'arrangements'),
    favorites_notes_exact=rows(b,'song_user_state','songUuid,favorite,notes')==rows(c,'song_user_state','songUuid,favorite,notes'),
    arrangement_offsets_exact=rows(b,'arrangement_user_state')==rows(c,'arrangement_user_state'),
    setlist_names_ids_exact=rows(b,'setlists','id,name')==rows(c,'setlists','id,name'),
    setlist_references_order_exact=rows(b,'setlist_entries','setlistId,songId,position')==rows(c,'setlist_entries','setlistId,songId,position'),
    managed_artwork_exact=artwork(a.before,b)==artwork(a.after,c),
    schema_version=c.execute('PRAGMA user_version').fetchone()[0],
    integrity_check=c.execute('PRAGMA integrity_check').fetchone()[0],
    foreign_key_violations=len(c.execute('PRAGMA foreign_key_check').fetchall()),
    index_rows=c.execute('SELECT count(*) FROM library_index').fetchone()[0],
    fts_rows=c.execute('SELECT count(*) FROM library_grams').fetchone()[0],
    recent_timestamps_exact=rows(b,'song_user_state','songUuid,lastOpened')==rows(c,'song_user_state','songUuid,lastOpened'),
)
before_preferences=logical(decode(a.before/'files/datastore/preferences.preferences_pb'))
after_preferences=logical(decode(a.after/'files/datastore/preferences.preferences_pb'))
report['preferences_restored_exact']=all(math.isclose(value,after_preferences[key],abs_tol=1e-5) if isinstance(value,float) else value==after_preferences[key] for key,value in before_preferences.items())
if 'entryId' in {r[1] for r in b.execute('PRAGMA table_info(setlist_entries)')}:
    report['legacy_setlist_occurrence_details_exact']=rows(b,'setlist_entries')==rows(c,'setlist_entries')
    report['legacy_setlist_full_records_exact']=rows(b,'setlists')==rows(c,'setlists')
print(json.dumps(report,indent=2))
assert report['before_arrangements']==report['after_arrangements']
assert all(v for k,v in report.items() if k.endswith('_exact'))
assert report['integrity_check']=='ok' and report['foreign_key_violations']==0 and report['schema_version']==4
