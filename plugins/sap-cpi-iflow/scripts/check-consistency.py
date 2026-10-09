#!/usr/bin/env python3
"""Consistency checks for the sap-cpi-iflow knowledge package.

Run from the repository root:

    python3 scripts/check-consistency.py

It verifies, for every Markdown file:
  * the file starts with a single level-1 heading (SKILL.md may start with YAML front matter)
  * code fences are balanced
  * every relative link resolves to a file that exists
  * no trailing whitespace, single trailing newline

and, for every Groovy file under examples/:
  * brackets and braces are balanced outside strings and comments
  * the CPI entry function `def Message processData(Message message)` is present
  * no trailing whitespace, single trailing newline

Exit code is 0 when everything is consistent, 1 otherwise.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
SKIP_SCHEMES = ("http://", "https://", "mailto:", "#", "tel:")
PAIRS = {")": "(", "]": "[", "}": "{"}
ENTRY_FUNCTION = "def Message processData(Message message)"


def iter_files(extension):
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in (".git", "node_modules")]
        for name in sorted(filenames):
            if name.endswith(extension):
                yield os.path.join(dirpath, name)


def strip_literals(src):
    """Remove Groovy strings and comments, preserving newlines."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            while i < n and src[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            i += 2
            while i + 1 < n and not (src[i] == "*" and src[i + 1] == "/"):
                if src[i] == "\n":
                    out.append("\n")
                i += 1
            i += 2
            continue
        if src.startswith("'''", i) or src.startswith('"""', i):
            quote = src[i:i + 3]
            i += 3
            while i < n and not src.startswith(quote, i):
                if src[i] == "\n":
                    out.append("\n")
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 3
            continue
        if c in "'\"":
            quote = c
            i += 1
            while i < n and src[i] != quote:
                if src[i] == "\\":
                    i += 1
                if i < n and src[i] == "\n":
                    out.append("\n")
                i += 1
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check_markdown(path, problems):
    name = os.path.relpath(path, ROOT)
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    lines = text.split("\n")

    if not text.endswith("\n"):
        problems.append(f"{name}: missing trailing newline")
    elif text.endswith("\n\n"):
        problems.append(f"{name}: extra blank line at end of file")

    for number, line in enumerate(lines, 1):
        if line != line.rstrip():
            problems.append(f"{name}:{number}: trailing whitespace")
            break

    is_skill = lines[0] == "---" and "name:" in text[:400]
    if not is_skill and not lines[0].startswith("# "):
        problems.append(f"{name}: does not start with a single '# ' heading")

    fences = sum(1 for line in lines if line.lstrip().startswith("```"))
    if fences % 2 != 0:
        problems.append(f"{name}: unbalanced code fences ({fences})")

    for match in LINK_RE.finditer(text):
        target = match.group(1).strip()
        if target.startswith(SKIP_SCHEMES):
            continue
        target = target.split("#")[0]
        if not target:
            continue
        resolved = os.path.normpath(os.path.join(os.path.dirname(path), target))
        if not os.path.exists(resolved):
            problems.append(f"{name}: broken relative link -> {target}")


def check_groovy(path, problems):
    name = os.path.relpath(path, ROOT)
    with open(path, encoding="utf-8") as fh:
        src = fh.read()

    if not src.endswith("\n"):
        problems.append(f"{name}: missing trailing newline")

    for number, line in enumerate(src.split("\n"), 1):
        if line != line.rstrip():
            problems.append(f"{name}:{number}: trailing whitespace")
            break

    if ENTRY_FUNCTION not in src:
        problems.append(f"{name}: missing '{ENTRY_FUNCTION}' entry function")

    stack = []
    number = 1
    for char in strip_literals(src):
        if char == "\n":
            number += 1
        elif char in "([{":
            stack.append((char, number))
        elif char in ")]}":
            if not stack:
                problems.append(f"{name}:{number}: unmatched closing '{char}'")
                return
            opening, opened_at = stack.pop()
            if opening != PAIRS[char]:
                problems.append(
                    f"{name}:{number}: '{char}' closes '{opening}' opened at line {opened_at}")
                return
    if stack:
        problems.append(f"{name}: unclosed '{stack[-1][0]}' opened at line {stack[-1][1]}")


def main():
    problems = []
    markdown = list(iter_files(".md"))
    groovy = list(iter_files(".groovy"))

    for path in markdown:
        check_markdown(path, problems)
    for path in groovy:
        check_groovy(path, problems)

    print(f"Checked {len(markdown)} Markdown files and {len(groovy)} Groovy files.")
    if problems:
        print("\nProblems found:")
        for problem in problems:
            print("  -", problem)
        return 1
    print("No inconsistencies found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
