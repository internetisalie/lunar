#!/usr/bin/env bash
# TOOLING-00-01: POSIX PUC-Lua source-build spike
# Executable specification for TOOLING-04's SourceBuildStrategy.
# Usage: build-lua-posix.sh <prefix>
# Design: docs/features/tooling/00-de-risking/design.md §2.1, §3.1
# Pass threshold (TC 1):
#   - exits 0
#   - <prefix>/bin/lua -v prints "Lua 5.4.8"
#   - package.path begins with <prefix>/share/lua/5.4/?.lua
#   - ldd <prefix>/bin/lua contains no "readline"
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
LUA_VERSION="5.4.8"
LUA_TARBALL="lua-${LUA_VERSION}.tar.gz"
PRIMARY_URL="https://www.lua.org/ftp/${LUA_TARBALL}"
MIRROR_URL="https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/${LUA_TARBALL}"
# SHA-256 pin recorded from lua.org/ftp listing (TOOLING-00-01):
SHA256_PIN="4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae"

# ── Arguments ─────────────────────────────────────────────────────────────────
if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <prefix>" >&2
    exit 1
fi
PREFIX="$(realpath "$1")"

# ── Cache directory (§3.1) ───────────────────────────────────────────────────
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/lunar-spikes"
mkdir -p "$CACHE_DIR"
CACHED_ARCHIVE="$CACHE_DIR/$LUA_TARBALL"

# ── Download-and-verify (design §3.1) ────────────────────────────────────────
download_and_verify() {
    local url="$1"
    local dest_part="${CACHED_ARCHIVE}.part"

    echo "→ Downloading $url ..."
    if ! curl -fL --retry 3 -o "$dest_part" "$url"; then
        rm -f "$dest_part"
        return 1
    fi

    local actual
    actual="$(sha256sum "$dest_part" | awk '{print $1}')"

    if [[ -z "$SHA256_PIN" ]]; then
        echo "SHA-256 pin is empty. Computed hash:" >&2
        echo "  $actual" >&2
        echo "Set SHA256_PIN in this script to that value and re-run." >&2
        rm -f "$dest_part"
        exit 1
    fi

    if [[ "$actual" != "$SHA256_PIN" ]]; then
        echo "SHA-256 mismatch — aborting." >&2
        echo "  expected: $SHA256_PIN" >&2
        echo "  actual:   $actual" >&2
        rm -f "$dest_part"
        exit 1
    fi

    mv "$dest_part" "$CACHED_ARCHIVE"
    echo "✓ SHA-256 verified: $actual"
}

# ── Cache-hit check (§3.1 step 1) ────────────────────────────────────────────
if [[ -f "$CACHED_ARCHIVE" ]]; then
    actual="$(sha256sum "$CACHED_ARCHIVE" | awk '{print $1}')"
    if [[ "$actual" == "$SHA256_PIN" ]]; then
        echo "→ Cache hit: $CACHED_ARCHIVE (verified)"
    else
        echo "→ Cache file present but hash mismatch — re-downloading"
        rm -f "$CACHED_ARCHIVE"
        download_and_verify "$PRIMARY_URL" || download_and_verify "$MIRROR_URL"
    fi
else
    download_and_verify "$PRIMARY_URL" || download_and_verify "$MIRROR_URL"
fi

# ── Extract ───────────────────────────────────────────────────────────────────
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "→ Extracting to $WORK_DIR ..."
tar -xzf "$CACHED_ARCHIVE" -C "$WORK_DIR"
SRC_DIR="$WORK_DIR/lua-${LUA_VERSION}"

# ── Patch src/luaconf.h (design §2.1 step 4) ─────────────────────────────────
LUACONF="$SRC_DIR/src/luaconf.h"
echo "→ Patching $LUACONF with prefix-baked paths ..."
# Insert the block immediately before the final #endif
PATCH_BLOCK="
#undef LUA_PATH_DEFAULT
#define LUA_PATH_DEFAULT \"${PREFIX}/share/lua/5.4/?.lua;${PREFIX}/share/lua/5.4/?/init.lua;./?.lua;./?/init.lua\"
#undef LUA_CPATH_DEFAULT
#define LUA_CPATH_DEFAULT \"${PREFIX}/lib/lua/5.4/?.so;${PREFIX}/lib/lua/5.4/loadall.so;./?.so\""

# Find the last #endif line and insert before it
python3 - <<PYEOF
import re, pathlib
path = pathlib.Path("$LUACONF")
text = path.read_text()
# Find the last #endif in the file
idx = text.rfind('#endif')
if idx == -1:
    raise SystemExit("Could not find final #endif in luaconf.h")
patched = text[:idx] + """$PATCH_BLOCK
""" + text[idx:]
path.write_text(patched)
print("  Patched luaconf.h successfully")
PYEOF

# ── Compile per TU (design §2.1 steps 5–6) ───────────────────────────────────
CC="gcc"
CFLAGS="-O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3"
LDFLAGS="-Wl,-E -ldl -lm"

BUILD_DIR="$SRC_DIR/build"
mkdir -p "$BUILD_DIR"

# macOS variant (NOT executed — no macOS host; documented per design §2.1):
# CC=cc
# CFLAGS="-O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3"
# LDFLAGS="-lm"
# (no -Wl,-E, no -ldl)

echo "→ Compiling per TU ..."
# Collect all src/*.c sorted, excluding onelua.c
mapfile -t SOURCES < <(find "$SRC_DIR/src" -maxdepth 1 -name '*.c' ! -name 'onelua.c' | sort)

for src in "${SOURCES[@]}"; do
    name="$(basename "$src" .c)"
    echo "  cc $name.c"
    $CC $CFLAGS -c -o "$BUILD_DIR/${name}.o" "$src"
done

# ── Archive (design §2.1 step 7) ─────────────────────────────────────────────
echo "→ Building liblua54.a ..."
# All objects except lua.o and luac.o go into the static library
mapfile -t LIB_OBJS < <(
    find "$BUILD_DIR" -name '*.o' \
        ! -name 'lua.o' ! -name 'luac.o' \
        | sort
)
ar rcu "$BUILD_DIR/liblua54.a" "${LIB_OBJS[@]}"
ranlib "$BUILD_DIR/liblua54.a"
echo "  Built liblua54.a ($(du -h "$BUILD_DIR/liblua54.a" | cut -f1))"

# ── Link (design §2.1 step 8) ────────────────────────────────────────────────
mkdir -p "$PREFIX/bin"
echo "→ Linking bin/lua ..."
$CC -o "$PREFIX/bin/lua" "$BUILD_DIR/lua.o" "$BUILD_DIR/liblua54.a" $LDFLAGS
echo "→ Linking bin/luac ..."
$CC -o "$PREFIX/bin/luac" "$BUILD_DIR/luac.o" "$BUILD_DIR/liblua54.a" $LDFLAGS

# ── Install (design §2.1 step 9) ─────────────────────────────────────────────
echo "→ Installing headers and library ..."
mkdir -p \
    "$PREFIX/include" \
    "$PREFIX/lib" \
    "$PREFIX/lib/lua/5.4" \
    "$PREFIX/share/lua/5.4"

# Copy headers — use patched luaconf.h from SRC_DIR/src (already patched above)
for hdr in lua.h luaconf.h lualib.h lauxlib.h lua.hpp; do
    cp "$SRC_DIR/src/$hdr" "$PREFIX/include/$hdr"
done

# Install static library
cp "$BUILD_DIR/liblua54.a" "$PREFIX/lib/liblua54.a"

echo ""
echo "═══════════════════════════════════════════════════"
echo " Build complete: $PREFIX"
echo "═══════════════════════════════════════════════════"
echo ""
echo "TC 1 verification:"
echo "  lua -v:"
"$PREFIX/bin/lua" -v
echo "  package.path:"
"$PREFIX/bin/lua" -e 'print(package.path)'
echo "  readline linkage (expect NO_READLINE_LINKAGE):"
ldd "$PREFIX/bin/lua" | grep -i readline || echo "NO_READLINE_LINKAGE"
