#!/usr/bin/env python3
"""Lint planning readiness for the Lunar docs tree.

Enforces the machine-checkable part of the plan-feature "Definition of Done": a
feature may only claim `status: planned` (or `in_progress`) once it has cleared the
planning bar (see .agents/skills/plan-feature/SKILL.md). The full bar — algorithms
specified, parse formats defined, every Must covered — needs human review, but two
necessary conditions are checkable here:

  1. The feature has a design doc (`design*.md`) in its directory.
  2. That design's "Open Questions" section is empty (unresolved items belong in
     risks-and-gaps.md as de-risking tasks, not parked in the design).

Scope: only LEAF features (type `feature` with no child feature) at status `planned`
or `in_progress`. `todo` is "not defined" and is intentionally exempt; `done`/
`cancelled`/`blocked` are out of scope.

Usage:
    python3 lint_planning.py [docs_root]      # default docs_root: "docs"
Exit code 1 if any planning-readiness ERROR is found, else 0.
"""
import os
import re
import sys

try:
    import yaml
except ImportError:
    sys.exit("lint_planning: PyYAML is required (pip install pyyaml)")

GATED_STATUSES = {"planned", "in_progress"}
DESIGN_GLOB = re.compile(r"^design.*\.md$", re.IGNORECASE)
OPEN_Q_HEADING = re.compile(r"^\s{0,3}#{2,6}\s+(?:\d+\.\s*)?open questions\b.*$",
                            re.IGNORECASE)
NEXT_HEADING = re.compile(r"^\s{0,3}#{1,6}\s")
COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)

errors = []


def frontmatter(path):
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    if not text.startswith("---"):
        return {}
    parts = text.split("---", 2)
    if len(parts) < 3:
        return {}
    try:
        data = yaml.safe_load(parts[1])
    except yaml.YAMLError:
        return {}
    return data if isinstance(data, dict) else {}


def open_questions_nonempty(path):
    """Return the residual text of the design's Open Questions section, or ''."""
    with open(path, encoding="utf-8") as fh:
        body = COMMENT.sub("", fh.read())
    lines = body.splitlines()
    collecting = False
    captured = []
    for line in lines:
        if collecting:
            if NEXT_HEADING.match(line):
                break
            captured.append(line)
        elif OPEN_Q_HEADING.match(line):
            collecting = True
    substantive = []
    for line in captured:
        stripped = line.strip()
        if not stripped:
            continue
        norm = re.sub(r"[^a-z0-9]", "", stripped.lower())
        if norm.startswith("none") or norm in ("na", "tbd"):
            continue
        substantive.append(stripped)
    return " ".join(substantive)


def check_feature(fm, path):
    if fm.get("type") != "feature":
        return
    if fm.get("status") not in GATED_STATUSES:
        return
    feature_dir = os.path.dirname(path)
    designs = [f for f in os.listdir(feature_dir) if DESIGN_GLOB.match(f)]
    status = fm.get("status")
    fid = fm.get("id", "?")
    if not designs:
        errors.append((path, f"{fid} is '{status}' but has no design*.md in "
                             f"{feature_dir} — not implementable (planning bar)"))
        return
    for design in sorted(designs):
        residual = open_questions_nonempty(os.path.join(feature_dir, design))
        if residual:
            preview = residual[:80] + ("…" if len(residual) > 80 else "")
            errors.append((os.path.join(feature_dir, design),
                           f"{fid} is '{status}' but {design} has unresolved Open "
                           f"Questions — move to risks-and-gaps.md: \"{preview}\""))


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "docs"
    if not os.path.isdir(root):
        sys.exit(f"lint_planning: '{root}' not found")

    docs = []
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if name.endswith(".md"):
                path = os.path.join(dirpath, name)
                docs.append((frontmatter(path), path))

    # Leaf = no other feature doc declares this feature as its parent.
    child_feature_parents = {
        str(fm.get("parent_id", "")).strip()
        for fm, _ in docs if fm.get("type") == "feature"
    }
    count = 0
    for fm, path in docs:
        if fm.get("type") == "feature":
            count += 1
            if str(fm.get("id", "")).strip() in child_feature_parents:
                continue  # non-leaf: design delegated to child features
            check_feature(fm, path)

    for rel, msg in sorted(errors):
        print(f"ERROR {rel}: {msg}")
    print(f"\nlint_planning: {count} feature(s) checked, {len(errors)} error(s)")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
