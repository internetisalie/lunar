#!/bin/sh
# Release notes for a tag: the entries of CHANGELOG.md's top "## [..]" block that the previous v* tag
# did not have. CHANGELOG.md is per-milestone, so the top block holds every patch of the minor; a
# patch release lists only what it added. An entry is a "- **headline**" bullet and its continuation
# lines; shipped entries are never edited, so the headline identifies it. Falls back to the whole
# block when the previous tag is another milestone, or there is none, or nothing new is found.
# Usage: scripts/release-notes.sh <tag> [changelog]   (needs full history and tags)
set -eu
tag=$1
log=${2:-CHANGELOG.md}

top_block() { awk '/^## \[/ { if (seen) exit; seen = 1; print; next } seen { print }'; }

current=$(top_block < "$log")
heading=$(printf '%s\n' "$current" | head -1)
body=$(printf '%s\n' "$current" | tail -n +2)

prev=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null || true)
previous=""
[ -n "$prev" ] && previous=$(git show "$prev:CHANGELOG.md" 2>/dev/null | top_block || true)

if [ -z "$previous" ] || [ "$(printf '%s\n' "$previous" | head -1)" != "$heading" ]; then
  printf '%s\n' "$body"
  exit 0
fi

seen=$(printf '%s\n' "$previous" | grep '^- \*\*' || true)
added=$(printf '%s\n' "$body" | awk -v seen="$seen" '
  BEGIN { n = split(seen, s, "\n"); for (i = 1; i <= n; i++) old[s[i]] = 1 }
  /^- \*\*/ { keep = !($0 in old) }
  /^## |^### / { keep = 0 }
  keep { print }
')
if [ -z "$added" ]; then
  printf '%s\n' "$body"
else
  printf '%s\n' "$added"
fi
