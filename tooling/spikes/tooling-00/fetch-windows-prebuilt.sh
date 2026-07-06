#!/usr/bin/env bash
# TOOLING-00-02: Windows prebuilt-provisioning spike — Stage 1 (Linux acquisition)
# Downloads LuaBinaries Win64 + standalone LuaRocks, verifies magic bytes & sha256,
# extracts into <destdir>/bin, and asserts the required layout.
# Executable specification for TOOLING-04's ReleaseBinaryStrategy (Windows story).
#
# Usage: fetch-windows-prebuilt.sh <destdir>
# Design: docs/features/tooling/00-de-risking/design.md §2.2 (stage 1), §4
# Pass threshold (TC 2 — Linux assertions):
#   - exits 0
#   - <destdir>/bin/lua54.exe  exists and is non-empty
#   - <destdir>/bin/lua54.dll  exists and is non-empty
#   - <destdir>/bin/luarocks.exe exists and is non-empty
#
# NOTE on SourceForge URLs (§4): the /download redirect path returns a JavaScript-rendered
# HTML page to non-browser clients even with -L; use downloads.sourceforge.net directly.
# The magic-bytes guard (PK\x03\x04) catches any HTML error page before hashing.
set -euo pipefail

# ── Configuration ─────────────────────────────────────────────────────────────
LUA_VERSION="5.4.2"
LUA_ZIP="lua-${LUA_VERSION}_Win64_bin.zip"
# downloads.sourceforge.net serves the archive directly (no JS/cookie redirect):
LUA_URL="https://downloads.sourceforge.net/project/luabinaries/${LUA_VERSION}/Tools%20Executables/${LUA_ZIP}"
# Confirmed SourceForge group name: "Tools Executables" (verified 2026-07-06, TOOLING-00-02)
# SHA-256 pin recorded by Stage 1 execution (TOOLING-00-02):
LUA_SHA256="5f1e1385ed95a3643f7ed67c4f3767942b2f0f388b66f63e5667e9c3d96293f5"

ROCKS_VERSION="3.13.0"
ROCKS_ZIP="luarocks-${ROCKS_VERSION}-windows-64.zip"
ROCKS_URL="https://luarocks.github.io/luarocks/releases/${ROCKS_ZIP}"
# The zip contains a root dir "luarocks-3.13.0-windows-64/" that is stripped on extract.
# SHA-256 pin recorded by Stage 1 execution (TOOLING-00-02):
ROCKS_SHA256="0897ade5d459d55cd1962a948153745a6749feb345403c68aaa9207388557ab9"

# ── Arguments ─────────────────────────────────────────────────────────────────
if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <destdir>" >&2
    exit 1
fi
DESTDIR="$(realpath "$1")"
BINDIR="${DESTDIR}/bin"

# ── Cache directory (§3.1) ───────────────────────────────────────────────────
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/lunar-spikes"
mkdir -p "$CACHE_DIR" "$BINDIR"

# ── Helpers ───────────────────────────────────────────────────────────────────
# download_verify <url> <filename> <sha256pin>
# Implements §3.1: cache hit → reuse; miss → download → magic-bytes → hash → move.
# Progress is written to stderr; the cached path is stored in global DOWNLOAD_PATH.
download_verify() {
    local url="$1"
    local filename="$2"
    local pin="$3"
    local cached="${CACHE_DIR}/${filename}"
    local part="${cached}.part"

    if [[ -f "$cached" ]]; then
        local existing_hash
        existing_hash="$(sha256sum "$cached" | awk '{print $1}')"
        if [[ "$existing_hash" == "$pin" ]]; then
            echo "  [cache] ${filename} — sha256 OK (reused)" >&2
            DOWNLOAD_PATH="$cached"
            return 0
        else
            echo "  [cache] ${filename} — hash mismatch, re-downloading" >&2
            rm -f "$cached"
        fi
    fi

    echo "  [download] ${filename} ..." >&2
    curl -fL --retry 3 --max-time 120 -o "$part" "$url" >&2

    # Magic-bytes check (design §4): ZIP signature PK\x03\x04 = hex 504b0304
    local magic
    magic="$(python3 -c "import sys; f=open('${part}','rb'); b=f.read(4); sys.stdout.write(b.hex())")"
    if [[ "$magic" != "504b0304" ]]; then
        echo "[ERROR] ${filename}: magic-bytes check FAILED" >&2
        echo "        Expected: 504b0304 (PK ZIP signature)" >&2
        echo "        Got:      ${magic}" >&2
        echo "        The server returned an HTML error page instead of a ZIP." >&2
        echo "        URL: ${url}" >&2
        rm -f "$part"
        exit 1
    fi
    echo "  [magic-bytes] ${filename} — PK ZIP signature OK (504b0304)" >&2

    # Verify sha256 (§3.1 step 3)
    if [[ -n "$pin" ]]; then
        local actual_hash
        actual_hash="$(sha256sum "$part" | awk '{print $1}')"
        if [[ "$actual_hash" != "$pin" ]]; then
            echo "[ERROR] ${filename}: sha256 mismatch" >&2
            echo "        expected: ${pin}" >&2
            echo "        actual:   ${actual_hash}" >&2
            rm -f "$part"
            exit 1
        fi
        echo "  [sha256] ${filename} — ${actual_hash} OK" >&2
    else
        local computed_hash
        computed_hash="$(sha256sum "$part" | awk '{print $1}')"
        echo "  [sha256] ${filename} — ${computed_hash}" >&2
        echo "[ERROR] No sha256 pin provided for ${filename}." >&2
        echo "        Pin the hash above and re-run." >&2
        rm -f "$part"
        exit 1
    fi

    mv "$part" "$cached"
    DOWNLOAD_PATH="$cached"
}

# assert_nonempty <path> <label>
assert_nonempty() {
    local path="$1"
    local label="$2"
    if [[ -f "$path" && -s "$path" ]]; then
        local size
        size="$(wc -c < "$path")"
        printf "  [PASS] %-30s  %d bytes\n" "$label" "$size"
        return 0
    else
        printf "  [FAIL] %-30s  missing or empty\n" "$label"
        return 1
    fi
}

# ── Stage 1a: Download LuaBinaries Win64 ──────────────────────────────────────
echo "=== LuaBinaries Win64 (lua ${LUA_VERSION}) ==="
echo "  URL: ${LUA_URL}"
DOWNLOAD_PATH=""
download_verify "$LUA_URL" "$LUA_ZIP" "$LUA_SHA256"
LUA_ARCHIVE="$DOWNLOAD_PATH"

# ── Stage 1b: Download LuaRocks standalone ────────────────────────────────────
echo ""
echo "=== LuaRocks standalone (${ROCKS_VERSION}) ==="
echo "  URL: ${ROCKS_URL}"
DOWNLOAD_PATH=""
download_verify "$ROCKS_URL" "$ROCKS_ZIP" "$ROCKS_SHA256"
ROCKS_ARCHIVE="$DOWNLOAD_PATH"

# ── Stage 1c: Extract both into <destdir>/bin ─────────────────────────────────
echo ""
echo "=== Extracting into ${BINDIR} ==="

# Lua zip: flat archive (no root dir prefix per design §2.2)
echo "  [extract] lua zip ..."
unzip -o -q "$LUA_ARCHIVE" -d "$BINDIR"

# LuaRocks zip: has root dir "luarocks-3.13.0-windows-64/"; strip it on extraction
echo "  [extract] luarocks zip ..."
ROCKS_SUBDIR="luarocks-${ROCKS_VERSION}-windows-64"
ROCKS_TMP="${BINDIR}/.rocks_tmp"
unzip -o -q "$ROCKS_ARCHIVE" -d "$ROCKS_TMP"
if [[ -d "${ROCKS_TMP}/${ROCKS_SUBDIR}" ]]; then
    mv "${ROCKS_TMP}/${ROCKS_SUBDIR}/"* "$BINDIR/"
    rm -rf "$ROCKS_TMP"
else
    # Flat archive (no root dir) — move everything
    find "$ROCKS_TMP" -maxdepth 1 -mindepth 1 -exec mv {} "$BINDIR/" \;
    rm -rf "$ROCKS_TMP"
fi

# ── Stage 1d: Layout assertions ───────────────────────────────────────────────
echo ""
echo "=== Layout assertions ==="
PASS=true

assert_nonempty "${BINDIR}/lua54.exe"    "lua54.exe"    || PASS=false
assert_nonempty "${BINDIR}/lua54.dll"    "lua54.dll"    || PASS=false
assert_nonempty "${BINDIR}/luarocks.exe" "luarocks.exe" || PASS=false

echo ""
echo "=== SHA-256 pins (recorded for toolchain-feed.json) ==="
echo "  lua     (${LUA_ZIP}):    ${LUA_SHA256}"
echo "  luarocks (${ROCKS_ZIP}): ${ROCKS_SHA256}"

echo ""
echo "=== Directory listing: ${BINDIR} ==="
ls -lh "$BINDIR"

echo ""
if [[ "$PASS" == "true" ]]; then
    echo "=== RESULT: PASS ==="
    echo "    lua ${LUA_VERSION}: lua54.exe + lua54.dll present and non-empty."
    echo "    luarocks ${ROCKS_VERSION}: luarocks.exe present and non-empty."
    echo "    SourceForge group confirmed: 'Tools Executables'"
    echo "    Layout ready for Stage 2 (live VM execution — supervisor over VNC)."
    exit 0
else
    echo "=== RESULT: FAIL ===" >&2
    echo "    One or more required assets missing or empty." >&2
    exit 1
fi
