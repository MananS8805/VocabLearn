"""
Builds app/src/main/assets/dictionary.db - the offline dictionary WallLearn
searches - from the Princeton WordNet 3.0 database files.

Usage:
    1. Download and extract https://wordnetcode.princeton.edu/3.0/WNdb-3.0.tar.gz
    2. python tools/build_dictionary.py <path-to-extracted-dict-dir>

Schema (kept deliberately small, since it ships inside the APK):
    words(id INTEGER PRIMARY KEY, word TEXT UNIQUE)      -- lowercase lemma
    synsets(id INTEGER PRIMARY KEY, pos TEXT,            -- n / v / a / r
            definition TEXT, example TEXT)
    senses(word_id INTEGER, rank INTEGER, synset_id INTEGER)
        -- one row per meaning of a word; rank 0 is the most common sense

PRAGMA user_version holds DB_VERSION; bump it whenever the data changes so
the app knows to replace its installed copy.
"""

import os
import sqlite3
import sys

DB_VERSION = 1

POS_FILES = [("noun", "n"), ("verb", "v"), ("adj", "a"), ("adv", "r")]
POS_ORDER = {"n": 0, "v": 1, "a": 2, "r": 3}


def parse_gloss(gloss):
    """Splits a WordNet gloss into (definition, first example sentence)."""
    definitions, example = [], ""
    for part in gloss.split("; "):
        part = part.strip()
        if not part:
            continue
        if part.startswith('"'):
            if not example:
                example = part.strip('"').strip()
        else:
            definitions.append(part)
    return "; ".join(definitions), example


def read_synsets(dict_dir, file_suffix, pos):
    synsets = {}
    with open(os.path.join(dict_dir, "data." + file_suffix), encoding="utf-8") as f:
        for line in f:
            if line.startswith("  "):  # license header
                continue
            head, _, gloss = line.partition(" | ")
            offset = head.split(" ", 1)[0]
            definition, example = parse_gloss(gloss.strip())
            synsets[offset] = (pos, definition, example)
    return synsets


def read_index(dict_dir, file_suffix):
    """Yields (lemma, tagsense_cnt, [synset offsets in frequency order])."""
    with open(os.path.join(dict_dir, "index." + file_suffix), encoding="utf-8") as f:
        for line in f:
            if line.startswith("  "):
                continue
            fields = line.split()
            lemma = fields[0].replace("_", " ")
            synset_cnt = int(fields[2])
            p_cnt = int(fields[3])
            rest = fields[4 + p_cnt:]
            tagsense_cnt = int(rest[1])
            offsets = rest[2:2 + synset_cnt]
            yield lemma, tagsense_cnt, offsets


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    dict_dir = sys.argv[1]
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_path = os.path.join(root, "app", "src", "main", "assets", "dictionary.db")

    synset_ids = {}  # (pos, offset) -> synset row id
    synset_rows = []
    lemma_groups = {}  # lemma -> [(tagsense_cnt, pos, [synset ids])]

    for file_suffix, pos in POS_FILES:
        for offset, (spos, definition, example) in read_synsets(dict_dir, file_suffix, pos).items():
            synset_ids[(pos, offset)] = len(synset_rows) + 1
            synset_rows.append((len(synset_rows) + 1, spos, definition, example))
        for lemma, tagsense_cnt, offsets in read_index(dict_dir, file_suffix):
            ids = [synset_ids[(pos, o)] for o in offsets]
            lemma_groups.setdefault(lemma, []).append((tagsense_cnt, pos, ids))

    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.executescript(
        """
        CREATE TABLE words(id INTEGER PRIMARY KEY, word TEXT NOT NULL UNIQUE);
        CREATE TABLE synsets(id INTEGER PRIMARY KEY, pos TEXT NOT NULL,
                             definition TEXT NOT NULL, example TEXT NOT NULL);
        CREATE TABLE senses(word_id INTEGER NOT NULL, rank INTEGER NOT NULL,
                            synset_id INTEGER NOT NULL,
                            PRIMARY KEY(word_id, rank)) WITHOUT ROWID;
        """
    )
    db.executemany("INSERT INTO synsets VALUES (?, ?, ?, ?)", synset_rows)

    word_rows, sense_rows = [], []
    for word_id, lemma in enumerate(sorted(lemma_groups), start=1):
        word_rows.append((word_id, lemma))
        # The part of speech used most often in tagged text comes first.
        groups = sorted(lemma_groups[lemma], key=lambda g: (-g[0], POS_ORDER[g[1]]))
        rank = 0
        for _, _, ids in groups:
            for synset_id in ids:
                sense_rows.append((word_id, rank, synset_id))
                rank += 1
    db.executemany("INSERT INTO words VALUES (?, ?)", word_rows)
    db.executemany("INSERT INTO senses VALUES (?, ?, ?)", sense_rows)

    db.execute(f"PRAGMA user_version = {DB_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"{len(word_rows)} words, {len(synset_rows)} meanings -> {out_path} "
          f"({os.path.getsize(out_path) / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
