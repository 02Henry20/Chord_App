"""Compare private BEFORE/AFTER app-data copies; emit no song or lyric content."""
import argparse
import hashlib
import json
import sqlite3
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("before", type=Path)
parser.add_argument("after", type=Path)
args = parser.parse_args()

def connect(folder):
    return sqlite3.connect((folder / "databases/chord.db").resolve().as_uri() + "?mode=ro", uri=True)

before, after = connect(args.before), connect(args.after)
def rows(db, table, columns="*"):
    return sorted(db.execute(f"SELECT {columns} FROM {table}").fetchall())

def artwork(folder):
    root = folder / "files/artwork"
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob("*") if p.is_file()}

report = {
    "before_arrangements": len(rows(before, "arrangements")),
    "after_arrangements": len(rows(after, "arrangements")),
    "catalog_exact": rows(before, "catalog_songs") == rows(after, "catalog_songs"),
    "arrangements_exact_including_canonical_json_and_fingerprints": rows(before, "arrangements") == rows(after, "arrangements"),
    "favorite_and_notes_exact": rows(before, "song_user_state", "songUuid,favorite,notes") == rows(after, "song_user_state", "songUuid,favorite,notes"),
    "transpose_and_capo_exact": rows(before, "arrangement_user_state") == rows(after, "arrangement_user_state"),
    "setlists_exact": rows(before, "setlists") == rows(after, "setlists"),
    "setlist_entries_exact": rows(before, "setlist_entries") == rows(after, "setlist_entries"),
    "managed_artwork_exact": artwork(args.before) == artwork(args.after),
    "after_integrity_check": after.execute("PRAGMA integrity_check").fetchone()[0],
    "after_foreign_key_violations": len(after.execute("PRAGMA foreign_key_check").fetchall()),
}
print(json.dumps(report, indent=2))
before.close()
after.close()
assert report["before_arrangements"] == report["after_arrangements"]
assert all(v for k, v in report.items() if k.endswith("exact") or k.endswith("fingerprints"))
assert report["after_integrity_check"] == "ok" and report["after_foreign_key_violations"] == 0
