#!/usr/bin/env python3
"""Move top-level Kotlin declarations between files without retyping them.

Used for splitting MainActivity.kt (see docs/refactor/PLAN_PECAH_MAINACTIVITY.md).

A top-level declaration starts on a column-0 line (`@Composable`, `private fun`,
`internal val`, `data class`, ...). KDoc / `//` comments and annotation lines directly
above it belong to it. It ends right before the next declaration (or end of file).
Code inside a declaration body is always indented in this project, except closing
`) {` / `)` / `}` lines, which are not declaration starts.

Usage:
  python3 tools/refactor/move_decls.py list FILE
  python3 tools/refactor/move_decls.py internalize FILE
  python3 tools/refactor/move_decls.py move --from SRC --to DST --names A,B,C [--internal] [--dry-run]

`internalize` turns every top-level `private` declaration of FILE into `internal`
(indented members such as `private val` inside a class are left alone), so moving
declarations to other files never breaks visibility.
`move` cuts the named declarations from SRC and appends them to DST, in SRC order.
If DST does not exist it is created with SRC's package line and full import block
(unused imports are only warnings). `--internal` turns a leading `private` into
`internal` on the moved declarations, so code left in other files can still call them.
A name that matches several declarations (e.g. two `isOngoingLive` extensions) moves all of them.
The script refuses to run if a name is missing, so nothing is half-moved.
"""
import argparse
import os
import re
import sys

DECL_START = re.compile(r'^(@|private |internal |public |fun |val |var |class |data class |enum class |sealed |object |interface |typealias |const |inline |/\*\*|//)')
NAME = re.compile(r'\b(?:fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w<>?, ]+\.)?(`?\w+`?)')
HEADER_LINE = re.compile(r'^(@|/\*\*|\s\*|\*/|//)')


def split(text):
    lines = text.splitlines(keepends=True)
    # Header = package + imports + blank lines before the first declaration.
    i = 0
    while i < len(lines) and (lines[i].startswith(('package ', 'import ')) or not lines[i].strip()):
        i += 1
    header = lines[:i]
    starts = []
    in_kdoc = False
    j = i
    while j < len(lines):
        line = lines[j]
        if in_kdoc:
            if '*/' in line:
                in_kdoc = False
            j += 1
            continue
        if DECL_START.match(line):
            prev = lines[j - 1] if j > i else ''
            prev_is_header = j > i and (HEADER_LINE.match(prev) or prev.startswith(' *')) and starts and starts[-1] < j
            # Lines directly under a comment/annotation line continue the same declaration.
            if not (starts and prev_is_header and _block_is_header(lines, starts[-1], j)):
                starts.append(j)
            if line.startswith('/**') and '*/' not in line:
                in_kdoc = True
        j += 1
    chunks = []
    for k, s in enumerate(starts):
        e = starts[k + 1] if k + 1 < len(starts) else len(lines)
        body = lines[s:e]
        decl = _decl_line(body)
        m = NAME.search(decl) if decl else None
        name = m.group(1).strip('`') if m else None
        chunks.append({'start': s + 1, 'end': e, 'name': name, 'decl': decl or '', 'lines': body})
    return header, chunks


def _decl_line(body):
    """First line that declares something (an `@Composable private fun x(` line counts; a bare annotation does not)."""
    for l in body:
        if l.startswith(('/**', ' *', '*/', '//')):
            continue
        if l.startswith('@') and not NAME.search(l):
            continue
        return l
    return None


def _block_is_header(lines, start, j):
    """True when every line from `start` up to `j` is a comment/annotation line (so `j` belongs to it)."""
    for l in lines[start:j]:
        if not (HEADER_LINE.match(l) or l.startswith(' *') or not l.strip()):
            return False
    return True


def cmd_list(path):
    _, chunks = split(open(path, encoding='utf-8').read())
    for c in chunks:
        vis = 'private' if re.match(r'^(@\w+ )*private ', c['decl']) else ''
        print(f"{c['start']:>6}-{c['end']:<6} {c['end'] - c['start'] + 1:>5} {c['name'] or '?':<34} {vis}")


def cmd_move(src, dst, names, internal, dry):
    text = open(src, encoding='utf-8').read()
    header, chunks = split(text)
    wanted = [n.strip() for n in names.split(',') if n.strip()]
    missing = [n for n in wanted if not any(c['name'] == n for c in chunks)]
    if missing:
        sys.exit(f"ERROR: not found in {src}: {', '.join(missing)}")
    moved = [c for c in chunks if c['name'] in wanted]
    kept = [c for c in chunks if c['name'] not in wanted]
    out = []
    for c in moved:
        body = list(c['lines'])
        if internal:
            body = [re.sub(r'^(@Composable )?private ', r'\1internal ', l) if not l.startswith((' ', '\t')) else l for l in body]
        out.extend(body)
    while out and not out[-1].strip():
        out.pop()
    moved_text = ''.join(out) + '\n'
    new_src = ''.join(header) + ''.join(l for c in kept for l in c['lines'])
    if os.path.exists(dst):
        existing = open(dst, encoding='utf-8').read().rstrip('\n') + '\n\n'
        new_dst = existing + moved_text
    else:
        new_dst = ''.join(header).rstrip('\n') + '\n\n' + moved_text
    print(f"move {len(moved)} declarations ({sum(len(c['lines']) for c in moved)} lines) {src} -> {dst}")
    for c in moved:
        print(f"  {c['name']} (lines {c['start']}-{c['end']})")
    if dry:
        return
    open(src, 'w', encoding='utf-8').write(new_src)
    open(dst, 'w', encoding='utf-8').write(new_dst)


def cmd_internalize(path):
    text = open(path, encoding='utf-8').read()
    out, n = re.subn(r'^((?:@\w+(?:\([^)]*\))? )*)private ', r'\1internal ', text, flags=re.M)
    open(path, 'w', encoding='utf-8').write(out)
    print(f"internalize: {n} top-level declarations in {path}")


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest='cmd', required=True)
    l = sub.add_parser('list')
    l.add_argument('file')
    i = sub.add_parser('internalize')
    i.add_argument('file')
    m = sub.add_parser('move')
    m.add_argument('--from', dest='src', required=True)
    m.add_argument('--to', dest='dst', required=True)
    m.add_argument('--names', required=True)
    m.add_argument('--internal', action='store_true')
    m.add_argument('--dry-run', action='store_true')
    a = p.parse_args()
    if a.cmd == 'list':
        cmd_list(a.file)
    elif a.cmd == 'internalize':
        cmd_internalize(a.file)
    else:
        cmd_move(a.src, a.dst, a.names, a.internal, a.dry_run)


if __name__ == '__main__':
    main()
