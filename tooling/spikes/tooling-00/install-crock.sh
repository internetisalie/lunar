#!/usr/bin/env bash
# TOOLING-00-04: C-rock install & failure UX spike
# Provisions LuaRocks 3.13.0 into the TOOLING-00-01 prefix,
# then exercises Run A (cc present) and Run B (cc absent, fresh prefix).
# Usage: install-crock.sh <prefix>
# Design: docs/features/tooling/00-de-risking/design.md §2.4, §3.1, §3.2
# Pass threshold (TC 4): Run A exits 0; busted --version prints 2.x
# Pass threshold (TC 5): Run B exits non-zero; output captured for heuristic
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
LUAROCKS_VERSION="3.13.0"
LUAROCKS_TARBALL="luarocks-${LUAROCKS_VERSION}.tar.gz"
LUAROCKS_URL="https://luarocks.github.io/luarocks/releases/${LUAROCKS_TARBALL}"
# SHA-256 pin for LuaRocks 3.13.0 tarball (recorded from actual download):
LUAROCKS_SHA256="245bf6ec560c042cb8948e3d661189292587c5949104677f1eecddc54dbe7e37"

# Pin and source for Lua 5.4.8 (reused for Run B fresh prefix)
LUA_VERSION="5.4.8"
LUA_TARBALL="lua-${LUA_VERSION}.tar.gz"
LUA_PRIMARY_URL="https://www.lua.org/ftp/${LUA_TARBALL}"
LUA_MIRROR_URL="https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/${LUA_TARBALL}"
LUA_SHA256="4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae"

BUSTED_VERSION="2.2.0-1"

# ── Arguments ─────────────────────────────────────────────────────────────────
if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <prefix>" >&2
    exit 1
fi
PREFIX="$(realpath "$1")"

if [[ ! -x "$PREFIX/bin/lua" ]]; then
    echo "Error: $PREFIX/bin/lua not found. Run build-lua-posix.sh <prefix> first." >&2
    exit 1
fi

# ── Cache directory (§3.1) ───────────────────────────────────────────────────
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/lunar-spikes"
mkdir -p "$CACHE_DIR"
CACHED_LUAROCKS="$CACHE_DIR/$LUAROCKS_TARBALL"
CACHED_LUA="$CACHE_DIR/$LUA_TARBALL"

# ── Generic: download-and-verify (design §3.1) ────────────────────────────────
download_and_verify() {
    local url="$1"
    local pin="$2"
    local dest="$3"
    local dest_part="${dest}.part"

    echo "→ Downloading $url ..."
    if ! curl -fL --retry 3 -o "$dest_part" "$url"; then
        rm -f "$dest_part"
        return 1
    fi

    local actual
    actual="$(sha256sum "$dest_part" | awk '{print $1}')"

    if [[ "$actual" != "$pin" ]]; then
        echo "SHA-256 mismatch — aborting." >&2
        echo "  expected: $pin" >&2
        echo "  actual:   $actual" >&2
        rm -f "$dest_part"
        exit 1
    fi

    mv "$dest_part" "$dest"
    echo "✓ SHA-256 verified: $actual"
}

verify_cache() {
    local path="$1"
    local pin="$2"
    local url="$3"
    local mirror="${4:-}"
    if [[ -f "$path" ]]; then
        local actual
        actual="$(sha256sum "$path" | awk '{print $1}')"
        if [[ "$actual" == "$pin" ]]; then
            echo "→ Cache hit: $path (verified)"
            return 0
        fi
        echo "→ Cache stale — re-downloading"
        rm -f "$path"
    fi
    download_and_verify "$url" "$pin" "$path" \
        || { [[ -n "$mirror" ]] && download_and_verify "$mirror" "$pin" "$path"; }
}

# ── Ensure archives are cached ────────────────────────────────────────────────
verify_cache "$CACHED_LUAROCKS" "$LUAROCKS_SHA256" "$LUAROCKS_URL"
verify_cache "$CACHED_LUA"     "$LUA_SHA256"     "$LUA_PRIMARY_URL" "$LUA_MIRROR_URL"

# ── Helper: install luarocks into a prefix that already has lua ───────────────
install_luarocks_into() {
    local rocks_prefix="$1"
    local lua_bin_dir="$2"   # where lua binary lives (for --with-lua)
    local work_dir
    work_dir="$(mktemp -d)"

    tar -xzf "$CACHED_LUAROCKS" -C "$work_dir"
    local src="$work_dir/luarocks-${LUAROCKS_VERSION}"

    (cd "$src" && ./configure \
        --prefix="$rocks_prefix" \
        --with-lua="$rocks_prefix" \
        --with-lua-bin="$lua_bin_dir" \
        --with-lua-include="$(dirname "$lua_bin_dir")/include" \
        2>&1)

    (cd "$src" && make build 2>&1)
    (cd "$src" && make install 2>&1)
    rm -rf "$work_dir"
    echo "LuaRocks installed: $("$rocks_prefix/bin/luarocks" --version | head -1)"
}

# ── Helper: build lua 5.4.8 into a prefix ────────────────────────────────────
build_lua_into() {
    local dest_prefix="$1"
    local work_dir
    work_dir="$(mktemp -d)"

    echo "→ Extracting Lua $LUA_VERSION ..."
    tar -xzf "$CACHED_LUA" -C "$work_dir"
    local src="$work_dir/lua-${LUA_VERSION}"
    local luaconf="$src/src/luaconf.h"

    # Patch luaconf.h with baked paths
    python3 - <<PYEOF
import pathlib
path = pathlib.Path("$luaconf")
text = path.read_text()
patch = """
#undef LUA_PATH_DEFAULT
#define LUA_PATH_DEFAULT "${dest_prefix}/share/lua/5.4/?.lua;${dest_prefix}/share/lua/5.4/?/init.lua;./?.lua;./?/init.lua"
#undef LUA_CPATH_DEFAULT
#define LUA_CPATH_DEFAULT "${dest_prefix}/lib/lua/5.4/?.so;${dest_prefix}/lib/lua/5.4/loadall.so;./?.so"
"""
idx = text.rfind('#endif')
path.write_text(text[:idx] + patch + text[idx:])
PYEOF

    local BUILD="$src/build"
    mkdir -p "$BUILD"
    local CC="gcc"
    local CFLAGS="-O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3"
    local LDFLAGS="-Wl,-E -ldl -lm"

    mapfile -t SOURCES < <(find "$src/src" -maxdepth 1 -name '*.c' ! -name 'onelua.c' | sort)
    for csrc in "${SOURCES[@]}"; do
        local name
        name="$(basename "$csrc" .c)"
        $CC $CFLAGS -c -o "$BUILD/${name}.o" "$csrc"
    done

    mapfile -t LIB_OBJS < <(
        find "$BUILD" -name '*.o' ! -name 'lua.o' ! -name 'luac.o' | sort
    )
    ar rcu "$BUILD/liblua54.a" "${LIB_OBJS[@]}"
    ranlib "$BUILD/liblua54.a"

    mkdir -p "$dest_prefix/bin" "$dest_prefix/include" \
             "$dest_prefix/lib" "$dest_prefix/lib/lua/5.4" \
             "$dest_prefix/share/lua/5.4"

    $CC -o "$dest_prefix/bin/lua"  "$BUILD/lua.o"  "$BUILD/liblua54.a" $LDFLAGS
    $CC -o "$dest_prefix/bin/luac" "$BUILD/luac.o" "$BUILD/liblua54.a" $LDFLAGS

    for hdr in lua.h luaconf.h lualib.h lauxlib.h lua.hpp; do
        cp "$src/src/$hdr" "$dest_prefix/include/$hdr"
    done
    cp "$BUILD/liblua54.a" "$dest_prefix/lib/liblua54.a"

    rm -rf "$work_dir"
    echo "Lua built: $("$dest_prefix/bin/lua" -v 2>&1)"
}

# ═════════════════════════════════════════════════════════════════════════════
# Run A: gcc present — use the provided prefix (design §2.4 step 2, TC 4)
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════════════"
echo " Run A: install LuaRocks + busted (cc present)"
echo " prefix: $PREFIX"
echo "════════════════════════════════════════════════"

# Install LuaRocks into PREFIX (with-lua=PREFIX — lua is already there)
if [[ ! -x "$PREFIX/bin/luarocks" ]]; then
    install_luarocks_into "$PREFIX" "$PREFIX/bin"
else
    echo "→ LuaRocks already installed: $("$PREFIX/bin/luarocks" --version | head -1)"
fi

# Install busted (force so re-runs work)
echo "→ luarocks install busted $BUSTED_VERSION --force ..."
RUN_A_EXIT=0
"$PREFIX/bin/luarocks" install "busted" "$BUSTED_VERSION" --force || RUN_A_EXIT=$?

if [[ "$RUN_A_EXIT" -ne 0 ]]; then
    echo "✗ Run A: FAILED (exit $RUN_A_EXIT)" >&2
    exit 1
fi
echo "✓ Run A: exit 0"

echo ""
echo "TC 4 verification:"
echo "  busted --version:"
BUSTED_BIN="$PREFIX/bin/busted"
if [[ ! -x "$BUSTED_BIN" ]]; then
    echo "✗ TC 4 FAILED: $BUSTED_BIN not found" >&2
    exit 1
fi
if "$BUSTED_BIN" --version; then
    echo "✓ TC 4: busted --version exits 0"
else
    echo "✗ TC 4 FAILED: busted --version non-zero" >&2
    exit 1
fi

# ═════════════════════════════════════════════════════════════════════════════
# Run B: cc absent — fresh separate prefix (design §2.4 step 3, TC 5)
# ═════════════════════════════════════════════════════════════════════════════
PREFIX_B="${PREFIX}-run-b"
rm -rf "$PREFIX_B"

echo ""
echo "════════════════════════════════════════════════"
echo " Run B: fresh prefix, CC=/nonexistent/cc"
echo " prefix: $PREFIX_B"
echo "════════════════════════════════════════════════"

echo "→ Building fresh Lua into $PREFIX_B ..."
build_lua_into "$PREFIX_B"

echo "→ Installing LuaRocks into $PREFIX_B ..."
install_luarocks_into "$PREFIX_B" "$PREFIX_B/bin"

RUN_B_OUTPUT_FILE="/tmp/run-b-output.txt"
echo ""
echo "→ Running: luarocks install busted $BUSTED_VERSION CC=/nonexistent/cc LD=/nonexistent/cc"

# Capture combined stdout+stderr; allow non-zero exit (expected)
set +e
"$PREFIX_B/bin/luarocks" install "busted" "$BUSTED_VERSION" \
    CC=/nonexistent/cc LD=/nonexistent/cc \
    2>&1 | tee "$RUN_B_OUTPUT_FILE"
RUN_B_EXIT="${PIPESTATUS[0]}"
set -e

echo ""
echo "Run B exit code: $RUN_B_EXIT"

if [[ "$RUN_B_EXIT" -eq 0 ]]; then
    echo "⚠ Run B unexpectedly succeeded. Heuristic needs revision." >&2
else
    echo "✓ Run B: non-zero exit ($RUN_B_EXIT) — as expected (TC 5)"
fi

echo ""
echo "════════════════════════════════════════════════"
echo " Spike complete."
echo " Run A: $PREFIX (busted installed, version above)"
echo " Run B exit: $RUN_B_EXIT"
echo " Run B output: $RUN_B_OUTPUT_FILE"
echo "════════════════════════════════════════════════"
