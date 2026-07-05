---
id: "TOOLING-RESEARCH-HEREROCKS"
title: "Research: Hererocks Internals Dossier"
type: "spec"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# Research: Hererocks Internals Dossier

## Overview

TOOLING-04 replaces the Python `hererocks` tool with a native Kotlin provisioning engine
(user decision, 2026-07-05: no Python dependency; generalize beyond lua/luarocks). This
dossier documents exactly what hererocks does — download sources, build procedures, prefix
layout, toolchain detection, caching — plus the prebuilt-binary landscape that lets the
native engine avoid compiling where possible.

Primary source: `hererocks.py` @ master (`0.26.0.dev0`, 3,319 lines), fetched 2026-07-05
from <https://raw.githubusercontent.com/luarocks/hererocks/master/hererocks.py>. The whole
tool is this single file; zero dependencies beyond Python stdlib + external `git` + a C
toolchain. All line-level claims verified against that source; items not verifiable are
flagged [UNVERIFIED].

## Findings / Key Components

### 1. Download sources

**PUC-Rio Lua** (`RioLua` class)
- Release URL pattern, tried over two mirrors:
  1. `https://www.lua.org/ftp/lua-{version}.tar.gz`
  2. `https://webserver2.tecgraf.puc-rio.br/lua/mirror/ftp/lua-{version}.tar.gz`
- Pre-releases (`work`/`alpha`/`beta`/`rc` in version): `https://www.lua.org/work/lua-{version}.tar.gz`
  (`-rcN` suffix stripped from the extracted dir name).
- Supported versions (hardcoded): 5.1, 5.1.1–5.1.5, 5.2.0–5.2.4, 5.3.0–5.3.6, 5.4.0–5.4.8, 5.5.0.
- Alias resolution is a **hardcoded table**, not a network query: `"5"→"5.5.0"`,
  `"5.1"→"5.1.5"`, `"5.1.0"→"5.1"` (5.1.0 shipped as `lua-5.1.tar.gz`), `"5.2"→"5.2.4"`,
  `"5.3"→"5.3.6"`, `"5.4"→"5.4.8"`, `"5.5"→"5.5.0"`, `"^"`/`"latest"` → `"5.5.0"`. A native
  implementation must own this table.
- Git installs: arg containing `@` → git; default repo `https://github.com/lua/lua` (sources
  in repo **root**, not `src/` — handled via `get_source_files_prefix()`); `repo@ref` for
  arbitrary repos; shallow `--depth=1 --branch=<ref>` unless commit-hash ref or dumb-HTTP
  host (shallow whitelist: `github.com`, `bitbucket.com`).
- **SHA-256 checksums for every archive are hardcoded in the script** and verified after
  download; mismatch aborts (unless `--ignore-checksums`).

**LuaJIT** (+ moonjit/RaptorJIT variants)
- Tarballs from **GitHub archives, not luajit.org**:
  `https://github.com/LuaJIT/LuaJIT/archive/v{version}.tar.gz` (2.0.1 → `v2.0.1-fixed.tar.gz`).
- Supported: 2.0.0–2.0.5, 2.1.0-beta1..beta3; aliases `"2"`/`"2.0"`/`"^"`/`"latest"` → 2.0.5,
  `"2.1"` → 2.1.0-beta3. **Badly stale** — luajit.org is rolling-release git-only now
  ("There are no release tarballs available"): `https://luajit.org/git/luajit.git`, mirror
  `https://github.com/LuaJIT/LuaJIT` (source: <https://luajit.org/download.html>). Modern
  usage is `--luajit @v2.1` (git branch).
- `needs_git_dir_for_build = True`: LuaJIT's build derives the rolling version from git
  metadata since upstream `50e0fa03c48c`, so `.git` must be kept in the build tree.
- moonjit: `https://github.com/moonjit/moonjit/archive/{version}.tar.gz` (2.1.1–2.2.0);
  RaptorJIT: `https://github.com/raptorjit/raptorjit/archive/v{version}.tar.gz` (1.0.0–1.0.3).

**LuaRocks**
- Base URL: `https://luarocks.github.io/luarocks/releases` (canonical mirror; identical at
  luarocks.org/releases).
- POSIX: `luarocks-{version}.tar.gz`; Windows (hererocks' choice): `luarocks-{version}-win32.zip`
  (source + `install.bat`, bundles Lua 5.1) — **not** the standalone binary zip (see §6).
- Supported: 2.0.8–2.4.4, 3.0.0–3.13.0; aliases `"3"`/`"^"`/`"latest"` → 3.13.0, each
  `"3.x"` → newest patch. Compat: Lua 5.3 needs LuaRocks ≥ 2.2.0; 5.4 ≥ 3.0.0; 5.5 ≥ 3.13.0.
- Default git repo: `https://github.com/luarocks/luarocks`.

### 2. Build procedure per target

**(a) PUC Lua on POSIX — hererocks does NOT use Lua's Makefile.** `RioLua.make()`
reimplements the build, one compiler invocation per translation unit:

1. Compiler `gcc` (`cc` on macOS); `-std=gnu99` added for 5.3/5.4/5.5.
2. CFLAGS = `-O2 -Wall -Wextra` + target defines + compat defines + user cflags:
   - linux/freebsd/macosx: `-DLUA_USE_POSIX -DLUA_USE_DLOPEN`; 5.2 adds
     `-DLUA_USE_STRTODHEX -DLUA_USE_AFORMAT -DLUA_USE_LONGLONG`; `-DLUA_USE_READLINE`
     unless `--no-readline`. (`LUA_USE_LINUX` is never used — expanded to constituents.)
   - Compat: 5.2 → `-DLUA_COMPAT_ALL`; 5.3 → `-DLUA_COMPAT_5_1`/`-DLUA_COMPAT_5_2`;
     5.4 → `-DLUA_COMPAT_5_3`; 5.5 → `-DLUA_COMPAT_MATHLIB`; 5.1 `--compat none` via
     `#undef LUA_COMPAT_*` injected into `luaconf.h`.
3. LDFLAGS: linux `-Wl,-E -ldl -lreadline` (5.1 also `-lhistory -lncurses`); freebsd
   `-Wl,-E -lreadline`; macosx `-lreadline`; always `-lm`.
4. **`patch_redefines()` rewrites `src/luaconf.h` before compiling**: splices
   `#undef/#define LUA_PATH_DEFAULT` / `LUA_CPATH_DEFAULT` with absolute prefix paths
   (bakes the prefix into the binary) + compat `#undef`s, in front of the final `#endif`.
5. Compile every `src/*.c` (sorted, excluding `onelua.c`): `gcc <cflags> -c -o x.o x.c`;
   `luac.o`/`print.o` use static cflags (minus `-DLUA_BUILD_AS_DLL` on Windows targets).
6. `ar rcu liblua5<X>.a <objs except lua.o,luac.o,print.o>` then `ranlib`.
7. Link: `gcc -o luac luac.o print.o liblua5X.a <lflags>`; `gcc -o lua lua.o liblua5X.a <lflags>`.
   (Git trees may lack `luac.c`/`print.c` → luac skipped.)

**(b) PUC Lua on Windows**
- Target default `vs`, or `mingw` when gcc on PATH and cl absent (`platform_to_lua_target`).
- MinGW: per-TU gcc with `-DLUA_BUILD_AS_DLL`; `gcc -shared -o lua5X.dll <libobjs>`;
  `strip --strip-unneeded`; `gcc -o lua.exe -s lua.o lua5X.dll`; static `.a` for luac.
- MSVC: `cl /nologo /MD /O2 /W3 /c /D_CRT_SECURE_NO_DEPRECATE` (+`-DLUA_BUILD_AS_DLL`) per
  `.c`; `link /nologo /DLL /out:lua5X.dll <objs>` (+ `mt` if manifest);
  `link /nologo /out:lua.exe lua.obj lua5X.lib`; `link /nologo /out:luac.exe luac.obj
  print.obj <objs>`.

**(c) LuaJIT** — uses upstream makefiles: POSIX `make` (FreeBSD `gmake`; mingw
`mingw32-make`, `SHELL=cmd` outside MSYS) with `XCFLAGS` (`-DLUAJIT_ENABLE_LUA52COMPAT` for
`--compat 5.2|all`); macOS sets `MACOSX_DEPLOYMENT_TARGET` if unset; MSVC edits
`src/msvcbuild.bat`'s `@set LJCOMPILE=` line then runs it under vcvars. No `make install` —
hand-copied: `luajit(.exe)` installed **as `bin/lua(.exe)`**, `libluajit.a →
lib/libluajit-5.1.a`, `libluajit.so → lib/libluajit-5.1.so.2`, `lua51.dll → bin/`, headers
incl. `luajit.h`, `jit/` → `share/lua/5.1/jit`.

**(d) LuaRocks** — POSIX: `./configure --prefix=<loc> --with-lua=<loc>`, `make build`
(plain `make` for 2.0.x), `make install`. Windows: `install.bat /P <loc>\luarocks /LUA
<loc> /F` + `/MW` (mingw Lua) + `/LV <5.x>` (≥2.0.13) + `/NOREG /Q` (≥2.1.2) + `/NOADMIN`
(flag support probed via `install.bat /?`); then `luarocks.bat`/`luarocks-admin.bat`
copied to `<loc>\bin`. Post-install, hererocks appends to the LuaRocks config:
`cmake_generator` (Windows) and `variables = {CFLAGS = "<default> <user cflags>"}`
(defaults `/nologo /MD /O2` cl, `-O2` mingw, `-O2 -fPIC` otherwise).

### 3. Prefix layout

For `hererocks <loc> -l 5.4 -r latest`:

```
<loc>/
├── bin/                lua(.exe), luac, lua5X.dll (win), luarocks(.bat), luarocks-admin(.bat),
│                       activation scripts, rock-installed wrappers (busted, luacheck…)
├── include/            lua.h, luaconf.h (patched!), lualib.h, lauxlib.h, lua.hpp [, luajit.h]
├── lib/                liblua5X.a | lua5X.lib | libluajit-5.1.a …
│   └── lua/5.X/        C modules (baked into LUA_CPATH_DEFAULT; luarocks installs here)
├── share/lua/5.X/      Lua modules [, jit/ for LuaJIT]
├── etc/luarocks/config-5.X.lua   (POSIX) | luarocks/config-5.X.lua (Windows)
├── luarocks/           (Windows: LuaRocks program tree from install.bat /P)
└── hererocks.manifest  JSON install manifest (version 3)
```

- **Paths are baked, not env-based**: `LUA_PATH_DEFAULT` =
  `<loc>/share/lua/5.X/?.lua;<loc>/share/lua/5.X/?/init.lua;./?.lua` (order: `./?.lua`
  first for 5.1, last otherwise; 5.3/5.4 also append `./?/init.lua`); `LUA_CPATH_DEFAULT`
  under `<loc>/lib/lua/5.X/` with `?.so`/`?.dll` + `loadall.so`. No `LUAROCKS_CONFIG` env —
  LuaRocks' own configure/install.bat writes the hardcoded-prefix config.
- Activation scripts in `bin/` (templated on absolute location): POSIX `activate`,
  `activate_posix`, `activate.csh`, `activate.fish`, `get_deactivated_path.lua`; Windows
  `activate.bat`, `deactivate-lua.bat`, `activate.ps1`. They only prepend `<loc>/bin` to PATH.
- `hererocks.manifest`: JSON `{version: 3, lua: {identifiers…}, luarocks: {…}}`
  (name/source/version/repo/commit/location/target/compat/cflags/patched/readline
  [+ vs year/arch]); re-runs compare `hash_identifiers()` and skip when equal.

### 4. Compiler/toolchain detection

- **POSIX: none.** `gcc`/`cc`, `ar`, `ranlib`, `make` assumed on PATH; missing tool →
  plain error. readline/ncurses on by default with **no probing** — absent
  `readline/readline.h` fails the build; `--no-readline` is the escape hatch (part of the
  identifiers hash).
- **Windows**: check `program_exists("cl")`; else (1) **vswhere**:
  `"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products *
  -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`,
  then run `<install>\VC\Auxiliary\Build\vcvars64.bat & set` and import
  PATH/INCLUDE/LIB; (2) registry (VS ≤ 2015) HKLM `Software\Microsoft\VisualStudio\{14.0…9.0}\Setup\VC`
  (+VCExpress, +Wow6432Node) → `vcvars*.bat`, Windows SDK `setenv.cmd`; then **re-invokes
  itself recursively** inside that environment via a temp `hererocks.bat` +
  `--actual-argv-file`. cl banner parsed for VS year (→ LuaRocks `cmake_generator`).
- **Inline patches** (`--patch`): embeds the official lua.org/bugs.html diffs with its own
  ~100-line unified-diff engine. Patch counts: 5.1.5×1, 5.3.2×3, 5.3.3×3, 5.3.4×7, 5.3.5×1,
  5.4.0×8, 5.4.2×2, 5.4.3×4, 5.4.4×4, 5.4.6×3, 5.4.7×5, 5.4.8×3, 5.5.0×2 (none for 5.2.x,
  5.4.1, 5.4.5). `patched` participates in the identifier hash.

### 5. Caching

- **Download cache** (default on): POSIX `$XDG_CACHE_HOME/hererocks` → `~/.cache/hererocks`;
  Windows `%LOCALAPPDATA%\HereRocks\Cache`. Contents: raw archives keyed by filename +
  **full git clones** of default repos (`lua/`, `LuaJIT/`, `luarocks/`); repos reused via
  `git rev-parse --verify <ref>` → `fetch` → `checkout` → `pull --rebase` on branches.
  Cached archives re-verified (SHA-256) on every use.
- **Build cache** (opt-in `--builds DIR`): post-compile source tree keyed by the flattened
  identifiers hash — **location is part of the key** (paths baked into artifacts), so
  builds never cross prefixes.
- Install idempotency via `hererocks.manifest` comparison.

### 6. Prebuilt-binary alternatives (compile-free paths)

| Tool | Source | Pattern / assets | Gaps |
|---|---|---|---|
| PUC Lua | **LuaBinaries** <https://luabinaries.sourceforge.net> (de-facto standard; 5.1.5–5.5.0) | `lua-{ver}_Win32_bin.zip`, `lua-{ver}_Win64_bin.zip`, `lua-{ver}_Linux{XY}_64_bin.tar.gz`; libs `lua-{ver}_Win64_dllw6_lib.zip` (MinGW-w64); via `https://sourceforge.net/projects/luabinaries/files/{ver}/{group}/{file}/download` | **No macOS (any arch)**; Linux x86_64/glibc only; checksums only via SourceForge UI/API [UNVERIFIED exact API] |
| LuaJIT | none official (luajit.org: git-only rolling) | distro/Homebrew/MSYS2 or self-hosted CI builds | everything |
| LuaRocks | official releases (verified) | source `luarocks-{ver}.tar.gz` (+`.asc`); **standalone** `luarocks-{ver}-windows-32.zip` / `-windows-64.zip` (self-contained `luarocks.exe`, embedded Lua); `luarocks-{ver}-linux-x86_64.zip` (single binary; **zip drops exec bit** — restore after extract); legacy `-win32.zip` (source+install.bat) | no aarch64 Linux, **no macOS standalone** |
| StyLua | GitHub releases (verified, latest v2.5.2) | `https://github.com/JohnnyMorganz/StyLua/releases/download/v{ver}/stylua-{os}-{arch}.zip`; assets: linux-x86_64(+musl), linux-aarch64(+musl), macos-x86_64, macos-aarch64, windows-x86_64; each zip = one static binary | no checksum assets |
| lua-language-server | GitHub releases (verified, latest 3.18.2; **no `v` tag prefix**) | `…/releases/download/{ver}/lua-language-server-{ver}-{platform}.{ext}`: darwin-arm64/x64 `.tar.gz`, linux-arm64/x64 `.tar.gz`, win32-ia32/x64 `.zip`; a directory tree (`bin/lua-language-server`), not a single binary | no musl, no checksums |
| luacheck | GitHub releases, lunarmodules/luacheck v1.2.0 (verified) | `luacheck` (Linux x86_64 standalone [UNVERIFIED arch naming]), `luacheck.exe` (win64), `luacheck32.exe` (+ `.asc` sigs) | no macOS binary |
| busted / luacov | luarocks-only | `luarocks install busted` / `luacov` | busted pulls **C rocks** (luasystem, lua-term, luafilesystem via penlight) → needs cc + headers; luacov is pure Lua |

### 7. Tool installation via luarocks

- Into a provisioned tree (wrappers carry hardcoded config — absolute path suffices, no
  activation): `<prefix>/bin/luarocks install luacheck|busted|luacov` (Windows
  `luarocks.bat`; pin `install busted 2.2.0-1` for reproducibility).
- C-toolchain needs: luacheck → luafilesystem (C); busted → luasystem + lua-term +
  luafilesystem-via-penlight (all C; verified from busted 2.2.0 / penlight 1.14.0
  rockspecs); luacov pure Lua. C rocks compile against `<prefix>/include` using
  `variables.CFLAGS`/`LUA_INCDIR` from `config-5.x.lua`.
- Binaries land in `<prefix>/bin` as **generated wrappers** (POSIX: Lua script with exec
  header pointing at the tree's interpreter; Windows: generated `.bat` invoking `lua.exe`)
  while the real scripts live under `<prefix>/lib/luarocks/rocks-5.x/<rock>/<ver>/bin/`.
  [Mechanism verified from LuaRocks behavior + hererocks CLI help, not hererocks source.]

### 8. Risk notes for a JVM-native reimplementation

1. **Windows compilation is the hard 30%** (~300 lines of MSVC bootstrap). Prefer prebuilt
   binaries on Windows; treat compile-from-source as POSIX-only.
2. **Non-relocatable prefixes**: source builds bake `LUA_PATH_DEFAULT`/`CPATH` and luarocks
   configs hardcode the prefix — trees cannot move. Prebuilt generic binaries have no baked
   prefix → set `LUA_PATH`/`LUA_CPATH` in the run environment instead (a better fit for an
   IDE plugin anyway).
3. **LuaJIT**: rolling git-only; needs `.git` in the build tree, `MACOSX_DEPLOYMENT_TARGET`,
   msvcbuild on Windows; arm64 macs need a recent `v2.1` checkout. Highest-risk target —
   consider descoping or git+make-only.
4. **readline**: default-on POSIX link dep with no probing; headless machines often lack
   `libreadline-dev`. Native engine should default to no-readline semantics.
5. **C rocks after provisioning**: `luarocks install busted` needs cc + headers even with a
   prebuilt interpreter; mixing MinGW-built `lua54.dll` with MSVC-compiled rocks is an
   ABI/`/MD` hazard — keep toolchain consistent per tree.
6. **Version tables are ours to own**: aliases + SHA-256 pins are hardcoded knowledge;
   lua.org/ftp publishes checksums for cross-verification; LuaRocks ships only GPG `.asc`;
   StyLua/LuaLS ship none (TLS + self-pinned checksums).
7. **git dependency** for `@ref` installs — JGit could cover it; consider dropping
   git-source support in v1.
8. **Proxies**: JVM `HttpRequests` inherits IDE proxy settings (improvement over urllib);
   git subprocesses would not.
9. **Archive quirks**: preserve exec bits/symlinks from tars; LuaRocks Linux standalone is
   a zip (exec bit lost); GitHub archive dirs are `LuaJIT-{ver}`; `-rcN` stripped for PUC
   work releases.
10. **Copy the idempotency design**: identifiers-hash manifest + filename-keyed download
    cache + always-verified SHA-256 maps cleanly to Kotlin.

## Recommendations

- **Strategy order per kind** (feeds `ProvisioningSpec` in the architecture contract):
  Windows = prebuilt-first (LuaBinaries Lua, standalone LuaRocks, luacheck.exe, StyLua/LuaLS
  releases), source-build not offered in v1; POSIX = source-build for PUC Lua (hererocks'
  exact per-TU recipe, no-readline default), prebuilt for StyLua/LuaLS/luacheck(linux-x64),
  luarocks source configure/make.
- **LuaJIT**: v1 scope = git clone + `make` on POSIX only, or defer entirely to a
  de-risking outcome (TOOLING-00 spike).
- **Own the version/checksum table** as versioned plugin data (JSON resource), following
  the JdkList feed pattern; include alias→version mapping and SHA-256 pins.
- **Bake prefixes for source builds** (hererocks-compatible trees, luarocks configure does
  the config) and rely on env injection for prebuilt binaries.
- **Manifest + cache**: reimplement `hererocks.manifest` (same idempotency semantics) under
  a Lunar name; cache downloads under the IDE system dir.

## Open Questions

Feed TOOLING-00 de-risking: (1) LuaBinaries SourceForge download reliability/checksum API;
(2) LuaJIT v2.1 git build on darwin-arm64 and Windows; (3) C-rock installs on a
toolchain-less Windows host (busted without cc — acceptable to fail with guidance?);
(4) macOS PUC-Lua prebuilt gap — accept source-build-only on macOS?
