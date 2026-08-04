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

    /** A `@field`'s member name and the type string the engine should give it. */
    data class FieldMember(val name: String, val typeName: String)

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
(`luacats.bnf:107-111`):

- `fieldDescriptor ::= (ArgKeyword fieldScope? ArgName fieldNameDescriptor) | (ArgKeyword fieldScope? ArgType fieldKeyDescriptor)`
- `fieldNameDescriptor ::= NAME '?'?` → surfaces as `argName`, marker included in its text.
- `fieldKeyDescriptor ::= '[' type ']'` → surfaces as `argType` (`---@field [string] number`,
  keyed `[string]`; both paths already agree here — verified by probe, TC-4 locks it).
- `fieldScope ::= 'private' | 'protected' | 'public'` is a **separate `argKeyword` child**, so it
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
| `lang/psi/stubs/LuaLocalVarStub.kt:12` | field type `String?` → `List<String>` |
| `lang/psi/stubs/impl/LuaLocalVarStubImpl.kt:16` | constructor parameter type |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:26` | `createStub` calls `parentTypeNames(classTag)` |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:58` | `serialize` (below) |
| `lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:76` | `deserialize` (below) |
| `lang/psi/types/LuaTypeManagerImpl.kt:212` | `split(',')` deleted; consumes the list |
| `test/.../LuaStubSerializationTest.kt:57,73` | `assertEquals("Base", …)` → `assertEquals(listOf("Base"), …)` |

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

The per-declaration branch becomes a source selection, extracted to a helper so `materializeClass`
stays inside the ≤30-logic-line / ≤3-argument tripwires:

```kotlin
private fun declaredParts(decl: LuaLocalVarDecl): DeclaredParts {
    val stub = decl.stub
    if (stub != null) {
        return DeclaredParts(
            members = stub.luacatsFields.map { (n, t) -> LuaCatsDeclarations.FieldMember(n, t) },
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

`materializeClass` then loops over `decls`, converts each `FieldMember` to a `LuaTypeMember` and
each name to a `LuaTypeReference(name, decl)`, and calls `LuaImplicitFields.collect` +
`collectMethodMembers` exactly as today.

**One asymmetry is kept deliberately**: `LuaTypeMember.sourceElement` is the `@field` tag on the
AST path and the host `decl` on the stub path, because a stub has no tag PSI to point at. This is
provenance, not meaning, and is the one difference the parity harness must tolerate — §5 asserts
on member **names and types**, not on `sourceElement`.

### 4.2 The existing un-hosted `declaredParts` (`:287-303`)

Renamed to `unhostedParts(tag)` to avoid colliding with §4.1, and reduced to:

```kotlin
private fun unhostedParts(tag: LuaCatsClassTag): DeclaredParts? {
    val cats = PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java) ?: return null
    return DeclaredParts(LuaCatsDeclarations.fieldMembers(cats), LuaCatsDeclarations.parentTypeNames(tag))
}
```

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

**File**: `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaCatsStubAstParityTest.kt`
(`BasePlatformTestCase`).

```kotlin
private data class ParityCase(
    val id: String,
    val source: String,
    val className: String,
    val expectedMembers: Set<String>,
    val expectedSupertypes: List<String>,
)
```

For each case, the class is resolved twice:

| Arm | Fixture call | Required branch |
| :-- | :-- | :-- |
| stub | `myFixture.addFileToProject("stub_$id.lua", source)` | `decl.stub != null` |
| ast | `myFixture.configureByText("ast_$id.lua", source)` | `decl.stub == null` |

The fixture→branch mapping is **measured, not assumed** (requirements, "Evidence"), and the harness
**asserts the branch it got** before asserting anything else:

```kotlin
assertNotNull("case ${case.id}: stub arm must be stub-backed", decl.stub)
```

Without that guard the harness silently degrades to testing the AST branch twice — which is
precisely the failure mode that let BUG-401 ship with two green tests. Both arms are then asserted
against `expectedMembers` / `expectedSupertypes` **and** against each other, with `case.id` in every
message.

Corpus: TC-1 … TC-8 from `requirements.md`. TC-2 is the BUG-402 regression test and fails on the
stub arm before MAINT-34-02.

## 6. Verification

- `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` — full suite, never an
  isolated `--tests` pattern.
- `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"`.
- MAINT-33 corpus re-baseline (`-PwithCorpus`) after MAINT-34-02, since correcting parent
  extraction can change inspection counts on real projects.

## Open Questions

None — two questions are tracked as de-risking tasks in [`risks-and-gaps.md`](risks-and-gaps.md): DR-01 (does a parameterized parent resolve once passed intact?) and DR-02 (is the "Unknown" doc rendering real?). Neither blocks a requirement — MAINT-34-02 is specified to deliver the parent name intact regardless of DR-01's outcome, and MAINT-34-07 is a `Could`.
