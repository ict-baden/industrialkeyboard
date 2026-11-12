#!/usr/bin/env python3
"""Builds the suggestion dictionaries in app/src/main/assets/dict/ from the AOSP LatinIME word lists.

Source: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/dictionaries/
(Apache License 2.0). Run from the repository root:  python tools/dictionaries/convert.py

Output per language: <tag>.dic (gzip; not .gz, because the Android build unpacks .gz assets and drops the extension), UTF-8 lines "word<TAB>frequency" (0-255), most frequent first,
at most MAX_WORDS words. Words marked not_a_word, possibly_offensive and shortcuts are left out.
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
    words.sort(key=lambda w: (-w[0], w[1]))
    text = "".join(f"{w}\t{f}\n" for f, w in words[:MAX_WORDS])
    path = os.path.join(OUT, tag + ".dic")
    with gzip.GzipFile(path, "wb", compresslevel=9, mtime=0) as out:
        out.write(text.encode("utf-8"))
    print(f"{tag}: {min(len(words), MAX_WORDS)} words, {os.path.getsize(path) // 1024} KB")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    for name, tag in LANGUAGES.items():
        convert(name, tag)


if __name__ == "__main__":
    sys.exit(main())
