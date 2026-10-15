---
id: "BUG-504"
title: "One `@deprecated` dotted function makes every reference to its root table a deprecated-API warning"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-504: `---@deprecated function vim.tbl_islist()` flags every `vim`

Found by TARGET-11's prototype (DR-02, DR-10). Blocks TARGET-11. `$S` =
`~/.cache/claude-scratch/lunar/target-11`; every run on the libvirt builder with
`test --rerun --no-build-cache`.

## 1. Reproduction (executed)

Library root (`LibraryRootTestCase.registerLibraryRoot`), two files laid out like Neovim `779c3696`:

```lua
-- vim/_core/shared.lua
_G.vim = _G.vim or {}

function vim.list_extend(dst, src) end

--- @deprecated
function vim.tbl_islist(t) end
```

```lua
-- vim/lsp/log.lua
local log = {}

---@deprecated
function log.should_log(level) end

return log
```

Project files, `LuaDeprecatedApiInspection` enabled, each highlighted in turn:

```lua
-- lazy/minit.lua
local islist = vim.islist or vim.tbl_islist
vim.list_extend({}, {})
return islist
```

```lua
-- lazy/manage.lua (function M.log)       -- lazy/spec.lua
local M = {}                               local Spec = {}
function M.log(opts) end                   function Spec:log(msg, level) end
return M                                   return Spec
```

Probe `Target11R3Bug504FixtureProbeTest`, which registers exactly the files above
(`$S/r3/results/a-base/probe.txt`; source in that run's `tree.diff`). Every line it printed, verbatim:
each hit, then the declarations its reference multi-resolves to.

```
[probe:n1] lazy/minit.lua:1 @15-18 'vim' targets=[vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.list_extend deprecated=false, vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.tbl_islist deprecated=true]
[probe:n1] lazy/minit.lua:1 @29-32 'vim' targets=[vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.list_extend deprecated=false, vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.tbl_islist deprecated=true]
[probe:n1] lazy/minit.lua:1 @33-43 'tbl_islist' targets=[vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.tbl_islist deprecated=true]
[probe:n1] lazy/minit.lua:2 @44-47 'vim' targets=[vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.list_extend deprecated=false, vim/_core/shared.lua:LuaFuncDeclImpl decl=vim.tbl_islist deprecated=true]
[probe:n1] lazy/minit.lua hits=4
[probe:n1] lazy/manage.lua:2 @24-27 'log' targets=[vim/lsp/log.lua:LuaFuncDeclImpl decl=log.should_log deprecated=true]
[probe:n1] lazy/manage.lua hits=1
[probe:n1] lazy/spec.lua:2 @30-33 'log' targets=[vim/lsp/log.lua:LuaFuncDeclImpl decl=log.should_log deprecated=true]
[probe:n1] lazy/spec.lua hits=1
```

Control: the same shapes with every `@deprecated` removed report `hits=0` for every file
(`Target11R2DeprecatedProbeTest`, `$S/r2/results/r1-base/`).

On the real Neovim library pinned for lazy.nvim, the sweep's `inspection.LuaDeprecatedApi` goes from
absent to `540`. TARGET-11 DR-10 classifies every hit: this bug accounts for those on `vim` and the four
on lazy.nvim's own `log` declarations.

**On main after [[BUG-509]]** the `log` hits above no longer reproduce as written: `local log = {}`
keeps `log` out of the global-declaration index, so nothing resolves to `log.should_log` at all. The
`log` shape still reproduces when the root is a genuine global (`log = {}`), which is what the
regression test uses (§5).

## 2. Expected vs actual

- **Expected:** a reference is deprecated only when it names the deprecated declaration's member:
  `vim.tbl_islist` is, `vim` and `vim.list_extend` are not, and a declaration's own name never is.
- **Actual:** every reference that multi-resolves to a deprecated dotted function's declaration is
  reported, whichever segment of the function name it is.

## 3. Root cause (executed)

A root name multi-resolves to the **`LuaFuncDecl`** of every `function <root>.<name>` declaration
(the `LuaFuncDeclImpl` targets above). `LuaDeprecatedApiInspection.getDeprecatedTag`
returned the declaration's tag as soon as `resolved is LuaCatsCommentOwner`, without asking which
segment of the function name the reference is. The identifier branch below it had the
same flaw: it compared against `funcName.nameRef`, the root segment, not the declared member, so a
call of a deprecated method `t:m()` never warned (§5, mutation (a)).

The `log` hits add a second gap: `isDeclaration` does not treat a `LuaNameRef` under
`LuaFuncNameProperty` or `LuaFuncNameMethod` as a declaration, so `log` in `function M.log` is
resolved, lands on `function log.should_log`, and inherits its tag. A name check cannot close this
gap. Against a library `---@deprecated function legacy()`, the segment `legacy` in a user's
`function M.legacy()` has the declared name and resolves to that bare function, and it warned
(`but was:<[(24, 30), (37, 43)]>`, run `30-r3-red`). A function-name segment is a declaration, so the
fix skips it.

Fixing only the identifier branch's comparison exposes the second half of its flaw (found in review,
executed): **resolving to the declared member's leaf does not mean naming it.** Two bare references
reach that leaf:

- **Implicit `self`.** `LuaScopeProcessor` resolves `self` inside `function t:m()` to the method's
  name leaf `m`, by design. Comparing against the declared member made every `self` in a deprecated
  method warn: `function t:m() return self end` gave `but was:<[(55, 59), (66, 67)]>` (run `10-b1-red`).
- **A bare name through a required file.** `LuaFileBindingsIndex.funcDeclNameLeaves` records the
  method leaf of `function M:close()` as a file binding, so a bare, undeclared `close` in a file that
  `require`s the module resolves to it, and warned (`but was: <[(27, 32)]>`, same run).

Both are the same mistake: a dotted or colon function is reachable only as a member (`t.m`, `t:m`),
so no bare reference can name it, whatever leaf it resolves to. A check on the reference's own text
would catch `self` but not `close` (its text is the declared name), so the guard is on the
reference's **shape**. That `close` resolves to a method leaf at all is a resolution defect of its
own, BUG-512. This fix does not change it.

The same shape rule applies where the root multi-resolves to the declaration itself (the `vim`
path), and only it is correct there. A name check alone lets through a root named like its own
deprecated member. Library `log = {}` / `---@deprecated function log.log()` flagged every bare `log`
(`but was:<[(10, 13), (14, 17), (18, 21), (48, 51)]>`, run `30-r3-red`).

**Why a shape rule alone does not hold.** Resolution itself returns declarations the reference does
not name. One case is a member access with no qualified key: `t[1].x` or `(e).x`, where
`LuaNameReference.getQualifiedName` returns null. It falls back to the bare-name index and lands on a
dotted function's root-occurrence key. `local handlers = {{}}` / `local l = handlers[1].log`, against
the §1 library, warned at `44..47` (`but was:<[(44, 47)]>`, run `50-r4-red` on the round-3 commit).
That reference is a member access, so the shape rule lets it through. Every rule that tries to work
out which declaration a reference "really" meant from the resolved PSI leaks wherever resolution is
wrong.

**Containment is not identity.** The leaf path took the tag from whatever deprecated declaration
*encloses* the resolved leaf (`PsiTreeUtil.getParentOfType(resolved, LuaCatsCommentOwner)`). In the
input below, the read `config.timeout` correctly resolves (through `LuaMemberFieldNavigation`) to
the field written inside the deprecated `M.timeout`'s body:

```lua
local M = {}
local config = {}
---@deprecated gone
function M.timeout(ms) config.timeout = ms end
function M.get() return config.timeout end
```

That field leaf is inside `M.timeout`, so the read inherited its tag: `but was:<[(129, 136), (143,
150)]>`, and `(17, 24)` from another file (run `72-r5-red2` on the round-4 commit). Main gave no
warning, because it compared against the receiver `M`. The same containment test let parameters
through on the other owners:
- a parameter named like a deprecated `local function` / `local`: `local function timeout(timeout)
  return timeout end` gave `(59, 66)`, and the `local` form gave `(61, 68)`. These are the old
  text fallback.
- any parameter of a deprecated 5.5 `global` variable, which had no guard at all: `global timeout =
  function(ms) return ms end` gave `(57, 59)`.

## 4. Fix strategy (executed)

**The invariant.** A deprecated function's tag applies to a reference only when the reference's own
name equals the function's declared name: the segment `LuaDeclarationSite.functionNameLeafOf`
returns (method, else last property, else bare name; the one existing home of that rule).

The inspection checks this locally, from the reference text and the declaration PSI. It does not
depend on why resolution returned that declaration, so it holds wherever resolution is wrong. It
rejects:
- the root `vim` and `log` (≠ `tbl_islist`, `should_log`);
- `self` (≠ `m`);
- a parameter (≠ the function's name);
- `handlers[1].log` (≠ `should_log`).

The one case equal names cannot tell apart is a **bare** name against a **dotted or colon** function
of that name: the root `log` against `function log.log()`, or a bare `close` bound through a required
file to `M:close`. A dotted or colon function is reachable only as a member (`t.f`, `t:f`), so the
inspection also requires the reference to be a member access when the declaration is a member
function. That second check is about the reference's syntax, not about what it resolved to.

These two checks are **necessary**, not sufficient: they say whether the reference *could* name the
declaration, not whether the resolved element *is* it. The leaf path therefore also requires
**identity** (below).

**Identity on the leaf path.** When resolution returns a leaf, not the declaration itself, the tag
applies only when that leaf IS a name the owner declares, not merely an element inside it:
- for a `function`, the leaf `functionNameLeafOf` returns, plus the name and bare/member checks
  above (`self` resolves to its method's own name leaf, so identity alone would pass it);
- for a `local function` or 5.5 `global function`, its name identifier;
- for a `local` or 5.5 `global` variable, one of its `attName` identifiers (and, for `local`, the
  declaration itself, as before).

The 5.5 `global *` declares no name and keeps its previous behaviour.

**Where the root/stub-index path returns the declaration itself** (`vim`, `log`, `handlers[1].log`),
the name and bare/member checks decide alone, as before. There is no leaf to compare.

**What the inspection cannot decide locally.** It cannot catch a resolution that lands exactly on a
declaration's own name, with the right name and an allowed shape, when the reference is nevertheless
of the wrong kind. Run, and recorded in BUG-512:
- `handlers[1].legacy` → a bare global `function legacy()`, through the bare-name index;
- `config.timeout` → a `local function timeout` / `local timeout` / `global function timeout` /
  `global timeout` in scope. The member access resolves **lexically** to the bare variable's own
  name leaf (`Bug504F1ProbeTest`, run `71-f1-probe`). It warned on the round-4 commit (run
  `70-r5-red`). Main's identity-or-text branches would pass it too; that is reasoned, not run on main.

In `LuaDeprecatedApiInspection`:

- `isDeclaration` also skips a `LuaNameRef` under `LuaFuncNameProperty` or `LuaFuncNameMethod`, so a
  function-name segment is never inspected.
- `getDeprecatedTag(resolved: PsiElement, reference: LuaNameRef)`; the caller passes the `LuaNameRef`.
- Root path: a resolved `LuaFuncDecl` is rejected when `isNotTheDeclaredFunction(owner, reference)`
  holds. That is true when the name differs, or when `isBareReferenceToAMemberFunction` holds: the
  reference's parent is not `LuaIndexExpr` / `LuaMethodExpr` and the declaration is dotted or colon.
  That rule assumes `_G` / `_ENV` are not modelled (`function _G.f()` is a bare `f` in Lua);
  resolution does not alias `_G` today, and the KDoc says so.
- Leaf path: `isTheDeclaredName(owner, resolved, reference)`, which is the identity rule above and
  replaces every containment branch.

The leaf path's earlier comparisons were identity-or-**text**. Text is redundant with the name
check, because every leaf-producing source is keyed by name, so the resolved text is the reference
text:
- lexical scope;
- the colon branch;
- required-file bindings;
- `LuaMemberFieldNavigation` (the last segment of the qualified name);
- `LuaGlobalAssignmentNavigation` (file-scope only).

The one exception is `self`. Text equality was exactly what let a contained leaf with the
declaration's name through, so identity is the stricter and correct test.

### 4.1 NAV-13 edits this fix makes

A call of a deprecated method `t:m()` warns at the call site. NAV-13 case 22 (a) pinned the opposite,
a limitation of the old guard, which compared against the receiver `t`. This fix makes these edits,
and no others, to NAV-13 files:

1. **`LuaColonCallDeprecationTest`, case (a)**: renamed
   `aDeprecatedMethodRaisesNoWarningWhenTheReceiverNameDiffers` to
   `aDeprecatedMethodWarnsAtTheCallSiteWhenTheReceiverNameDiffers`; expected `listOf(54 to 55)`; KDoc
   names its two mutations (colon branch; compare against the receiver again).
2. **Same file, case (b)**: KDoc only. (b) is now the control for (a)'s second mutation: receiver and
   member are both `m`, so the two comparisons give the same answer.
3. **NAV-13 `requirements.md` case 22**: expected result and mutation cells as above. The acceptance
   checklist line on case 22's mutation is updated to agree with that row.
4. **NAV-13 `risks-and-gaps.md`**: the "TBD (not this feature's)" entry on the `LuaFuncDecl` guard is
   closed with BUG-504 as owner.
5. **NAV-13 `design.md` and `implementation-plan.md`**: the line anchors into the inspection
   (`:81-86`) now name `isNotTheDeclaredFunction`. The "Direction 2" prose now says the guard is
   fixed. The dated measurement tables are left as recorded. The checked Phase step on case 22's
   mutation now says what was run at delivery, and that `88-mut5-c` no longer reddens (a).

## 5. Test strategy (executed)

`net.internetisalie.lunar.definitions.LuaDeprecatedRootNameTest : LibraryRootTestCase`, the §1 library
tree registered except that `vim/lsp/log.lua` is `log = {}` / `---@deprecated` /
`function log.should_log(level) end` — a genuine global, so the case still reaches the defect with
[[BUG-509]] on main — and `LuaDeprecatedApiInspection` enabled:

| Case | File highlighted | Expected `Deprecated API` ranges | Mutation that turns it red |
| :-- | :-- | :-- | :-- |
| `testOnlyTheDeprecatedMemberIsFlagged` | `lazy/minit.lua` | exactly `33..43` (`tbl_islist`) | (b) → `15..18`, `29..32`, `44..47` reappear; (a) → `[]` |
| `testADeclarationNamedLikeADeprecatedRootIsNotFlagged` | `lazy/manage.lua`, `lazy/spec.lua` | none in either | none alone: protected by `isDeclaration` (segments skipped) and by the name check (`log` ≠ `should_log`) |
| `testAMemberAccessWithNoQualifiedKeyIsNotFlaggedByARootOccurrence` | `lazy/handlers.lua` = `local handlers = {{}}` / `local l = handlers[1].log` / `return l` | none | (n) or (b) or (a) → `44..47` |
| `testAFieldWrittenInsideADeprecatedFunctionIsNotDeprecated` | `local M = {}` / `local config = {}` / `---@deprecated gone` / `function M.timeout(ms) config.timeout = ms end` / `function M.get() return config.timeout end` / `M.timeout(5)` / `return M` | exactly `143..150` (the call) | (i) or (c) → `129..136` (the read) reappears; (a) → `[]` |
| `testAFieldWrittenInsideADeprecatedFunctionIsNotDeprecatedFromAnotherFile` | `field_decl.lua` = `M = {}` / `local config = {}` / `---@deprecated gone` / `function M.timeout(ms) config.timeout = ms end`; `field_user.lua` = `local v = config.timeout` / `M.timeout(5)` / `return v` | exactly `27..34` (the call) | (i) or (c) → `17..24`; (b) → `25..26` (the root `M`); (a) → `[]` |
| `testAParameterNamedLikeADeprecatedLocalFunctionIsNotFlagged` | `---@deprecated gone` / `local function timeout(timeout) return timeout end` / `timeout(5)` | exactly `71..78` (the call) | (iL) → `59..66` |
| `testAParameterNamedLikeADeprecatedLocalVariableIsNotFlagged` | `---@deprecated gone` / `local timeout = function(timeout) return timeout end` / `timeout(5)` | exactly `73..80` | (iV) → `61..68` |
| `testAParameterOfADeprecatedGlobalVariableIsNotFlagged` | `---@deprecated gone` / `global timeout = function(ms) return ms end` / `timeout(5)` | exactly `64..71` | (iG) → `57..59` |
| `testARootNamedLikeItsDeprecatedMemberFlagsOnlyTheMember` | own library `log = {}` / `---@deprecated gone` / `function log.log() end`; `user/use_log.lua` = `local x = log` / `log.log()` / `local M = {}` / `function M.log() end` / `return x` | exactly `18..21` (the member in `log.log()`) | (s) or (b) → `10..13`, `14..17` (bare `log`) reappear |
| `testABareCallOfADeprecatedGlobalFunctionWarnsInTheSameFile` | `global_same.lua` = `---@deprecated gone` / `function f() end` / `f()` | exactly `37..38` | (f) → `[]` |
| `testABareCallOfADeprecatedGlobalFunctionWarnsFromAnotherFile` | `legacy_lib.lua` = `---@deprecated gone` / `function legacy() end`; `global_other.lua` = `local M = {}` / `function M.legacy() end` / `legacy()` / `return M` | exactly `37..43` (the call) | (f) → `[]`; (g) → `24..30` (the segment) reappears |
| `testAParameterOfADeprecatedFunctionIsNotFlagged` | `param.lua` = `local t = {}` / `---@deprecated gone` / `function t.f(x) return x end` | none | (c) → `56..57`. No single-check mutation: protected by identity (`x` is not `f`'s leaf), the name check (`x` ≠ `f`) and the shape rule (bare `x`, dotted `t.f`) |
| `testSelfInsideADeprecatedMethodIsNotFlagged` | `self.lua` = `local t = {}` / `---@deprecated gone` / `function t:m() return self end` / `t:m()` | exactly `66..67` (the call site) | (c) → `55..59` (`self`) appears; (a) or colon → `[]`. For `self`, no single-check mutation: protected by the name check (`self` ≠ `m`) and the shape rule (bare `self`, colon `t:m`); identity passes it by design |
| `testSelfMemberAccessInsideADeprecatedMethodIsNotFlagged` | `function t:m() local a = self.x; self:m() return a end` / `t:m()` | no range at either `self` (`58`, `66`); the call site `90..91` warns | (c) → `58..62`, `66..70`; (a) or colon → call site gone. The same two checks protect each `self` |
| `testAParameterOfADeprecatedBareFunctionIsNotFlagged` | `bare_param.lua` = `---@deprecated gone` / `function f(x) return x end` | none | (c) → `41..42`. No single-check mutation: protected by identity and the name check (`x` ≠ `f`) |

Plus `net.internetisalie.lunar.analysis.inspections.LuaDeprecatedBareNameThroughRequireTest`, a heavy,
real-disk fixture, because `require` resolution reads `LocalFileSystem` only and a light fixture's
`temp://` module is never found. Without the real-disk fixture the case passes without reaching the
defect: a light-fixture version was green on the unfixed commit, and a probe showed `close`
resolving to nothing. `mod.lua` = `local M = {}` / `---@deprecated gone` /
`function M:close() end` / `return M`; `main.lua` = `local mod = require("mod")` / `close()`;
`sourcePath` = the temp dir. It asserts the reach precondition first (bare `close` multi-resolves
into `mod.lua`), then no `Deprecated API` range. Mutation (s) or (c) → `27..32`.

The member comparison (against `functionNameLeafOf`, not the receiver) is also pinned by NAV-13 case
22 (a), edited per §4.1: mutation (a).

Mutations, each on the final fix, restored from a snapshot afterwards:

- (n) delete the name-equality line in `isNotTheDeclaredFunction`;
- (s) make `isNotTheDeclaredFunction` ignore the shape rule (`return false` after the name check);
- (a) compare the name against `owner.funcName.nameRef` (the receiver) instead of `functionNameLeafOf`;
- (b) delete the `resolved is LuaFuncDecl` line (root/stub-index path) in `getDeprecatedTag`;
- (c) make the `LuaFuncDecl` arm of `isTheDeclaredName` always true (no leaf-path check at all);
- (i) drop identity from the `LuaFuncDecl` arm (name and shape only);
- (iL) / (iV) / (iG) make the `LuaLocalFuncDecl` / `LuaLocalVarDecl` / `LuaGlobalVarDecl` arm always
  true (containment, as before);
- (f) drop the `isMemberFunction` half of the shape rule (any bare reference to any function rejected);
- (g) delete the function-name-segment line from `isDeclaration`;
- colon: delete the colon branch from `LuaNameReference.multiResolve`.

The `LuaGlobalFuncDecl` arm has no test of its own. A parameter of a 5.5 `global function` resolves
to nothing (`Bug504F1ProbeTest`: `globalfunc ms@83 targets=[]`), so there is no leaf inside it to
reach. The arm is there for consistency and is unpinned.

Executed on the libvirt builder, each a separate run of the four deprecation classes
(`LuaDeprecatedRootNameTest`, `LuaDeprecatedBareNameThroughRequireTest`, `LuaColonCallDeprecationTest`,
`LuaDeprecatedApiInspectionTest`; 24 tests in the final round). Logs and JUnit XML are under
`~/.cache/claude-scratch/lunar/bug-504/{logs,xml}/<run>`. The rows before `73-r5-green` are the red
runs of earlier rounds. Every mutation row is the final round, run on the final code by
`run-mutants.sh` (summary `mutants-r5-summary.txt`).

| Run | Source | Result |
| :-- | :-- | :-- |
| `01-red` | base inspection, first-round tests | 3 of 6 fail: case 22 (a) `expected:<[(54, 55)]> but was:<[]>`; minit `but was:<[(15, 18), (29, 32), (33, 43), (44, 47)]>`; manage `but was:<[(24, 27)]>`. The dotted-parameter case and 22 (b) pass, as expected of controls |
| `15-red2-on-commit` | first-round fix (`d830b3a7`) | self `but was:<[(55, 59), (66, 67)]>`; require `but was: <[(27, 32)]>` (precondition passed) |
| `30-r3-red` | second-round fix (`16e5d916`), all 18 tests | 2 fail: root-named-like-member `but was:<[(10, 13), (14, 17), (18, 21), (48, 51)]>`; cross-file global `but was:<[(24, 30), (37, 43)]>`. Same-file global, self-access green |
| `50-r4-red` | third-round fix (`c59c1f4e`), all 19 tests | 1 fails: unqualified member `handlers[1].log` `expected:<[]> but was:<[(44, 47)]>` |
| `72-r5-red2` | fourth-round fix (`8df454b6`), all 24 tests | 5 fail: F1 same-file `but was:<[(129, 136), (143, 150)]>`; F1 cross-file `[(17, 24), (27, 34)]`; local-function parameter `[(59, 66), (71, 78)]`; local-variable parameter `[(61, 68), (73, 80)]`; global-variable parameter `[(57, 59), (64, 71)]` |
| `73-r5-green` | final fix | 24/24 pass |
| `84-mut5-n` | (n) | unqualified member `(44, 47)` |
| `85-mut5-s` | (s) | root-named-like-member `but was:<[(10, 13), (14, 17), (18, 21)]>`; require `(27, 32)` |
| `86-mut5-a` | (a) | 7 fail: 22 (a) `but was:<[]>`; F1 same-file `[]`; F1 cross-file `[]`; unqualified member `(44, 47)`; self `[]`; self-access "call site 'm' at 90 warns, in []"; minit `[]` |
| `87-mut5-b` | (b) | root-named-like-member `[(10, 13), (14, 17), (18, 21)]`; unqualified member `(44, 47)`; F1 cross-file `[(25, 26), (27, 34)]`; minit `[(15, 18), (29, 32), (33, 43), (44, 47)]` |
| `88-mut5-c` | (c) | 7 fail: dotted param `(56, 57)`; F1 same-file `[(91, 93), (129, 136), (143, 150)]`; self-access `(58, 62), (66, 70)`; self `(55, 59), (66, 67)`; F1 cross-file `[(17, 24), (27, 34)]`; bare param `(41, 42)`; require `(27, 32)` |
| `80-mut5-i` | (i) | F1 same-file `[(129, 136), (143, 150)]`; F1 cross-file `[(17, 24), (27, 34)]` |
| `81-mut5-iL` | (iL) | local-function parameter `[(59, 66), (71, 78)]` |
| `82-mut5-iV` | (iV) | local-variable parameter `[(61, 68), (73, 80)]` |
| `83-mut5-iG` | (iG) | global-variable parameter `[(57, 59), (64, 71)]` |
| `89-mut5-f` | (f) | same-file global `expected:<[(37, 38)]> but was:<[]>`; cross-file global `expected:<[(37, 43)]> but was:<[]>` |
| `90-mut5-g` | (g) | cross-file global `but was:<[(24, 30), (37, 43)]>` |
| `91-mut5-colon` | colon | 22 (a) and 22 (b) `expected:<[(54, 55)]> but was:<[]>`; case 21 `but was:<[(95, 96), (99, 100)]>`; self `[]`; self-access call site gone |

## Resolution

Delivered as §4 and §4.1. In `LuaDeprecatedApiInspection`, a deprecated `function`'s tag applies,
on both the root path and the leaf path, only when both of these hold:
- the reference's own name equals the declared member's name;
- the reference is a member access whenever the declaration is dotted or colon.

On the leaf path, every comment owner also requires identity: the resolved leaf must BE a name the
owner declares, not an element inside it. Function-name segments are declarations. A plain global
`function f()` still warns at a bare call, in the same file and from another file.

Each check has a test that turns red when that check alone is removed: §5's mutations (n), (s), (a),
(b), (c), (i), (iL), (iV), (iG), (f), (g) and the colon-branch deletion, each run on the final code.
Some tests have no single-check mutation because more than one check protects them (one-line notes
in §5):
- the declaration case: `isDeclaration` + name;
- the dotted parameter: identity + name + shape;
- the bare parameter: identity + name;
- `self`: name + shape.

The `LuaGlobalFuncDecl` identity arm is unpinned, because no leaf inside a 5.5 `global function`
resolves. `isMemberAccess`'s parent list dropped `LuaFuncNameProperty` and `LuaFuncNameMethod`.
Those references are declarations now and never reach it, so either list behaves the same.

The full suite passes on the formatted tree with `test --rerun --no-build-cache` (run `92-full`):
3218 tests, 0 failures, 0 errors, 4 skipped.

The corpus ratchet (`--tests '*Corpus*' -PwithCorpus`, run on the first-round fix) passes with
every observed inspection tally equal to its recorded baseline, so nothing was re-recorded. That is no evidence either way: the
pinned corpus raises no `LuaDeprecatedApi` warning before or after the fix, so it cannot see this
change. The regression tests above are what pin it.

**User-visible change in the other direction:** a call of a `---@deprecated` colon method now warns at
the call site whatever the receiver is named; before, it warned only when receiver and method shared
a name. NAV-13 case 22 (a) records this.

## Out of scope

- A function-name segment after the head (`log` in `function M.log`) still **resolves** through the
  bare-name lookups. This inspection now skips it as a declaration, and other consumers are
  unchanged.
- BUG-512: a name resolves to a declaration of the wrong kind. The executed cases:
  - a bare `close` resolves to a required module's `M:close`;
  - `handlers[1].legacy` resolves to a bare global `function legacy()`;
  - `config.timeout` resolves lexically to a `local` / `global` `timeout` in scope.

  In the last two cases the resolved element is the declaration's own name leaf and the names match,
  so against a deprecated declaration this inspection still warns. That is a resolution defect, not
  something the inspection can decide locally (§4).
- `function _G.f()` / `_ENV.f`: reachable as a bare `f` in Lua, but resolution does not model `_G`,
  so a bare `f` gives no warning before or after this fix. The shape rule's KDoc records that it
  would need an exemption if `_G` aliasing is ever modelled.
- NAV-13 `design.md` and `risks-and-gaps.md` DR-05/DR-06 evidence tables still record
  `textEq=false` / "no warning" for `function t:m()`: they are measurements taken at the time, not
  current expectations, and are left as recorded.
