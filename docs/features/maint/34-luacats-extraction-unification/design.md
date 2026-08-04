---
id: "MAINT-34-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-34"
folders:
  - "[[features/maint/34-luacats-extraction-unification/requirements|requirements]]"
---

# Technical Design: MAINT-34 — LuaCATS Extraction Unification

## 1. Architecture Overview

### Current state — one rule, three implementations

```
                    ┌─ LuaLocalVarStubElementType.createStub    (:23-44)   ─┐
LuaCatsComment PSI ──┼─ LuaTypeManagerImpl.materializeClass  AST branch (:219-235) ─┼─► members / supertypes
                    └─ LuaTypeManagerImpl.declaredParts         (:287-303) ─┘
                       LuaCatsDocumentationRenderer.buildFieldTag (:439-450)  ─► docs (4th, partial)
```

Which of the first three runs is decided by `decl.stub != null` — i.e. by whether the containing
file's AST is loaded, which is incidental. The three have drifted three times (requirements,
"Evidence"), and the drift is invisible to `configureByText`-style tests.

### Target state — one implementation, two data sources

```
                    ┌─ createStub ──► serialized stub ─┐
LuaCatsComment PSI ──► LuaCatsDeclarations ─┤                                  ├─► materializeClass
                    └─ (AST, no stub) ─────────────────┘
```

`LuaCatsDeclarations` is the only code that reads a LuaCATS tag into a `(name, type-string)` pair.
The stub stores **what the extractor produced**, so `materializeClass` never re-derives anything;
its stub/AST fork collapses to picking a source. The fork cannot be deleted — a stub is a
serialized snapshot with no PSI — but after this change the two arms cannot disagree about
*meaning*, only about provenance.

## 2. New Component

**File**: `src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsDeclarations.kt`
**Package**: `net.internetisalie.lunar.luacats.lang.psi` — alongside the existing hand-written
`LuaCatsBaseElements.kt` in the same package as the generated tag PSI it reads.

```kotlin
object LuaCatsDeclarations {

    /**
     * A `@field`'s member name, the type string the engine should give it, and the tag it came
     * from. [tag] is non-null on every PSI-read path and null only when the pair is rebuilt from a
     * stub, which has no PSI to point at — see §4.1 on `sourceElement`.
     */
    data class FieldMember(val name: String, val typeName: String, val tag: LuaCatsFieldTag?)

    fun fieldMembers(comment: LuaCatsComment): List<FieldMember>
    fun fieldMember(tag: LuaCatsFieldTag): FieldMember
    fun fieldDisplayName(tag: LuaCatsFieldTag): String
    fun parentTypeNames(tag: LuaCatsClassTag): List<String>
    fun paramTypes(comment: LuaCatsComment): Map<String, String>
    fun returnTypeName(comment: LuaCatsComment): String?
    fun aliasTarget(comment: LuaCatsComment): String?
}
```

No `plugin.xml` registration: this is a plain object, not an extension point. **No extension
point, service, or index is added or changed by this feature.**

### 2.1 `fieldMember(tag)` — the rule, specified

```
declared := tag.fieldDescriptor.argName?.text
         ?: tag.fieldDescriptor.argType?.text
         ?: ""
if declared ends with "?":
    name     := declared without the trailing "?"
    typeName := "(" + tag.argType.text + ") | nil"
else:
    name     := declared
    typeName := tag.argType.text
```

This is today's AST-branch rule verbatim (`LuaTypeManagerImpl.kt:220-230`); the stub adopts it.

Two grammar facts make the `argName ?: argType` fallback necessary and sufficient
(`luacats.bnf:107-112`):

- `fieldDescriptor ::= (<<ArgKeyword fieldScope>>? <<ArgName fieldNameDescriptor>>) | (<<ArgKeyword fieldScope>>? <<ArgType fieldKeyDescriptor>>)` (`:108-109`, quoted verbatim — the `?` binds to the meta-rule call)
- `fieldNameDescriptor ::= NAME '?'?` (`:111`) → surfaces as `argName`, marker included in its text.
- `fieldKeyDescriptor ::= '[' type ']'` (`:112`) → surfaces as `argType` (`---@field [string] number`,
  keyed `[string]`; both paths already agree here — verified by probe, TC-4 locks it).
- `fieldScope ::= 'private' | 'protected' | 'public'` (`:110`) is a **separate `argKeyword` child**, so it
  is excluded from both accessors with no extra handling (TC-5 locks it).

`fieldDisplayName(tag)` returns `declared` **unmodified** — quick-doc should render `beta?`, which
is what LuaLS itself shows. Keeping it in this object makes that difference a deliberate, adjacent
choice rather than a fourth private copy (MAINT-34-07).

### 2.2 `parentTypeNames(tag)` — no string splitting

```kotlin
fun parentTypeNames(tag: LuaCatsClassTag): List<String> =
    tag.parentTypes?.argTypeList.orEmpty()
        .map { it.text.trim() }
        .filter { it.isNotEmpty() }
```

`parentTypes ::= <<ArgType parentType>> { ',' <<ArgType parentType>> }*` (`luacats.bnf:92`) — the
grammar has already separated the parents, so the comma never needs to be re-interpreted. This is
the whole of the BUG-402 fix: `LuaTypeManagerImpl.kt:212`'s `split(',')` is deleted, and the stub
stops flattening a list into a string.

### 2.3 The remaining three

```kotlin
fun paramTypes(comment: LuaCatsComment): Map<String, String> =
    comment.getParamTagList().associate { (it.argName?.text ?: "") to it.argType.text }

fun returnTypeName(comment: LuaCatsComment): String? =
    comment.getReturnTagList().flatMap { it.returnTypeDescriptorList }.firstOrNull()?.argType?.text

fun aliasTarget(comment: LuaCatsComment): String? =
    comment.getAliasTagList().firstOrNull()?.argType?.text
```

All three are today's rules verbatim, each currently written 2–3 times.

## 3. Stub Shape Change (MAINT-34-02, -06)

`LuaLocalVarStub.luacatsExtends: String?` → **`luacatsParents: List<String>`**.

| File | Change |
| :-- | :-- |
| `lang/psi/stubs/LuaLocalVarStub.kt:12` | rename `luacatsExtends: String?` → `luacatsParents: List<String>` |
| `lang/psi/stubs/impl/LuaLocalVarStubImpl.kt:16` | same rename on the constructor parameter |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:26` | `createStub` calls `parentTypeNames(classTag)`; the old `parentTypes?.text?.removePrefix(":")?.trim()` is deleted |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:46` | **`LuaLocalVarStubImpl(...)` call — must switch to named arguments** (see below) |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:58` | `serialize` (below) |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:76` | `deserialize` (below) |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:84` | **second `LuaLocalVarStubImpl(...)` call — named arguments** |
| `lang/psi/types/LuaTypeManagerImpl.kt:212` | `split(',')` deleted; consumes the list |
| `test/.../LuaStubSerializationTest.kt:57,73` | `assertEquals("Base", …)` → `assertEquals(listOf("Base"), …)` |

**Named arguments are mandatory at both construction sites, not a style preference.**
`LuaLocalVarStubImpl`'s constructor is positional (`LuaLocalVarStubImpl.kt:9-18`) and parameter 2
is already `names: List<String>`. Changing parameter 7 from `String?` to `List<String>` makes the
two type-identical, so a `names`/`luacatsParents` transposition **compiles silently** and corrupts
both the payload and the declared names. Named arguments are the only compile-time guard; no test
in this plan would otherwise distinguish the two.

Serialization, replacing the single `writeName`, and matching the existing `luacatsFields` idiom
in the same file (`:59-63`):

```kotlin
// serialize
dataStream.writeInt(stub.luacatsParents.size)
stub.luacatsParents.forEach { dataStream.writeName(it) }

// deserialize
val parentCount = dataStream.readInt()
val parents = mutableListOf<String>()
repeat(parentCount) { dataStream.readName()?.string?.let { parents.add(it) } }
```

Field order in the stream is unchanged apart from this slot; `LuaFileElementType.getStubVersion()`
goes **3 → 4** (MAINT-34-06), which discards every stale on-disk stub.

## 4. Call-Site Migration

### 4.1 `materializeClass` (`LuaTypeManagerImpl.kt:203-243`)

**`DeclaredParts` is redefined by this feature.** Its current declaration
(`LuaTypeManagerImpl.kt:284`) is:

```kotlin
private data class DeclaredParts(val members: Map<String, LuaTypeMember>, val superTypes: List<LuaType>)
```

It becomes — carrying *extraction output* rather than already-built engine types, so both callers
can share it:

```kotlin
private data class DeclaredParts(
    val members: List<LuaCatsDeclarations.FieldMember>,
    val superTypeNames: List<String>,
)
```

Both consumers change with it: `materializeClass` (§4.1) and `materializeUnhostedClass`
(`:253-271`, consuming lines `:260-262` — §4.2). Neither may keep the old field names.

The per-declaration branch becomes a source selection, extracted to a helper so `materializeClass`
stays inside the ≤30-logic-line / ≤3-argument tripwires:

```kotlin
private fun hostedParts(decl: LuaLocalVarDecl): DeclaredParts {
    val stub = decl.stub
    if (stub != null) {
        return DeclaredParts(
            members = stub.luacatsFields.map { (n, t) -> LuaCatsDeclarations.FieldMember(n, t, tag = null) },
            superTypeNames = stub.luacatsParents,
        )
    }
    val cats = LuaPsiImplUtil.getCatsComment(decl) ?: return DeclaredParts(emptyList(), emptyList())
    return DeclaredParts(
        members = LuaCatsDeclarations.fieldMembers(cats),
        superTypeNames = cats.getClassTagList().firstOrNull()
            ?.let { LuaCatsDeclarations.parentTypeNames(it) }.orEmpty(),
    )
}
```

It is named `hostedParts`, **not** `declaredParts`, so it cannot be confused with — or accidentally
overload — the existing un-hosted function of that name (§4.2). Kotlin would happily accept
`declaredParts(LuaLocalVarDecl)` and `declaredParts(LuaCatsClassTag)` as an overload pair, so there
would be no compile error to catch a half-applied rename.

`materializeClass` then loops over `decls` and, for each, converts the parts to engine types with
this exact rule — **last write wins, matching today's `membersMap[fName] = …` at `:210`/`:229`**:

```kotlin
val parts = hostedParts(decl)
parts.members.forEach { member ->
    membersMap[member.name] = LuaTypeMember(
        member.name,
        LuaTypeReference(member.typeName, decl),
        sourceElement = member.tag ?: decl,
    )
}
parts.superTypeNames.forEach { superTypes.add(LuaTypeReference(it, decl)) }
```

then calls `LuaImplicitFields.collect` and `collectMethodMembers` exactly as today (`:238-241`).

**`sourceElement` is preserved exactly, and that is what `FieldMember.tag` exists for.**
`member.tag ?: decl` reproduces today's behaviour on both paths: the `@field` tag on the AST path
(where `fieldMembers` supplies it) and the host `decl` on the stub path (where there is no PSI, so
`tag` is null). This is not cosmetic — `LuaOverrideLineMarkerProvider.kt:64` uses
`superMembers.mapNotNull { it.sourceElement }` as **gutter navigation targets** and `:72-75`
derives `isAbstractMember` from it, so collapsing it to `decl` would silently regress override
navigation. §5's parity assertions cover names and types only and would **not** catch that, hence
the dedicated check in the DoD.

### 4.2 The existing un-hosted `declaredParts` (`:287-303`) and its caller (`:260`)

`declaredParts(tag)` is renamed `unhostedParts(tag)` and reduced to extraction only:

```kotlin
private fun unhostedParts(tag: LuaCatsClassTag): DeclaredParts? {
    val cats = PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java) ?: return null
    return DeclaredParts(LuaCatsDeclarations.fieldMembers(cats), LuaCatsDeclarations.parentTypeNames(tag))
}
```

Its **only** caller is `materializeUnhostedClass` (`:253-271`), which consumes the old shape at
`:260-262`:

```kotlin
val parts = declaredParts(tag) ?: return@forEach
parts.members.forEach { (memberName, member) -> membersMap.putIfAbsent(memberName, member) }
parts.superTypes.forEach { if (it !in superTypes) superTypes.add(it) }
```

Both member lines break under the new shape. The replacement preserves **observable behaviour
exactly**:

```kotlin
val parts = unhostedParts(tag) ?: return@forEach
parts.members.forEach { member ->
    membersMap.putIfAbsent(
        member.name,
        LuaTypeMember(member.name, LuaTypeReference(member.typeName, tag), sourceElement = member.tag ?: tag),
    )
}
parts.superTypeNames.forEach { superTypes.add(LuaTypeReference(it, tag)) }
```

**The dropped `if (it !in superTypes)` guard is dead code today, and dropping it is therefore
behaviour-preserving — not a behaviour change.** `LuaTypeReference` is a plain class with no
`equals` override (`LuaTypeReference.kt:5-20`), so `!in` compares identity; and the current
`declaredParts(tag)` constructs a **fresh** `LuaTypeReference` per tag on every call (`:300-301`),
so the same object is never re-added and the guard has never suppressed anything. Two
`---@class Same` tags that both declare `: P` accumulate two `P` references today, and must
continue to.

Replacing it with a name comparison (`superTypes.none { it.name == ref.name }`) would look like a
tidy-up and would in fact be a **silent behaviour change** — the exact class of drift this feature
exists to eliminate — so it is deliberately *not* done here. If de-duplicating supertypes is
wanted, it is its own change with its own test.

`putIfAbsent` (first-wins) is kept as-is, and deliberately differs from §4.1's last-wins, because
multiple `---@class` tags for one name are merged here.

### 4.3 `funcTypeFromStub` (`:361-381`) and the two func stub builders

- `LuaFuncStubElementType.kt:24-30` and `LuaLocalFuncStubElementType.kt:23-29` — byte-identical
  copies today — both become `LuaCatsDeclarations.returnTypeName(cats)` /
  `LuaCatsDeclarations.paramTypes(cats)`.
- `funcTypeFromStub`'s AST fallback (`:368-371`) calls the same two.
- The `---@return self` → receiver-class substitution (`:375-379`) stays in `funcTypeFromStub`: it
  needs the class name, which is a type-engine concern, not a tag-reading one.

### 4.4 `materializeAlias` (`:383-392`) and `LuaCatsDocumentationRenderer` (`:439-450`)

`materializeAlias`'s AST arm calls `LuaCatsDeclarations.aliasTarget(cats)`.
`buildFieldTag` calls `LuaCatsDeclarations.fieldDisplayName(tag)` in place of
`fieldDescriptor.argName?.text ?: "Unknown"` — which also removes a **suspected** rendering gap
where a key-descriptor field (`---@field [string] number`, `argName` null) renders as literally
"Unknown". That suspicion is **inferred from the code, not measured**; DR-02 measures it first.

## 5. Parity Harness (MAINT-34-05)

**File**: `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaCatsStubAstParityTest.kt`.
**Base class**: `IndexedBasePlatformTestCase`
(`src/test/kotlin/net/internetisalie/lunar/lang/types/IndexedBasePlatformTestCase.kt`) — the
existing base for stub-index-dependent type tests; it forces a stub-index rebuild in `setUp`.
Direct predecessors to follow for idiom: `LuaOptionalFieldTest.kt` and
`LuaUnhostedClassResolutionTest.kt` in the same package.

### 5.1 Why the two arms need different class names

The obvious design — same class name, one file per arm — **does not work**, and would silently
produce a harness that tests nothing:

- `doResolveType` (`LuaTypeManagerImpl.kt:181-185`) searches `GlobalSearchScope.allScope(project)`
  and passes **every** matching declaration to `materializeClass(name, classDecls)`, whose loop
  (`:206`) runs the stub branch on one decl and the AST branch on the other and **merges** both
  into one `membersMap`/`superTypes`. There is no stub arm and no AST arm — one blended result.
  For TC-2 that would be 3 supertypes (`Base<string`, `number>`, `Base<string, number>`), matching
  neither expectation.
- `typeCache` is keyed on the name alone (`:34`, `:185`), so a second `resolveType` of the same
  name on the same manager returns the identical cached object and "the arms agree" becomes a
  tautology.

Hence the substitution rule in `requirements.md`: **every `__` in a case's source is replaced by
`<arm><caseIndex>`** (`S1`/`A1`, …), so the arms declare genuinely different classes and no two
cases collide either. As belt-and-braces, each arm resolves through a **fresh**
`LuaTypeManagerImpl(project)` instance.

**Sentinel rule**: `__` is the substitution marker and must not appear in a case source for any
other purpose. `__` is chosen over `$` deliberately — `$` is Kotlin string-template syntax, and the
repo's fixture idiom is the raw string (`LuaStubSerializationTest.kt:19-20` uses `"""` +
`trimIndent()`), where there is no `\$` escape and a literal `$` must be written `${'$'}`. `__` is
inert in both string forms and is a valid identifier character in Lua, so `C__` substitutes to a
legal name. Use `String.replace(String, String)` (a literal replace); a `Regex` overload would
treat `$` in the replacement as a group reference.

### 5.2 Structure

```kotlin
private data class ParityCase(
    val id: String,
    val source: String,              // uses `__` where an identifier must be arm-unique
    val className: String,           // also uses `__`, e.g. "C__"
    val expectedMembers: Set<String>,
    val expectedSupertypes: List<String>,
    val expectedMemberTypeContains: Map<String, String> = emptyMap(),
)

private fun substitute(text: String, arm: String, index: Int) = text.replace("__", arm + index)
```

**One test method per case** (7 class-model methods — TC-1…TC-5, TC-7, TC-8; TC-6 and TC-9 are separate, §5.4), each running both arms so a failure names the case
directly. Per case, with `index` its ordinal:

Each case is set up once, then resolved once per arm. **`substitute` is applied to `source`,
`className` *and* `expectedSupertypes`** — TC-3 and TC-8 name other classes in their expectations,
and those names are arm-qualified too.

```kotlin
val stubName = substitute(case.className, "S", index)      // e.g. "CS3"
val astName  = substitute(case.className, "A", index)
myFixture.addFileToProject("stub$index.lua", substitute(case.source, "S", index))
val usage = myFixture.configureByText("ast$index.lua", substitute(case.source, "A", index))
```

`usage` — the resolution context — is the `configureByText` `PsiFile`, **for both arms**. It is
used only for its project and scope, so sharing it is correct, and it is the one safe choice:
taking a context element from inside the `addFileToProject` file would load that file's AST and
break the very `stub != null` assertion the stub arm depends on.

| Step | Stub arm | AST arm |
| :-- | :-- | :-- |
| 1 | file added via `addFileToProject`, never opened | file opened via `configureByText` |
| 2 | `StubIndex.getElements(LuaClassNameIndex.KEY, stubName, project, GlobalSearchScope.allScope(project), LuaLocalVarDecl::class.java)` | same, with `astName` |
| 3 | **assert `decls.size == 1`**, then **assert `decls.first().stub != null`** | **assert `decls.size == 1`**, then **assert `decls.first().stub == null`** |
| 4 | `LuaTypeManagerImpl(project).resolveType(stubName, usage) as? LuaClassType` inside `runReadAction`, on a fresh manager instance | same, with `astName`, on a second fresh instance |
| 5 | `getMembers().keys`, `superTypes.map { it.name }` | same |

Then assert each arm against `expectedMembers` / the substituted `expectedSupertypes` /
`expectedMemberTypeContains`, **and** the two arms against each other. Every assertion message
carries `case.id` and the arm name.

`expectedMemberTypeContains` is a substring match against `LuaTypeMember.type.name`. That is
sufficient for TC-7's function expectation because `LuaFunctionType.name` renders the whole
signature — `fun(x: string): boolean` (`LuaStructuredTypes.kt:100-107`) — so one substring covers
parameter and return type together.

`configureByText` is called **after** `addFileToProject` so the AST-arm file is the one opened in
the editor; the stub-arm file is never opened and stays stub-backed.

### 5.3 The step-3 assertions are the point

Steps 3's two assertions are not defensive noise — they are what makes the harness a parity test
rather than the same branch run twice. `decls.size == 1` catches accidental cross-case name
collisions (the merge failure above); `stub != null` / `stub == null` catches the fixture→branch
mapping changing under a platform upgrade. That mapping is **measured, not contracted**
(`requirements.md`, "Evidence"), so it can change without notice. Without these assertions the
harness degrades silently to testing the AST branch twice — exactly the failure mode that let
BUG-401 ship with two green tests.

### 5.4 Cases outside the class model

- **TC-6** (`@alias`) — its own test method: resolve and assert a `LuaAliasType` with the expected
  target type name in both arms.
- **TC-10** (un-hosted multi-tag supertypes) — its own test method, and **not** a parity case:
  with no host declaration there is no `LuaLocalVarDecl`, so there is no stub arm to compare
  against. It is the only coverage of `materializeUnhostedClass`'s accumulate-duplicates
  behaviour, which §4.2 deliberately preserves, so it is the regression lock on that decision.
  - Both files go in via `addFileToProject` (neither is opened); **the sentinel is not used** —
    `Shared` and `P` are written literally, because there are no arms to keep apart and the two
    files must name the *same* class for the tags to merge.
  - The resolution context follows the named predecessor `LuaUnhostedClassResolutionTest.kt`
    (`:31`, `:52`, `:72`, `:90`, `:103`): a throwaway
    `myFixture.configureByText("consumer.lua", "local x = 1\n")`. §5.2's `usage` definition does
    not apply here, since this case opens no fixture file of its own.
  - Assertion: the resolved `LuaClassType.superTypes` has **size 2**, both named `P`. Asserting
    the *count* is the whole point — a name-based de-duplication would silently make it 1.

- **TC-9** (`getStubVersion`) — its own test method:
  `assertEquals(4, LuaParserDefinition.FILE.stubVersion)`.
  **Assert through the singleton, never `LuaFileElementType()`.** `LuaFileElementType`
  (`LuaFileElementType.kt:17`) is an `IStubFileElementType`, and the platform's `IElementType`
  registry has a hard size limit — instantiating element types per test exhausts it and throws
  `ArrayIndexOutOfBoundsException` during bulk runs (agent-guide lesson, "Element types must be
  static singletons"). The canonical instance already exists: `LuaParserDefinition.FILE`
  (`LuaParserDefinition.kt:61-63`).

### 5.5 `sourceElement` regression check

Separate from parity, one test asserts that an AST-path `@field` member's `sourceElement` is the
`LuaCatsFieldTag` and a stub-path member's is the `LuaLocalVarDecl` (design §4.1). This is the only
check that would catch the `FieldMember`-drops-PSI regression, since §5.2 compares names and types
only.

## 6. Verification

- `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — full suite, never an
  isolated `--tests` pattern.
- `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"`.
- MAINT-33 corpus re-baseline (`-PwithCorpus`) after MAINT-34-02, since correcting parent
  extraction can change inspection counts on real projects.

## Open Questions

None — two questions are tracked as de-risking tasks in [`risks-and-gaps.md`](risks-and-gaps.md): DR-01 (does a parameterized parent resolve once passed intact?) and DR-02 (is the "Unknown" doc rendering real?). Neither blocks a requirement — MAINT-34-02 is specified to deliver the parent name intact regardless of DR-01's outcome, and MAINT-34-07 is a `Could`.
