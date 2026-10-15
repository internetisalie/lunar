---
id: "BUG-501"
title: "Type-inspection results on annotated code vary between identical runs — lazy.nvim's diagnostics come and go from one JVM to the next"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-501: the same annotated code gets different type diagnostics on different runs

Found by [[MAINT-39]]'s prototype (risks-and-gaps DR-07). It blocks MAINT-39, because a corpus gate
on a count that moves between identical runs either flakes or gets disabled.

## Reproduction

Sweep `lazy.nvim` at tag `v11.17.5` (commit `85c7ff37`) as a corpus member, repeatedly, on the same
tree and builder (libvirt). The member-alone and full-shape sweep runs are described in MAINT-39's
DR-04, DR-07 and DR-10.

## Expected

Identical inspection counts on every run.

## Actual

Thirteen identical runs on the libvirt builder:
- `inspection.LuaTypeAssignability` read 447 six times, 445 three times, 443 twice, and 439 and 442
  once each.
- Three of the runs also read `inspection.LuaReturnTypeMismatch=54`, against `56` in the rest.
- Every other baseline line was identical across runs.

A per-hit dump from runs 4–6 isolates one varying pair. Runs 5 and 6 add, over run 4:

```
lua/lazy/view/render.lua LuaTypeAssignability @15167-15241
lua/lazy/view/render.lua @15171-15180 Too many arguments: expected at most 2, got 3
```

on `self:markdown(msg.msg, hls[msg.level] or "LazyTaskOutput", { indent = 6 })`. `M:markdown(str, hl,
opts)` (`view/render.lua:482`) takes three parameters and is annotated
`---@param opts? {indent?: number, …}`.

This is user-visible as well as a corpus problem: the same file can show an error in one IDE
session and not in the next.

## Reduced reproduction

Three files in one light fixture (`BasePlatformTestCase`, `myFixture.addFileToProject`):

```lua
-- text.lua
---@class Text
local Text = {}
function Text:nl() return self end
return Text

-- util.lua   (stands in for lazy/util.lua:218)
---@class LazyUtil
local M = {}
---@param msg string|string[]
---@param opts? table
function M.markdown(msg, opts) end
return M

-- render.lua (stands in for lazy/view/render.lua)
---@class LazyRender:Text
local M = {}
---@param str string
---@param hl? string
---@param opts? {indent?: number, prefix?: string, wrap?: boolean}
function M:markdown(str, hl, opts) end
function M:tasks()
  self:markdown("a", "b", { indent = 6 })
end
return M
```

Then `LuaTypeManager.resolveType("LazyRender", render).resolveMember("markdown")` and
`LuaTypesSnapshot.forFile(render).getErrors()`. Within one JVM the answer is fixed; across JVMs it is
not. Eleven separate builder runs (separate test JVMs) of the same fixture:

| Outcome | JVMs | `LazyRender.markdown` | `LazyUtil.markdown` | `self:markdown` arity pair |
| :-- | :-- | :-- | :-- | :-- |
| util's entry first | 8 | **missing** | `(msg, opts)` from util.lua | absent (5 errors) |
| render's entry first | 3 | `(str, hl)` from render.lua | **missing** | present (7 errors) |

The two-parameter signature is **render.lua's own** `markdown`, not util.lua's: the third tag is
dropped by a separate parse defect (below). The order dependence decides whether that signature is
attached to `LazyRender` at all. Both classes are wrong in one of the two outcomes.

A second variant, a sibling file holding `M.markdown = 1` (an index `FIELD`, not a function), drops
`LazyRender.markdown` the same way: 2 of 4 JVMs.

## Root cause

**`LuaReceiverMemberIndex.membersIn` keeps one entry per member name across all files, and which file
supplies it depends on a per-JVM hash order. `LuaTypeManagerImpl.addMethodsOf` then trusts that one
entry's kind and separator.**

1. A `---@class` on `local M = {}` makes `collectMethodMembers`
   (`lang/psi/types/LuaTypeManagerImpl.kt:513`) scan receiver `M`, confined to the declaring file
   (BUG-398). Almost every lazy.nvim module names its table `M`.
2. `addMethodsOf` (`:547`) asks `LuaReceiverMemberIndex.membersIn("M", …)`
   (`lang/indexing/LuaReceiverMemberIndex.kt:589`), which unions every file's members with
   `seen.putIfAbsent(it.name, it)` (`:600`). That is first-wins by name, so util.lua's
   `markdown/FUNCTION/DOT` and render.lua's `markdown/FUNCTION/COLON` collapse to whichever is visited
   first.
3. `addMethodsOf` skips the entry unless its `kind` is `FUNCTION` (`:556`). `declaredMethod` (`:571`)
   builds the stub-index key from the entry's separator (`:576`), so it looks up `M.markdown`, and
   then confines the result to render.lua (`:585`). There is no `M.markdown` in render.lua, so
   `LazyRender` gets no `markdown`. A `FIELD` entry from another file is skipped at `:556` the same
   way.
4. **Why the visit order differs between JVMs.** `processValues` iterates the key's
   `ValueContainerImpl`, whose value-to-files mapping is a `ValueToInputMap`, an
   `Object2ObjectOpenHashMap` keyed by the indexed value (intellij-community
   `platform/util/src/com/intellij/util/indexing/impl/ValueContainerImpl.java:670`). Each file's value is
   a `List<LuaReceiverMember>`, and `LuaReceiverMember` is a data class
   (`LuaReceiverMemberIndex.kt:93`) whose `kind` and `separator` are enums. `Enum.hashCode()` is the
   identity hash, which is different in every JVM, so the list hashes and the bucket order change
   from one run to the next. Measured: each run printed different enum hashes and list hashes
   (for example `FUNCTION=264252208 DOT=1947602163` and `FUNCTION=72710772 DOT=982383976`), and the
   raw `processValues` order flipped between `util.lua, render.lua` and `render.lua, util.lua` with
   them. The file-creation order in the fixture made no difference: `utilFirst` and `renderFirst`
   always agreed within a JVM.

**Scope in lazy.nvim.** A static scan of the checkout (`collisions.py`, below) finds **37**
class-hosted method members whose name also appears under receiver `M` in another file with a
different kind or separator, `markdown` among them. Each can be dropped or kept per JVM. That is
consistent with the spread of 439 to 447. It is not proven to account for every varying hit. The
`LuaReturnTypeMismatch` 54/56 wobble is plausibly the same, since a dropped method loses its declared
`@return`, but it is unmeasured.

### A separate defect that this exposes, not caused by the order dependence

`---@param opts? {indent?: number, …}` does not parse as a LuaCATS `PARAM_TAG`. It falls back to an
LDoc `LDOC_PARAM_TAG` with a `DESCRIPTION` body. No error element is produced, which is why DR-05
found no parse error on `render.lua:482`. `LuaCatsDeclarations.paramTypes` reads only `paramTagList`,
so the parameter disappears and `markdown` is declared with two parameters. Because a declared
signature is a contract (BUG-419), the third argument is reported as a `WARNING`. Measured variants:

| Tag | Parsed as LuaCATS `@param` |
| :-- | :-- |
| `opts? {indent?: number, prefix?: string}` | no |
| `opts {indent?: number}` | no |
| `opts? {indent: number, prefix: string}` | yes |
| `opts? {indent: number}` | yes |
| `opts? table` | yes |

The trigger is an optional key `name?:` inside a table-literal type (DR-05's first class). The `?` on
the parameter name is not involved. **Once the order dependence is fixed, the false
"Too many arguments" on `self:markdown(…)` is reported on every run instead of on some.** It needs its
own bug. That is outside BUG-501's scope, and a new bug ID should be assigned when the bug is filed.

### Relationship to BUG-498

Same family, different mechanism. Neither fix covers the other.
- BUG-498 merges `---@class` declarations from `StubIndex.getElements(LuaClassNameIndex…)`, last-wins.
  A stub index's per-file value is a `StubIdList`, whose `hashCode` is computed from its contents
  (intellij-community `platform/indexing-impl/src/com/intellij/psi/stubs/StubIdList.java:38`). It is
  not an identity hash, so BUG-501's order source cannot be BUG-498's. What does vary in BUG-498's
  order was not probed here and remains open there.
- BUG-501 is a file-based index whose values hash by identity, followed by a first-wins dedupe by
  name in `membersIn`.
- The shared lesson is that index iteration order must not choose a winner. BUG-501's fix removes
  the choice rather than sorting it, so it does not carry over to BUG-498's merge.
- One BUG-498-shaped dependence remains in this code and is left alone here. For a project-wide scan
  (`MethodScan.onlyIn == null`, a class-name receiver), `declaredMethod` takes `firstOrNull` over the
  stub-index elements when two files both declare `function Class.m`. The reproduction does not reach
  it.

### DR-08 (zerobrane `LuaSuspiciousConcatenation` 21 → 19)

**Not established.** `LuaSuspiciousConcatenation` reads `LuaTypesSnapshot`, so this mechanism can
reach it. Zerobrane has almost no `---@class`, though: two tags, both in `lualibs/lexers/`. The only
way in is a class-name receiver: a stdlib or definition-library class such as `string` or a wxlua
class, scanned project-wide. Two things count against this mechanism: the identity hashes vary in
**every** JVM whether or not lazy.nvim is present, and the two control records without the member
did not move. Two controls cannot exclude a rate of about 1 in 4, so the full-shape repeat below
settles it.

### Probes (all temporary; nothing is left in the tree)

Probe code is preserved in `~/.cache/claude-scratch/lunar/bug-501/` (`Bug501ProbeTest.kt`,
`multi-jvm.sh`, `collisions.py`). The worktree was at `39bc885d`.

| # | What | Command | Output |
| :-- | :-- | :-- | :-- |
| P1 | Reduced fixture, three orders, one JVM | `builder-run libvirt test --rerun --tests '*Bug501ProbeTest*'` | `probe1.log`; render's entry first in all three tests |
| P2 | Same, six fresh JVMs, plus raw `processValues` order and enum hashes | `bash multi-jvm.sh 6 p2` | `p2-run{1..6}/*.txt`: util's entry first in runs 1–4, render's in 5–6 |
| P3 | Prototype fix (below) applied temporarily, five JVMs, plus the `FIELD` variant and the `@param` PSI dump | `bash multi-jvm.sh 5 fix` | `fix-run{1..5}/*.txt`: `LazyRender.markdown` from render.lua and `LazyUtil.markdown` from util.lua in **every** run, including runs 3–5, where the index put the `DOT`/`FIELD` entry first |
| P4 | Prototype restored (`cp` of the `.orig` snapshot), four JVMs | `bash multi-jvm.sh 4 base` | `base-run{1..4}/*.txt`: `utilFirst` loses `LazyRender.markdown` in 4/4; `fieldCollision` loses it in 2/4 |
| P5 | Static collision scan of lazy.nvim | `python3 -I collisions.py <lazy.nvim>/lua` | `collisions.out`: `classHostedFiles=18 atRiskMemberHosts=37` |

## Fix strategy

A scan confined to a declaring local's file takes its candidate members **from that file's own index
entry**, never from the cross-file union. The change is in `LuaTypeManagerImpl` only. `membersIn`
keeps its contract.

1. `addMethodsOf` asks `membersIn(scan.receiver, project, candidateScope(scan, scope))`. The new
   `candidateScope` is `GlobalSearchScope.fileScope(scan.onlyIn)` for a confined scan, and the
   existing `allScope` otherwise. From one file, `membersIn`'s first-wins keeps that file's **first
   declaration** of each name, so the entry's separator is this file's own. `declaredMethod` is
   unchanged and still builds the key from that separator.
2. `addMethodsOf` no longer reads `member.kind`. The entry only nominates a name, and `declaredMethod`
   admits a member only when a real `LuaFuncDecl` stub stands behind it (the KDoc's stated filter).

**Constraint honoured: COMP-09-07's declared dot/colon rule.** When one file declares both `function
R.m` and `function R:m`, the member is the **first declaration in file order**. The supervisor ruling
of 2026-08-12 in COMP-09's risks-and-gaps forbids re-litigating that rule, and
`MemberEnumerationMaterializationTest.testDotColonCollisionResolvesToTheFirstDeclarationInFileOrder`
pins it. Taking the separator from the declaring file's own first entry is that rule, so the test
passes unmodified.

**Cost.** A confined scan now reads one file's entry instead of every file's, so it does less index
work. Class-name scans (`onlyIn == null`) are unchanged, and they are what COMP-09-09's work-bound
tests measure (`MemberEnumerationWorkBoundGateTest`, `MemberEnumerationMaterializationTest`). A
`FIELD` entry now costs one stub-index lookup where it cost none.

**Not fixed here:**
- the `@param name {k?: T}` parse fallback, filed separately;
- the project-wide `firstOrNull` noted under BUG-498;
- for a class-name receiver, a dot/colon collision **split across two files**, which is still
  first-wins in `processValues` order. COMP-09's ruling explicitly leaves this case out ("a collision
  split across two files is not pinned by this ruling").

### Decision record: the first strategy was replaced

The strategy first written here probed both keys, `:` then `.`, and ignored the entry's separator.
Applied exactly as written on the libvirt builder:
- The new regression test was green in 3 of 3 runs.
- The full suite (3175 tests) had **one failure**: `MemberEnumerationMaterializationTest`
  `:272`, `expected:<[fun(dotArg: string): string]> but was:<[fun(colonArg: number): number]>`. The
  determinism assertion at `:264` still passed. Only the declared tie-break moved: colon-first had
  overturned COMP-09-07's file-order rule.

The implementer stopped with ABORT_REPLAN. The supervisor rejected:
- **entry-separator-first** probing, because it stays order-dependent when the declaring file has
  both forms and another `M` module collides;
- **colon-first**, because it overturns a ruling.

The supervisor chose the declaring file's own entry (above). The fallback was a positional tie-break
when both forms are in one file. It was not needed, because the work-bound tests pass.

## Test strategy

The regression test must fail **before** the fix in nearly every JVM, not in half of them. With a
single sibling file it would fail only about half the time, because one hash bit decides the order.
So the test uses many siblings:

- **Test** (light fixture, in `src/test/kotlin/net/internetisalie/lunar/type/`): a host file
  `---@class Host` / `local M = {}` with sixteen methods `function M:m1(a, b, c)` … `m16`, each with
  three plain `---@param` tags (`string`/`table`, avoiding the `{k?:}` defect). Add sixteen sibling
  files. Sibling *i* declares only `m`*i*, alternating between `function M.m`*i*`(x) end` (`DOT`) and
  `M.m`*i*` = 1` (`FIELD`). Assert that every `resolveType("Host").resolveMember("m"+i)` is a
  `LuaFunctionType` with three parameters whose `sourceElement` is in the host file. Before the fix the
  test passes only when the host's list is visited before all sixteen siblings' lists, about 1 in 17
  JVMs.
- **Mutation check** (`mutation-proof` skill): run the test against the unfixed code in at least three
  separate builder runs and require red in each.
- **Control that must pass before and after:** the host file alone, with no siblings. All sixteen
  methods resolve with three parameters. If this fails, the fixture is wrong, not the order.
- **Second control (no leakage):** a `---@class Sib` on one sibling's `local M`. `Sib` must resolve only
  its own `m`*i*, with one parameter, and none of the host's methods. Confinement to the declaring
  file (BUG-398) has to survive dropping the separator.
  **As implemented (accepted deviation):** the `Sib` file declares a unique `function M.own(x)`, not a
  colliding `m`*i*. A colliding name would make the control itself order-dependent before the fix,
  and the controls must pass before and after. It asserts that `Sib.own` has one parameter and comes
  from `sib.lua`, and that none of Host's sixteen methods resolve on `Sib`.

## Done means

BUG-501 owns all of these checks. None depends on MAINT-39 or on a `corpus.json` entry on `main`.

1. The test and both controls above pass in the full suite (`test --rerun`), and the mutation check
   is red on unfixed code.
2. **Member-alone repeat, N = 8.** Run the lazy.nvim member-alone sweep in eight separate builder runs
   (eight test JVMs). Use MAINT-39's probe harness: probe root `/home/builder/maint39`, staged by
   `~/.cache/claude-scratch/lunar/maint-39/plan/stage-builder.sh`, with the throwaway
   `testProbeLazyAlone` method from `probe/LuaCorpusSweepTest.probeA.kt`, applied and restored per the
   `temporary-edits` skill. Every rendered metrics block (`[probe] render-begin … render-end`) must
   be byte-identical. **Why 8:** before the fix, 13 runs gave 447×6, 445×3, 443×2, 442×1, 439×1. The
   chance that N runs all agree is Σ pᵢᴺ = (6/13)⁸ + (3/13)⁸ + … ≈ 0.0021 for N = 8. A residual that
   flips a single hit with p = ½ would show all-equal with chance 2·(½)⁸ ≈ 0.008. Either way eight
   runs catch it more than 99% of the time.
3. **Full-shape-equivalent repeat, N = 8, with zerobrane checked.** Apply the probe-B
   `testLazynvimCorpus` method temporarily (template:
   `~/.cache/claude-scratch/lunar/maint-39/plan/withlazy-records.sh`, with the probe root's own
   manifest). Run `test --rerun --no-build-cache -PwithCorpus -PrecordCorpusBaseline --tests '*Corpus*'`
   eight times. After each run, compare every re-recorded baseline's SHA-256 against the committed
   file (`collect-full.sh`) and restore it. lazy.nvim must be identical across all eight runs, and
   every other member must equal its committed baseline. For DR-08 at its observed rate of 1 in 4,
   eight clean runs leave a 0.75⁸ ≈ 10% chance of having missed it. Sixteen runs (0.75¹⁶ ≈ 1%, about
   1h45m of builder time) settle DR-08 at the same confidence as the lazy.nvim check.

## Resolution

Fixed in `LuaTypeManagerImpl` (`addMethodsOf` and the new `candidateScope`) as the Fix Strategy
describes. The regression test and both controls are in
`src/test/kotlin/net/internetisalie/lunar/type/LuaReceiverMemberCollisionTest.kt`.

Everything below was measured on the libvirt builder, on parent `3ebb3d96` plus the fix commit's
diff, before that diff was committed. The logs are under `~/.cache/claude-scratch/lunar/bug-501/fix/`.

**Regression test, red before and green after.** Each row is a separate builder run, so a separate
test JVM. The controls passed in every run.

| Run | Code | `testClassHostedMethodsSurviveSameNamedSiblingMembers` |
| :-- | :-- | :-- |
| red-run1 | unfixed (`3ebb3d96`) | FAIL, dropped `[m1, m2, m6, m8, m9, m10, m12, m13, m15, m16]` |
| red-run2 | unfixed | FAIL, dropped `[m4, m5, m8, m10, m11, m14, m16]` |
| red-run3 | unfixed | FAIL, dropped `[m6]` |
| red-run4 | unfixed | FAIL, dropped `[m3, m5, m6, m15, m16]` |
| green2-run1..3 | fixed | pass, 3 of 3 |

The dropped set differs in every unfixed run, which is the per-JVM hash order showing through.

**Gates.**
- Full suite (`test --rerun --no-build-cache`): 3175 tests, 0 failures, 0 errors, 4 skipped. Both
  COMP-09 tests passed unmodified: `MemberEnumerationWorkBoundGateTest` (3/3) and
  `MemberEnumerationMaterializationTest` (7/7, including the file-order tie-break).
- Corpus gate (`test --rerun --no-build-cache --tests '*Corpus*' -PwithCorpus`): green, with no
  IMPROVED or REGRESSED line. `git status --porcelain -- src/test/resources/corpus/` is empty.
- `ktlintCheck`: green.

**Done means 2 (member-alone, N = 8).** MAINT-39 probe A `testProbeLazyAlone` ran in 8 separate
builder runs. All 8 rendered blocks are byte-identical: sha256 `a0987fdc80f2c281…`, 89 lines,
`inspection.LuaTypeAssignability=447`, `inspection.LuaReturnTypeMismatch=56`.

**Done means 3 (full shape, N = 16).** MAINT-39 probe B `testLazynvimCorpus` ran under
`-PwithCorpus -PrecordCorpusBaseline --tests '*Corpus*'` in 16 separate builder runs.
- lazy.nvim's block was byte-identical in all 16, and identical to the member-alone block (sha256
  `a0987fdc80f2c281…`).
- In every run, all six re-recorded files equalled the committed ones by SHA-256: `luacheck`,
  `luarocks`, `penlight`, `zerobrane` and `torture-fuzzing-lua` baselines, and
  `torture-fuzzing-lua.expected-accepts`.
- **zerobrane did not move in any of the 16 runs** (`847df02e424f6229`). DR-08's 21 → 19 was not
  reproduced. At its observed rate of 1 in 4, sixteen clean runs leave about a 1% chance that it was
  missed. Its cause remains unestablished; it belongs to MAINT-39.
- The probe edits were restored with `cp` after each batch and checked against HEAD. Nothing about
  lazy.nvim entered `corpus.json` or `src/test/resources/corpus/`.

**What is not established.**
- Whether every hit that varied between 439 and 447 came from this mechanism. The count is now
  constant at 447, the maximum previously seen.
- The project-wide residuals named under the Fix Strategy remain:
  - `firstOrNull` across files;
  - a dot/colon collision split across two files for a class-name receiver.

The `{k?: T}` `@param` fallback is filed separately as [[BUG-502]]. It now reports its false
*Too many arguments* on every run.
