---
id: "MAINT-52-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-52"
folders:
  - "[[features/maint/52-one-unknown-rule/requirements|requirements]]"
---

# MAINT-52: Risks & Gaps

## Executed De-risking

Every row ran on the libvirt builder with `--rerun --no-build-cache`, on a base commit plus a throwaway
prototype that switched between variants at run time. The base is `7ec1adb7` unless the row says
`79380bf0`; no file under `lang/psi/types/` differs between the two. Evidence is under
`~/.cache/claude-scratch/lunar/maint-52/` (`r1/` for `7ec1adb7`), with the prototype in
`r1/prototype.diff` and the probe test in `r1/Maint52ProbeTest.kt.probe`.

| Variant | What it changes from the base |
| :-- | :-- |
| V0 | nothing |
| V0G | V0, plus `demandTypeFor` adds no `Nil` to an `Undefined` type |
| V1 | `fromLuaType` maps `LuaPrimitiveType.UNKNOWN` to `Any`; the BUG-16, BUG-11 and BUG-22 conversions deleted; design §2.5; the `and`/`or` conversion kept |
| V3 | V1, plus `visitBinOpExpr` placeholders `Any`, `falsyPart` keeps `Any`, and the `and`/`or` conversion deleted |
| D | **the design**: §2.1–§2.5, with §2.5 in both `LuaTypeGraph` and `LuaUnionDiagnostics` |
| M1…M6 | D with design §6's mutation of the same name |

The probe cases are the `c`- and `e`-numbered entries in requirements' `Run` column, plus the ones
named below. Each prints the distinct `ERROR`/`HYPOTHESIS` messages with their line, and the named
locals' types. Probe command: `test --rerun --no-build-cache --tests '*Maint52ProbeTest*'`.

| ID | Question | Observed | Verdict |
| :-- | :-- | :-- | :-- |
| DR-01 | Where do the unknown sites disagree today? (V0, `r1/probes/V0.txt`) | `c06-field-unres \| errors=[]`; `c07-field-any \| errors=[ERROR@7:Missing required field 'a']`; `c08-field-unknown \| errors=[]`. The BUG-16, BUG-11 and BUG-22 shapes (c01–c05, c10, c14, c21) give `errors=[]`, because each conversion holds them. The controls report: `c19-ctl-field-required \| errors=[ERROR@7:Missing required field 'a']`, `c16 … ERROR@2:{ ... } is not assignable to union nil \| string \| { ... }`, `c18 … ERROR@1:number is not assignable to union nil \| string` | The field demand is the one place where an unknown spelled `Any` and one spelled `Undefined` behave differently. Everywhere else they agree only because of the call-site conversions, except `demandTypeFor` (DR-06) |
| DR-02 | Which rule removes the conversions without moving anything else? (`r1/probes/*.txt`, `r1/probes2/r2-*.txt`) | **D vs V0**: differs only on `c07-field-any \| errors=[]`, `c27` (DR-06), and the closest-match cases (DR-04). **M1**: `c01 … ERROR@2:number is not assignable to string`; `c02 … ERROR@2:{ ... } is not assignable to union nil \| { ... }`; `c04 … ERROR@1:{ ... } is not assignable to nil, ERROR@2:number is not assignable to nil`; `c05 … ERROR@6:{ ... } is not assignable to nil`; `c10 … ERROR@2:number is not assignable to nil, ERROR@2:{ ... } is not assignable to nil`; `c12 … types=[x=string, d=string, e=number, k=number, m={ ... }]` with `ERROR@7:string is not assignable to number`; `c14 … ERROR@6:number is not assignable to string, ERROR@8:number \| string is not assignable to number, ERROR@8:string is not assignable to number` (and the same two at HYPOTHESIS), `z=number \| string`; `c20 … q=string` with `ERROR@7:string is not assignable to number`; `c21 … ERROR@3:Missing required field 'buffer'`; `c27` as DR-06. With M1 as the default variant, `test --tests '*LuaMethodArgumentTypeTest*'` (`r1/D-M1-method-arg.log`): `13 tests completed, 2 failed`, `testOptionalParamOfUnresolvedTypeAcceptsATable` with `expected:<[]> but was:<[{ ... } is not assignable to nil@{ pattern = "x" }]>` and `testOptionalParamOfUnresolvedTypeDemandsNoNil` with `but was:<[HYPOTHESIS:{ ... } is not assignable to nil]>`. **M2**: `c07 … ERROR@7:Missing required field 'a'`. **M3**: `c18 … ERROR@1:number is not assignable to string, ERROR@2:nil value is not assignable to string`. **M4**: `c16 … errors=[]`, `c19 … errors=[]`. **Algebra** (`r1/probes/algebra.txt`): D `[String, Undefined] = Any`, `[Undefined] = Undefined`, `[] = Undefined`, `[Undefined, Nil] = Any`; M1 `[String, Undefined] = String`, `[Undefined, Nil] = Nil`. **On `79380bf0`**: V1 `c13 … types=[y=any, r=any, u=any]` (V0: `r=number, u=number`) and `c22-generic-method` gains `ERROR@8:number is not assignable to T, ERROR@9:string is not assignable to T`; V3 `c12 … e=any`, `c20 … q=string` with `ERROR@7:string is not assignable to number` | D chosen. Each mutation reddens the cases requirements assigns it. V1 and V3 rejected (Risk 1.1, DR-05). c22 is identical under V0 and D |
| DR-03 | D on the suite and the corpus | `test -PwithCorpus --rerun --no-build-cache` (`r1/A-full-corpus-D.log`): `3534 tests completed, 2 failed` (the count includes the probe tests in `Maint52ProbeTest`), namely `LuaTypeAlgebraTest > undefined is dropped unless it is the only member` and `LuaTypeGoldenMemberFilesTest`; no corpus test fails. Corpus recorded in CI's shape, `test --rerun --no-build-cache -PwithCorpus -PrecordCorpusBaseline --tests '*Corpus*'`, copied back under the lock: D (`r1/B-record-D.log`, `r1/corpus-D/`) differs from the committed baselines only in `lazynvim.baseline` (`r1/lazynvim-D.diff`): `inspection.LuaReturnTypeMismatch=16` → `=14` and `symbol.LuaReturnTypeMismatch.__index=2` deleted. **Control**, V0 recorded the same way (`r1/C-record-V0.log`, `r1/corpus-V0/`): no difference from the committed baselines. Goldens, `test --rerun --no-build-cache -PrecordTypeGolden --tests '*TypeGolden*' --tests '*MemberEnumerationGoldenTest*'` (`r1/E-golden-D.log`, `r1/golden-D/`): only `member-files/zerobrane-commands.golden` differs, losing lines 12–19, all `HYPOTHESIS\|nil \| { ... } is not assignable to { ... }` rows at `(30780,30782)` … `(31033,31041)`. **On `79380bf0`**, per-hit dump, V0 vs a variant of D with §2.2's per-member conversion deleted outright and §2.5 in `LuaTypeGraph` only: the two lazynvim hits that go are `lua/lazy/manage/semver.lua:80` and `:88`, both `LuaReturnTypeMismatch Missing required field '__index'`; with M2 the hits equal V0's and the golden test passes | Every corpus and golden movement comes from design §2.5, and each removes a finding |
| DR-04 | Does sharing the predicate with `LuaUnionDiagnostics` change its message? (`r1/probes2/r2-*.txt`) | Each fixture declares its two classes with a `local` between them, so no field crosses classes. `e1-closest-any` (TC-18): D `{ ... } is not assignable to E1A \| E1B; closest match 'E1A': incompatible fields`; M6 and V0 `… closest match 'E1A': missing field 'a'`. `e4-closest-tie` (`---@class E4A` with `x: number`, `a: any`, `b: string`; `---@class E4B` with `x: number`, `c: string`; `f({ x = "s" })`): D `closest match 'E4A': missing field 'b'`; M6 and V0 `closest match 'E4B': missing field 'c'`. Control `e2-closest-string` (`a: string`): `closest match 'E2A': missing field 'a'` under V0, D and M6 | Established: the shared predicate changes the closest-match wording, both which member is named and which fields are listed. Whether an error is reported does not change, because `closestMatch` runs only after `checkCompatibility` has found no compatible member (`LuaTypeGraph.kt:596-629`) |
| DR-05 | V1 on the suite and the corpus (`run3-summary.txt`, `run3-v1-full-corpus.log`, on `79380bf0`) | `test -PwithCorpus --rerun --no-build-cache`: `3525 tests completed, 4 failed`, namely `LuaTypeGoldenMemberFilesTest`, `LuaTypeGoldenShapesTest`, `LuaCorpusSweepTest > testLazynvimCorpus` and `MemberEnumerationGoldenTest`; `Corpus regression: inspection.LuaTypeAssignability: baseline 78 → observed 80` | Re-spelling a producer moves the corpus; V1 rejected (Risk 1.1) |
| DR-06 | What does `---@param o? unknown` on an in-file function demand? (`c27`, `r1/probes/`) | V0: `errors=[ERROR@2:number is not assignable to nil, ERROR@2:{ ... } is not assignable to nil, ERROR@7:number is not assignable to nil, …]` and the same at HYPOTHESIS. V0G: `errors=[]`. D: `errors=[]`. M1: as V0 | `LuaTypeGraphBridge.demandTypeFor` is the site: today it demands `nil` and reports at ERROR; under D it demands `Any`. TC-15 |
| DR-07 | What does a declared union whose members all fail to resolve become? (`r1/probes/`) | `c24-all-unres-type`: V0 and D `types=[w2=any, z2=number \| any]`; M5 `types=[w2=undefined, z2=number]`. `c25-all-unres-return` (in-file `---@return Unres1\|Unres2`): V0 and D `r=any`; M5 `r=number`. `c26-xfile-all-unres-return` (the same in `lib.lua`): V0 and D `r=any`; M5 `r=number`. `c23-all-unres-param` (`---@param o Unres1\|Unres2`, `f(1)`, `f({})`): `errors=[]` under every variant | Without §2.2's narrowed conversion the union becomes `Undefined`, so a local typed by it is dropped from its reaching definitions and a return shows the demand made of it. With it, every case equals V0. TC-16, TC-17 |

### Re-record rows (TC-13)

Both rows were recorded on `7ec1adb7` + D (DR-03).

| File | Change |
| :-- | :-- |
| `src/test/resources/corpus/lazynvim.baseline` | `inspection.LuaReturnTypeMismatch=16` → `=14`; delete `symbol.LuaReturnTypeMismatch.__index=2` |
| `src/test/resources/types-golden/member-files/zerobrane-commands.golden` | delete lines 12–19, the rows DR-03 names |

If the re-record shows any other difference, stop and report it. Do not accept it.

## Critical Risks

### Risk 1.1: Undefined has more than one meaning outside the union
- **Impact**: `LuaPrimitiveType.UNKNOWN` covers an unresolved name, a written `unknown`, a declared
  return that is absent (`funcTypeFromStub`), and the round trip of `Undefined` through
  `graphTypeToLuaType`. In the visitor, a node's `Undefined` write likewise covers both "nothing
  assigned" and "cannot model". Re-spelling a producer changes every consumer: V1 moved the corpus
  (DR-05) and changed generic diagnostics, and V3 turned `a:b():c() or "s"` into a certain `string`
  (DR-02).
- **Likelihood**: certain if producers are touched. This feature does not touch them. Splitting the
  meanings is [[BUG-27]].
- **Mitigation**: the rule acts only where a union is formed, where DR-03 measured no movement.

### Risk 1.2: an absent `Undefined` now widens a union
- **Impact**: a member that is `Undefined` because nothing is known (`truthyPart(nil)`, for example)
  makes the union `Any`, so the engine reports less. `local x = nil or "s"` is `any` under V0 and D
  alike (c12), because the deleted `and`/`or` conversion already did this.
- **Likelihood**: low. DR-03 found no case on the suite or the corpus.
- **Mitigation**: the direction is under-reporting, the posture BUG-419 chose. It cannot create a false
  positive.

## Design Gaps

### Gap 2.1: the reaching-definition merge does not absorb into `Any`
`resolveWrite` builds a `LuaGraphType.Union` directly, not through `canonicalize`, so an `Any` arm
does not absorb scalars there: `---@type Unres|string` / `local z = 1` displays `number | any` under
V0 and D (c14). The checker is unaffected, because it tests each reaching definition alone. This is
display-only and out of scope: [[BUG-25]].

### Gap 2.2: an unknown right operand shows its demand as its type
`local e = cond and wx.thing` is `Undefined` (design §2, `rightType` row), and `LuaTypes.typeOf`
then reports what is demanded of `e`: `e=number` in c12 under V0, D and M1 (V3 gives `e=any`, because
it re-spells the producer). This is the display path of [[BUG-553]], unchanged here: [[BUG-26]].

## Technical Debt & Future Work
- [[BUG-27]]: split the meanings of `LuaPrimitiveType.UNKNOWN` (Risk 1.1). A prerequisite for spelling
  "cannot model" as `Any` at the producers.

## Test Case Gaps
- Corpus symbol keys outside lazynvim are not gated (`CorpusMetrics.compare` gates inspection
  totals). DR-03's D recording covers every member's file, symbol keys included, and only lazynvim
  differs.
- c03 (a *local* function with `---@param o? Unres`) is silent under M1 too, so it cannot test the
  rule and is not a TC.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
