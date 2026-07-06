---
id: "TOOLING-00-RESULT-01"
title: "POSIX PUC-Lua Source Build — Spike Results"
type: "results"
parent_id: "TOOLING-00"
spike: "TOOLING-00-01"
verdict: "PASS"
---

# TOOLING-00-01: POSIX PUC-Lua Source Build — Spike Results

**Date**: 2026-07-06  
**Executor**: gce-builder VM (Debian 12, gcc 12.2.0)  
**Script**: `tooling/spikes/tooling-00/build-lua-posix.sh`  
**Design ref**: `design.md §2.1`

## Verdict: PASS

All TC 1 assertions hold on the gce-builder image.

## Environment

- Host: Debian 12 (bookworm) on GCE
- Compiler: `gcc (Debian 12.2.0-14+deb12u1) 12.2.0`
- `ar`: GNU Binutils 2.40
- Prefix used: `/tmp/lunar-spike-54`

## Recorded SHA-256 Pin

| Artifact | SHA-256 |
|----------|---------|
| `lua-5.4.8.tar.gz` | `4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae` |

Source: actual download from `https://www.lua.org/ftp/lua-5.4.8.tar.gz` verified against the published SHA-256 listed on the `https://www.lua.org/ftp/` page. This pin is used in `toolchain-feed.json` for the `SOURCE_BUILD` item.

## TC 1 Verification (verbatim output)

```
$ /tmp/lunar-spike-54/bin/lua -v
Lua 5.4.8  Copyright (C) 1994-2025 Lua.org, PUC-Rio

$ /tmp/lunar-spike-54/bin/lua -e 'print(package.path)'
/tmp/lunar-spike-54/share/lua/5.4/?.lua;/tmp/lunar-spike-54/share/lua/5.4/?/init.lua;./?.lua;./?/init.lua

$ ldd /tmp/lunar-spike-54/bin/lua | grep -i readline || echo NO_READLINE_LINKAGE
NO_READLINE_LINKAGE
```

- `lua -v` prints `Lua 5.4.8  Copyright (C) 1994-2025 Lua.org, PUC-Rio` — PASS
- `package.path` begins with `/tmp/lunar-spike-54/share/lua/5.4/?.lua` — PASS (prefix-baked)
- No readline linkage — PASS (no `-DLUA_USE_READLINE`, no `-lreadline`)

## Build Timing

- Download: ~0.5s (from cache; first download ~0.9s on the VM)
- Compile (33 TUs): ~8.6s user time
- Total wall time (from cache): ~9.4s

## Linux Flag-Set Used

```bash
CC=gcc
CFLAGS="-O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3"
LDFLAGS="-Wl,-E -ldl -lm"
```

No `-DLUA_USE_READLINE`. No `-lreadline`.

## macOS Variant Flag-Set (Gap 2.1 — not executed, no macOS host)

Per design §2.1 and hererocks dossier §2a step 3, the macOS variant substitutes:

```bash
CC=cc
CFLAGS="-O2 -Wall -Wextra -std=gnu99 -DLUA_USE_POSIX -DLUA_USE_DLOPEN -DLUA_COMPAT_5_3"
LDFLAGS="-lm"
```

Differences from Linux:
- `CC=cc` (system clang, not gcc) — Xcode Command Line Tools provides it
- `LDFLAGS="-lm"` only — no `-Wl,-E` (macOS linker does not support it) and no `-ldl` (macOS does not have a separate `libdl`; `dlopen` is in `libSystem`)
- No other changes to `CFLAGS` needed for standard amd64/arm64 macOS builds

**Assessment**: these flags are the standard hererocks recipe for macOS and are well-established. There is no blocking finding for macOS (Gap 2.1 answer: macOS is buildable with this flag-set). TOOLING-04's `SourceBuildStrategy` should switch on `os.name` to select the flag variant.

## luaconf.h Patch

The patch inserts immediately before the final `#endif` in `src/luaconf.h`:

```c
#undef LUA_PATH_DEFAULT
#define LUA_PATH_DEFAULT "<prefix>/share/lua/5.4/?.lua;<prefix>/share/lua/5.4/?/init.lua;./?.lua;./?/init.lua"
#undef LUA_CPATH_DEFAULT
#define LUA_CPATH_DEFAULT "<prefix>/lib/lua/5.4/?.so;<prefix>/lib/lua/5.4/loadall.so;./?.so"
```

The patched `luaconf.h` is also installed to `<prefix>/include/luaconf.h` so that C rocks compiled against this prefix pick up the correct paths.

## Sources Compiled (33 TUs, excluding `onelua.c`)

`lapi.c lauxlib.c lbaselib.c lcode.c lcorolib.c lctype.c ldblib.c ldebug.c ldo.c ldump.c lfunc.c lgc.c linit.c liolib.c llex.c lmathlib.c lmem.c loadlib.c lobject.c lopcodes.c loslib.c lparser.c lstate.c lstring.c lstrlib.c ltable.c ltablib.c ltm.c lua.c luac.c lundump.c lutf8lib.c lvm.c lzio.c`

Library objects (not `lua.o`, not `luac.o`): 31 objects → `liblua54.a` (~500K).

## Downstream Handoff

- **TOOLING-04 `SourceBuildStrategy`**: use the Linux flag-set above for `os.linux`; the macOS variant for `os.mac`; the `luaconf.h` patch script is the executable specification.
- **`toolchain-feed.json`**: SHA-256 pin `4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae` filled for the `lua 5.4.8 SOURCE_BUILD` item.
