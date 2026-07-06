#!/usr/bin/env bash
# TOOLING-00-02 (Stage 2 follow-up): self-built Windows Lua via MinGW cross-compile.
#
# Proves Lunar can produce its OWN Windows Lua binaries from official lua.org source,
# with NO SourceForge and NO user-machine compiler — the acquisition path chosen after
# the Cloudflare/trust finding (see docs .../tooling-risks-and-gaps.md Gap 2.2, Risk 1.1).
#
# Recipe reproduces dyne/luabinaries' proven MinGW cross-compile (their root Makefile),
# with two deliberate departures:
#   * source is fetched + SHA-256-pinned from lua.org (canonical), not a bundled tarball;
#   * NO upx packing — dyne's CI runs `upx -9`, which yields a 25 KB exe but is a well-known
#     antivirus false-positive trigger. Our build ships the plain (stripped) binary instead.
#
# Usage: build-lua-windows-mingw.sh [destdir]     (default: ./build/win64 under the repo)
# Pass threshold:
#   - exits 0
#   - produces lua54.exe + lua54.dll + luac54.exe, each a valid PE32+ (via `file`)
#   - if `wine` is available: lua54.exe -v prints "Lua 5.4.8" (else defer to the win11 VM)
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
LUA_VERSION="5.4.8"
LUA_ABI="54"                       # lua54.dll / lua54.exe
LUA_TARBALL="lua-${LUA_VERSION}.tar.gz"
PRIMARY_URL="https://www.lua.org/ftp/${LUA_TARBALL}"
MIRROR_URL="https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/${LUA_TARBALL}"
# Same canonical lua.org pin used by build-lua-posix.sh (TOOLING-00-01):
SHA256_PIN="4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae"
TARGET="x86_64-w64-mingw32"

# ── Arguments ─────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST_DIR="$(realpath -m "${1:-$SCRIPT_DIR/build/win64}")"
mkdir -p "$DEST_DIR"

# ── Toolchain presence check ──────────────────────────────────────────────────
missing=()
for t in gcc ar ranlib strip; do
    command -v "${TARGET}-${t}" >/dev/null 2>&1 || missing+=("${TARGET}-${t}")
done
if [[ ${#missing[@]} -gt 0 ]]; then
    echo "ERROR: missing MinGW cross toolchain: ${missing[*]}" >&2
    echo "  Install on Debian/Ubuntu: sudo apt-get install -y gcc-mingw-w64-x86-64" >&2
    exit 1
fi
echo "→ Toolchain: $(command -v ${TARGET}-gcc)  ($(${TARGET}-gcc -dumpversion))"

# ── Download-and-verify (mirrors build-lua-posix.sh) ─────────────────────────
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/lunar-spikes"
mkdir -p "$CACHE_DIR"
CACHED_ARCHIVE="$CACHE_DIR/$LUA_TARBALL"

download_and_verify() {
    local url="$1" dest_part="${CACHED_ARCHIVE}.part"
    echo "→ Downloading $url ..."
    if ! curl -fL --retry 3 -o "$dest_part" "$url"; then rm -f "$dest_part"; return 1; fi
    local actual; actual="$(sha256sum "$dest_part" | awk '{print $1}')"
    if [[ "$actual" != "$SHA256_PIN" ]]; then
        echo "SHA-256 mismatch — aborting." >&2
        echo "  expected: $SHA256_PIN" >&2
        echo "  actual:   $actual" >&2
        rm -f "$dest_part"; exit 1
    fi
    mv "$dest_part" "$CACHED_ARCHIVE"
    echo "✓ SHA-256 verified: $actual"
}

if [[ -f "$CACHED_ARCHIVE" ]] && \
   [[ "$(sha256sum "$CACHED_ARCHIVE" | awk '{print $1}')" == "$SHA256_PIN" ]]; then
    echo "→ Cache hit: $CACHED_ARCHIVE (verified)"
else
    rm -f "$CACHED_ARCHIVE"
    download_and_verify "$PRIMARY_URL" || download_and_verify "$MIRROR_URL"
fi

# ── Extract ───────────────────────────────────────────────────────────────────
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
echo "→ Extracting to $WORK_DIR ..."
tar -xzf "$CACHED_ARCHIVE" -C "$WORK_DIR"
SRC_DIR="$WORK_DIR/lua-${LUA_VERSION}"

# ── Make CC/CFLAGS/LDFLAGS/LIBS overridable (dyne's sed) ──────────────────────
sed -i -e 's/^CC=/CC?=/' -e 's/^LIBS=/LIBS?=/' \
       -e 's/^CFLAGS=/CFLAGS?=/' -e 's/^LDFLAGS=/LDFLAGS?=/' "$SRC_DIR/src/Makefile"

# ── Cross-compile with the stock `mingw` target ──────────────────────────────
# Lua's own `mingw` target builds lua${ABI}.dll (LUA_BUILD_AS_DLL), lua.exe, luac.exe.
echo "→ Cross-compiling lua ${LUA_VERSION} for Windows x64 (mingw, no upx) ..."
make -C "$SRC_DIR" \
    CC="${TARGET}-gcc" \
    AR="${TARGET}-ar rcu" \
    RANLIB="${TARGET}-ranlib" \
    CFLAGS="-O3 -mthreads" \
    LDFLAGS="-L/usr/${TARGET}/lib" \
    LIBS="-l:libm.a -l:libpthread.a -lssp" \
    mingw

# ── Strip + collect artifacts ─────────────────────────────────────────────────
"${TARGET}-strip" "$SRC_DIR/src/lua.exe" "$SRC_DIR/src/luac.exe"
cp "$SRC_DIR/src/lua.exe"            "$DEST_DIR/lua${LUA_ABI}.exe"
cp "$SRC_DIR/src/luac.exe"           "$DEST_DIR/luac${LUA_ABI}.exe"
cp "$SRC_DIR/src/lua${LUA_ABI}.dll"  "$DEST_DIR/lua${LUA_ABI}.dll"

# ── Verify (PASS/FAIL) ────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
echo " Built artifacts in: $DEST_DIR"
echo "═══════════════════════════════════════════════════"
fail=0
for f in "lua${LUA_ABI}.exe" "luac${LUA_ABI}.exe" "lua${LUA_ABI}.dll"; do
    path="$DEST_DIR/$f"
    ftype="$(file -b "$path")"
    printf '  %-14s %8s B  %s\n' "$f" "$(stat -c%s "$path")" "$ftype"
    case "$ftype" in
        PE32+*) : ;;
        *) echo "    [FAIL] not a PE32+ binary"; fail=1 ;;
    esac
done
echo ""
echo "  SHA-256:"
( cd "$DEST_DIR" && sha256sum "lua${LUA_ABI}.exe" "luac${LUA_ABI}.exe" "lua${LUA_ABI}.dll" | sed 's/^/    /' )

# ── Optional functional check via wine (else defer to the win11 VM) ──────────
echo ""
if command -v wine >/dev/null 2>&1; then
    echo "→ Functional check via wine: lua${LUA_ABI}.exe -v"
    banner="$(cd "$DEST_DIR" && wine "lua${LUA_ABI}.exe" -v 2>/dev/null || true)"
    echo "    $banner"
    if [[ "$banner" == *"Lua ${LUA_VERSION}"* ]]; then
        echo "    [PASS] banner reports Lua ${LUA_VERSION}"
    else
        echo "    [FAIL] expected 'Lua ${LUA_VERSION}' banner"; fail=1
    fi
else
    echo "→ wine not present — run lua${LUA_ABI}.exe -v on the win11 VM to confirm the banner."
fi

echo ""
if [[ $fail -eq 0 ]]; then
    echo "=== RESULT: PASS === self-built Windows Lua ${LUA_VERSION} from lua.org, no SourceForge."
else
    echo "=== RESULT: FAIL ==="; exit 1
fi
