---
id: "MAINT-51-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-51"
folders:
  - "[[features/maint/51-one-declared-signature/requirements|requirements]]"
---

# MAINT-51: Risks & Gaps

## Executed De-risking

Every row ran on the libvirt builder with `--rerun --no-build-cache`, on `dccb8b18` plus [[MAINT-52]]'s
design §2 (the code, not its KDoc), plus a throwaway prototype that switched between variants at run
time. Evidence is under `~/.cache/claude-scratch/lunar/maint-51/`: the MAINT-52 layer in
`base/base-maint52.diff`, the round-1 and round-2 prototype in `probe/prototype.diff`, and the
final-shape prototype in `probe/round3/round3-full-m7.diff`. The probe test is
`probe/round2/Maint51ProbeTest.kt.probe` for rounds 1-2 and `probe/round3/Maint51ProbeTest.kt.round3`
for round 3. Each probe case prints the sorted, distinct `SEVERITY:message@element-text` of
`LuaTypesSnapshot.getErrors()`, which is what `LuaDeclaredSignatureTest`'s `errors` helper returns.

| Variant | What it changes from the MAINT-52 layer |
| :-- | :-- |
| B | nothing |
| U | `parameterDemand` and `informativeArms`; design §2.5; each declaration-typed argument wired into a fresh `VariableNode` that carries the use |
| T | U, plus a `Table` demand copied member by member onto fresh nodes instead of skipped |
| N1, N2, N3 | U without `informativeArms` in `isCompatible` (N1); without §2.5 step 3 (N2); with the "must not ENABLE" guard asking `isCompatible(valueType, …)` (N3) |
| Z | **the design**: §2.1–§2.5, the argument wired straight into `parameterUse`'s result |
| M1, M2, M6, M7, N4, N5 | Z with design §6's mutation of the same name; N1 and N2 are also run on Z in round 3 |

| ID | Question | Observed | Verdict |
| :-- | :-- | :-- | :-- |
| DR-01 | Does the probe measure on MAINT-52's code? | `base/apply_maint52.py` applies MAINT-52 design §2.1–§2.5 to `LuaTypeAlgebra`, `LuaGraphType`, `LuaTypesVisitor`, `LuaTypeGraph` and `LuaUnionDiagnostics`; `base/base-maint52.diff` is the result. Every later row has it applied | Every expected value in `requirements.md` is relative to MAINT-52 |
| DR-02 | What does unifying the `?` rule change? (round 1, `probe/round1/out/*.txt`) | U equals B on every case except two wordings. `C10` (`k(nil)` into an in-file `string\|number`): B `[ERROR:nil is not assignable to union number \| string@local function k(v) , ERROR:nil is not assignable to union number \| string@v]`; U `[ERROR:nil value is not assignable to number \| string@v]`. `C12x` (cross-file `---@generic T` / `---@param x? T`, `gid("a")`): B `[ERROR:string is not assignable to union T \| nil@"a"]`; U `[ERROR:string is not assignable to T@"a"]`. M2, the argument wired into the seed's node: `C5` `[HYPOTHESIS:number is not assignable to fun()@1]`, `C5c` `[]`, `C13a` `[HYPOTHESIS:nil value is not assignable to number@nil]` | The `?` rule moves no result. `C12x` is an ERROR in both, and wrong in both: [[TYPE-14]] case 3 |
| DR-03 | Do the two `nil` rules disagree on live code? (round 2, `probe/round2/out/*.txt`) | B: `R1` (`(string\|nil)[]` into `string[]\|number`) three `ERROR:nil \| string[] is not assignable to union number \| string[]` entries; `R1x` one; `R2` (`{a: string\|nil}` into `{a: string}\|number`) three `closest match '{ ... }': incompatible fields` entries; `R3` (`local v = nil` / `if os.time() then v = 1 end` into `string\|number`) `[ERROR:nil is not assignable to union number \| string@local function k(v) , …@v]`; `R3x` one. Controls against a single type (`R1c`, `R2c`, `R3c`, `R3xc`): `[]`. U: all of `R1`, `R1x`, `R2`, `R3`, `R3x` give `[]`; `R1b` (`(string\|boolean)[]`) still gives three `boolean \| string[]` errors. N1: `R1`, `R2` as B, `R3` `[]`. N2: `R1`, `R2` `[]`, `R3` as B. N3: as U | Two live false positives, each from one rule, each fixed by one change and only by it. N3 rejected (design §8) |
| DR-04 | B and U on the full suite (`probe/S-suite-{B,U}.log`) | Both `3526 tests completed, 2 failed`: `LuaTypeGoldenMemberFilesTest` and `LuaTypeAlgebraTest > undefined is dropped unless it is the only member`, both MAINT-52's own re-records. U's golden failure adds two diffs to B's (`probe/golden-fail-{B,U}.txt`): `luarocks-zip.golden` line 9, `(10022,10024)` from `{ ... }` to `File \| nil`, and `penlight-Date.golden` line 15, a `HYPOTHESIS\|{ ... } \| nil \| { ... } is not assignable to { ... }` row gone | Attributed in DR-07 |
| DR-05 | B and U on the corpus in CI's shape, `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'` (`probe/K-corpus-{B,U}.log`, per-site dumps `probe/out/sites-{B,U}.txt`) | B: exit 0 (the control). U: `testLuarocksCorpus` `inspection.LuaTypeAssignability: baseline 9 → observed 10`; `testZerobraneCorpus` `baseline 7 → observed 8`. The two new sites: `src/luarocks/fs/unix/tools.lua:250` `number \| string is not assignable to nil \| number` (`os.date("*t", time)`) and zerobrane `src/editor/style.lua:519` `function \| nil \| boolean is not assignable to function \| number` (`setfenv(cfgfn,cfg)`) | **Refutes** the fresh `VariableNode` per argument: it merges the argument's reaching definitions into one write and checks their union. Z wires the argument straight into the use; M7 restores the hop (DR-07) |
| DR-06 | Can table demands be copied as cheaply as function demands? T on the corpus (`probe/K-corpus-T.log`, `probe/out/sites-T.txt`) | Time per corpus test, B / U / T (s): luacheck 59.3 / 54.7 / 58.1; luarocks 74.1 / 77.7 / 80.8; penlight 23.4 / 23.6 / 24.4; lazynvim 39.1 / 38.8 / 38.7; zerobrane 64.5 / 65.0 / 64.6. Nodes the copier minted, over the sweep: U 32 759, T 46 020. Findings: lazynvim `LuaTypeAssignability: baseline 78 → observed 128`; luarocks `LuaReturnTypeMismatch: baseline 0 → observed 1`; zerobrane `LuaReturnTypeMismatch: baseline 0 → observed 2` and `LuaTypeAssignability: baseline 7 → observed 9`. Most new lazynvim sites read `X[] is not assignable to { ... }`, an array against a `table` demand ([[BUG-481]]). Probe: `C11a` `useW(function() end)` `[ERROR:fun() is not assignable to W@function() end]`, `C11b` `useW({})` `[ERROR:Missing required field 'name'@{}]` | As cheap in time; not as cheap in correctness. Deferred (MAINT-51-07, design §8) |
| DR-07 | Z, the final shape (round 3, `probe/round3/`) | Probe (`A-probe-out/Z.txt`): every TC-01 to TC-16 value in `requirements.md`. Controls `R1c`, `R2c`, `R3c`, `R3xc`: `[]`. Mutations (`A-probe-out/*.txt`): every value in `requirements.md`'s mutation column. With M1 as the default variant, `LuaMethodArgumentTypeTest` and `LuaOptionalParamExplicitNilTest` (`A-M1.log`): `38 tests completed, 6 failed`. With M6 (`A-M6.log`): `38 tests completed, 2 failed`, TC-19. With Z, `LuaCrossFileFunctionDemandTest`, `LuaMethodArgumentTypeTest`, `LuaOptionalParamExplicitNilTest`, `LuaDeclaredContractErrorTest`, `ExpectedCallbackResolverTest` and the probe (`A-probe.log`): `BUILD SUCCESSFUL`. Full suite (`S-suite-Z.log`): `3544 tests completed, 2 failed`, the same two as DR-04, and `LuaDeclaredSignatureTest` 16 of 16 pass. Corpus in CI's shape (`K-corpus-Z.log`): `BUILD SUCCESSFUL`, and the per-site dump (`K-corpus-Z-out/sites-Z.txt`, 115 sites) equals B's; time per corpus test, B / Z (s): luacheck 59.3 / 60.0, luarocks 74.1 / 72.1, penlight 23.4 / 23.4, lazynvim 39.1 / 37.4, zerobrane 64.5 / 62.9. Corpus recorded in that shape and copied back under the lock (`C-record-corpus-Z.log`, `corpus-Z/`): differs from the committed baselines only in MAINT-52's own `lazynvim.baseline` row. Goldens recorded (`C-record-golden-Z.log`, `golden-Z/`, `golden-Z.diff`): `zerobrane-commands.golden` loses MAINT-52's own rows, and two files change for this feature, listed under Re-record rows. With N1 as the default variant (`G-golden-N1.log`), `LuaTypeGoldenMemberFilesTest` fails on `zerobrane-commands.golden` alone; with N2 and N4 it fails on all three. With M7 (`K-corpus-M7.log`, `K-corpus-M7-out/sites-M7.txt`): `testLuarocksCorpus` `inspection.LuaTypeAssignability: baseline 9 → observed 10`, `testZerobraneCorpus` `baseline 7 → observed 8`, at the two sites DR-05 names and no other. | The design reproduces every DR-03 fix and every TC. The corpus does not move. Both golden changes come from `informativeArms` in `isCompatible` (§2.5 step 1). M7 is DR-05's refutation, isolated |

### Re-record rows

Both rows were recorded on Z (DR-07), with MAINT-52's own rows set aside.

| File | Change |
| :-- | :-- |
| `src/test/resources/types-golden/member-files/penlight-Date.golden` | delete lines 15–19, the five `HYPOTHESIS\|{ ... } \| nil \| { ... } is not assignable to { ... }` rows at `(15299,15301)`, `(17998,18000)`, `(18030,18032)`, `(18069,18071)` and `(18100,18102)` |
| `src/test/resources/types-golden/member-files/luarocks-zip.golden` | 157 rows change in place, each keeping its range and PSI class: 112 from `{ ... }` to `File \| nil`; 20 from `undefined` to `string`; 9 from `fun(p, p)` to `fun(filename, mode?)`; and 16 keep their type and change only the hash (6 `{ ... }`, 3 each `fun(zh, cd_entries)`, `fun(zh)` and `fun(zh, cdr)`, 1 `fun(zipfile)`). No row is added or deleted |

## Critical Risks

### Risk 1.1: the luarocks-zip display change is measured, not explained
- **Impact**: the 157 rows above move from a guessed shape (`{ ... }`, `fun(p, p)`, `undefined`) to the
  declared standard-library types (`io.open`'s `fun(filename, mode?)` and its `File | nil` result).
  DR-07 attributes the change to `isCompatible` forgiving a `nil` arm, but nobody has traced which
  edge it enables.
- **Likelihood**: the change itself is certain; a hidden false positive behind it is low, because the
  corpus per-site dump does not move (DR-07).
- **Mitigation**: the re-record must equal the rows above exactly.

### Risk 1.2: the base moved after the measurements
- **Impact**: every DR row was measured on `dccb8b18` plus MAINT-52's design. MAINT-52's branch commit
  (`f47381d0`) differs from that layer only in comments and expression form, but [[MAINT-53]]
  (`5d6f7e67`, member resolution by receiver type) also landed after `dccb8b18` and was never
  measured with this change.
- **Likelihood**: low. MAINT-53 changes reference resolution, not the demand or `nil` rules, and
  design §2 finds each site by symbol, not line.
- **Mitigation**: task 7's re-record must equal the rows above, and the full suite runs on the rebased
  branch with no exemptions. Any other difference stops the work.

### Risk 1.3: a certain `nil` changes wording and anchor
- **Impact**: `k(nil)` into an in-file `string|number` now reports once, at the parameter, as
  `nil value is not assignable to number | string`, instead of twice as `nil is not assignable to
  union number | string` (DR-02, TC-12). Anything matching the old text breaks.
- **Likelihood**: certain, and intended: it is the single-type wording.
- **Mitigation**: DR-07's full suite found no test matching it.

## Design Gaps

None. The open question in this feature's original stub requirements, whether table demands can be copied as cheaply as function
demands, is DR-06.

## Technical Debt & Future Work
- **Table demands on the per-call path** (MAINT-51-07). Copying them is cheap, and the findings it adds
  are not triaged (DR-06). [[BUG-481]] (an array against a `table` demand) accounts for most of
  them, and [[MAINT-54]] measures what the checker silently skips. Revisit once both land.
- **The cross-file generic `?` parameter** reports `string is not assignable to T` (DR-02, `C12x`).
  [[TYPE-14]] case 3.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
