#!/usr/bin/env python3
"""Builds the suggestion dictionaries in app/src/main/assets/dict/ from the AOSP LatinIME word lists.

Source: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/
(Apache License 2.0). Run from the repository root:  python tools/dictionaries/convert.py

Output per language: <tag>.dic (gzip; not .gz, because the Android build unpacks .gz assets and drops the extension), UTF-8 lines "word<TAB>frequency" (0-255), most frequent first,
at most MAX_WORDS words. Words marked not_a_word, possibly_offensive and shortcuts are left out.

Two small curated additions, kept in this folder:
- boost/<language>.txt: everyday words the Wikipedia-weighted AOSP lists rank too low ("hallo",
  weekdays, greetings); "word<TAB>frequency", the higher frequency counts.
- next/<language>.txt: next-word suggestions, because the AOSP lists contain no bigrams;
  "word<TAB>next<TAB>next…", copied as <language>.nxt (plain UTF-8 text).

Only some languages:  python tools/dictionaries/convert.py de en-US en-GB
"""
import base64
import gzip
import os
import re
import sys
import urllib.request

BASE = "https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/"
# Latin-script languages the keyboard has subtypes for; file name -> asset tag.
LANGUAGES = {
    "cs": "cs", "da": "da", "de": "de", "en_GB": "en-GB", "en_US": "en-US", "es": "es", "fi": "fi",
    "fr": "fr", "hr": "hr", "it": "it", "lt": "lt", "lv": "lv", "nb": "nb", "nl": "nl", "pl": "pl",
    "pt_BR": "pt-BR", "pt_PT": "pt-PT", "ro": "ro", "sl": "sl", "sv": "sv", "tr": "tr",
}
MAX_WORDS = 80000
OUT = os.path.join("app", "src", "main", "assets", "dict")
HERE = os.path.dirname(os.path.abspath(__file__))


def curated(kind: str, language: str) -> list:
    """Lines of boost/ or next/ for a language (de, en …), without comments and blank lines."""
    path = os.path.join(HERE, kind, language + ".txt")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [line.rstrip("\n") for line in f if line.strip() and not line.startswith("#")]


LINE = re.compile(r"^ word=([^,]+),f=(\d+)(.*)$")


def fetch(name: str) -> bytes:
    with urllib.request.urlopen(BASE + name + "_wordlist.combined.gz?format=TEXT") as response:
        return gzip.decompress(base64.b64decode(response.read()))


def convert(name: str, tag: str) -> None:
    words = []
    for line in fetch(name).decode("utf-8").splitlines():
        m = LINE.match(line)
        if not m:
            continue
        word, freq, rest = m.group(1), int(m.group(2)), m.group(3)
        if "not_a_word=true" in rest or "possibly_offensive=true" in rest or "\t" in word or " " in word:
            continue
        words.append((freq, word))
    boost = {}
    for line in curated("boost", tag.split("-")[0]):
        word, freq = line.split("\t")
        boost[word.lower()] = (word, int(freq))
    merged = {}
    for freq, word in words:
        key = word.lower()
        if key not in merged or merged[key][0] < freq:
            merged[key] = (freq, word)
    for key, (word, freq) in boost.items():
        if key not in merged or merged[key][0] < freq:
            merged[key] = (freq, word)
    words = list(merged.values())
    words.sort(key=lambda w: (-w[0], w[1]))
    text = "".join(f"{w}\t{f}\n" for f, w in words[:MAX_WORDS])
    path = os.path.join(OUT, tag + ".dic")
    with gzip.GzipFile(path, "wb", compresslevel=9, mtime=0) as out:
        out.write(text.encode("utf-8"))
    print(f"{tag}: {min(len(words), MAX_WORDS)} words, {os.path.getsize(path) // 1024} KB")


def next_words(language: str) -> None:
    """next/<language>.txt -> <language>.nxt: one line per word, followers merged, first ones first."""
    merged = {}
    for line in curated("next", language):
        parts = line.replace(" ", "\t").split("\t")
        followers = merged.setdefault(parts[0].lower(), [])
        followers += [w for w in parts[1:] if w and w not in followers]
    if not merged:
        return
    path = os.path.join(OUT, language + ".nxt")
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        for word, followers in merged.items():
            out.write(word + "\t" + "\t".join(followers[:5]) + "\n")
    print(f"{language}: {len(merged)} next-word entries")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    only = set(sys.argv[1:])
    for name, tag in LANGUAGES.items():
        if not only or tag in only:
            convert(name, tag)
    for language in sorted({tag.split("-")[0] for tag in LANGUAGES.values()}):
        next_words(language)


if __name__ == "__main__":
    sys.exit(main())
