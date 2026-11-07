---
id: "MAINT-54-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-54"
folders:
  - "[[features/maint/54-type-false-negative-audit/requirements|requirements]]"
---

# Technical Design: MAINT-54 — Type Checking False-Negative Audit

## 1. Architecture Overview

New Kotlin goes in `src/test/kotlin/net/internetisalie/lunar/corpus/audit/`; `src/main` is untouched.
Measured behaviour is cited from risks-and-gaps DR-01 and DR-01b. **Prior art, reused unchanged:**
`CorpusManifest.entry`/`checkoutDir`, `CorpusTarget.pin`/`restore`, `CorpusGuards.assertCorpusFetched`,
`CorpusSourcePath`, `CorpusDefinitionLibraries.refreshRoots`, and the root copy of `CorpusSweep.kt:208`.
`CorpusSweep.run` is not called (it measures an unedited tree). Both type inspections report
`LuaTypesSnapshot.forFile(file).getErrors()` minus `HYPOTHESIS`, split by `ElementError.isReturnRelated()`
(`analysis/TypeErrorClassification.kt:23`); the audit reads the same list the same way. Shape tests such
as `LuaCrossFileFunctionDemandTest` stay.

## 2. Core Components

Kotlin runs on the test's EDT (`BasePlatformTestCase`): PSI reads are direct, edits go through
`WriteCommandAction.runWriteCommandAction(project)`; objects live inside one test method. No name may
contain `Corpus` (`SweepNamingGuardTest`); only the audit class matches `*FalseNegativeAudit*`.

| FQCN (all `net.internetisalie.lunar.corpus.audit.*`) | Key API |
|---|---|
| `AuditModel.kt` | `enum Kind { PRIMITIVE, TABLE, RECORD, CLASS, ARRAY, UNION, FUNCTION, GENERIC }`; `data class DeclaredKind(kind: Kind, text: String, optional: Boolean = false, bound: DeclaredKind? = null, arms: List<String> = emptyList())`; `enum CalleePath { SAME_FILE, PROJECT_FILE, CHECKOUT_FILE, LIBRARY }`; `enum CalleeForm { LOCAL, GLOBAL, MEMBER, METHOD, FIELD }`; `enum Anchor { AT_SITE, AT_PARAM, AT_DECL, BODY, DECLARING_FILE }`; `enum AuditOutcome { REPORTED, REPORTED_OTHER_RULE, SILENT, LENIENT_BY_DESIGN, NOISY, SKIPPED_PREEXISTING, FAILED }`; `data class AuditCell(shape: String, path: CalleePath, form: CalleeForm)`; `data class AuditSite(cell: AuditCell, file: String, edit: TextRange, wrong: String, control: String?, site: TextRange, paramRange: TextRange?, declRange: TextRange?, declFile: String?, declaredType: String)`; `data class AuditEntry(severity: String, rule: String, message: String, anchor: Anchor)`; `data class AuditRecord(cell, file, line: Int, declaredType, wrong, outcome, newErrors: List<AuditEntry>)`; `data class AuditCoverage(calls, stringOrTableArgs, unresolvedCallee, unresolvedMethod, formMismatch, noTag, arityGuard, eligible, fieldUntagged, ldocParam: Int)`; `data class AuditPopulation(cell: AuditCell, population: Int)`; `data class AuditPayload(schema: Int, member: String, commit: String, cap: Int, coverage: AuditCoverage, cells: List<AuditPopulation>, records: List<AuditRecord>)` |
| `AuditKinds` (object) | `fun classify(typeText: String, comment: LuaCatsComment): DeclaredKind?`; `fun wrong(kind: DeclaredKind): String?`; `fun control(kind: DeclaredKind): String?` (§3.1) |
| `AuditSiteFinder` (object) | `fun scan(file: LuaFile, checkoutDir: File): AuditScan`, `data class AuditScan(sites: List<AuditSite>, coverage: AuditCoverage)`; `internal fun fieldTag(member: LuaTypeMember, fieldName: String): LuaCatsFieldTag?` (§3.2) |
| `AuditSelection` (object) | `fun pick(n: Int, cap: Int): List<Int>`; `fun select(sites: List<AuditSite>): List<AuditSite>`; `const val CAP = 4` (§3.3) |
| `AuditInjector(fixture: CodeInsightTestFixture)` | `fun measure(site: AuditSite): AuditRecord`; `internal fun outcome(site: AuditSite, delta: List<AuditEntry>, noise: List<AuditEntry>): AuditOutcome`; `internal fun verifyReverted(document: Document, original: String)`, throws `RevertMismatch` (§3.4) |
| `AuditOutput` (object) | `fun render(payload: AuditPayload): String`; `fun write(repoRoot: File, member: String, payload: AuditPayload)` (§4) |
| `LuaTypeFalseNegativeAuditTest : BasePlatformTestCase` | `testLuacheckMember`, `testLuarocksMember`, `testZerobraneMember`, `testPenlightMember`, `testLazynvimMember` → `audit(name)` (§3.5) |
| `TypeAuditHarnessTest : IndexedBasePlatformTestCase` (routine suite) | TC-2 to TC-19 |
| `tooling/corpus/type_audit_summary.py` + `test_type_audit_summary.py` | `load(dir) -> list[dict]`, `aggregate(payloads) -> dict`, `verdict(cells) -> dict`, `render(aggregated: dict, result: dict) -> str` (`aggregate`'s and `verdict`'s outputs), `main(argv)` (§3.6) |

## 3. Algorithms

### 3.1 Classification, wrong value and control value
Input: tag type text `t` (trimmed `argType.text`) and the tag's `LuaCatsComment`. First match wins:
1. `t` is a name in `LuaCatsDeclarations.genericParameters(comment)` → GENERIC, `bound` =
   `classify(boundText)` or null when unbounded.
2. `t` ends with `?` → classify the rest, `optional = true`.
3. `t` has a `|` at bracket depth 0 (depth counts `(` `{` `<` `[` up, closers down) → split there into
   trimmed `arms`. If `"nil"` is an arm and exactly one other arm remains → classify that arm,
   `optional = true`. Otherwise UNION.
4. `t` starts with `fun(` or equals `function` → FUNCTION.
5. `t` ends with `[]` → ARRAY.
6. `t` starts with `{` → RECORD.
7. `t == "table"` or starts with `table<` → TABLE.
8. `t` ∈ {`string`, `number`, `integer`, `boolean`} → PRIMITIVE.
9. `LuaTypeManager.getInstance(comment.project).resolveType(t, comment)` is `LuaClassType` → CLASS.
10. Otherwise null (ineligible: `any`, `unknown`, `nil`, aliases, prose, unresolved names).

The finder also sets `optional` on a `@param` when `LuaCatsDeclarations.isOptionalParam(tag)`. The
primitive literals are: wrong `"audit"` for boolean, else `true`; control `"ctl"` for string, `7` for
number and integer, `false` for boolean. "Original" means the control is the site's own text, so the
control run is skipped (DR-01b: `{}` raises *Missing required field* against a class).

| Shape | Kind condition | Wrong | Control |
|---|---|---|---|
| `arg.primitive` / `arg.optional` | PRIMITIVE, not optional / optional | primitive | primitive |
| `arg.table` | TABLE, not optional | `function() end` | `{}` |
| `arg.record` / `arg.class` | RECORD / CLASS, not optional | `function() end` | original |
| `lenient.primitiveForTable` | TABLE, RECORD or CLASS, not optional | `true` (by design) | original |
| `arg.array` | ARRAY, not optional | `true` | `{}` |
| `arg.union` | UNION, every arm ∈ {string, number, integer, boolean, nil} | `true` if no `boolean` arm, else `"audit"` if no `string` arm, else ineligible | control of the arm matching the argument's literal kind (§3.2 step 7), else of the first non-nil arm |
| `arg.function` | FUNCTION, not optional | `42` | `function(p1, …, pn) end`, `n` = entries at depth 0 inside `fun(…)` (0 for `function`); an entry starting `...` gives `...` |
| `arg.generic` | GENERIC whose `bound` is PRIMITIVE | bound's primitive | bound's primitive |
| `arity.tooMany` / `arity.tooFew` | §3.2 step 8 | §3.2 step 8 | original |
| `return.mismatch` / `field.assign` | `@return` / `@field` type PRIMITIVE, not optional | primitive | primitive |

### 3.2 Site enumeration (`AuditSiteFinder.scan`)
For each `LuaFuncCall` (`PsiTreeUtil.findChildrenOfType`), `coverage.calls++`; only the first
`nameAndArgs` of a chain is a site, as `visitFuncCall` models only that.
1. `na = call.nameAndArgsList.firstOrNull()`; unless `na.args.text` starts with `(`, `stringOrTableArgs++`, next.
   `args = na.args.exprList?.exprList ?: emptyList()`; `callee = call.varOrExp.var`.
2. `ref = na.methodExpr?.nameRef ?: callee?.varSuffixList?.lastOrNull()?.indexExpr?.nameRef ?:
   callee?.nameRef`; `target = ref?.reference?.resolve()`.
3. Declaration, by what `resolve()` returns (DR-01b resolution rows): `target is LuaFuncDecl` → it
   (GLOBAL, MEMBER); `target.parent is LuaNameRef` and `target.parent.parent is LuaLocalFuncDecl` → that
   (LOCAL); `target.parent.parent is LuaFuncNameMethod` → `PsiTreeUtil.getParentOfType(target,
   LuaFuncDecl::class.java)` (METHOD). With `target` null, `recv = callee?.nameRef` set, and either
   `na.methodExpr != null` with no `varSuffixList` or no method and exactly one suffix, use the engine's
   own cross-file route (DR-01d): `src = LuaTypeManager.getInstance(file.project).resolveGlobal(recv.text,
   call)?.resolveMember(ref.text)?.sourceElement`; only when `PsiTreeUtil.getParentOfType(src,
   LuaFuncName::class.java, false)` is non-null is decl = `PsiTreeUtil.getParentOfType(src,
   LuaFuncDecl::class.java, false)` (a `src` outside a `LuaFuncName` is a field or value, not a function
   declaration). Still none → `unresolvedMethod++`
   when `na.methodExpr != null`, else `unresolvedCallee++`; next call.
4. Form: `LuaLocalFuncDecl` → LOCAL; `funcName.funcNameMethod != null` → METHOD; non-empty
   `funcNamePropertyList` → MEMBER; else GLOBAL. `(na.methodExpr != null) != (form == METHOD)` →
   `formMismatch++`, next.
5. Path: same `containingFile` → SAME_FILE (`declRange = decl.textRange`); declaring file's
   `virtualFile.url` starts with `temp://` → PROJECT_FILE (`declFile` = its path relative to the temp
   root); its `path` starts with `checkoutDir.canonicalPath` → CHECKOUT_FILE; else LIBRARY.
6. `params` = `decl.parList?.nameList?.nameRefList`; `comment = decl.catsComment`. No comment or empty
   `paramTagList` → `noTag++`, next. `parList.text` contains `...` or `args.size != params.size` →
   `arityGuard++`, next. Else `eligible++`. So `calls` = `stringOrTableArgs` + `unresolvedCallee` + `unresolvedMethod` +
   `formMismatch` + `noTag` + `arityGuard` + `eligible`.
7. Per index `i`: the tag whose `argName.text == params[i].text`; classify; emit each matching shape of
   §3.1 with `edit = args[i].textRange`, `site = call.textRange`, `paramRange = params[i].textRange`
   when SAME_FILE. Literal kind of `args[i].text`: starts with `"`, `'` or `[[`/`[=` → string; matches
   `-?\d` or `0x` → number (an `integer` arm also matches); `true`/`false` → boolean; else none.
8. `arity.tooMany`: edit = empty range at `na.args.textRange.endOffset - 1`, wrong `", true"` (`"true"` if
   `args` is empty). `arity.tooFew`: only when `args` is non-empty and the last parameter has a tag that
   classifies non-null and not optional; delete from `args[size-2].textRange.endOffset` (or
   `args[0].startOffset` when size is 1) to `args.last().textRange.endOffset`, wrong `""`. A last
   parameter with no tag or a null kind (`any`, an alias) gives no `tooFew` site.

**LDoc:** per file, `ldocParam +=` lines matching `^\s*--(?!-)\s*@param\b`. They are not LuaCATS
(`LuaLexer.kt:100-106` keeps a comment only when every line starts with `---`), so nothing reads them.

**Returns:** each `LuaFuncDecl`/`LuaLocalFuncDecl` whose first `@return` has one
`returnTypeDescriptorList` entry classifying PRIMITIVE; each `LuaFinalStatement` whose nearest
`LuaFuncDecl`/`LuaLocalFuncDecl`/`LuaFuncDef` ancestor is that declaration and whose `exprList` holds one
expression → edit it; `site` = the statement's range, `paramRange` null, `declRange` = the declaration's
range (DR-01: the report lands on the declaration); SAME_FILE, form of the declaration.

**Fields:** with `types = LuaTypesSnapshot.forFile(file)`, each `LuaAssignmentStatement` with one target
and one value, target `LuaVar` with a `nameRef` and one suffix whose `indexExpr.nameRef` is set and
`indexExpr.expr` is null. Receiver type `types.graphTypeToLuaType(types.getValueType(var.nameRef))`;
first `LuaClassType` among its `LuaUnionType.types` (or itself); `tag = fieldTag(resolveMember(fieldName),
fieldName)` = `sourceElement as? LuaCatsFieldTag`, else, for the stub path's host declaration
(`sourceElement = member.tag ?: decl`, `LuaTypeManagerImpl.kt:396-401`), `(sourceElement as?
LuaCatsCommentOwner)?.catsComment?.fieldTagList` entry whose `fieldDescriptor.argName?.text == fieldName` (as `LuaCatsDeclarations.kt:76`); null →
`fieldUntagged++`. A PRIMITIVE `tag.argType` text → edit the value; `site` = the assignment's range,
`paramRange` and `declRange` null; form FIELD; path by the tag's file as step 5.

### 3.3 Selection
Group sites by `AuditCell`, sort by `(file, edit.startOffset)`, keep indices `pick(n, CAP)` =
`(0 until k).map { it * n / k }` with `k = min(n, CAP)` (integer division). Group size is `population`.

### 3.4 Measurement (`AuditInjector.measure`)
`rule(e)` = `LuaReturnTypeMismatch` if `e.isReturnRelated()` else `LuaTypeAssignability`; the expected rule
is `LuaReturnTypeMismatch` for `return.mismatch`, else `LuaTypeAssignability`. `data class Shift(at: Int,
delta: Int)`, `Shift.NONE = Shift(Int.MAX_VALUE, 0)`. `keys(file, shift)` = the multiset of `(severity,
rule, message, s', e')` over `getErrors()` minus `HYPOTHESIS`, `x' = x − shift.delta` when `x ≥ shift.at`,
else `x`. For the edited file and a replacement text `r`, `shift = Shift(edit.startOffset + r.length,
r.length − span.length)`; the baseline and the declaring file (`DB`, `DW`, `DC`) always use `Shift.NONE`,
as that file is never edited. Steps 1–4 run in one `try`: any exception except `RevertMismatch` →
`FAILED`; `RevertMismatch` propagates.
1. `psiFile = PsiManager.getInstance(project).findFile(fixture.findFileInTempDir(site.file))`;
   `doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)`; `original = doc.text`;
   `span = doc.getText(site.edit)`.
2. `B = keys(psiFile, Shift.NONE)`. A baseline error whose range intersects `site.site` →
   `SKIPPED_PREEXISTING`. With `declFile` set, `DB` = its keys.
3. `apply(r)`: in one write command replace `site.edit` with `r`; `commitDocument`; read the edited file's
   keys and the declaring file's; write `span` back over `[edit.startOffset, edit.startOffset + r.length)`;
   commit; `verifyReverted(doc, original)`, which throws `RevertMismatch` (a private `class RevertMismatch(message:
   String) : IllegalStateException(message)` in `AuditInjector.kt`) on any difference and aborts the member.
4. `W = apply(wrong)`; `C = apply(control)`, or `B` when the control is original.
5. Anchor an entry: `DECLARING_FILE` for the declaring file's; `AT_SITE` if `site.site.contains(TextRange(s',
   e'))`; `AT_PARAM` if inside `paramRange`; `AT_DECL` if equal to `declRange`; else `BODY`. Direct =
   `AT_SITE`, `AT_PARAM`, `AT_DECL` (DR-01/01b: where this engine reports).
6. `noise` = anchored `(C − B) ∪ (DC − DB)`; `delta` = anchored `((W − B) − (C − B)) ∪ ((DW − DB) − (DC −
   DB))`; `newErrors = delta` as `AuditEntry`s.
7. `outcome(site, delta, noise)`: a direct `noise` entry → `NOISY` (BODY and DECLARING_FILE noise is
   already subtracted from `delta`); else a direct `delta` entry with the expected rule → `REPORTED`;
   direct entries with other rules only → `REPORTED_OTHER_RULE`; none → `LENIENT_BY_DESIGN` for
   `lenient.*`, else `SILENT`.

Dedup: `LuaTypeGraph.addError` drops a repeat element+message (`LuaTypeGraph.kt:229`), so a `HYPOTHESIS`
can shadow the injected `ERROR`; the site then reads `SILENT`, as the user sees it.

### 3.5 Per member
`getTestDataPath()` returns `System.getProperty("user.dir")`, as `LuaCorpusSweepTest`; `repoRoot =
File(testDataPath)`. `setUp`: `super.setUp()`; `CorpusDefinitionLibraries.requireSettingsSubscriber(project)`, as
`LuaCorpusSweepTest`; `VfsRootAccess.allowRootAccess(testRootDisposable,
File(repoRoot, CorpusManifest.CORPUS_DIR).canonicalPath)`; `enabledDefinitionLibraries = mutableListOf()`;
`savedSourcePath = CorpusSourcePath.snapshot(project)`. `audit(name)`: `entry = CorpusManifest.entry`;
`CorpusGuards.assertCorpusFetched`; `savedTarget = CorpusTarget.pin(project,
CorpusTarget.memberTarget(entry))`; with `entry.moduleRoot`, `sourcePath = "$base/?.lua;$base/?/init.lua"`
for `base` the canonical `checkoutDir/moduleRoot`; copy each root as `CorpusSweep.kt:208`; `scan` each
`.lua` file; select; measure; `AuditOutput.write`; print `[type-audit:<member>] files=<n>
population=<n> sites=<n> elapsedMs=<n>`, where `elapsedMs` runs from `audit(name)`'s first statement to
just after `AuditOutput.write` (setUp and tearDown excluded). `tearDown`: if `CorpusSourcePath.restore(project,
savedSourcePath)` is true, `CorpusDefinitionLibraries.refreshRoots(project)`; then, in `finally`,
`CorpusTarget.restore(project, savedTarget)`; then `super.tearDown()`.

### 3.6 Aggregation and verdict (`type_audit_summary.py`), fixed before the run
**Scope: every member, every callee path** (resolves risks-and-gaps DR-03). The population is every
non-`lenient.` site in all five members on all four callee paths — `LIBRARY`, `SAME_FILE`,
`PROJECT_FILE` and `CHECKOUT_FILE` — pooled. Mechanisms A and B are checks the engine makes whatever
file declares the callee, so a `LIBRARY` site (a bundled runtime stub) exercises the same mechanism
as a project one; `CHECKOUT_FILE` counts as `PROJECT_FILE` does. Only lazynvim carries LuaCATS
`---@param` tags of its own; the other four reach tagged declarations only through `LIBRARY`, and the
harness's `ldocParam` counter (§3.2) reports their LDoc `-- @param` lines. Every member is pinned to
its manifest target with definition libraries disabled (§3.5), so lazynvim's `vim.*` calls count as
unresolved. Every figure is also reported stratified by callee path and by member, with the share of
`P` and `S` each path carries; the strata are reporting, not inputs to the verdict. `arg.array` is
reported as unmeasurable on this engine wherever it checks the argument: every table argument to an
array type already errors (DR-01e), so such sites fall out as `SKIPPED_PREEXISTING` or `NOISY`; sites on
a route that checks nothing still read `SILENT` and stay in `F`.

**Pooled across members:** per `(shape, path, form)`, `population` and each outcome count are summed over
members. Over cells whose shape does not start with `lenient.` and with `measured ≥ 1` (measured =
REPORTED + REPORTED_OTHER_RULE + SILENT; NOISY, SKIPPED, FAILED and LENIENT are out):
`rate = SILENT / measured`, `S = Σ population·rate`, `P = Σ population`, `F = S / P`.
- **Node-carrying (survey A):** `arg.table` and `arg.class` with path ≠ SAME_FILE (DR-01 rows
  `pTable`/`pClass`). `arg.record` is excluded: it is silent on every path (DR-01), an annotation-model gap
  counted in `F` only.
- **Route-divergent (survey B):** `measured ≥ 3`, `rate ≥ 0.8`, and some cell with the same shape, another
  `(path, form)`, `measured ≥ 3` and `rate ≤ 0.2`.
- `R = Σ population·rate over node-carrying ∪ route-divergent / S` (0 when `S = 0`).
- **`REOPEN` iff `F ≥ 0.25` and `R ≥ 0.5`, else `HOLD`.** A quarter of declared-demand sites silent,
  mostly through mechanisms A and B, exceeds the 16–23 % of bugs the survey credits a redesign with.
- A cell no site reaches is absent from `F`. Beside the verdict the script prints the pooled
  `unresolvedCallee` and `unresolvedMethod` as "unmeasured" and `ldocParam` as "not read by the checker
  (not LuaCATS)"; neither enters `F`.

Output (Markdown): shape table and cell table (population, measured, reported, other-rule, silent, lenient,
noisy, skipped, failed, `REPORTED` with no `AT_SITE` entry, entries anchored `BODY`/`DECLARING_FILE`),
coverage per member and pooled, then `F`, `R`, verdict.

## 4. External Data
`AuditOutput.render` is Gson (as `CorpusFindingsFile`); `write` puts it in `build/type-audit/<member>.json`.
Field names are the data-class names, `cell` nested as `{"shape", "path", "form"}`, each `newErrors`
entry an object `{"severity", "rule", "message", "anchor"}` (messages contain `|`, e.g. *boolean is not
assignable to union number | string*, DR-01c), enums by name, `line` 1-based, `schema` 1, `commit` =
`entry.commit`. One schema, two tests: `tooling/corpus/testdata/type_audit_sample.json` is both the Kotlin
golden (TC-19) and the Python input (TC-20). The script exits 2
on `schema != 1` or a missing directory.

## 5. Integration Points
No `plugin.xml` change. In `build.gradle.kts`'s `test` task, beside the `-PwithDocker` filter and outside
every `withCorpus` block: `if (!project.hasProperty("withTypeAudit")) { filter {
excludeTestsMatching("*FalseNegativeAudit*"); isFailOnNoMatchingTests = false } } else { val typeAuditDir =
layout.buildDirectory.dir("type-audit").get().asFile; doFirst { typeAuditDir.deleteRecursively() } }`, as
`build.gradle.kts:432-435` does for `corpus-findings`.
**The audit command:** `tooling/gce-builder/gce-builder.sh run "test -PwithTypeAudit --rerun --no-build-cache
--tests '*FalseNegativeAudit*'"`, never with `-PwithCorpus` (BUG-418). Copy-back (libvirt
backend): `rm -rf build/type-audit && . tooling/gce-builder/config.sh && scp -i "$LV_SSH_KEY" -r
"$REMOTE_USER@$LV_SSH_HOST:$REMOTE_DIR/build/type-audit" build/` (`REMOTE_DIR` is relative to
`REMOTE_USER`'s home; the `builder` ssh alias logs in as root).

## 6. Requirement Coverage
MAINT-54-01 §5 · -02 §3.1 · -03 §3.2, §3.3 · -04 §3.4 · -05 §3.5, §4 · -06 §3.6 · -07, -08
implementation-plan Phase 2 · -09 (S) §3.2 counters, §3.6 · -10 (S) §3.4 steps 5–7.

## 7. Alternatives Considered
Classifying from `resolveCalleeType`, counting only errors at the injected range, and the caller
snapshot's receiver type for unresolved methods (a classless `LuaTableLiteralType`) are each refuted by
DR-01/01b; `resolveGlobal` is used instead (DR-01d). A highlighting pass adds only cost;
a fresh file per site would redeclare the original's globals.

## 8. Open Questions

_None — runtime budget is DR-02, with its branch stated._
