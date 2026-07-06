#!/usr/bin/env bash
# TOOLING-00-03: LuaJIT git+make spike
#
# Usage: build-luajit-posix.sh <prefix>
#
# Clones LuaJIT v2.1 (keeping .git for version metadata), builds with plain
# POSIX make, and hand-installs per hererocks layout.  The .git dir is required
# by LuaJIT's Makefile — it derives its rolling version string from git metadata.
#
# Design §2.3 method, verbatim:
#   Step 1. git clone --depth=1 --branch v2.1 (keep .git)
#   Step 2. make -C <src> -j$(nproc)
#   Step 3. Hand-install: luajit -> bin/lua ; libluajit.a -> lib/ ; jit/ -> share/lua/5.1/jit/
#
# Pass threshold (TC 3):
#   <prefix>/bin/lua -v  first line starts with "LuaJIT 2.1"
#   <prefix>/bin/lua -e 'print(jit.version)'  exits 0
#
# Idempotent: if <src> already exists and was already built, re-uses it.

set -euo pipefail

# ---------------------------------------------------------------------------
# Args
# ---------------------------------------------------------------------------
if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <prefix>" >&2
    exit 1
fi

PREFIX="$(realpath -m "$1")"
SRC_DIR="${PREFIX%/}-luajit-src"
LUAJIT_REPO="https://github.com/LuaJIT/LuaJIT"
LUAJIT_BRANCH="v2.1"

echo "=== TOOLING-00-03: LuaJIT git+make spike ==="
echo "Prefix : ${PREFIX}"
echo "Source : ${SRC_DIR}"
echo ""

# ---------------------------------------------------------------------------
# Step 1: Clone (depth=1, keep .git)
# ---------------------------------------------------------------------------
if [[ -d "${SRC_DIR}/.git" ]]; then
    echo "[step 1] Source dir already exists with .git — reusing."
else
    echo "[step 1] Cloning LuaJIT branch ${LUAJIT_BRANCH} ..."
    git clone --depth=1 --branch "${LUAJIT_BRANCH}" "${LUAJIT_REPO}" "${SRC_DIR}"
fi

# Verify .git is present (required for version derivation)
if [[ ! -d "${SRC_DIR}/.git" ]]; then
    echo "ERROR: .git directory missing in ${SRC_DIR}; the LuaJIT Makefile requires it." >&2
    exit 1
fi

echo "[step 1] Clone OK. Git head:"
git -C "${SRC_DIR}" log --oneline -1

# ---------------------------------------------------------------------------
# Step 2: Build with plain POSIX make (no XCFLAGS, no 5.2-compat)
# ---------------------------------------------------------------------------
echo ""
echo "[step 2] Building with make -j$(nproc) ..."
make -C "${SRC_DIR}" -j"$(nproc)"
echo "[step 2] Build OK."

# Confirm the built binary exists
LUAJIT_BIN="${SRC_DIR}/src/luajit"
LUAJIT_LIB="${SRC_DIR}/src/libluajit.a"
JIT_DIR="${SRC_DIR}/src/jit"

for f in "${LUAJIT_BIN}" "${LUAJIT_LIB}" "${JIT_DIR}"; do
    if [[ ! -e "$f" ]]; then
        echo "ERROR: Expected build artifact missing: $f" >&2
        exit 1
    fi
done

echo "[step 2] Built artifacts:"
ls -lh "${LUAJIT_BIN}" "${LUAJIT_LIB}"
echo "         jit/ files: $(ls "${JIT_DIR}"/*.lua 2>/dev/null | wc -l) .lua files"

# ---------------------------------------------------------------------------
# Step 3: Hand-install per hererocks layout (no make install)
# ---------------------------------------------------------------------------
echo ""
echo "[step 3] Hand-installing into ${PREFIX} ..."

mkdir -p "${PREFIX}/bin"
mkdir -p "${PREFIX}/lib"
mkdir -p "${PREFIX}/share/lua/5.1/jit"

# luajit -> bin/lua  (hererocks installs the luajit binary as 'lua')
cp -f "${LUAJIT_BIN}" "${PREFIX}/bin/lua"
chmod 755 "${PREFIX}/bin/lua"

# libluajit.a -> lib/libluajit-5.1.a
cp -f "${LUAJIT_LIB}" "${PREFIX}/lib/libluajit-5.1.a"

# src/jit/ -> share/lua/5.1/jit/
cp -r "${JIT_DIR}/." "${PREFIX}/share/lua/5.1/jit/"

echo "[step 3] Installed:"
ls -lh "${PREFIX}/bin/lua"
ls -lh "${PREFIX}/lib/libluajit-5.1.a"
echo "         jit/ files in prefix: $(ls "${PREFIX}/share/lua/5.1/jit/"*.lua 2>/dev/null | wc -l)"

# ---------------------------------------------------------------------------
# Verify pass threshold
# ---------------------------------------------------------------------------
echo ""
echo "=== Verifying TC 3 pass threshold ==="

LUA_V_OUT="$("${PREFIX}/bin/lua" -v 2>&1 || true)"
echo "lua -v output: ${LUA_V_OUT}"

FIRST_LINE="$(echo "${LUA_V_OUT}" | head -n1)"
if echo "${FIRST_LINE}" | grep -qE "^LuaJIT 2\.1"; then
    echo "PASS: lua -v first line starts with 'LuaJIT 2.1'"
else
    echo "FAIL: lua -v first line does not start with 'LuaJIT 2.1'" >&2
    echo "      Got: ${FIRST_LINE}" >&2
    exit 1
fi

JIT_VERSION_OUT="$("${PREFIX}/bin/lua" -e 'print(jit.version)' 2>&1)"
JIT_EXIT=$?
echo "jit.version output: ${JIT_VERSION_OUT}"
if [[ ${JIT_EXIT} -eq 0 ]]; then
    echo "PASS: jit.version exits 0"
else
    echo "FAIL: jit.version exited with code ${JIT_EXIT}" >&2
    exit 1
fi

echo ""
echo "=== TOOLING-00-03: ALL CHECKS PASSED ==="
echo "Verdict: PASS"
echo ""
echo "Recorded version: ${FIRST_LINE}"
echo "jit.version     : ${JIT_VERSION_OUT}"
