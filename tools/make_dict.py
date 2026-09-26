#!/usr/bin/env python3
"""Convert an AOSP LatinIME wordlist (*.combined or *.combined.gz) into the
compact dictionary format used by Simple Keyboard's suggestions.

Output: UTF-8 text, one "word<TAB>frequency" per line, most frequent first.
Frequencies are 1..255. The output is gzip-compressed if its name ends in .gz
(the APK is already compressed, so bundled dictionaries are plain text).

Words flagged possibly_offensive in the source list are left out.

Usage: make_dict.py <input.combined[.gz]> <output.dict[.gz]> [max_words]

The bundled app/src/main/assets/dicts/en.dict was made from the AOSP LatinIME
dictionaries/en_US_wordlist.combined.gz (Apache License 2.0) with max_words=50000.
"""
import gzip
import sys


def open_text(path):
    with open(path, 'rb') as f:
        magic = f.read(2)
    if magic == b'\x1f\x8b':
        return gzip.open(path, 'rt', encoding='utf-8')
    return open(path, 'r', encoding='utf-8')


def parse(path):
    words = {}
    with open_text(path) as f:
        for line in f:
            if not line.startswith(' word='):
                continue
            fields = dict(part.split('=', 1) for part in line.strip().split(',') if '=' in part)
            word = fields.get('word', '')
            flags = fields.get('flags', '')
            if not word or 'possibly_offensive' in flags or fields.get('not_a_word') == 'true':
                continue
            try:
                freq = int(fields.get('f', '0'))
            except ValueError:
                continue
            if freq <= 0 or any(c.isspace() for c in word):
                continue
            words[word] = max(freq, words.get(word, 0))
    return words


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    max_words = int(sys.argv[3]) if len(sys.argv) > 3 else 50000
    words = parse(sys.argv[1])
    ranked = sorted(words.items(), key=lambda kv: (-kv[1], kv[0]))[:max_words]
    data = ''.join('%s\t%d\n' % (word, min(freq, 255)) for word, freq in ranked)
    if sys.argv[2].endswith('.gz'):
        with gzip.GzipFile(sys.argv[2], 'wb', compresslevel=9, mtime=0) as out:
            out.write(data.encode('utf-8'))
    else:
        with open(sys.argv[2], 'w', encoding='utf-8', newline='\n') as out:
            out.write(data)
    print('%d words written to %s' % (len(ranked), sys.argv[2]))


if __name__ == '__main__':
    main()
