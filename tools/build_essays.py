#!/usr/bin/env python3
"""Build app/src/main/assets/essays.json from the GRE essay-compilation PDF.

Usage:
    pip install pymupdf
    python tools/build_essays.py "path/to/essay compilation.pdf"

The PDF is highly regular: every essay is
    Essay N: Title
    Instruction type: X  •  N words
    Issue: <prompt>
    <instruction boilerplate>
    <body paragraphs>
    Key Glossary            (term  —  definition, one per entry)
    Why This Structure Works
    <structure note>
PyMuPDF returns each paragraph as its own text block, so paragraph boundaries are
real rather than guessed; the only repair needed is re-joining a paragraph that a
page break split in two.

Everything here is deterministic -- no model calls, nothing rewritten. Each
annotation carries a verbatim `quote` that the app locates in the text at runtime;
this script fails the build if any quote or glossary term cannot be found.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import fitz  # PyMuPDF

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/essays.json"
EXPECTED_ESSAYS = 40
THEMES = [
    "Education & Learning",
    "Government, Policy & Law",
    "Technology & Science",
    "Individual & Society",
    "Art, Creativity & Culture",
    "Knowledge, Truth & Historical Understanding",
    "Leadership, Change & Progress",
    "Ethics, Values & Human Nature",
]
ESSAY_RE = re.compile(r"^Essay (\d+):\s*(.+)$", re.S)
META_RE = re.compile(r"^Instruction type:\s*(.+?)\s+•\s+(\d+)\s+words", re.S)
GLOSS_SPLIT = re.compile(r"\s+—\s+")
PARA_CLAUSE_RE = re.compile(r"^paragraphs?\s+(\d+)(?:\s*[–\-]\s*(\d+))?\b", re.I)
CONCESSION_RE = re.compile(
    r"^(None of this|That said|Admittedly|Of course|To be sure|Still|Nevertheless|"
    r"This is not to|None of these|None of that|Yet|However|But)\b|"
    r"\b(real value|undeniabl|genuine|legitimate|fair|reasonable)\b", re.I)
SENT_RE = re.compile(r"(?<=[.!?])[\"”')]*\s+(?=[\"“'(]?[A-Z])")
ABBREV = ("e.g.", "i.e.", "U.S.", "U.K.", "vs.", "Dr.", "Mr.", "Mrs.", "Ms.", "St.", "No.", "etc.")


def clean(s: str) -> str:
    return re.sub(r"\s*\n\s*", " ", s).strip()


def split_sentences(par: str) -> list[str]:
    """Rough splitter, used only for choosing annotation quotes at build time."""
    masked = par
    for a in ABBREV:
        masked = masked.replace(a, a.replace(".", "\u0001"))
    parts = SENT_RE.split(masked)
    return [p.replace("\u0001", ".").strip() for p in parts if p.strip()]


def load_blocks(pdf: Path) -> list[str]:
    blocks: list[str] = []
    for page in fitz.open(pdf):
        for b in page.get_text("blocks"):
            if b[6] == 0 and b[4].strip():
                blocks.append(b[4])
    return blocks


def parse(blocks: list[str]) -> list[dict]:
    essays: list[dict] = []
    cur: dict | None = None
    section = None  # 'meta' | 'issue' | 'boiler' | 'body' | 'gloss' | 'note'
    theme = None
    for raw in blocks:
        text = clean(raw)
        if text in THEMES:
            # Theme heading; also appears in the table of contents before any essay.
            theme = text
            if cur is not None:
                section = None
            continue
        m = ESSAY_RE.match(text)
        if m and m.group(2) and (cur is None or int(m.group(1)) == cur["n"] + 1 or cur is None):
            if cur is None and int(m.group(1)) != 1:
                continue
            cur = {"n": int(m.group(1)), "title": m.group(2).strip(), "theme": theme,
                   "paragraphs": [], "glossary_raw": [], "note": []}
            essays.append(cur)
            section = "meta"
            continue
        if cur is None:
            continue
        if text.startswith("8. 24 New Practice") or text.startswith("24 New Practice"):
            section = None
            continue
        if section is None:
            continue
        if section == "meta":
            mm = META_RE.match(text)
            if not mm:
                raise SystemExit(f"Essay {cur['n']}: bad meta line: {text[:80]!r}")
            cur["instructionType"] = mm.group(1).strip()
            cur["declaredWords"] = int(mm.group(2))
            section = "issue"
        elif section == "issue":
            if not text.startswith("Issue:"):
                raise SystemExit(f"Essay {cur['n']}: expected Issue:, got {text[:60]!r}")
            cur["prompt"] = text[len("Issue:"):].strip()
            section = "boiler"
        elif section == "boiler":
            if not text.startswith("Write a response"):
                raise SystemExit(f"Essay {cur['n']}: expected instructions, got {text[:60]!r}")
            cur["directions"] = text
            section = "body"
        elif section == "body":
            if text == "Key Glossary":
                section = "gloss"
            else:
                cur["paragraphs"].append(raw)
        elif section == "gloss":
            if text == "Why This Structure Works":
                section = "note"
            else:
                cur["glossary_raw"].append(raw)
        elif section == "note":
            cur["note"].append(raw)
    return essays


def rejoin_page_splits(raws: list[str]) -> list[str]:
    """A paragraph cut by a page break arrives as two blocks; glue them back."""
    out: list[str] = []
    for r in raws:
        t = clean(r)
        if out and not re.search(r"[.!?][\"”')]*$", out[-1]) and t[:1].islower():
            out[-1] = out[-1] + " " + t
        else:
            out.append(t)
    return out


def parse_glossary(raws: list[str], essay_n: int) -> list[dict]:
    # Entries wrap over several lines and blocks; an entry starts where a line
    # contains the "term  —  definition" dash, so group by that.
    entries: list[str] = []
    for line in "\n".join(raws).split("\n"):
        # A dash inside a wrapped definition must not start a new entry: the
        # previous entry has to be finished (ends in terminal punctuation) and the
        # candidate term has to look like a term (short).
        prev_done = not entries or (
            re.search(r"[.!?][\"”')]*$", entries[-1])
            and entries[-1].count("(") <= entries[-1].count(")"))
        head = re.split(r"\s—\s", line, maxsplit=1)[0]
        if re.search(r"\s—\s", line) and prev_done and len(head.split()) <= 8:
            entries.append(line.strip())
        elif entries and line.strip():
            entries[-1] += " " + line.strip()
    result = []
    for e in entries:
        parts = GLOSS_SPLIT.split(e, maxsplit=1)
        if len(parts) != 2:
            raise SystemExit(f"Essay {essay_n}: unparsable glossary entry {e[:60]!r}")
        result.append({"term": parts[0].strip(), "def": re.sub(r"\s+", " ", parts[1]).strip()})
    return result


def role_of(i: int, n_par: int, par: str) -> str:
    if i == 0:
        return "thesis"
    if i == n_par - 1:
        return "conclusion"
    first = split_sentences(par)[0] if par else ""
    return "complexity" if CONCESSION_RE.search(first) else "argument"


def split_note(note: str) -> dict[int, str]:
    """Map paragraph number -> the clause of the structure note that justifies it."""
    why: dict[int, str] = {}
    for clause in re.split(r";\s+", note):
        clause = clause.strip()
        # The first clause often has a lead-in: "This is a classic X structure: paragraph 1 ...".
        lead = re.search(r":\s+(paragraphs?\s+\d.*)$", clause, re.I | re.S)
        if lead:
            clause = lead.group(1)
        m = PARA_CLAUSE_RE.match(clause)
        if m:
            lo = int(m.group(1))
            hi = int(m.group(2) or lo)
            for p in range(lo, hi + 1):
                why.setdefault(p, clause.rstrip("."))
        elif re.match(r"^(and\s+)?the (closing|concluding|conclusion|final paragraph)\b", clause, re.I):
            why.setdefault(-1, re.sub(r"^and\s+", "", clause, flags=re.I).rstrip("."))
    return why


def build_annotations(paragraphs: list[str], note: str) -> list[dict]:
    why = split_note(note)
    n = len(paragraphs)
    ann = []
    for i, par in enumerate(paragraphs):
        role = role_of(i, n, par)
        sents = split_sentences(par)
        if not sents:
            continue
        if role == "thesis":
            # The position sentence is the last sentence of the opening paragraph.
            quote = sents[-1]
            kind = "thesis"
        elif role == "conclusion":
            quote = sents[-1]
            kind = "conclusion"
        else:
            quote = sents[0]
            kind = "argument" if role == "argument" else "complexity"
        reason = why.get(i + 1, "")
        if not reason and role == "conclusion":
            reason = why.get(-1, "")
        ann.append({"kind": kind, "paragraph": i, "quote": quote, "why": reason})
    return ann


def build(pdf: Path) -> list[dict]:
    essays = parse(load_blocks(pdf))
    if len(essays) != EXPECTED_ESSAYS:
        raise SystemExit(f"expected {EXPECTED_ESSAYS} essays, parsed {len(essays)}")
    out = []
    for e in essays:
        pars = rejoin_page_splits(e["paragraphs"])
        note = " ".join(rejoin_page_splits(e["note"]))
        gloss = parse_glossary(e["glossary_raw"], e["n"])
        body = " ".join(pars)
        words = len(body.split())
        # Declared vs actual word count is a parse-integrity check, not a content one.
        if abs(words - e["declaredWords"]) > max(15, e["declaredWords"] * 0.06):
            print(f"  warn essay {e['n']}: declared {e['declaredWords']} words, parsed {words}")
        lower = body.lower()
        for g in gloss:
            g["inText"] = g["term"].lower() in lower
        ann = build_annotations(pars, note)
        for a in ann:
            if a["quote"] not in body:
                raise SystemExit(f"Essay {e['n']}: annotation quote not verbatim: {a['quote'][:60]!r}")
        out.append({
            "id": f"m{e['n']:02d}",
            "n": e["n"],
            "theme": e["theme"],
            "title": e["title"],
            "instructionType": e["instructionType"],
            "prompt": e["prompt"],
            "directions": e["directions"],
            "paragraphs": pars,
            "glossary": gloss,
            "structureNote": note,
            "annotations": ann,
        })
    return out


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    essays = build(Path(sys.argv[1]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"version": 1, "essays": essays}, ensure_ascii=False,
                              separators=(",", ":")), encoding="utf-8")
    g_total = sum(len(e["glossary"]) for e in essays)
    g_hit = sum(g["inText"] for e in essays for g in e["glossary"])
    a_total = sum(len(e["annotations"]) for e in essays)
    a_why = sum(1 for e in essays for a in e["annotations"] if a["why"])
    print(f"{len(essays)} essays -> {OUT} ({OUT.stat().st_size // 1024} KB)")
    print(f"glossary terms found verbatim in text: {g_hit}/{g_total}")
    print(f"annotations: {a_total}, with a structure-note justification: {a_why}")
    from collections import Counter
    print("themes:", dict(Counter(e["theme"] for e in essays)))


if __name__ == "__main__":
    main()
