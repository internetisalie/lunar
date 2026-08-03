#!/usr/bin/env bash
# Fetch the pinned corpus fixtures listed in corpus.tsv into <repo>/test/corpus/<name>.
#
# The checkouts are stripped of .git so the gce-builder rsync (which pushes the whole test/ tree
# dereferenced) stays small. A .corpus-sha stamp makes re-runs a no-op when already at the pin.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MANIFEST="$SCRIPT_DIR/corpus.tsv"
CORPUS_ROOT="${LUNAR_CORPUS_ROOT:-$REPO_ROOT/test/corpus}"

log() { printf '\033[1;34m[corpus]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[corpus] %s\033[0m\n' "$*" >&2; exit 1; }

[ -f "$MANIFEST" ] || die "Manifest not found: $MANIFEST"
mkdir -p "$CORPUS_ROOT"

fetch_one() {
  local name="$1" url="$2" commit="$3" prune="${4:-}"
  local dest="$CORPUS_ROOT/$name"
  local stamp="$dest/.corpus-sha"

  if [ -f "$stamp" ] && [ "$(cat "$stamp")" = "$commit" ]; then
    log "$name already at ${commit:0:8} — skipping"
    return
  fi

  log "fetching $name @ ${commit:0:8} from $url"
  rm -rf "$dest"
  mkdir -p "$dest"
  git -C "$dest" init --quiet
  git -C "$dest" remote add origin "$url"
  # Depth-1 fetch of a specific SHA; fine for tag tips, which is all the manifest pins.
  git -C "$dest" fetch --quiet --depth 1 origin "$commit"
  git -C "$dest" checkout --quiet FETCH_HEAD
  rm -rf "$dest/.git"
  for victim in ${prune//,/ }; do
    rm -rf "${dest:?}/$victim"
  done
  printf '%s\n' "$commit" > "$stamp"
  log "$name → $(find "$dest" -name '*.lua' | wc -l) .lua files, $(du -sh "$dest" | cut -f1)"
}

while IFS=$'\t' read -r name url commit roots prune lualevel; do
  case "$name" in ''|\#*) continue ;; esac
  [ -n "${url:-}" ] && [ -n "${commit:-}" ] || die "Malformed manifest row: $name"
  fetch_one "$name" "$url" "$commit" "${prune:-}"
  for root in ${roots//,/ }; do
    [ -d "$CORPUS_ROOT/$name/$root" ] || die "$name: declared root '$root' missing from the checkout"
  done
done < "$MANIFEST"

log "Corpus ready at $CORPUS_ROOT"
