---
id: "TOOLING-00-03-RESULT"
title: "LuaJIT git+make Spike Results"
type: "results"
parent_id: "TOOLING-00"
folders:
  - "[[features/tooling/00-de-risking/requirements|requirements]]"
---

# TOOLING-00-03: LuaJIT git+make Spike Results

**Verdict: PASS**

Spike executed 2026-07-06 on the gce-builder VM (Linux x86_64, Debian-based, gcc present).

---

## Exact Commands and Outputs

### Environment

```
OS: Debian-based Linux x86_64 (gce-builder VM)
gcc: /usr/bin/gcc  (present)
make: /usr/bin/make
git: /usr/bin/git
nproc: 8
```

### Step 1: Clone (keeping .git)

```bash
git clone --depth=1 --branch v2.1 https://github.com/LuaJIT/LuaJIT /tmp/lunar-luajit-luajit-src
```

Git HEAD committed by the clone:
```
a2bde60 FFI/MacOS: Fix calling convention for on-stack varargs.
```

Note: `git describe --tags` returned no output (shallow clone; no local tags), which is
expected for `--depth=1`. The LuaJIT Makefile derives the version from `git log` rather
than `git describe` — the `.git` directory was preserved and the build succeeded.

### Step 2: Build

```bash
make -C /tmp/lunar-luajit-luajit-src -j8
```

Build completed successfully. Key output lines:
```
==== Building LuaJIT 2.1 ====
OK        Successfully built LuaJIT
==== Successfully built LuaJIT 2.1 ====
```

Artifacts produced:
- `/tmp/lunar-luajit-luajit-src/src/luajit`       — 560 KiB executable
- `/tmp/lunar-luajit-luajit-src/src/libluajit.a`  — 938 KiB static library
- `/tmp/lunar-luajit-luajit-src/src/jit/`         — 19 `.lua` JIT-support modules

No `XCFLAGS` were passed. No 5.2-compat flags were used (spike intentionally plain).

### Step 3: Hand-install (hererocks layout)

```bash
cp src/luajit   /tmp/lunar-luajit/bin/lua
cp src/libluajit.a  /tmp/lunar-luajit/lib/libluajit-5.1.a
cp -r src/jit/. /tmp/lunar-luajit/share/lua/5.1/jit/
```

Installed layout:
```
/tmp/lunar-luajit/bin/lua              560 KiB  rwxr-xr-x
/tmp/lunar-luajit/lib/libluajit-5.1.a 938 KiB
/tmp/lunar-luajit/share/lua/5.1/jit/  19 .lua files (bc, bcsave, dis_*, dump, p, v, vmdef, zone)
```

### TC 3 Verification

```
$ /tmp/lunar-luajit/bin/lua -v
LuaJIT 2.1.1782726002 -- Copyright (C) 2005-2026 Mike Pall. https://luajit.org/

$ /tmp/lunar-luajit/bin/lua -e 'print(jit.version)' && echo JIT_OK
LuaJIT 2.1.1782726002
JIT_OK
```

**PASS**: first line of `lua -v` starts with `LuaJIT 2.1`.
**PASS**: `jit.version` exits 0.

---

## darwin-arm64 Paper Assessment

No macOS hardware is available in the harness. This is a paper assessment only.

### `MACOSX_DEPLOYMENT_TARGET` requirement

LuaJIT's Makefile (`src/Makefile`) conditionally sets Darwin-specific flags. When building
on macOS and `MACOSX_DEPLOYMENT_TARGET` is unset, recent GCC/Clang toolchains may warn or
produce unexpected behavior for the deployment target. The LuaJIT documentation and common
practice require setting this before building on macOS:

```bash
export MACOSX_DEPLOYMENT_TARGET=11.0  # or the host's minimum target
make -C <src> -j$(nproc)
```

For `TOOLING-04`'s `SourceBuildStrategy` on macOS, the provisioning engine must set
`MACOSX_DEPLOYMENT_TARGET` to the host's macOS version (or a reasonable floor, e.g.
`11.0` for Apple Silicon) before invoking `make`. This is a **configuration requirement**,
not a blocking architectural finding — it is a well-documented prerequisite and can be
automated.

### arm64 (Apple Silicon) support

The LuaJIT `v2.1` branch (rolling release) has supported arm64/AArch64 on macOS since
mid-2021. Commit `a2bde60` (the HEAD of our depth-1 clone) post-dates that support. The
arm64 JIT backend (`lj_asm_arm64.h`, `dis_arm64.lua`) is included in the `src/jit/`
tree installed by the spike.

**Conclusion**: only recent `v2.1` branch checkouts (which is what we clone) support arm64
macs. Pinning to `--branch v2.1` HEAD is correct. A `--branch v2.1.0-beta3`-style tag
(if one existed and predated arm64 support) would not; since LuaJIT's rolling `v2.1` is
what we target, this is not an issue.

**Assessment**: No blocking finding for arm64 macOS. The `MACOSX_DEPLOYMENT_TARGET`
requirement is a minor configuration step that `SourceBuildStrategy` must handle.

---

## Binding Decision (design §2.3 Decision Matrix)

| Linux run | darwin-arm64 assessment | Decision |
|-----------|------------------------|----------|
| **PASS**  | No blocking finding (`MACOSX_DEPLOYMENT_TARGET` = configuration, not blocker; arm64 supported in v2.1 HEAD) | `provisioning = [SourceBuildStrategy(git+make)]`, POSIX-only; Windows excluded |

**Decision: PASS — LuaJIT provisioning ships POSIX-only in v1 via `SourceBuildStrategy(git+make)`.**

Windows is excluded (Risk 1.1 mitigation: `msvcbuild.bat` complexity is out of scope for v1).
macOS is in scope with the `MACOSX_DEPLOYMENT_TARGET` pre-condition.

---

## Feed Format Extension (git source)

Per design §2.5, a LuaJIT entry is added to the feed because the decision is PASS.
Since LuaJIT has no release tarballs, the entry uses `"packageType": "git"` with a new
`"gitRef"` field; `"sha256"` is omitted (the `.git` clone provides its own integrity via
the commit SHA).

Format extension used for the LuaJIT feed entry:
- `"packageType": "git"` — distinguishes from `"tar.gz"` and `"zip"`
- `"gitRef": "v2.1"` — the branch/tag to clone with `--branch`
- `"sha256"` — omitted (not applicable for git sources)
- `"url"` — the clone URL

The `LuaProvisioningClasspathSpikeTest` accepts git-type entries with `sha256` absent
(field is optional in the feed schema for `packageType: git`).

---

## Downstream Binding

| Downstream feature | Effect |
|-------------------|--------|
| TOOLING-04 | `luajit` kind has `provisioning = [SourceBuildStrategy(git+make)]`, POSIX (linux + macOS with `MACOSX_DEPLOYMENT_TARGET` pre-condition); Windows excluded |
| TOOLING-01 | `luajit` kind descriptor ships with provisioning support (not register-existing-binary-only) |
| Risk 1.3 | Closed: Linux probability confirmed low (PASS); darwin-arm64 no blocking finding |
| Gap 2.4 | Resolved: POSIX git+make ships in v1 |

---

## See Also
- Script: `tooling/spikes/tooling-00/build-luajit-posix.sh`
- Design: `docs/features/tooling/00-de-risking/design.md` §2.3
- Risks: `docs/features/tooling/tooling-risks-and-gaps.md` (Risk 1.3, Gap 2.4)
