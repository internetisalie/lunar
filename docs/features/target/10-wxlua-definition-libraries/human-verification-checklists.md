---
id: TARGET-10-CHECKLIST
parent_id: TARGET-10
type: qa
folders:
  - "[[features/target/10-wxlua-definition-libraries/requirements|requirements]]"
title: "Verification Checklists"
---

# Verification Checklists: TARGET-10 — wxLua Definition Libraries

> These run against a **real IDE with the real published tarball** — not `registerLibraryRoot`
> fixtures. Risk 1.2 exists because a green fixture suite has hidden a dead feature before (the
> schema-engine EP-registration bug). Everything below therefore starts from an unmodified sandbox
> with the library **not** yet enabled.

## 1. Fetch, enable, and register

### Scenario 1.1: First enable fetches and registers the root

- **Setup**: fresh sandbox IDE; a Lua project; `wxlua` not in the enable list; network available.
- **Steps**:
  1. Open *Settings → Lua Project → Definition Libraries*.
  2. Confirm a **wxLua (wx / wxstc / wxaui)** row is listed with its licence and attribution link.
  3. Tick it and click **OK**.
  4. Watch the background progress indicator to completion.
- **Expected**: a background task named for the fetch runs and finishes without an error balloon;
  a directory `<system>/lunar/definitions/wxlua-<sha>/` (`LuaDefinitionLibraryFetcher.kt:47`) exists
  and contains the emitted tree; the Project view's *External Libraries* shows the wxLua definition
  root. Note the `sha256` is verified **ADVISORY** — a mismatch only logs — so also check
  `idea.log` for a verification-mismatch warning; its absence is part of this scenario passing.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Reopening the project uses the cache

- **Setup**: Scenario 1.1 passed; close the project.
- **Steps**:
  1. Disconnect the network.
  2. Reopen the project.
- **Expected**: the wxLua root is registered immediately; no fetch is attempted; no error balloon.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: Enabling offline fails closed

- **Setup**: a *different* fresh sandbox with an empty definitions cache; network disconnected.
- **Steps**:
  1. Tick **wxLua** and click **OK**.
- **Expected**: an ERROR balloon on the `notification.group.lunar.tools` group; no wxLua root
  appears; the IDE stays responsive and indexing is not broken. Reconnecting and re-applying
  succeeds.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Resolution and completion

### Scenario 2.1: Constants complete across all three namespaces

- **Setup**: Scenario 1.1 passed; a new file `probe.lua`.
- **Steps**:
  1. Type `wx.wxID_` and invoke completion.
  2. Type `wxstc.wxSTC_STYLE_` and invoke completion.
  3. Type `wxaui.wxAUI_NB_` and invoke completion.
- **Expected**: (1) offers `wxID_ANY` among others; (2) offers `wxSTC_STYLE_DEFAULT`;
  (3) offers `wxAUI_NB_DEFAULT_STYLE`. Ctrl+B on each lands in the corresponding library file.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: A constructor infers its class

- **Setup**: as above.
- **Steps**:
  1. Type `local frame = wx.wxFrame(wx.NULL, wx.wxID_ANY, "Test")`.
  2. On the next line type `frame:` and invoke completion.
- **Expected**: completion offers `Show`, `Close`, `SetTitle` — i.e. members of `wxFrame` **and** of
  its bases (`wxTopLevelWindow`, `wxWindow`). Quick documentation on `Show` renders its `@param` and
  `@return`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Cross-namespace inheritance resolves

- **Setup**: as above.
- **Steps**:
  1. Type `local editor = wxstc.wxStyledTextCtrl(wx.NULL, wx.wxID_ANY)`.
  2. On the next line type `editor:` and invoke completion.
- **Expected**: offers both `wxstc`-declared members (e.g. `SetLexer`, `StyleSetFont`) and members
  inherited from `wx`'s `wxControl` / `wxWindow` (e.g. `Enable`, `SetFocus`). This is the case that
  proves flat type names work across files and namespaces.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.4: `require("wx")` resolves

- **Setup**: as above.
- **Steps**:
  1. Type `local wx = require("wx")`.
  2. Ctrl+B (Go to Declaration) on the `"wx"` literal.
- **Expected**: navigation lands in `library/wx.lua` inside the definition root. No
  *unresolved module* warning on the line.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.5: Global objects and the curated supplement resolve

- **Setup**: as above.
- **Steps**:
  1. Type `wx.wxDefaultPosition:` and invoke completion. *(Generated — `#define_object wxPoint
     wxDefaultPosition`, `wxcore_gdi.i:19`.)*
  2. Type `wxstc.wxSTC_SCMOD_CTRL` on its own line. *(Generated — behind a `!%wxchkver_3_1_1`
     negated guard, `wxstc_stc.i:2449`; this is the one that catches a guard-strip regex missing
     its leading `!`.)*
  3. Type `wxaui.wxAUI_TB_PLAIN_BACKGROUND` on its own line. *(Curated — genuinely absent from
     every `.i` file; supplied by `supplement.lua`.)*
- **Expected**: (1) offers `wxPoint` members such as `GetX`; (2) and (3) produce no *undefined*
  warning. Step 3 is the only one exercising `supplement.lua`; steps 1–2 prove the parser handles
  the two forms an earlier draft wrongly believed were absent from the source.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.6: Static methods (records the DR-02 branch)

- **Setup**: as above.
- **Steps**:
  1. Type `wx.wxFileName.` and invoke completion.
- **Expected**: **Branch A** — offers `GetCwd` and other statics. **Branch B** — offers nothing, and
  that is the documented accepted limitation. Record which branch was implemented and whether the
  observed behaviour matches it.
- **Branch implemented**: ⬜ A (dotted) / ⬜ B (on-class)
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Real-world file

### Scenario 3.1: A ZeroBrane source file

- **Setup**: Scenario 1.1 passed; open the pinned ZeroBrane checkout
  (`test/corpus/zerobrane`, commit `483ef0dc`) as a project with `wxlua` enabled and the language
  level set to Lua 5.1.
- **Steps**:
  1. Open `src/editor/editor.lua` (or another `wx`-heavy file) and let analysis settle.
  2. Compare the `LuaUndeclaredVariable` warning count in that file against the same file with
     `wxlua` disabled.
- **Expected**: a substantial drop, concentrated on `wx.*`, `wxstc.*` and `wxaui.*` identifiers.
  Remaining warnings are ZeroBrane's own globals (`ide`, `ide.config`, `mobdebug`), which this
  feature does not address. Note the before/after numbers — MAINT-37 needs them.
- **Before / after**: ______ / ______
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: No false incompatibilities

- **Setup**: as Scenario 3.1.
- **Steps**:
  1. Scan the file for `LuaTypeAssignability` / `LuaReturnTypeMismatch` errors introduced by
     enabling the library.
- **Expected**: **zero** new type errors. Any that appear are `map_type` mapping a C++ type too
  narrowly (design §3.4) — record the C++ type and the expression; the fix is to widen the mapping
  toward `any`, never to narrow the code.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Disable and hygiene

### Scenario 4.1: Disabling removes the roots

- **Setup**: Scenario 1.1 passed.
- **Steps**:
  1. Untick **wxLua** in *Settings → Lua Project → Definition Libraries*; click **OK**.
  2. Type `wx.wxID_` in `probe.lua` and invoke completion.
- **Expected**: the root disappears from *External Libraries*; completion offers nothing for `wx.`;
  the cache directory is **left on disk** (re-enabling is instant and offline). Re-tick and confirm
  it comes back without a fetch.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Attribution ships

- **Setup**: a built plugin distribution zip.
- **Steps**:
  1. Unzip and open `THIRD-PARTY.md` at the plugin root.
  2. Open the attribution link shown on the wxLua row in the settings page.
- **Expected**: `THIRD-PARTY.md` names `lunar-definitions-wxlua`, its upstream `pkulchenko/wxlua`,
  the wxWindows Library Licence v3, and states the tree is fetched at runtime and never bundled. No
  `.lua` file derived from wxLua is present anywhere inside the zip. The attribution link opens the
  published repository, whose `LICENCE` is the verbatim wxWindows licence and whose `PROVENANCE.md`
  names the wxLua commit.
- **Result**: ⬜ Pass / ⬜ Fail
