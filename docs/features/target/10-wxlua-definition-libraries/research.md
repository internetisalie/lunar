---
id: TARGET-10-RESEARCH
parent_id: TARGET-10
type: spec
folders:
  - "[[features/target/10-wxlua-definition-libraries/requirements|requirements]]"
title: "Research: Where wx / wxstc / wxaui definitions come from"
---

# Research: Where `wx` / `wxstc` / `wxaui` definitions come from

> Investigated 2026-08-07. This is the *investigate* half of the roadmap row
> "`wx`/`wxstc`/`wxaui` definition libraries — investigate, then catalog". Everything below was
> **executed**, not read: each finding names the command that produced it. Conclusions flow into
> [requirements.md](requirements.md) (In/Out of Scope) and [design.md](design.md).

## Overview

TARGET-08 shipped on-demand definition libraries: a bundled catalog of pinned tarball URLs, fetched
and extracted per user, registered as `SyntheticLibrary` roots. Its four v1 entries (`busted`,
`luassert`, `love2d`, `openresty`) all point at **existing upstream LuaCATS repositories**.

TARGET-10 asks for the same treatment for wxLua's three global tables. The whole question is whether
that is a catalog-data change (add three rows) or something larger. It is something larger: **no
upstream definition library for wxLua exists anywhere**, so one has to be produced before it can be
catalogued.

The work matters now because it unblocks **MAINT-37** (corpus sweeps run with pinned definition
libraries). MAINT-33's ZeroBrane baseline is currently uninterpretable for exactly this reason —
[`33-corpus-sweep/human-verification-checklists.md:145`](../../maint/33-corpus-sweep/human-verification-checklists.md)
records ZeroBrane's 19 `unresolvedRequires` as "`wx`/`mobdebug`/`socket`/`lfs`/`copas`/`bit`,
resolvable only by TARGET-08", and
[`33-corpus-sweep/risks-and-gaps.md:116` (Gap 2.2)](../../maint/33-corpus-sweep/risks-and-gaps.md)
flags that ZeroBrane's implicit `wx` global surface may swamp `LuaUndeclaredVariable` entirely.

## Potential Use Cases

- A ZeroBrane Studio plugin/config author gets completion and go-to-definition on `wx.wxFrame`,
  `wxstc.wxSTC_STYLE_DEFAULT`, `wxaui.wxAuiPaneInfo`.
- Any wxLua application (wxLua ships a standalone `wxlua` interpreter) gets the same.
- MAINT-37 can re-baseline the ZeroBrane corpus with the library pinned, so the remaining
  `LuaUndeclaredVariable` floor becomes attributable instead of dominated by wxLua globals.

## Findings / Key Components

### 1. No upstream LuaCATS / LuaLS definition library exists for wxLua

Two authoritative indexes were enumerated, not sampled:

```bash
curl -sS "https://api.github.com/orgs/LuaCATS/repos?per_page=100"
curl -sS "https://raw.githubusercontent.com/LuaLS/LLS-Addons/main/.gitmodules"
```

- **LuaCATS org: 28 repositories** (as of 2026-08-07). `busted`, `luassert`, `defold`, `cocos4.0`,
  `jass`, `openresty`, `luafilesystem`, `love2d`, `lovr`, `luaecs`, `skynet`, `.github`, `lpeg`,
  `luasocket`, `md5`, `lzlib`, `luazip`, `tex-luatex`, `slnunicode`, `tex-lualatex`, `luaharfbuzz`,
  `bee`, `tex-lualibs`, `tex-luametatex`, `lmathx`, `luv`, `ffi-reflect`, `dontstarve`.
  **No `wx*` entry.**
- **`LuaLS/LLS-Addons`: 86 submodules** (as of 2026-08-07; the addon registry the LuaLS extension
  installs from). Includes third-party trees such as `penlight`, `garrysmod`, `fivem`, `luaposix`,
  `xmake`. **No wxLua/wxWidgets addon.**

Both counts track upstream and will drift; the *conclusion* does not, and either command re-run
takes seconds.

A GitHub repository search for `wxlua` returns 52 repos: wxLua forks, Windows build artefacts, and
tutorials. None is a definition/annotation library.

> **Conclusion:** TARGET-10 cannot be a catalog-only change. The definitions must be generated.

### 2. ZeroBrane's own `api/lua/wxwidgets.lua` is a dead end

The obvious candidate — ZeroBrane Studio already offers wx autocomplete, so it must hold an API
description — does not survive reading the file. At the corpus-pinned commit
`483ef0dcb593f9599ff27715472425e39a0588e8` (`tooling/corpus/corpus.json:53-59`), the file is
**812 bytes**:

```lua
local function populateAPI(t)
  local api = {}
  local function processFields(fields)
    for k,v in pairs(fields) do
      if type(v) == "table" then
        api[k] = { type = "class", description = "", returns = "", childs = populateAPI(v) }
      else
        api[k] = { type = (type(v) == "function" and "function" or "value"),
                   description = "", returns = "" }
      end
    end
  end
  processFields(t)
  return api
end

return { wx    = { type = "lib", description = "wx lib",    childs = populateAPI(wx) },
         wxstc = { type = "lib", description = "wxSTC lib", childs = populateAPI(wxstc) },
         wxaui = { type = "lib", description = "wxAUI lib", childs = populateAPI(wxaui) } }
```

It is a **runtime introspector**, not a description. It walks the live `wx` table inside a running
wxLua process and emits `description = ""`, `returns = ""` for every member. It carries no
signatures, no parameter names, no types, and no inheritance — and it cannot be evaluated at all
without a wxLua binary present. Unusable as a source.

The rest of `api/lua/` at that commit is unrelated (`baselib.lua`, `corona.lua`, `gideros.lua`,
`love2d.lua`, `luajit2.lua`, `marmalade.lua`, `moai.lua`).

### 3. wxLua's `.i` binding-interface files are the authoritative machine-readable source

`pkulchenko/wxlua` @ `4d83c8d44eeccf88683ca0146a13b16d0b0d4264` (`master`, the actively maintained
fork; supports Lua 5.1–5.4 + LuaJIT and wxWidgets 3.x). `wxLua/bindings/wxwidgets/` holds **69
files**: **42** `.i` interface files, **15** `*_rules.lua` generator-config files, 11
`*_override.hpp` hand-written C++, and `wx_datatypes.lua` / `wxclassref.txt`. (`ls *.i | wc -l`,
`ls *_rules.lua | wc -l`, `ls | wc -l`.)

The `.i` files are what wxLua's own generator (`wxLua/bindings/genwxbind.lua`) consumes to emit the
C++ binding tables — i.e. they *define* the Lua-visible surface by construction. Measured:

```bash
grep -hE "^\s*class\s+wx" *.i | wc -l    # 275
grep -hE "#define\s+wx"    *.i | wc -l   # 3071
grep -hc "%wxEventType"    *.i | paste -sd+ | bc   # 525
grep -hcE "^\s*enum"       *.i | paste -sd+ | bc   # 254
```

### 4. The namespace split is declared, not guessable

Each `<cppns>_rules.lua` sets `hook_lua_namespace`, which is the Lua table the bindings land in.
Enumerated:

```bash
grep -h "hook_lua_namespace" *_rules.lua | sort | uniq -c
#  1 hook_lua_namespace = ""
# 11 hook_lua_namespace = "wx"
#  1 hook_lua_namespace = "wxaui"
#  1 hook_lua_namespace = "wxstc"
#  1 hook_lua_namespace = "wxwebview"
```

So `wx` is the union of eleven C++ groups (`wxbase`, `wxcore`, `wxadv`, `wxhtml`, `wxnet`, `wxxml`,
`wxxrc`, `wxgl`, `wxmedia`, `wxpropgrid`, `wxrichtext`); `wxstc` and `wxaui` are one group each. A
fourth namespace, `wxwebview`, exists and the roadmap row does not mention it. The empty
`hook_lua_namespace = ""` belongs to `wxdatatypes_rules.lua`, which emits no Lua surface.

**Class names do not collide across namespaces** — measured 0 duplicates over
`{wx: 718, wxaui: 29, wxstc: 5, wxwebview: 7}` declared class names. That makes flat LuaCATS type
names safe and is the fact the design's type-naming rule rests on.

### 5. A throwaway parser already recovers most of the surface that is actually used

A 40-line spike (`%wxEventType` + `%attr`-prefixed `class` + `#define` + `enum` only) parsed all 42
`.i` files and produced a name set per namespace. That set was intersected with every
`wx.*` / `wxstc.*` / `wxaui.*` member reference in the pinned ZeroBrane checkout (72 `.lua` files
across `src`, `interpreters`, `api`, `cfg`; 1894 references, 479 distinct members):

| Namespace | Distinct members ZeroBrane uses | Recovered by the spike | Coverage |
|---|---|---|---|
| `wx` | 311 | 256 | **82%** |
| `wxstc` | 144 | 139 | **96%** |
| `wxaui` | 24 | 23 | **95%** |

**These are a lower bound on the design, not a target.** The spike implemented four of the ten
declaration forms §6 enumerates; the design's parser implements all ten, so the Phase 3 coverage
floors are set from the first real generator run, not from this table.

Diagnosing the residual is where the important correction happened. The first reading —
"global object instances such as `wxDefaultPosition` and `wxBLACK` do not appear in any `.i` file" —
came from `grep -rn "wxDefaultPosition\s*;" *.i` returning nothing, and it was **wrong**: that
pattern only matches the C++ variable-declaration shape. Re-run without the shape assumption:

```
wxcore_gdi.i:19    #define_object wxPoint wxDefaultPosition
wxcore_gdi.i:88    #define_object wxDefaultSize
wxcore_gdi.i:649   #define_object wxNullColour
wxcore_gdi.i:650   %rename wxBLACK      #define_pointer wxLua_wxBLACK
wxstc_stc.i:2449   !%wxchkver_3_1_1 #define wxSTC_SCMOD_CTRL
```

So the residual is **entirely** declaration forms the spike lacked, not names missing from the
source. Only three names in the whole measured set survive a full grep as genuinely absent:
`wxSTC_SETLEXERLANGUAGE`, `wxAUI_TB_PLAIN_BACKGROUND`, and `NULL` — which is why the design's
curated supplement is three rows, not thirty.

> The lesson generalises: a negative grep proves the *pattern* absent, never the *thing*. Any
> "X is not in the source" claim in this feature has to name the pattern it searched with.

### 6. The declaration forms, exhaustively — with counts

Measured across all 42 `.i` files at the pin, not sampled. A form with a non-zero count and no
parser rule is a hole; the counts are what makes that checkable.

| Form | Count | Example (verbatim) |
|---|---:|---|
| `#define NAME` | 2874 *(anchored, non-comment; an unanchored grep says 3071 by counting commented lines)* | `#define wxSTC_STYLE_DEFAULT` *(`wxstc_stc.i:110`)* |
| `%wxEventType NAME` | 525 | `%wxEventType wxEVT_COMMAND_MENU_SELECTED   // EVT_MENU(winid, func)` *(`wxcore_event.i:57`)* |
| `!%<guard> <decl>` — **negated** guard | 457 | `!%wxchkver_3_1_1 #define wxSTC_SCMOD_CTRL` |
| `#define_wxstring NAME [= wxT("…")]` | 143 | `#define_wxstring wxDirSelectorPromptStr wxT("Select a directory");` |
| `%rename LuaName <decl>` | 84 | `%rename wxDateTimeFromJDN wxDateTime(double dateTime);` |
| `#define_string NAME` | 68 | `%wxchkver_2_9_0 #define_string wxFileSelectorDefaultWildcardStr` |
| `#define_pointer NAME` | 45 | `%rename wxBLACK      #define_pointer wxLua_wxBLACK` |
| `#define_object [Type] NAME` | 31 | `#define_object wxPoint wxDefaultPosition` |
| `%override_name CppName <decl>` | 24 | `%override_name wxLua_wxBitmapFromBitTable_constructor wxBitmap(LuaTable charTable, int width, int height, int depth /* = 1 */);` |
| `%member_func Type m_field;` | 14 | `%rename X %member_func wxInt32 m_x;` |
| `enum [Name] { … };` | 254 | `enum wxSignal { wxSIGNONE, wxSIGHUP, … };` *(`wxcore_defsutils.i:43`)* |
| `class [%delete] Name [: public Base]` | **789** — of which **487** carry `%delete` *between* `class` and the name | `class %delete wxColour : public wxGDIObject` *(`wxcore_gdi.i:647`)* |
| bare `wxUSE_*` condition prefix | 23 | `wxUSE_ACCEL virtual wxAcceleratorEntry *GetAccel() const;` |
| compound guard `%A && %B <decl>` | 170 | `%wxchkver_3_0_0 && %gtk wxString GetInstallPrefix() const;` |
| `struct [%delete] Name` | 8 | `struct %delete wxRendererVersion` |
| pure virtual `… = 0;` | 220 | `virtual bool IsContainer( const wxDataViewItem &item ) const = 0;` |
| `operator …` — **skip** | 187 | `wxDataViewItem operator[](size_t nIndex);` |
| Method / free function | — | `%wxchkver_3_0_0 bool SafeYield(wxWindow *win, bool onlyIfNeeded);` |
| `//`-commented — **discard** | 5301 | `// void OnAssertFailure(const wxChar *file); // not supported` |
| `%property` | **0** | *(does not occur; no rule needed)* |

Reproduce, in `wxLua/bindings/wxwidgets/`:

```bash
grep -ho "#define_[a-z]*"    *.i | sort | uniq -c
grep -hcE "^\s*%rename"      *.i | paste -sd+ | bc
grep -hcE "^\s*%override_name" *.i | paste -sd+ | bc
grep -hcE "^\s*!"            *.i | paste -sd+ | bc
grep -hc  "%member_func"     *.i | paste -sd+ | bc
grep -hE  "^\s*class\s"     *.i | grep -v "^\s*//" | wc -l
```

**Counting is not enough on its own — the rules have to be run.** A first draft of the design's
grammar was written from this table and, executed, produced an empty `wxaui` namespace, lost
`wxFileName`/`wxFile`/`wxDir`, and emitted ~4,800 class methods as global functions. Two further
forms — compound `%A && %B` guards and `struct` — were missing from the table itself and surfaced
only by **sampling the lines the parser did not recognise**; both were emitting invented API. The executable
reference is checked in at `tooling/spikes/target-10-wxi-grammar/probe.py`; design §3.2 records the
five ordering rules only running it revealed.

Three of these change the *exported name* rather than decorating a declaration, and treating them as
strippable attributes is the single easiest way to build a generator that emits API which does not
exist:

- **`%rename LuaName <decl>`** — `LuaName` is what wxLua exposes. `%rename GetPositionXY void
  GetPosition() const;` binds `GetPositionXY`, **not** `GetPosition`.
- **`%override_name CppName <decl>`** — `CppName` is a *C++* symbol; the Lua name comes from the
  declaration that follows. It looks like `%rename` and must not be treated like it.
- **`%member_func`** — exposes a C++ struct field as a zero-argument accessor, always paired with a
  preceding `%rename`.

Cross-cutting: `#if wxLUA_USE_wxColourPenBrush` / `#endif` wrap most regions; `///` lines are doc
comments; `/* … */` spans occur **inside parameter lists**; and `!%wxchkver_*` is a negated guard,
so an attribute-stripping regex that does not allow a leading `!` silently drops 457 declarations.

### 7. Licence: wxWindows Library Licence v3, and it is not Apache-2.0

`wxLua/docs/licence.txt` is the **wxWindows Library Licence, Version 3** — LGPL v2 (or later) plus
a binary-distribution exception. Every `.i` file header repeats `Licence: wxWidgets licence`.

Lunar itself is **Apache-2.0** (`LICENSE`), and `build.gradle.kts:220-229` already ships `LICENSE`,
`NOTICE` and `THIRD-PARTY.md` at the plugin root "required by Apache-2.0 §4(a)/(d)". So the repo
already has the mechanism for attributing a differently-licensed third-party artefact; what it has
never done is *derive* one. Definitions generated from the `.i` files are a derivative work of a
wxWindows-Licence source and must be distributed under that licence, in their own tree, with
attribution — never merged into the Apache-2.0 source tree.

### 8. What the TARGET-08 catalog will and will not accept

Read from the shipped implementation, not assumed:

- `LuaDefinitionEntry` (`definitions/LuaDefinitionCatalog.kt:55-66`) has exactly
  `id, displayName, version, urls, sha256, size, rootPrefix, license, attributionUrl, requires`.
  There is **no `detectionPatterns`** — TARGET-09 adds it and is still `planned`.
- Unknown JSON fields are ignored: `parseEntry` (`LuaDefinitionCatalogLoader.kt:85-100`) extracts
  named fields manually. A forward-compatible `detectionPatterns` can therefore be written today.
- **Every URL must contain the `version` string** (`LuaDefinitionCatalogLoader.kt:69-73`) — a
  mirror that does not pin the commit is corruption, by design.
- **`rootPrefix` must end in `/library`** — asserted for every bundled entry by
  `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed`. This pins the published
  repo's layout, not just a convention.
- `requires` must name a known id, and cycles are already handled
  (`LuaDefinitionCatalog.withDependencies`, `dependencyResolutionSurvivesCycles`).
- **`sha256` verification is `ADVISORY`, not `STRICT`.** `LuaDefinitionLibraryFetcher.kt:148-160`
  fetches "in ADVISORY mode"; `LuaArtifactDownloader.kt:22,31,123` defines
  `enum class ArtifactVerification { STRICT, ADVISORY }` where ADVISORY "logs a mismatch and keeps
  the file". The safety argument is stated at `LuaDefinitionCatalogLoader.kt:66-68`: the URL
  identifies immutable content by embedding the pinned commit SHA — which is precisely why the
  loader hard-rejects a URL that does not contain `version`. **A wrong hash does not fail the
  fetch**, so no design may claim it does.

### 9. The LuaCATS shape Lunar actually consumes

`@overload` is **parsed but not typed**. `LuaTypeGraphBridge` reads `getTypeTagList` /
`getClassTagList` / `@param` / `@return` / `@generic`
(`lang/psi/types/LuaTypeGraphBridge.kt:66-92`); the only consumers of `overloadTagList` outside the
LuaCATS PSI are `LuaParameterInfoHandler.kt:174,226` (parameter hints) and `LuaDocGenerator` /
`LuaComment` (rendering). So an `---@overload fun(…): T` gives hints but **no inferred type**.

The **class** idiom that demonstrably works — what `love2d` ships and Lunar indexes today
(`library/love/math.lua:478`):

```lua
---@class love.Transform: love.Object
local Transform = {}

---@param other love.Transform
---@return love.Transform transform
function Transform:apply(other) end
```

**What has no precedent is splitting one namespace table across files.** love2d never does it:
`library/love.lua:7-8` declares `---@class love` + `love = {}` and puts `love.getVersion` in the
*same* file; `library/love/math.lua:9-10` re-declares `---@class love.math` + `love.math = {}`
before adding to it. Lunar's own bundled stub does the same
(`runtime/standard/lua-5.4/string.lua:26-27`). wxLua has no sub-namespaces to hang such a
re-declaration on — every member sits directly on `wx` — so a multi-file layout would be doing
something neither reference does.

The mechanism that *should* support it is real: BUG-391 (resolved 2026-08-03) added
`LuaGlobalAssignmentIndex` (`lang/indexing/LuaGlobalAssignmentIndex.kt:28,38`) as the sibling of
`LuaMemberFieldIndex`, "which owns the dotted `receiver.field = value` case", and both are
library-wide. But that is a reading of an index's purpose, not an observation of completion working
across files — so the design tracks it as **DR-06** with a single-file-per-namespace fallback rather
than asserting it.

Module resolution from a library root is by **file name**:
`LuaLibraryModuleResolutionTest.testRequireResolvesIntoALibraryRoot` registers `libmod.lua` and
asserts `require("libmod")` resolves into the root. So `require("wx")` resolving requires a file
literally named `wx.lua` at the registered root — which is also what ZeroBrane's 19 unresolved
requires need.

`LibraryRootTestCase.registerLibraryRoot(files: Map<String, String>)`
(`src/test/kotlin/.../definitions/LibraryRootTestCase.kt:55`) registers an in-memory root, so every
acceptance test for this feature can run **without network and without a real tarball**.

## Prior Art & References

| Source | What to take | What to avoid |
|---|---|---|
| [LuaCATS/love2d](https://github.com/LuaCATS/love2d) | The emission idiom (§9), the `library/` + `config.json` repo layout, `---@meta` headers | Its hand-written prose descriptions — wxLua's `///` comments are far sparser |
| `pkulchenko/wxlua` `bindings/wxwidgets/*.i` | The entire surface (§3, §6) | Treating `//`-commented declarations as real; they mark *unsupported* API |
| `pkulchenko/wxlua` `bindings/genwxbind.lua` | Confirmation that `.i` + `*_rules.lua` is the complete input to the real generator | Reusing it directly — it emits C++ and needs a Lua interpreter in the build |
| ZeroBrane `api/lua/wxwidgets.lua` | Nothing | It is a runtime introspector (§2) |
| `tooling/corpus/fetch-corpus.py` | The offline-tooling convention: Python 3, stdlib-only, JSON manifest, pinned commit, `--` no shell parsing (BUG-407) | — |
| TARGET-08 `LuaDefinitionCatalogLoader` | Its invariants are hard gates (§8) | Assuming any field is optional |

## Recommendations

1. **Generate, don't source.** Produce a LuaCATS tree from the `.i` files with an offline generator
   checked into `tooling/`, pinned to one wxLua commit.
2. **Publish it as a standalone LuaCATS-shaped repository** and add **one** catalog entry pointing
   at a commit-pinned tarball. (User decision, 2026-08-07 — bundling in the plugin jar and a
   `bundled` catalog source-kind were both considered and rejected.)
3. **One entry, not three.** `wx`, `wxstc` and `wxaui` ship from one wxLua binary, ZeroBrane uses all
   three, and `wxstc.wxStyledTextCtrl` inherits `wxControl` from `wx` — splitting would buy three
   tarballs, three pins and a `requires` graph for no user-visible benefit. The roadmap row names
   three *namespaces*, which is a fact about wxLua, not a specification of three catalog rows.
4. **Cover the full surface, not just ZeroBrane's 479 members.** A partial library is worse than
   none: it turns "unknown global" into "this class has no such method". Generation makes full
   coverage nearly free.
5. **Include `wxwebview`** (§4) — it is one 10 KB `.i` file and excluding it would be an arbitrary
   omission the generator has to be taught.
6. **Carry a very small hand-curated supplement** — three names on current evidence
   (`wxSTC_SETLEXERLANGUAGE`, `wxAUI_TB_PLAIN_BACKGROUND`, `NULL`), registered from
   `*_override.hpp` / module C++ rather than declared in `.i` (§5). Keep it in its own file so the
   generated/curated boundary stays visible, and **re-derive it from a coverage run** rather than
   from a list — the first attempt at this list had ~28 entries and was mostly wrong.
7. **Count the declaration forms, don't sample them** (§6). Reading a few files suggested six forms;
   counting found ten. The generator's completeness is a property of that count, so the count is
   what a re-pin must re-check.

## Common Pitfalls

- **Trusting `//` lines.** wxLua comments out declarations it deliberately does not bind
  (`// void OnAssertFailure(...); // not supported`). Emitting them invents API.
- **Treating every `%token` as decoration.** `%rename` and `%member_func` *bind the exported name*;
  stripping them emits a method that does not exist and omits the one that does. `%override_name`
  looks identical and is the opposite — its argument is a C++ name to discard.
- **Writing the guard-strip regex without a leading `!`.** 457 declarations sit behind
  `!%wxchkver_*`, and they vanish silently.
- **Concluding "absent" from a negative grep.** A pattern that assumes the wrong shape returns
  nothing and reads like proof. `grep "wxDefaultPosition\s*;"` found nothing; the symbol is declared
  as `#define_object wxPoint wxDefaultPosition`. Always state the pattern alongside the claim.
- **Assuming a namespace table can be extended from sibling files.** Neither love2d nor Lunar's own
  stdlib stub does it (§9); it may work, but it has to be run, not reasoned.
- **Honouring `#if` / `%wxchkver_*` guards.** Tempting, but wrong here: the guard depends on the
  user's wxWidgets build, which we cannot know. A *missing* definition produces a false "undefined";
  a *surplus* one produces at worst a stale completion. Take the union.
- **Reaching for `---@overload` to type constructors.** It renders and hints but does not infer
  (§9).
- **Assuming `rootPrefix` may be anything.** The bundled-catalog test enforces `/library` (§8).
- **Assuming ZeroBrane's `wx` usage is representative of wxLua.** It is a coverage *floor* for
  MAINT-37, not the scope.

## Open Questions

None left open here — the three that survived investigation are tracked as de-risking tasks in
[risks-and-gaps.md](risks-and-gaps.md), each with every branch specified in the design:

- Whether Lunar resolves `wx.wxFileName.GetCwd()` when `wx.wxFileName` is also declared as a
  constructor function → **DR-02** (a `registerLibraryRoot` spike; both branches in design §3.6).
- Whether members written to `library/wx/<cpp>.lua` attach to the `---@class wx` declared in
  `library/wx.lua` → **DR-06** (both layouts in design §3.5.2).
- What the ~2 MB definition tree costs at index time and in the MAINT-37 sweep → **DR-04**.
