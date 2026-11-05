"""Helper for the step scripts: exact-text patches that either apply completely or not at all."""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
SRC = os.path.join(ROOT, 'app', 'src', 'main', 'java', 'id', 'flufclip', 'player')
SNIPPETS = os.path.join(ROOT, 'tools', 'refactor', 'snippets')


def fail(message):
    sys.exit(f"STEP FAILED, nothing was written: {message}")


class Patch:
    def __init__(self, name):
        self.path = os.path.join(SRC, name)
        if not os.path.exists(self.path):
            fail(f"{self.path} does not exist (did an earlier step run?)")
        self.text = open(self.path, encoding='utf-8').read()
        self.installs = []

    def rep(self, old, new, count=1):
        found = self.text.count(old)
        if found != count:
            fail(f"expected {count} occurrence(s), found {found}:\n{old[:200]}")
        self.text = self.text.replace(old, new)

    def sub(self, pattern, repl, expected=None):
        self.text, n = re.subn(pattern, repl, self.text)
        if expected is not None and n != expected:
            fail(f"pattern {pattern!r} replaced {n} time(s), expected {expected}")
        return n

    def between(self, start, end, new, include_end=False):
        """Replace text from the line holding `start` up to (not including) the line holding `end`."""
        if self.text.count(start) != 1:
            fail(f"start anchor must occur once: {start!r}")
        s = self.text.index(start)
        s = self.text.rfind('\n', 0, s) + 1
        e = self.text.find(end, s)
        if e < 0:
            fail(f"end anchor not found after start: {end!r}")
        e = self.text.rfind('\n', 0, e) + 1 if not include_end else self.text.index('\n', e) + 1
        self.text = self.text[:s] + new + self.text[e:]

    def install(self, snippet, target):
        dst = os.path.join(SRC, target)
        if os.path.exists(dst):
            fail(f"{dst} already exists")
        self.installs.append((os.path.join(SNIPPETS, snippet), dst))

    def save(self):
        for src, dst in self.installs:
            open(dst, 'w', encoding='utf-8').write(open(src, encoding='utf-8').read())
            print(f"created {os.path.relpath(dst, ROOT)}")
        open(self.path, 'w', encoding='utf-8').write(self.text)
        print(f"patched {os.path.relpath(self.path, ROOT)}")
