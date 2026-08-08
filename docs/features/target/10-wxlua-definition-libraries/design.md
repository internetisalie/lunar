---
id: TARGET-10-DESIGN
parent_id: TARGET-10
type: design
folders:
  - "[[features/target/10-wxlua-definition-libraries/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: TARGET-10 — wxLua (`wx` / `wxstc` / `wxaui`) Definition Libraries

## 1. Architecture Overview

### Current State

TARGET-08 is `done` and shipped. The plugin already:

- loads a bundled catalog — `LuaDefinitionCatalogLoader.load()` reading
  `/definitions/lunar-definitions-catalog.json`
  (`src/main/kotlin/net/internetisalie/lunar/definitions/LuaDefinitionCatalogLoader.kt`);
- fetches + verifies + extracts an entry on demand —
  `LuaDefinitionLibraryFetcher.ensureCached` over `LuaArtifactDownloader` / `LuaArchiveExtractor`;
- registers the extracted `rootPrefix` directory as a `SyntheticLibrary` source root —
  `LuaDefinitionLibraryProvider` (`plugin.xml` `additionalLibraryRootsProvider`);
- resolves globals, members, `require`, and `@class` inheritance out of such a root — BUG-394 /
  395 / 398 / 399, all `done`, with regression tests in
  `src/test/kotlin/net/internetisalie/lunar/definitions/`.

What is missing is **data**: there is no wxLua definition tree in existence to point an entry at
(see [research.md §1](research.md)). Nothing in the plugin is insufficient; the ecosystem is.

### Prior Art in This Repo

Searched `src/main` for definition-catalog handling, and `tooling/` for offline generators, before
designing:

| Component | file:line | Relationship |
|---|---|---|
| `LuaDefinitionCatalogLoader` / `LuaDefinitionEntry` | `definitions/LuaDefinitionCatalogLoader.kt:85`, `definitions/LuaDefinitionCatalog.kt:55` | **Used unchanged.** This feature adds one row of data. Its invariants (§4.2) are hard constraints on that row, not suggestions. |
| `LuaDefinitionLibraryFetcher` / `LuaDefinitionLibraryProvider` | `definitions/LuaDefinitionLibraryFetcher.kt`, `definitions/LuaDefinitionLibraryProvider.kt` | **Used unchanged.** No new provider; the tree is an ordinary catalog entry. |
| `PlatformLibraryProvider` (bundled `runtime/` stubs) | `project/PlatformLibraryProvider.kt:41` | **Not used, deliberately.** Bundling was the rejected alternative (§9); the tree ships out-of-band under its own licence. |
| `tooling/corpus/fetch-corpus.py` | `tooling/corpus/fetch-corpus.py:1` | **Pattern replicated** — Python 3, stdlib-only, JSON manifest, pinned commit, no shell string parsing (its docstring records the BUG-407 TSV failure this avoids). Not reused: different input and output. |
| `tooling/parser-gen/` | — | **Not related.** Grammar-Kit parser generation, a different pipeline. |
| `src/main/resources/runtime/**/*.lua` | `runtime/standard/lua-5.4/*.lua` | **Emission reference only.** The bundled stdlib stubs demonstrate the `---@class` + `function ns.name()` idiom that Lunar indexes; the wxLua tree copies the idiom, not the files. |
| `THIRD-PARTY.md` + `build.gradle.kts:220-231` | `build.gradle.kts:229-231` | **Extended** with one registry row. The build already ships the file; no build change. |

**No existing component generates or consumes wxLua definitions.** Nothing is duplicated; nothing is
replaced.

### Target State

Three artefacts, in two repositories:

```
THIS repo                                       PUBLISHED repo (new)
─────────                                       ────────────────────
tooling/definitions/wxlua/                      lunar-definitions-wxlua/
  generate.py            ─── generates ──────▶    library/wx.lua
  wxlua.json  (the pin)                           library/wx/{base,core,adv,…}.lua
  supplement.lua (curated)  ─── copied ──────▶    library/wxstc/wxstc.lua
  coverage-floor.json                             library/wxaui/wxaui.lua
                                                  library/wxwebview/wxwebview.lua
src/main/resources/definitions/                   library/supplement.lua
  lunar-definitions-catalog.json  ── points ─▶    config.json  LICENCE  PROVENANCE.md
    + one "wxlua" entry
THIRD-PARTY.md  + one row
```

Data flow: maintainer runs `generate.py` → tree is committed and tagged in the published repo →
the tarball's `sha256`/`size` are read back → the catalog entry is updated in this repo → a user
ticks **wxlua** in *Settings → Lua Project → Definition Libraries* → TARGET-08's existing machinery
fetches, verifies, extracts and registers the root → `wx.` completes.

**No Kotlin is written and no `plugin.xml` is touched.** §7 states this positively.

## 2. Core Components

The generator is Python, so "components" are modules and their top-level functions. Every signature
below is the contract a phase must implement.

### 2.1 `tooling/definitions/wxlua/generate.py`

- **Responsibility**: end-to-end driver — read the pin manifest, parse the checkout, emit the tree,
  write the coverage report.
- **Threading**: single-threaded CLI. Not invoked by Gradle, not invoked at runtime.
- **Collaborators**: `wxi_parser`, `emit`, `coverage` (all siblings in the same directory).
- **Key API**:
  ```python
  def main(argv: list[str]) -> int: ...
  # usage: generate.py --wxlua <checkout> --out <dir> [--corpus <zerobrane-checkout>] [--check]
  #   --check : emit to a temp dir and diff against --out; exit 1 on any difference (TC 9)
  ```

### 2.2 `tooling/definitions/wxlua/wxi_parser.py`

- **Responsibility**: turn `bindings/wxwidgets/` into a namespace → declarations model. Knows the
  `.i` grammar (§4.1) and nothing about LuaCATS.
- **Key API**:
  ```python
  @dataclass(frozen=True)
  class Param:      name: str; type: str; optional: bool
  @dataclass(frozen=True)
  class Func:       name: str; params: tuple[Param, ...]; returns: str | None; doc: str
  @dataclass(frozen=True)
  class Klass:      name: str; base: str | None; doc: str
                    ctors: tuple[Func, ...]; methods: tuple[Func, ...]; statics: tuple[Func, ...]
  @dataclass(frozen=True)
  class Const:      name: str; type: str; doc: str          # "number" for numeric consts (§3.4)
  @dataclass(frozen=True)
  class Group:      namespace: str; cpp: str                # e.g. ("wx", "wxcore")
                    classes: tuple[Klass, ...]; funcs: tuple[Func, ...]; consts: tuple[Const, ...]

  def namespaces(bindings_dir: Path) -> dict[str, str]: ...   # cpp prefix -> lua namespace (§3.1)
  def parse_group(bindings_dir: Path, cpp: str, namespace: str) -> Group: ...  # (§3.2, §3.3)
  ```

### 2.3 `tooling/definitions/wxlua/emit.py`

- **Responsibility**: render `Group`s as LuaCATS text. Knows LuaCATS and nothing about `.i`.
- **Key API**:
  ```python
  def render_group(group: Group, statics_mode: str) -> str: ...    # one library/<ns>/<cpp>.lua
  def render_namespace_root(namespace: str) -> str: ...            # library/<ns>.lua  (§3.5.1)
  def map_type(cpp_type: str, known_classes: frozenset[str]) -> str: ...   # (§3.4)
  ```
  `statics_mode` is `"dotted"` or `"on-class"` — the DR-02 branch (§3.6). It is a parameter, not a
  fork in the code, so switching branches is a one-line change in `generate.py`.

### 2.4 `tooling/definitions/wxlua/coverage.py`

- **Responsibility**: measure emitted names against the names a corpus checkout references, and
  enforce the floor.
- **Key API**:
  ```python
  def used_members(corpus_root: Path, roots: list[str], prune: list[str]) -> dict[str, set[str]]: ...
  def report(emitted: dict[str, set[str]], used: dict[str, set[str]]) -> dict: ...   # (§4.3)
  def check_floor(report: dict, floor_path: Path) -> bool: ...
  ```

### 2.5 `tooling/definitions/wxlua/wxlua.json` — the pin manifest

```json
{
  "$comment": "The wxLua revision the definition tree is generated from. See design §4.4.",
  "url": "https://github.com/pkulchenko/wxlua.git",
  "commit": "4d83c8d44eeccf88683ca0146a13b16d0b0d4264",
  "bindingsDir": "wxLua/bindings/wxwidgets",
  "licenceFile": "wxLua/docs/licence.txt",
  "corpus": { "name": "zerobrane", "roots": ["src", "interpreters", "api", "cfg"], "prune": ["bin"] }
}
```

### 2.6 `tooling/definitions/wxlua/supplement.lua` — the curated file

Hand-maintained LuaCATS for the Lua-visible names no `.i` file declares
([research.md §5](research.md)). Copied verbatim to `library/supplement.lua`; the generator **never
writes to it and never overwrites it at the destination** (TC 11). Its required initial contents are
listed in §4.5.

### 2.7 The catalog entry (data, in this repo)

One object appended to `libraries` in `src/main/resources/definitions/lunar-definitions-catalog.json`
— exact JSON in §7.

## 3. Algorithms

### 3.1 Namespace discovery

- **Input → Output**: `bindings_dir` → `dict[cpp_prefix, lua_namespace]`.
- **Steps**:
  1. For each file matching `*_rules.lua` in `bindings_dir`, sorted by name:
  2. `cpp = filename[:-len("_rules.lua")]`.
  3. Search the text for `hook_lua_namespace\s*=\s*"([^"]*)"`. No match → skip the file.
  4. If the captured value is **empty**, skip (`wxdatatypes_rules.lua` sets `""` and emits no Lua
     surface — TC 5). Otherwise record `cpp → value`.
- **Rules / edge handling**: a `*.i` file whose prefix has no rules entry contributes nothing. The
  prefix is the filename up to the **first** `_` (`wxcore_appframe.i` → `wxcore`).
- **Expected result on the pinned checkout** (measured, `grep -h hook_lua_namespace *_rules.lua | sort | uniq -c`):
  eleven prefixes → `wx`; `wxstc` → `wxstc`; `wxaui` → `wxaui`; `wxwebview` → `wxwebview`;
  `wxdatatypes` → skipped.

### 3.2 Line normalisation (runs before every other rule)

> **The `.i` grammar is empirical, and this section is a starting point with a verification
> obligation — not a frozen specification.** wxLua's `.i` files are 1.6 MB of a hand-maintained,
> undocumented format with no upstream grammar; four successive review rounds each found a form the
> previous round's rule list had missed, and each miss produced *invented* API (members merged into
> the wrong class, methods emitted as globals) rather than a clean omission. Freezing a rule list
> here and calling it complete would be asserting something no amount of reading can establish.
>
> So the plan does two things instead. (1) The rules below are **executed** — a reference
> implementation is checked in at
> [`tooling/spikes/target-10-wxi-grammar/probe.py`](../../../../tooling/spikes/target-10-wxi-grammar/probe.py)
> and every number in §3.3 and §3.8 is its output. (2) **Phase 1 owns closing the remainder**, under
> a bounded acceptance gate stated in §3.3: classify the parse residue, and diff the emitted name
> set in *both* directions. An implementer who finds a further form adds a rule under that gate —
> that is the expected workflow, not a plan failure.

> **These rules were executed, not written.** A reference implementation lives at
> [`tooling/spikes/target-10-wxi-grammar/probe.py`](../../../../tooling/spikes/target-10-wxi-grammar/probe.py);
> running it over the pinned checkout produces the firing counts in §3.3 and the coverage in §3.8.
> Five of the ten steps below exist **only** because an earlier, plausible-looking version of this
> section was run and silently produced wrong output. Each is flagged ⚠ with what it costs to omit.

Applied to each physical line of a `.i` file, in this order. **The order is load-bearing.**

1. ⚠ **Line comments first.** A line whose first non-space characters are `///` is a **doc line**:
   strip the `///` and one following space, append to `pending_doc`, consume the line. A line
   starting `//` is **discarded entirely** (wxLua marks deliberately-unbound API this way, TC 2).
   Otherwise strip a trailing `// …`.
   *Why first:* `wxaui_aui.i:10` is `// … copied from wxWidget's include/aui/*.h headers`. Taking
   block comments first opens block state on that `/*` and swallows the remaining ~2,400 lines —
   **the entire `wxaui` namespace emits as empty, with no error**. Measured: 8 of 42 files have
   unbalanced `/*` counts before this step; **0 of 42** after it.
2. **Inline block-comment spans.** Delete every `/* … */` span on the line. These occur *inside
   parameter lists*: `wxBitmap(LuaTable charTable, int width, int height, int depth /* = 1 */);`.
3. ⚠ **Multi-line block-comment state.** A `/*` left unclosed opens block state; discard lines until
   `*/`. Safe only because step 1 already ran (see step 1's measurement).
   *Why needed:* `wxbase_file.i:162` is doxygen prose containing an unmatched `(`; without this,
   step 5 accumulates it forever and **`wxFileName`, `wxFile`, `wxDir` and `wxStandardPaths` are all
   lost** — 4 classes and 22 constants ZeroBrane uses.
4. ⚠ **Detach pointer/reference sigils**: replace `([*&]+)` with ` \1 `.
   *Why:* `wxDataViewModel *GetOwner() const;` binds the `*` to the *name*, so a
   `<type><space><name>` pattern cannot match. Measured: recovers 215 declarations (213 methods + 2 free functions).
5. ⚠ **Bounded continuation join.** If the line has more `(` than `)`, hold it and prepend it to the
   next line. **Abandon after 10 held lines.** Measured: 264 declarations span lines.
   *Why bounded:* unbounded, a single stray `(` in prose consumes the rest of the file (step 3).
6. **Bare build-condition prefix.** Strip
   `^\s*(?:wxUSE_|wxLUA_USE_)[A-Za-z0-9_]*(?:\s*[|&]\s*[(%A-Za-z0-9_|&)]+)*\s+`.
   Measured: 23 declarations, e.g. `wxUSE_ACCEL virtual wxAcceleratorEntry *GetAccel() const;` and
   `wxUSE_TEXTDLG wxString wxGetTextFromUser(…)`.
7. ⚠ **Consume guard expressions and export names to a fixed point** (repeat steps a–c until the
   line stops changing, max 4 passes — 12 real lines put a guard *before* `%rename`, and
   `wxcore_windows.i:436` puts `%override_name` before it too):
   - a. `^\s*%rename\s+(\w+)\s+` → capture `\1` as `export_name`, remove the prefix. **`%rename`
     is a name binding, not decoration** — `%rename GetPositionXY void GetPosition() const;` binds
     `GetPositionXY`. Discarding it emits a method that does not exist *and* omits the one that
     does. Measured: 84 line-leading, 12 more behind a guard.
   - b. `^\s*%override_name\s+(\w+)\s+` → remove the prefix and **discard** the captured name: it
     is a *C++* symbol, and the declaration that follows carries the real Lua name. This is the one
     `%`-token that looks like `%rename` and must not be treated like it. Measured: 24.
   - c. Consume one leading **guard expression term**:
     `^\s*\(?\s*(TERM)\s*\)?\s*(?:&&|\|\||&|\|)?\s*` where
     `TERM = !?(?:%[A-Za-z_]\w*|wxUSE_\w*|wxLUA_USE_\w*)`. Stop at `%wxEventType` or
     `%member_func`.
     - ⚠ **A guard is an expression, not a token.** Measured: 170 `&&`/`||` chains
       (`%wxchkver_3_0_0 && %gtk wxString GetInstallPrefix() const;`), 8 single-character `&`/`|`
       (`%wxchkver_3_1_0 & %win bool Activate() const;`), 7 mixed `%guard && wxUSE_*`, 1
       parenthesised (`… && (wxUSE_FILE||wxUSE_FFILE) void AssignTempFileName(…)`). A loop that
       eats one token, or only `&&`/`||`, leaves a bare operator in front of the declaration, and
       **no anchored rule permits that** — measured cost 162 firings / 140 distinct symbols,
       including `wxRealPath`, `GetInstallPrefix` and `wxLocale`'s constructor.
     - ⚠ **Why this must interleave with (a)/(b) rather than run after them**: a guard-first line
       (`%wxchkver_3_0_0 %rename LeftDown bool LeftIsDown() const;`) reaches (c) with `%rename`
       still present, and an unrestricted strip eats it — losing both the export name and the
       declaration.
8. ⚠ **Strip any remaining `%` attributes anywhere on the line**, except `%wxEventType` and
   `%member_func`.
   - *Why not line-leading only:* `%delete` sits **between** `class` and the name — measured **487
     of 789** class lines. A line-anchored strip leaves it, the class rule fails to match, class
     state never opens, and every member of those classes falls to file scope. The observed result
     was **~4,800 methods emitted as namespace-level free functions** — `wx.SetOwner()`,
     `wx.GetFrameCount()` — i.e. inventing API, not merely dropping it. `%gc`/`%ungc` likewise
     appear inside parameter lists.
   - *Why the two exemptions:* `%wxEventType` (507 firings) and `%member_func` (14) are
     **declaration keywords** that §3.3's rules 1 and 13 match on. An unrestricted strip makes both
     rules unreachable — 0 of 507 event constants recognised, silently.
9. `#if`, `#ifdef`, `#ifndef`, `#else`, `#elif`, `#endif`, `#include` lines are consumed and
   **ignored** — no conditional state is tracked. Every branch of every conditional is emitted.
10. A blank line clears `pending_doc`. Any recognised declaration consumes `pending_doc` as its
    `doc` and `export_name` as its name override, then clears both.

**Why guards are ignored.** `%wxchkver_3_0_0` and `#if wxLUA_USE_wxColourPenBrush` depend on the
wxWidgets version and build options of the user's wxLua binary, which the plugin cannot know. A
symbol wrongly omitted becomes a false "undefined variable"; a symbol wrongly included is at worst a
stale completion. The union is the correct choice, and it is why the design deliberately does *not*
replicate wxLua's own preprocessor — and why a guard and its negation both contribute.

**`%property` is not a form.** It appears **0 times** in the pinned checkout and has no rule.

### 3.3 Declaration recognition

Applied to each normalised, non-empty line. Every pattern is **anchored at the start of the
normalised line** (`^\s*`); an unanchored match would fire on substrings inside comments and default
values. First match wins; the list is ordered. `name := export_name or the captured name` (§3.2.4a).

| # | Pattern (anchored on the normalised line) | Emits |
|---|---|---|
| 1 | `%wxEventType\s+(\w+)` | `Const(name)` |
| 2 | `#define_object\s+(\w+)\s+(\w+)\s*;?$` | `Const(name=\2, type=map_type(\1))` — typed global object |
| 3 | `#define_object\s+(\w+)\s*;?$` | `Const(name=\1, type="any")` — untyped global object |
| 4 | `#define_pointer\s+(\w+)` | `Const(name, type="any")` — a `%rename` almost always supplies the Lua name here |
| 5 | `#define_(?:string\|wxstring)\s+(\w+)` | `Const(name=\1, type="string")` |
| 6 | `#define\s+(\w+)\s*$` | `Const(name=\1, type="number")` |
| 7 | `enum\b\s*(\w+(?:::\w+)*)?` | opens enum state; `\1`, if present, is recorded as an alias name — **using its last `::` segment** (see below) |
| 8 | `(?:class\|struct)\s+(\w+(?:::\w+)*)\s*;\s*$` | **forward declaration** — consume and ignore. Must precede rule 9, or class state opens on a type with no body |
| 9 | `(?:class\|struct)\s+(\w+(?:::\w+)*)\s*(?::\s*(?:public\|protected\|private)\s+(\w+))?` | opens class state; `Klass(name=flatten(\1), base=\2)`. `%delete` is already gone (§3.2 step 8) |
| 10 | `}\s*;?` while class or enum state is open | decrements that state's depth (see bookkeeping below) |
| 11 | inside enum state: `(\w+)\s*(?:=[^,]*)?,?\s*$` | `Const(name=\1, type="number")` |
| 12 | any line containing the word `operator` | **skip** (179 occurrences) — see below |
| 13 | inside class state, `%member_func\s+(TYPE)\s+(\w+)\s*;` | `Func(name, params=(), returns=map_type(\1))` appended to `methods` — a C++ field exposed as a zero-arg accessor; `name` **must** come from the preceding `%rename` (measured: all 14 occurrences carry one). No `%rename` → skip and log at WARN |
| 14 | inside class state, `(?:(static)\s+)?(?:virtual\s+)?(TYPE)\s+(\w+)\s*\((.*)\)\s*(?:const\s*)?(?:=\s*0\s*)?;` | `Func` appended to `statics` if `\1`, else `methods` |
| 15 | inside class state, `(\w+)\s*\((.*)\)\s*;` where `\1 == class name` **or** an `export_name` is set | `Func` appended to `ctors` |
| 16 | at file scope, same pattern as 14 | `Func` appended to `Group.funcs` |
| 17 | anything else | ignored |

where **`TYPE`** is

```
(?:(?:const|unsigned|signed|struct|static|virtual|inline)\s+)*[\w:]+(?:\s*<[^>]*>)?(?:\s*[*&]+)?
```

A single-token type group is **not** sufficient: measured 185 `const T& Method(…)` and 59
`unsigned int/long/char Method(…)` declarations, all of which a `[\w:]+` group silently drops. The
`(?:=\s*0\s*)?` tail matches pure virtuals (220 occurrences), which are bound like any other method.

- **Rule 15's `export_name` clause** is what makes wxLua's renamed constructor overloads work:
  `%rename wxDateTimeFromJDN wxDateTime(double dateTime);` binds a *constructor* under the Lua name
  `wxDateTimeFromJDN`. Such a `Func` keeps `returns = <class name>` (it constructs one) but is
  emitted at namespace scope under its export name, not as `wx.wxDateTime`.
- **Enum aliases**: when an enum has a name, additionally emit `---@alias <Name> number` (§3.5.3).
  Anonymous enums emit their members only.
- **`{` / `}` bookkeeping**: class and enum state each carry a depth counter, adjusted by
  `line.count("{") - line.count("}")`. Enum state is checked **before** class state, because 11
  enums are declared inside class bodies; a single flat counter per state is sufficient only in that
  order. A single-line `class X { … };` opens and closes on the same line and its body is not
  parsed (measured: 0 such declarations exist, which is why TC 1's fixture is multi-line). A class
  whose depth never returns to zero before EOF is a parse error: exit non-zero naming the file and
  the open declaration.
- **Duplicates**: the same name may be declared in several `#if` branches. Within a group, keep the
  **first** occurrence and discard later ones. "First" is well defined because files are processed
  in sorted order (§3.7).
- **Class re-opening**: `class wxFoo` appearing twice in one group merges into the existing `Klass`;
  a second base is ignored if one is already recorded.
- **Skipping `operator` is safe, and the reason is not the obvious one.** "Not reachable from Lua by
  name" would be a poor justification, because BUG-424 (landing before this feature) models operator
  **metamethods** — so if wxLua bound `operator+` to `__add`, dropping it would make legal
  `wxPoint + wxPoint` an error against a class the engine knows has no `__add`. Checked: it does
  not. `wxLua/bindings/genwxbind.lua` contains no metamethod mapping, and the only `__add`/`__concat`/
  `__eq` hits anywhere in the tree are in the *bundled Lua interpreter* (`modules/lua-5.1/src/ltm.c`)
  and its manual. wxLua's own binding layer (`modules/wxlua/wxlbind.cpp`) uses `__index` and
  `__tostring` only. C++ operators are therefore genuinely unreachable from Lua, by name or by
  metamethod, and skipping them creates no gap under BUG-424. (Emitting `---@operator` would not help
  regardless: like `@overload`, it is parsed but consumed only by `LuaComment`/`LuaDocGenerator`,
  never by `LuaTypeGraphBridge`.)
- ⚠ **Qualified names (`Parent::Name`) must be captured whole.** 6 classes
  (`class %delete wxDateTime::TimeZone`, `wxString::const_iterator`, …) and 29 enums
  (`enum wxDateTime::Month`) are declared this way. A `(\w+)` name group stops at the `::` and
  captures the **parent** — so the class rule opens state on `wxDateTime`, the "class re-opening"
  rule below folds `TimeZone`'s body into the real `wxDateTime` (which then gains `Make` and
  `GetOffset`, methods it does not have), and 18 enum aliases collide with real class names. This
  is corruption, not loss, and neither the residue sampling nor the coverage ratchet can see it —
  the lines *match*.
  - **Classes**: flatten `Parent::Name` → `Parent_Name`, giving the nested type its own LuaCATS
    type name. wxLua exposes it as a distinct table, so a distinct type is correct.
  - **Enums**: the alias takes the **last** segment (`wxDateTime::Month` → `---@alias Month
    number`), which is what §3.4 step 2 produces for a parameter typed `wxDateTime::Month`. The two
    must agree or every qualified-enum parameter (95 sites) silently degrades to `any`.
- ⚠ **`struct` binds exactly like `class`** — 8 declarations, 7 emitted types (`wxMatrix2D`,
  `wxSplitterRenderParams`, `wxHeaderButtonParams`, `wxRendererVersion`, `wxComboCtrlFeatures`,
  `wxLanguageInfo`, `wxPGPaintData`). Rules 8/9 must accept both keywords. Measured cost of
  matching `class` only: those 7 types are never emitted (so every signature naming one degrades to
  `any`), **and** their members escape to file scope — `wx.IsCompatible` was emitted as a global
  function that does not exist. This is N-1's failure mode in miniature and it is why the
  §3.3 catch-all is dangerous rather than merely lossy.
- **Plain C++ struct fields** (`wxString caption;` inside a class body) match no rule and are
  ignored — correctly: wxLua exposes a field to Lua only via `%member_func` (rule 13).

**Measured outcome of these rules** over the 42 pinned `.i` files. Reproduce with
`python3 tooling/spikes/target-10-wxi-grammar/probe.py <bindings-dir>` — every number here is that
command's output, not a grep:

```
  10084  method        507  %wxEventType     44  #define_pointer
   2942  #define       254  enum             30  #define_object
   2307  enum_member   208  #define_string   14  %member_func
   1033  ctor          179  operator (skipped)
    764  class/struct  148  free_function

wx         consts=3448  classes=726  methods=8296  ctors=990  statics=494  free=140  aliases=186
wxaui      consts= 173  classes= 29  methods= 458  ctors= 36  statics=  2  free=  0  aliases= 13
wxstc      consts=2300  classes=  2  methods= 788  ctors=  2  statics=  1  free=  0  aliases=  0
wxwebview  consts=  34  classes=  7  methods=  57  ctors=  5  statics=  2  free=  0  aliases=  5
```

1,443 lines match no rule; **389** of those end in `;`.

**The Phase 1 acceptance gate — this is what replaces "the rule list is complete".** Phase 1 is not
done when the parser reproduces these counts. It is done when:

1. **Counts match** the table above (a floor, not a target — a higher method count may be a fix or
   may be invented API).
2. **The emitted name set matches `names2.json` in *both* directions.** A count-only check is
   worthless here: every hole found after round 2 *raised* a count. `wx.IsCompatible` was a surplus
   name produced by a missing `struct` rule.
3. **The `;`-terminated residue is classified, not spot-checked.** Every line is either a plain C++
   struct field (the large majority, correctly ignored) or gets a §3.3 rule. Two of the four
   grammar holes found during planning were sitting in this residue.
4. **No emitted class carries a member declared inside a differently-named type.** This is the
   qualified-name check; nothing else catches it.

Finding a further form under (3) is expected and is a Phase 1 task, not a re-plan.

### 3.3b Parameter-list parsing

- **Input → Output**: the raw text between the parentheses captured by rules 13/14/15/16 →
  `tuple[Param, ...]`.
- **Steps**:
  1. Trim. Empty, or exactly `void` → return `()`.
  2. Split on commas **at nesting depth 0**, where depth counts `(`, `[`, `<` as opening and
     `)`, `]`, `>` as closing. Depth-aware splitting is required by real defaults such as
     `wxDateTime::Month month = wxDateTime::Inv_Month` (no nesting) *and* by templated types.
  3. For each fragment, in order:
     a. If it is `...` → `Param(name="...", type="any", optional=False)`; stop processing this
        fragment.
     b. Split off a default value on the first top-level `=`; its presence sets `optional=True`.
        The default's *value* is discarded — the stub asserts types, never values.
     c. From the remainder, strip `const`, `*`, `&` and collapse whitespace.
     d. If the remainder has **two or more** tokens, the **last** is the parameter name and
        everything before it is the C++ type. If it has exactly **one** token, that token is the
        type and the parameter is **unnamed**.
     e. An unnamed parameter's name becomes `arg<N>`, `N` being its 1-based position in the list.
     f. `type = map_type(<the type tokens>)` (§3.4).
  4. Apply the optional cascade: once one parameter is optional, every later parameter is forced
     optional too (§3.5.7).
- **Rules / edge handling**: a fragment that reduces to nothing after step (c) — which a stray
  trailing comma would produce — is dropped. Array suffixes (`int values[]`) are stripped from the
  name and the type becomes `table`.

### 3.4 C++ → LuaCATS type mapping (`map_type`)

- **Input → Output**: a raw C++ type string, plus the set of class names and alias names known
  across *all* groups → a LuaCATS type name.
- **Steps**:
  1. Strip `const`, `*`, `&`, and surrounding whitespace. Collapse internal whitespace.
  2. Strip everything before the last `::` (so `wxDateTime::Month` → `Month`). **Do not strip a
     `wxLua` prefix** — `wxLuaPrintout`, `wxLuaHtmlWindow`, `wxLuaListCtrl`, `wxLuaTreeItemData`,
     `wxLuaArtProvider`, `wxLuaProcess`, `wxLuaGridTableBase`, `wxLuaDataObjectSimple`,
     `wxLuaFileDropTarget` and `wxLuaHtmlWinTagEvent` are all **bound classes** (verified: each has
     a `class …` declaration in a `.i` file), and stripping the prefix would degrade every one of
     them to `any`. `wxLuaObject` is the exception that proves the rule — it appears only as a
     parameter type, is never declared, and so correctly falls through to `any` at step 5.
  3. Look the result up in the table below. A hit returns its mapping.
  4. Otherwise, if the result is in `known_classes` **or** the alias-name set, return it verbatim
     (this is how `wxWindow*` becomes `wxWindow` and `wxDateTime::Month` becomes `Month`).
  5. Otherwise return `"any"`.

| C++ | LuaCATS |
|---|---|
| `void` | *(no return; the caller omits the `@return` line)* |
| `bool` | `boolean` |
| ⚠ every integral type — `int`, `long`, `short`, `size_t`, `ssize_t`, `unsigned*`, `wxCoord`, `wxWindowID`, `wxEventType`, `wxFileOffset`, `wxItemId`, `wxPrintQuality`, `wxInt8/16/32/64`, `wxUint8/16/32/64`, `wxByte`, `wxWord` | **`number`** — *not* `integer`; see below |
| `float`, `double` | `number` |
| `wxString`, `char`, `wxChar`, `wxUniChar` | `string` |
| `wxUIntPtr`, `wxIntPtr`, `WXWidget`, `WXHWND`, `WXDWORD`, `WXMSG` | `userdata` |
| `wxArrayString`, `wxArrayInt`, `wxSortedArrayString`, `wxArrayDouble` | `table` |
| `wxLuaState`, `lua_State` | `any` |
| anything in `known_classes` or the alias-name set | itself |
| anything else | `any` |

> **Note.** The mapping follows the *declared* type, never the intent. `wxFileName` is a bound
> class, so it falls through to rule 4 and maps to `wxFileName` — not to `string`, even though the
> surrounding C++ API frequently accepts `const wxString&` where a caller passes a path.
> A bare `void` reaching `map_type` as a *parameter* type (`void*` after step 1) maps to `userdata`;
> `void` as a *return* type is handled before `map_type` is called and emits no `@return` line.

- **Enum-typed parameters** (`wxSignal`, `wxKillError`, …) are class-less named enums; they resolve
  through the `---@alias <Name> number` emitted in §3.3, so rule 4 must consult the alias-name set
  as well as `known_classes`.

#### ⚠ The governing rule: map to the widest type that is still true

A definition library's false positives are **unactionable**. When Lunar's own inference is wrong the
user can add an annotation; when a *fetched, read-only* library asserts a type too narrowly, the
only remedies are disabling the inspection or disabling the library. So every row above resolves
ties toward the wider type, and an unmapped type becomes `any` (rule 5) rather than a guess.

**This is why integral C++ types map to `number`, not `integer`** — a change from an earlier draft
of this design, and the single highest-blast-radius decision in §3.4:

- **Blast radius**: 7,469 parameter and return sites carry an integral C++ type (4,512 are bare
  `int`). A wrong mapping here is not an edge case, it is most of the API surface.
- **Lua 5.1 and 5.2 have no integer subtype.** The pinned ZeroBrane corpus is `"luaLevel": "LUA51"`
  (`tooling/corpus/corpus.json:58`). Declaring `integer` there asserts a distinction the language
  does not have.
- **The type engine relates them not at all.** `LuaPrimitiveType.isAssignableTo`
  (`lang/psi/types/LuaPrimitiveType.kt:10-18`) returns true only for `other == ANY`, `this == other`,
  or union membership — there is **no** integer↔number widening or narrowing in either direction.
  Meanwhile `LuaGraphType` collapses both to `Number` (`LuaGraphType.kt:141,147`), so a value's
  inferred type and a declared `integer` contract are produced by different paths that need not
  agree.
- **The precision buys nothing.** No user benefits from `wxFrame:SetSize(w, h)` demanding `integer`
  over `number`.

`number` is true at every language level, assignable from every numeric literal, and costs no
capability. `integer` risks a false `LuaTypeAssignability` error on a large fraction of 7,469 sites
in a library the user cannot edit.

**`bool` → `boolean` carries the same shape of risk at smaller scale** and is covered by the same
DR-08 measurement below.

### 3.5 Emission grammar

Every emitted file opens with:

```lua
---@meta

-- Generated by tooling/definitions/wxlua/generate.py from wxLua @ <commit>.
-- Do not edit by hand. See PROVENANCE.md.
-- wxLua is distributed under the wxWindows Library Licence v3; so is this derived file.
```

#### 3.5.1 Namespace root — `library/<ns>.lua`

One per distinct namespace. Exists so `require("wx")` resolves by file name
(`LuaLibraryModuleResolutionTest.testRequireResolvesIntoALibraryRoot`, TC 14):

```lua
---@class wx
wx = {}

return wx
```

The global assignment makes `wx` an ambient global (wxLua's `wx` is global once the binary loads);
`return wx` makes the file a requirable module. Both are needed — ZeroBrane writes `require("wx")`
*and* uses `wx` bare.

#### 3.5.2 File layout — RESOLVED BY DR-06 (2026-08-07): minimal root + class bodies

**Both branches below were refuted by measurement; this is the layout that replaced them.**

- Namespace-level members — `wx.wxCONST = nil`, `function wx.wxFrame(...)` constructors, free
  functions — **must** be written into the file that declares `---@class wx` / `wx = {}`. A sibling
  file resolves nothing, **and re-declaring `---@class wx` in the sibling does not merge either**
  (measured; the option the original §3.5.2 never considered).
- Class bodies — `---@class wxFrame` / `local wxFrame = {}` / its methods — go in sibling files
  `library/<ns>/<cpp>.lua`. Flat type names resolve cross-file, including cross-namespace
  (`wxstc.wxStyledTextCtrl : wxControl` offers both its own and inherited members).

This is the arrangement that both resolves and keeps the root file as small as it can be. It is
**still over budget** — a 230 KiB root costs 12.9 s to first completion — which is BUG-429, and why
Phase 4 is blocked. Phases 1–3 are unaffected: the emitted text is the same either way.

<details><summary>Superseded: the original split-vs-single question</summary>

`wxcore_*.i` → `library/wx/wxcore.lua`; `wxstc_stc.i` → `library/wxstc/wxstc.lua`. One file per
**C++ group**, not per `.i` file, so filenames stay stable when upstream splits a header.

**This split is the design's least-proven choice and is gated on DR-06.** The namespace table `wx`
is declared in `library/wx.lua` (§3.5.1) while its members — `wx.wxSTC_START = nil`,
`function wx.wxFrame(…) end` — are written in *sibling* files that never re-declare `---@class wx`.
Neither reference layout does this: `LuaCATS/love2d` declares `---@class love.math` + `love.math = {}`
at the head of `library/love/math.lua` before adding to it, and Lunar's own bundled stub
(`runtime/standard/lua-5.4/string.lua:26`) declares and populates in one file. wxLua has no
sub-namespaces to hang such a re-declaration on — every member sits directly on `wx`.

The mechanism that should make it work is real and indexed: `LuaMemberFieldIndex` owns the dotted
`receiver.field = value` case and `LuaGlobalAssignmentIndex`
(`lang/indexing/LuaGlobalAssignmentIndex.kt:28,38`) is its sibling for bare globals — both added by
BUG-391 and both project-and-library wide. But "should" is a reading, and this design does not get
to assert behaviour from reading.

- **DR-06 verdict `split`** — keep this layout.
- **DR-06 verdict `single`** — the fallback, fully specified: emit **one file per namespace**
  (`library/wx.lua`, `library/wxstc.lua`, `library/wxaui.lua`, `library/wxwebview.lua`), each
  carrying its own `---@class <ns>` + `<ns> = {}` header, all of that namespace's groups
  concatenated in `cpp`-sorted order, and a single trailing `return <ns>`. `library/<ns>/` is not
  created. This matches both reference layouts exactly and costs only a large `wx.lua`
  (feeding Risk 1.3's measurement, not creating a new risk).

Nothing else in the design depends on which verdict lands: §3.5.3–§3.5.8 render identical text
either way, and only the file each block is written to changes.

</details>

#### 3.5.3 Constants and aliases

```lua
---@alias wxSignal number

---Define start of Scintilla messages.
---@type number
wx.wxSTC_START = nil
```

`= nil` rather than `= 0`: the stub asserts a type, never a value, and a wrong value is worse than
no value.

#### 3.5.4 Classes and instance methods

The **class** idiom Lunar demonstrably indexes today — `LuaCATS/love2d` `library/love/math.lua:478`
uses it verbatim, and `LuaTypeGraphBridge` reads exactly these tags. (That citation covers the
class-declaration idiom only; it is **not** a precedent for the cross-file namespace layout, which
is §3.5.2's DR-06.)

```lua
---A frame is a window whose size and position can be changed by the user.
---@class wxFrame : wxTopLevelWindow
local wxFrame = {}

---@param show boolean
---@return boolean
function wxFrame:Show(show) end
```

- `: <Base>` is emitted whenever a base is declared. If the base is **not** in `known_classes`,
  emit a bare `---@class <Base>` stub for it rather than dropping the `: <Base>` clause — dropping
  it severs the inheritance chain, and a subclass that no longer resolves as its parent produces a
  *false* incompatibility at every call site expecting the parent. Measured: only **3** of 150
  referenced bases are undeclared (`wxScrolled`, `wxHtmlListBox`, `wxVScrolledWindow`), so this
  costs three stub lines and removes the failure mode entirely.
- A `void` return emits no `@return` line.

#### 3.5.5 Constructors

```lua
---@param parent wxWindow
---@param id integer
---@param title string
---@return wxFrame
function wx.wxFrame(parent, id, title) end
```

Placed in the same group file as the class. The **return type is the whole point** — this is the one
inference that makes the library worth having, and it is why constructors outrank statics in §3.6.

#### 3.5.6 Overloads

wxLua binds several C++ signatures to one Lua name. Emit the **primary** signature as the
`function` declaration and every other as an `---@overload fun(...)` line above it.

- **Primary selection**: the signature with the **most** parameters. Ties broken by the rendered
  parameter-type string, lexicographically ascending. Rationale: the widest signature names the most
  parameters, and every narrower call is still accepted because trailing parameters render optional.
- **Overload ordering**: ascending by parameter count, then lexicographic — deterministic (§3.7).
- `@overload` drives `LuaParameterInfoHandler` hints (`lang/insight/hint/LuaParameterInfoHandler.kt:174`)
  but **not** inference (`LuaTypeGraphBridge` never reads `overloadTagList`). Nothing in this design
  depends on it for a type.

#### 3.5.7 Parameter rendering

The `Param` tuples come from §3.3b; this section covers only how they are written out.

- `Param.name` is used verbatim, unless it is a Lua keyword (`end`, `function`, `local`, `then`,
  `repeat`, `nil`, `true`, `false`, `and`, `or`, `not`, `in`, `do`, `if`, `else`, `elseif`, `for`,
  `while`, `return`, `break`, `until`, `goto`), in which case `_` is appended.
- `Param.optional` renders a `?` suffix on the `@param` name (`---@param segments? integer`),
  matching love2d's convention. The optional cascade is already applied by §3.3b step 4.
- `name == "..."` renders as `---@param ... any` and as `...` in the parameter list.
- Two parameters that collide after keyword-suffixing (or two unnamed ones that both became `argN`
  — impossible, since `N` is positional) get a `_2`, `_3`, … suffix in declaration order. Lua would
  otherwise silently shadow the first.

#### 3.5.8 Free functions

```lua
---@param filename string
---@return boolean
function wx.wxFileExists(filename) end
```

### 3.6 Static methods — the DR-02 branch

wxLua exposes statics at the same Lua path as the constructor: `wx.wxFileName.GetCwd()` alongside
`wx.wxFileName(path)`. In Lua a value cannot be both a function and a table, so the stub must choose
unless Lunar tolerates both declarations coexisting in a library file. That is not knowable by
reading; **DR-02 runs it** ([risks-and-gaps.md](risks-and-gaps.md)).

Both branches are fully specified. `emit.render_group` takes `statics_mode` (§2.3); DR-02 sets it.

**DR-02 RESOLVED 2026-08-07: Branch B.** Measured — `wx.wxFileName.<caret>` offers nothing when
`wx.wxFileName` is also declared as a constructor function. The constructor half works
(`local f = wx.wxFileName("x")` → `f:` offers instance members), so `statics_mode="on-class"` is the
implemented behaviour and `wx.wxFileName.GetCwd()` does not complete. Branch A is retained below for
the record.

**Branch A — `statics_mode="dotted"` (REFUTED).** Emit both:

```lua
---@return wxFileName
function wx.wxFileName(path) end        -- constructor (§3.5.5)

---@return string
function wx.wxFileName.GetCwd() end     -- static, at the real Lua path
```

**Branch B — `statics_mode="on-class"` (fallback; used if DR-02 fails).** The constructor keeps the
namespace path; statics move onto the class table:

```lua
---@class wxFileName
local wxFileName = {}

---@return string
function wxFileName.GetCwd() end        -- reachable through the wxFileName type

---@return wxFileName
function wx.wxFileName(path) end        -- constructor unchanged
```

Under Branch B, `wx.wxFileName.GetCwd()` does not complete. That is the accepted cost, and it is the
right way round: the pinned ZeroBrane corpus references `wx.wxFileName` 106 times, overwhelmingly as
a constructor call, and there are ~750 constructors against 499 statics across the whole binding.
Branch B additionally files a follow-on bug against the type engine (statics on a
constructor-function path), recorded in [risks-and-gaps.md](risks-and-gaps.md) → Technical Debt.

### 3.7 Determinism

Every ordering is explicit; nothing iterates a `set` or `os.listdir` directly.

1. `*_rules.lua` and `*.i` files: `sorted(dir.iterdir())` by name.
2. Within a group: constants sorted by name; aliases sorted by name; classes sorted by name;
   free functions sorted by name.
3. Within a class: constructors by (parameter count, rendered signature); methods by
   (name, parameter count, rendered signature); statics likewise.
4. Overload lines within one function: §3.5.6's ordering.
5. Output files are written with `"\n"` newlines and a single trailing newline, UTF-8, no BOM.
6. Sections within a group file appear in a fixed order: header, aliases, constants, classes
   (each: class block, then its instance methods, then its statics per §3.6, then its constructors),
   free functions.

`generate.py --check` re-runs generation into a temporary directory and `filecmp.dircmp`s it against
`--out`, exiting non-zero on any difference (TC 9).

### 3.8 Coverage measurement

- **Input → Output**: emitted names per namespace + a corpus checkout → a report dict (§4.3).
- **Steps**:
  1. Walk `corpus_root/<root>` for each configured root, skipping any path component in `prune`,
     collecting `*.lua` files.
  2. Scan each file with `\b(wx|wxstc|wxaui|wxwebview)\.([A-Za-z_][A-Za-z0-9_]*)`, accumulating
     `namespace → {member}`. (String-literal and comment occurrences are counted; this over-counts
     slightly and is stated as such in the report — a stricter scan would need a Lua parser, which
     the generator deliberately does not carry.)
  3. `emitted[ns]` is the set of names the generator wrote at namespace level: constants, class
     names, free functions.
  4. For each namespace: `covered = used & emitted`, `missing = sorted(used - emitted)`.
  5. Compare each namespace's `covered/used` percentage against `coverage-floor.json`. Below floor →
     exit non-zero, printing `missing`.
- **Floor semantics**: a ratchet. It may be raised when a run improves on it; lowering it requires
  editing the checked-in file, which is visible in review.
- **Expected values.** Running the §3.2/§3.3 reference
  (`tooling/spikes/target-10-wxi-grammar/probe.py`) and intersecting with the pinned ZeroBrane
  corpus gives:

  | Namespace | used | covered | % | not covered |
  |---|---:|---:|---:|---|
  | `wx` | 311 | 305 | **98** | `NULL`, `WXK_RAW_CONTROL`, `wxEXEC_BLOCK`, `wxEXEC_HIDE_CONSOLE`, `wxEXEC_NOEVENTS`, `dll` |
  | `wxstc` | 144 | 143 | **99** | `wxSTC_SETLEXERLANGUAGE` |
  | `wxaui` | 24 | 23 | **95** | `wxAUI_TB_PLAIN_BACKGROUND` |

  Percentages are **truncated**, not rounded (`100 * covered // used`) — 23/24 is 95, not 96. The
  floor file stores the same truncated integer, so a floor comparison is exact.

  `dll` is report noise — the scan is a regex, and `wx.dll` occurs in a ZeroBrane *comment*
  (`src/main.lua:375`). The other seven are §4.5's supplement. Phase 3 sets the floors from its own
  run; these are what it should expect to see, and a materially lower number means a §3.2/§3.3 step
  was mis-implemented rather than that the floor needs lowering.

## 4. External Data & Parsing

### 4.1 wxLua `.i` binding-interface files

- **Format**: C++-like declarations with `%`-prefixed attributes and preprocessor conditionals.
  Verbatim samples of **every** form §3.3 recognises, from the pinned checkout, with the measured
  occurrence count. The counts are the authority on whether the grammar is complete: a form with a
  non-zero count and no §3.3 rule is a hole.

  | Form | Grep | Rule fires | Verbatim sample |
  |---|---:|---:|---|
  | `#define NAME` | 2874 | 2942 | `#define wxSTC_STYLE_DEFAULT` |
  | `%wxEventType NAME` | 525 | 507 | `%wxEventType wxEVT_COMMAND_MENU_SELECTED   // EVT_MENU(winid, func)` |
  | `#define_wxstring NAME [= wxT("…")]` | 143 | 208 *(with `_string`)* | `#define_wxstring wxDirSelectorPromptStr wxT("Select a directory");` |
  | `#define_string NAME` | 68 | *(above)* | `%wxchkver_2_9_0 #define_string wxFileSelectorDefaultWildcardStr` |
  | `#define_pointer NAME` | 45 | 44 | `%rename wxBLACK      #define_pointer wxLua_wxBLACK` |
  | `#define_object [Type] NAME` | 31 | 30 | `#define_object wxPoint wxDefaultPosition` / `#define_object wxDefaultValidator` |
  | `%rename LuaName <decl>` | 84 | *(name capture)* | `%rename wxDateTimeFromJDN wxDateTime(double dateTime);` |
  | `%override_name CppName <decl>` | 24 | *(discarded)* | `%override_name wxLua_wxBitmapFromBitTable_constructor wxBitmap(LuaTable charTable, int width, int height, int depth /* = 1 */);` |
  | `%member_func Type m_field;` | 14 | 14 | `%rename X %member_func wxInt32 m_x;` |
  | `!%<guard> <decl>` (negated) | 457 | *(stripped)* | `!%wxchkver_3_1_1 #define wxSTC_SCMOD_CTRL` |
  | ⚠ `%A && %B <decl>` (compound guard) | 170 | *(stripped)* | `%wxchkver_3_0_0 && %gtk wxString GetInstallPrefix() const;` |
  | bare `wxUSE_*` condition prefix | 23 | *(stripped)* | `wxUSE_ACCEL virtual wxAcceleratorEntry *GetAccel() const;` |
  | `enum [Name] { … };` | 254 | 254 | `enum wxSignal { wxSIGNONE, wxSIGHUP, wxSIGINT };` |
  | `class [%delete] Name [: public Base]` | 789 *(487 with `%delete`)* | 764 *(with `struct`)* | `class %delete wxColour : public wxGDIObject` |
  | ⚠ `struct [%delete] Name` | 8 | *(above)* | `struct %delete wxRendererVersion` |
  | method / free function | — | 10071 / 146 | `%wxchkver_3_0_0 static bool IsMainLoopRunning();` |
  | pure virtual `… = 0;` | 220 | *(as method)* | `virtual bool IsContainer( const wxDataViewItem &item ) const = 0;` |
  | `operator …` — **skip** | 201 | 179 | `wxDataViewItem operator[](size_t nIndex);` |
  | `//`-commented — **discard** | 5301 | — | `// void OnAssertFailure(const wxChar *file); // not supported` |
  | `%property` | **0** | — | *(no rule — the form does not occur)* |

  "Grep" is the line count from the corresponding command below (non-comment lines only, except the
  `//` row itself); "Rule fires" is `probe.py` actually running. The two columns differ where a rule
  folds forms together (`_string`/`_wxstring`, `class`/`struct`) or where a grep counts a line a
  guard-strip later merges. **Both are needed.** A grep alone told an earlier draft the `class` form
  occurred 275 times, and the rule was written to that number. The two rows marked ⚠ were missing
  entirely from the previous revision of this table and were found only by sampling the parse
  residue — which is why §3.3 requires that sampling as a Phase 1 task.

  Reproduce with, in `wxLua/bindings/wxwidgets/`:

  ```bash
  # every "Grep" cell: a non-comment line count
  nc() { grep -hE "$1" *.i | grep -vE "^[[:space:]]*//" | wc -l; }
  nc '^[[:space:]]*(!?%[A-Za-z_0-9]+[[:space:]]+)*#define[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*$'
  nc '^[[:space:]]*!?%[A-Za-z_0-9]+[[:space:]]*(&&|\|\|)'     # compound guards
  nc '^[[:space:]]*(wxUSE_|wxLUA_USE_)[A-Za-z0-9_]*[[:space:]]+[A-Za-z_%]'
  nc '^[[:space:]]*class[[:space:]]' ; nc '^[[:space:]]*struct[[:space:]]' ; nc 'operator'
  grep -ho "#define_[a-z]*" *.i | sort | uniq -c
  python3 <repo>/tooling/spikes/target-10-wxi-grammar/probe.py .      # the "Rule fires" column
  ```

  A realistic combined region:

  ```c
  #if wxLUA_USE_wxApp
  #include "wx/app.h"
  wxApp* wxGetApp(); // %override wxApp* wxGetApp();

  class wxApp : public wxAppConsole
  {
      %wxchkver_3_0_0 wxApp();
      bool GetExitOnFrameDelete() const;
      %wxchkver_3_0_0 static bool IsMainLoopRunning();
      // void OnAssertFailure(const wxChar *file); // not supported
  };
  #endif
  ```

- **Parse strategy**: line-oriented, per §3.2 (normalisation) then §3.3 (recognition) and §3.3b
  (parameter lists). No C++ parser, no preprocessor. This is adequate because the files are
  machine-consumed by wxLua's own `genwxbind.lua`, which is itself a line-oriented Lua script — the
  format is disciplined by construction. It is **not** adequate if a form is missing from §3.3,
  which is why the table above is measured rather than sampled and why the coverage ratchet
  (§3.8) exists as a backstop for §3.3's silent catch-all (rule 17).
- **Maps to**: `Group` / `Klass` / `Func` / `Const` (§2.2).
- **Failure handling**: an unbalanced class or enum brace at EOF is a hard error (exit non-zero,
  naming the file and the open declaration). An unrecognised line is silently ignored — rule 11 —
  because the files carry large amounts of prose comment and C++ that binds nothing.

### 4.2 The catalog entry — constraints imposed by existing code

Read from `LuaDefinitionCatalogLoader.kt` and its test, not assumed:

| Constraint | Enforced by | Consequence here |
|---|---|---|
| every URL contains `version` | `LuaDefinitionCatalogLoader.kt:69-73` (`corrupt(...)`) | `version` **is** the published repo's commit SHA, and both mirrors embed it |
| `rootPrefix` ends `/library` | `LuaDefinitionCatalogLoaderTest.everyBundledEntryIsPinnedAndAttributed` | the published repo must put definitions in `library/` |
| `sha256` non-blank, `size > 0` | `LuaDefinitionCatalogLoader.kt:93-94` | both read back from the actual tarball |
| `id` unique | `LuaDefinitionCatalogLoader.kt:62-65` | `wxlua` is new |
| `requires` names known ids | `LuaDefinitionCatalogLoader.kt:76-81` | `[]` — one entry, no dependencies |
| unknown fields ignored | `parseEntry` extracts named fields manually (`:85-100`) | `detectionPatterns` is safe to write before TARGET-09 lands |

GitHub's archive endpoint extracts to `<repo>-<sha>/`, so `rootPrefix` is
`lunar-definitions-wxlua-<sha>/library`. Gitea's `.../archive/<sha>.tar.gz` extracts to `<repo>/`
— **so the Gitea URL cannot be a mirror of the GitHub one**: `LuaArchiveExtractor` strips one
`rootPrefix`, and two mirrors with different prefixes cannot share an entry. The Gitea mirror is
therefore **omitted** from `urls`; GitHub is the single source. This is a constraint discovered from
the extractor's contract, not a preference.

### 4.3 Coverage report format — `coverage.json` (written beside the output tree)

```json
{
  "wxluaCommit": "4d83c8d44eeccf88683ca0146a13b16d0b0d4264",
  "corpus": "zerobrane",
  "note": "usage is a regex scan; string-literal and comment occurrences are counted",
  "namespaces": {
    "wx":    { "used": 311, "emitted": 5981, "covered": 300, "percent": 96, "missing": ["dll"] },
    "wxstc": { "used": 144, "emitted": 2246, "covered": 144, "percent": 100, "missing": [] },
    "wxaui": { "used": 24,  "emitted": 200,  "covered": 24,  "percent": 100, "missing": [] }
  }
}
```

Counts above are illustrative of the *shape*; the real values come from the first generator run and
are what `coverage-floor.json` is then set from (§3.8).

`coverage-floor.json` is the minimal form, populated in Phase 3 from the first real run (§3.8):

```json
{ "wx": <measured>, "wxstc": <measured>, "wxaui": <measured> }
```

### 4.4 The wxLua checkout

Fetched exactly as the corpus is (`tooling/corpus/fetch-corpus.py`): `git init` + `git remote add` +
`git fetch --depth 1 origin <commit>` + `git checkout FETCH_HEAD`, `.git` removed. The generator
does **not** fetch — it takes `--wxlua <path>` and fails if `bindingsDir` is absent, so it is usable
offline and in CI without network.

### 4.5 `supplement.lua` — required initial contents

**Deliberately near-empty.** An earlier draft of this design listed ~28 global objects here on the
strength of `grep -rn "wxDefaultPosition\s*;" *.i` returning nothing. That grep only matched the C++
*variable-declaration* shape; re-running it without the shape assumption disproves it:

```
wxcore_gdi.i:19    #define_object wxPoint wxDefaultPosition
wxcore_gdi.i:88    #define_object wxDefaultSize
wxcore_gdi.i:649   #define_object wxNullColour
wxcore_gdi.i:650   %rename wxBLACK      #define_pointer wxLua_wxBLACK
wxstc_stc.i:2449   !%wxchkver_3_1_1 #define wxSTC_SCMOD_CTRL
```

Every one of those is declared, and §3.3's rules 2–6 now emit them. Curating them as well would put
the same name in a generated file **and** the curated file, breaking the "generated and curated
never mix" rule the supplement exists to uphold.

The initial supplement therefore contains only what a full grep across all 42 `.i` files finds no
declaration for at all. This is the measured residual of §3.8's coverage run:

| Name | Namespace | Emitted as | Why absent |
|---|---|---|---|
| `NULL` | `wx` | `---@type any` | wxLua registers `wx.NULL` from the module's C++ init |
| `WXK_RAW_CONTROL` | `wx` | `---@type number` | keycode registered outside `.i` |
| `wxEXEC_BLOCK`, `wxEXEC_HIDE_CONSOLE`, `wxEXEC_NOEVENTS` | `wx` | `---@type number` | `wxbase_override.hpp` |
| `wxSTC_SETLEXERLANGUAGE` | `wxstc` | `---@type number` | `wxstc_override.hpp` |
| `wxAUI_TB_PLAIN_BACKGROUND` | `wxaui` | `---@type number` | `wxaui_override.hpp` |

**Phase 3 must re-derive this list, not copy it.** The procedure: run the generator, run the
coverage report against the pinned ZeroBrane corpus, and add exactly the names in
`coverage.json`'s `missing` array — each with a `-- absent from .i: <why>` comment naming the
`*_override.hpp` or C++ site that registers it. Exclude regex noise (`dll`, §3.8). A name that turns
out to be declared after all belongs in §3.3, not here. Seven rows is the expected size; thirty
means a §3.2 step is broken.

Anything added later carries the same comment, so the curated/generated boundary stays auditable.

## 5. Data Flow

### Example 1: Maintainer regenerates the tree at a new wxLua pin

Edit `commit` in `tooling/definitions/wxlua/wxlua.json` → fetch the checkout → run
`generate.py --wxlua <checkout> --out <published-repo> --corpus test/corpus/zerobrane` → §3.1
discovers the namespaces → §3.2/§3.3 parse each group → §3.5 renders `library/wx.lua`,
`library/wx/wxcore.lua`, … → `supplement.lua` is copied only if absent (§2.6) → `coverage.json` is
written and checked against the floor (§3.8) → `git diff` in the published repo is reviewable
because the output is deterministic (§3.7) → commit, push, note the SHA → recompute
`sha256`/`size` of `https://github.com/<owner>/lunar-definitions-wxlua/archive/<sha>.tar.gz` →
update the catalog entry in this repo.

### Example 2: User enables wxlua and completes a member

*Settings → Lua Project → Definition Libraries* → tick **wxlua** → `LuaDefinitionLibraryEnabler.apply`
persists the id and dispatches the existing background fetch → `LuaArtifactDownloader` verifies
`sha256`/`size` → `LuaArchiveExtractor` strips `lunar-definitions-wxlua-<sha>/library` →
`LuaDefinitionLibraryProvider` registers the extracted dir → the indexer and the LuaCATS lexer
consume `---@meta` → typing `wx.wxFra` offers `wxFrame`; `local f = wx.wxFrame(nil, wx.wxID_ANY, "t")`
infers `wxFrame`; `f:` offers `Show`, `Close`, and everything inherited from `wxTopLevelWindow`.

### Example 3: MAINT-37 re-baselines ZeroBrane

MAINT-37 pins `wxlua` for the ZeroBrane sweep → `require("wx")` resolves via `library/wx.lua`
(§3.5.1), removing it from the 19 `unresolvedRequires` recorded in
[MAINT-33's checklist](../../maint/33-corpus-sweep/human-verification-checklists.md) → the
`LuaUndeclaredVariable` floor drops by the ~300 covered `wx`/`wxstc`/`wxaui` members, making the
residue attributable (MAINT-33 Gap 2.2's actual question).

## 6. Edge Cases

- **A class's base is in another namespace** (`wxstc.wxStyledTextCtrl : wxControl`). Handled by flat
  type names: `known_classes` is built across **all** groups before any file is rendered, so the
  base resolves and is emitted. Measured: 0 class-name collisions across all four namespaces.
- **An unknown base type.** Dropped (§3.5.4) rather than emitted — a dangling `: wxFoo` would make
  every member of the class unresolvable, which is worse than a flat class.
- **The same constant in two `#if` branches.** First occurrence wins (§3.3), deterministically.
- **A `%wxEventType` and a `#define` for the same name.** Same rule; one `Const` is emitted.
- **A method named like a Lua keyword.** Method *names* in wxWidgets are `PascalCase` and cannot
  collide with Lua keywords; parameter names can, and §3.5.7 handles them.
- **An anonymous enum.** Members emitted, no alias (§3.3).
- **`wxdatatypes_rules.lua`'s empty namespace.** Skipped at discovery (§3.1, TC 5) — not special-cased
  by name, so a future empty namespace behaves the same.
- **A `.i` file with no matching `*_rules.lua`.** Contributes nothing; the generator logs it at
  INFO so an upstream addition is visible rather than silent.
- **Regeneration over an existing output dir.** Generated files are overwritten;
  `library/supplement.lua`, `LICENCE`, `config.json` and `PROVENANCE.md` are written only if absent
  (TC 11). Files present in the output but no longer generated are **deleted**, so a removed upstream
  group does not leave a stale file — with `library/supplement.lua` explicitly exempt.
- **The catalog loads before the tarball exists.** Not possible: the entry is added only after the
  tarball is published and its hash read back. If the URL 404s, `ensureCached` returns null and the
  provider contributes no root (TARGET-08 §3.2/§3.5, unchanged) — no crash, no root.
- **A wrong `sha256` does NOT fail the fetch.** Definition libraries are fetched under
  `ArtifactVerification.ADVISORY` (`LuaDefinitionLibraryFetcher.kt:160`), which "logs a mismatch and
  keeps the file" (`LuaArtifactDownloader.kt:22,31,123`). The integrity argument is the URL, not the
  hash: it embeds the immutable commit SHA, which is why the loader rejects a URL not containing
  `version` (`LuaDefinitionCatalogLoader.kt:66-73`). Consequence for Phase 4: reading the hash back
  from the published tarball is a **provenance record**, not a gate — a stale hash produces a log
  line, not a failure, so it must be got right by process rather than trusted to fail loudly.

## 7. Integration Points

**No `plugin.xml` change. No Kotlin change.** The extension points this feature relies on are
already registered by TARGET-08 and are listed here only to make that explicit:

```xml
<!-- src/main/resources/META-INF/plugin.xml — ALREADY PRESENT, unmodified by TARGET-10 -->
<additionalLibraryRootsProvider
        implementation="net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider"/>
<projectConfigurable
        parentId="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"
        instance="net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable"
        id="net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable"
        displayName="Definition Libraries"
        nonDefaultProject="true"/>
```

**Catalog data** — append to `libraries` in
`src/main/resources/definitions/lunar-definitions-catalog.json`. `<sha>`, `<sha256>` and `<size>`
are filled from the published tarball in Phase 4:

```json
{
  "id": "wxlua",
  "displayName": "wxLua (wx / wxstc / wxaui)",
  "version": "<sha>",
  "urls": ["https://github.com/<owner>/lunar-definitions-wxlua/archive/<sha>.tar.gz"],
  "sha256": "<sha256>",
  "size": <size>,
  "rootPrefix": "lunar-definitions-wxlua-<sha>/library",
  "license": "WxWindows-exception-3.1",
  "attributionUrl": "https://github.com/<owner>/lunar-definitions-wxlua",
  "requires": [],
  "detectionPatterns": ["wx%.%w+", "wxstc%.%w+", "wxaui%.%w+"]
}
```

`license` carries the SPDX **exception** identifier `WxWindows-exception-3.1` (note the capital `W`;
it is a licence *exception* to LGPL-2.0+, not a licence id) — the exception attached to
LGPL-2.0+ that `wxLua/docs/licence.txt` reproduces. No other catalog entry uses a non-MIT licence,
so this is the first; nothing in the loader constrains the value beyond non-blank.

**Published repository** — `<owner>/lunar-definitions-wxlua` (GitHub). Required layout, driven by
§4.2 and by LuaCATS convention:

```
library/            <- what rootPrefix registers
config.json         <- LuaLS addon manifest: { "name": "wxLua", "words": [...] }
LICENCE             <- verbatim wxLua/docs/licence.txt
PROVENANCE.md       <- upstream URL + commit, generator path + this repo's commit, regeneration cmd
README.md
```

**Attribution** — one row in this repo's `THIRD-PARTY.md`: component `lunar-definitions-wxlua`,
upstream `pkulchenko/wxlua`, licence *wxWindows Library Licence v3*, note that the tree is derived
from `wxLua/bindings/wxwidgets/*.i` and is **fetched at runtime, never bundled**.

**Tests** — new file
`src/test/kotlin/net/internetisalie/lunar/definitions/LuaWxLuaDefinitionShapeTest.kt`, extending the
existing `LibraryRootTestCase` (`:33`). No new test infrastructure.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|---|---|---|
| TARGET-10-01 | M | §2.1, §2.2, §3.2, §3.3, §4.1 |
| TARGET-10-02 | M | §2.2 (`namespaces`), §3.1 |
| TARGET-10-03 | M | §2.3, §3.4, §3.5, §3.6 |
| TARGET-10-04 | M | §3.7, §2.1 (`--check`) |
| TARGET-10-05 | M | §2.6, §4.5, §6 (regeneration exemption) |
| TARGET-10-06 | M | §7 (published repository layout), §4.2 |
| TARGET-10-07 | M | §4.2, §7 (catalog JSON) |
| TARGET-10-08 | M | §3.5.1, §5 Example 3 |
| TARGET-10-09 | S | §2.4, §3.8, §4.3 |
| TARGET-10-10 | S | §7 (attribution) |
| TARGET-10-11 | C | §4.2 (unknown fields ignored), §7 (catalog JSON) |

## 9. Alternatives Considered

- **Point the catalog at an existing upstream tree.** Impossible — enumerated the LuaCATS org (29
  repos) and `LuaLS/LLS-Addons` (86 submodules); neither has wxLua
  ([research.md §1](research.md)). This is the finding that made TARGET-10 a generation feature.
- **Derive from ZeroBrane's `api/lua/wxwidgets.lua`.** Rejected on reading it: an 812-byte runtime
  introspector with `description = ""` for every member, requiring a live wxLua process
  ([research.md §2](research.md)).
- **Reuse wxLua's own `genwxbind.lua`.** Rejected: it emits C++ binding tables, and running it needs
  a Lua interpreter plus its rules-file machinery inside our tooling. The `.i` files are the input
  to *both* generators; consuming them directly is strictly less machinery.
- **Parse `wxLua/docs/wxluaref.html`.** Rejected: 1.6 MB of hand-rolled HTML 4.01 documenting wxLua
  **2.8.12 / wxWidgets 2.8.12**, badly stale against the 3.x binding it ships beside.
- **Bundle the tree in the plugin jar** (via `PlatformLibraryProvider`, like the stdlib stubs).
  Considered seriously — it removes hosting, sha256 pinning and the network path entirely. Rejected
  by the 2026-08-07 product decision in favour of the catalog path, which keeps the jar small and
  keeps a wxWindows-Licence derivative out of an Apache-2.0 distribution.
- **Extend the catalog with a `bundled` source kind.** Rejected with the above; it would also touch
  `LuaDefinitionEntry`, the loader, the fetcher and the provider — four classes changed to ship one
  library, against a feature whose whole shape is "data only".
- **Three catalog entries, one per namespace.** Rejected: wxLua ships one binary with all three
  tables, ZeroBrane uses all three, and `wxstc.wxStyledTextCtrl` inherits from `wx`'s `wxControl`.
  Splitting buys three pins and a `requires` graph for no user-visible benefit. The roadmap names
  three *namespaces* — a fact about wxLua, not a specification of three rows.
- **Restrict scope to the 479 members ZeroBrane uses.** Rejected: a partial library converts "unknown
  global" into "this class has no such method", which is a worse failure. Generation makes full
  coverage nearly free.
- **Honour `#if` / `%wxchkver_*` guards.** Rejected — §3.2. The guards resolve against the user's
  wxWidgets build, which the plugin cannot observe.
- **Mirror the tarball on Gitea as a second URL.** Rejected on a contract, not a preference: Gitea's
  archive prefix differs from GitHub's and `LuaArchiveExtractor` strips exactly one `rootPrefix`
  (§4.2).

## 10. Open Questions

_None — every remaining item is a **measurement** with all branches already specified here, tracked as a de-risking task in [risks-and-gaps.md](risks-and-gaps.md): DR-02 picks between the two static-method encodings in §3.6, DR-06 picks between the two file layouts in §3.5.2, DR-04 sets the indexing budget, and DR-03 names the published repository. None leaves an implementer a decision to invent._
