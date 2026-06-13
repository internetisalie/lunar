#!/usr/bin/env python3
"""Lint documentation front-matter for the Lunar docs tree.

Guarantees front-matter accuracy against the manage-docs standard. Hard ERRORS
(missing/invalid front-matter, missing required fields, off-vocabulary
type/status/priority, malformed folders) gate CI. WARNINGS flag likely issues
that are noisy to enforce given lunar's irregular epic IDs (e.g. DEBUG/RUN).

Usage:
    python3 lint_docs.py [docs_root]      # default docs_root: "docs"
Exit code 1 if any ERROR is found, else 0.
"""
import os
import re
import sys

try:
    import yaml
except ImportError:
    sys.exit("lint_docs: PyYAML is required (pip install pyyaml)")

# Canonical vocabularies (manage-docs standard + project conventions).
TYPES = {"epic", "feature", "user-story", "task", "design", "risk",
         "qa", "guide", "plan", "spec"}
STATUSES = {"todo", "planned", "in_progress", "done", "blocked", "cancelled"}
PRIORITIES = {"critical", "high", "medium", "low"}

# Types that must carry a lifecycle status (and a parent for features).
STATUS_REQUIRED = {"epic", "feature", "spec"}
WIKILINK = re.compile(r"^\[\[.+\]\]$")

errors = []
warnings = []


def split_frontmatter(text):
    if not text.startswith("---"):
        return None, "no front-matter block"
    parts = text.split("---", 2)
    if len(parts) < 3:
        return None, "unterminated front-matter block"
    try:
        fm = yaml.safe_load(parts[1])
    except yaml.YAMLError as exc:
        return None, f"invalid YAML: {exc}"
    if not isinstance(fm, dict):
        return None, "front-matter is not a mapping"
    return fm, None


def check(path):
    rel = path
    with open(path, encoding="utf-8") as fh:
        fm, err = split_frontmatter(fh.read())
    if fm is None:
        errors.append((rel, err))
        return

    # Required everywhere.
    for field in ("id", "title", "type"):
        if not str(fm.get(field, "")).strip():
            errors.append((rel, f"missing required field '{field}'"))

    doc_type = fm.get("type")
    if doc_type is not None and doc_type not in TYPES:
        errors.append((rel, f"type '{doc_type}' not in {sorted(TYPES)}"))

    status = fm.get("status")
    if status is not None and status not in STATUSES:
        errors.append((rel, f"status '{status}' not in {sorted(STATUSES)}"))
    elif status is None and doc_type in STATUS_REQUIRED:
        errors.append((rel, f"type '{doc_type}' requires a 'status'"))

    priority = fm.get("priority")
    if priority is not None and priority not in PRIORITIES:
        errors.append((rel, f"priority '{priority}' not in {sorted(PRIORITIES)}"))

    # folders: list of well-formed wikilinks (required for epic/feature).
    folders = fm.get("folders")
    if folders is None:
        if doc_type in ("epic", "feature"):
            warnings.append((rel, "no 'folders' wikilink"))
    elif not isinstance(folders, list) or not folders:
        errors.append((rel, "'folders' must be a non-empty list"))
    else:
        for entry in folders:
            if not (isinstance(entry, str) and WIKILINK.match(entry.strip())):
                errors.append((rel, f"malformed folders entry: {entry!r}"))

    # Features should declare a parent (warning: lunar epic IDs are irregular,
    # so we don't enforce the exact prefix).
    if doc_type == "feature" and not str(fm.get("parent_id", "")).strip():
        warnings.append((rel, "feature missing 'parent_id'"))


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "docs"
    if not os.path.isdir(root):
        sys.exit(f"lint_docs: '{root}' not found")
    count = 0
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(".md"):
                check(os.path.join(dirpath, name))
                count += 1

    for rel, msg in sorted(warnings):
        print(f"WARN  {rel}: {msg}")
    for rel, msg in sorted(errors):
        print(f"ERROR {rel}: {msg}")
    print(f"\nlint_docs: {count} docs checked, "
          f"{len(errors)} error(s), {len(warnings)} warning(s)")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
