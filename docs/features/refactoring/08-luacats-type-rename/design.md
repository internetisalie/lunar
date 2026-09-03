---
id: "REFACT-08-DESIGN"
title: "08: Design — a renameable LuaCATS type symbol"
type: "design"
parent_id: "REFACT-08"
folders:
  - "[[features/refactoring/08-luacats-type-rename/requirements|requirements]]"
---

# REFACT-08 Design

## 1. Architecture overview

### 1.1 Current state, measured

A LuaCATS type name is unlinked text. Three facts fix the shape of this feature and each was run,
not read:

| # | Fact | Evidence |
| :-- | :--- | :--- |
| F1 | No element spelling a type name answers `getReference()` or `getReferences()`, and neither the hand-written nor the generated LuaCATS PSI holds a `PsiNamedElement`. | `REFACT-01-00-DR-04` P1: `slots=70 withReferences=0 withSingleReference=0` |
| F2 | `LuaCatsTypeNameIndex` maps **declaration sites only**, so rewriting through it moves the `@class` and leaves every use byte-identical. | `REFACT-01-00-DR-04` P2/P4: `indexedFiles=[types.lua] spelledInUseFile=68`, `useFileUnchanged=true` |
| F3 | A `psi.referenceContributor` on the LuaCATS use shapes is a legal registration and is **inert**: `LuaCatsBaseElement` never consults `ReferenceProvidersRegistry`. | `REFACT-08-00-DR-02` first pass: contributor registered, pattern matching, `references=0`, `staleWidgetSpellings=11` |
| F4 | `GlobalSearchScope.allScope` reaches the plugin's own bundled runtime stubs, which declare **non-builtin** type names, and the platform's writability gate does not see a declaration leaf discovered inside `renameElement`. So resolution and rewrite cannot share a scope (§3.11). | `REFACT-08-00-DR-03` W1: for `File`, `leavesAll=2 leavesProject=1`, the extra leaf in `…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua` with `writable=false inProject=false` |
| F5 | `LuaCatsGenericType` does fire, and the same rule matches a genuine use and a parameterized **declaration head**. | `REFACT-08-00-DR-03` G1 `byHolder={GENERIC_TYPE=1, NAMED_TYPE=1}`; G2 before the guard rewrote `---@class Box<T>` to `---@class Crate<T>` |

F3 is the correction this design turns on. `REFACT-01-00-DR-04` recorded the opposite as read rather
than run ("cats PSI reports `LuaLanguage`, so a `psi.referenceContributor language="Lua"` …
suffices"), and following it without §2.2 reproduces F2's half-apply through a different mechanism.

The mechanism is in the platform: `PsiElementBase.getReferences()` returns
`SharedPsiElementImplUtil.getReferences(this)`, which is `getReference()` wrapped in an array and
never touches the registry
(`com.intellij.psi.impl.SharedPsiElementImplUtil.getReferences`, platform sources — verify in
`~/Documents/src/lua/intellij-community/platform/core-impl/src/com/intellij/psi/impl/SharedPsiElementImplUtil.java:80-83`).
The Lua side already works around
this — `LuaBaseElement.getReferences()` calls
`ReferenceProvidersRegistry.getReferencesFromProviders(this)` explicitly and merges it with the
element's own reference
([LuaBaseElements.kt:36-48](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt)).
`LuaCatsBaseElement`
([LuaCatsBaseElements.kt:11-15](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsBaseElements.kt))
has no such override.

### 1.2 The chosen route

**Create the renameable symbol on the use side, and reuse the declaration side that already
exists.** Concretely: attach a `PsiReference` to each of the three use spellings, resolve it through
`LuaCatsTypeNameIndex` (which already answers "name → declaration site" for Go-to-Class and
quick-doc), and let the platform's own rename machinery do the rest. Nothing here builds a second
name→declaration map, and nothing stubs the LuaCATS comment (Alternative A).

The declaration caret is made renameable by one clause in the existing
`LuaTargetElementEvaluator.getNamedElement` — the same hook `BUG-469` used to make a numeric-`for`
control variable renameable, for the same reason: a leaf with no reference and no `PsiNamedElement`
parent has no target otherwise. Measured (`DR-02` P2/P4): with the clause,
`findTargetElement=PsiElement(NAME) text='Widget'`; without it, `null`.

### 1.3 What this feature does NOT change

- `LuaRenameProcessor` is untouched. Its `canProcessElement` requires
  `element.node.elementType == LuaElementTypes.IDENTIFIER` (via `LuaDeclarationSite.kindOf`,
  [LuaDeclarationSite.kt:43-50](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt))
  or `element is LuaNameRef`; a LuaCATS name leaf is `LuaCatsElementTypes.NAME` and is neither, so
  the two processors are disjoint by construction and their `plugin.xml` order does not matter.
- `LuaCatsTypeNameIndex`, `LuaCatsTypeNavigation`, `LuaClassNameIndex` and `LuaAliasIndex` keep
  their current contents and versions. The index is **read**, never re-keyed.
- `LuaCatsParamRenamer` keeps the `---@param` half of `REFACT-01-16`. This is its sibling, not its
  replacement.

## 2. Components

### 2.1 `net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations`

The single reader of a type-name slot, in the package that owns LuaCATS PSI. Every other component
here asks it; none re-derives a slot rule.

```kotlin
object LuaCatsTypeDeclarations {
    val BUILTIN_KEYWORDS: Set<String>

    fun classDeclarationLeaf(tag: LuaCatsClassTag): PsiElement?
    fun aliasDeclarationLeaf(tag: LuaCatsAliasTag): PsiElement?
    fun isDeclarationLeaf(element: PsiElement): Boolean
    fun isDeclarationSlotHolder(holder: PsiElement): Boolean
    fun shadowedTypeParameterNames(comment: LuaCatsComment): Set<String>
    fun useHolderOf(element: PsiElement): PsiElement?
    fun useLeafOf(holder: PsiElement): PsiElement?
    fun declarationLeaves(name: String, project: Project, scope: GlobalSearchScope): List<PsiElement>
    fun outOfProjectDeclarationFiles(name: String, project: Project): List<String>
}
```

`BUILTIN_KEYWORDS` is the `builtinType` alternative of `luacats.bnf:206`, verbatim:
`nil any boolean string number integer function table thread userdata lightuserdata`.

**Relationship to `LuaCatsDeclarations`, which this neither extends nor replaces.**
[`LuaCatsDeclarations`](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsDeclarations.kt)
already lives in this package and its KDoc calls it "the single reader of a LuaCATS tag into the
`(name, type-string)` pairs the type engine consumes" — `fieldMembers`, `paramTypes`,
`returnTypeName`, `aliasTarget`, `parentTypeNames`. Its readers are the stub builders and
`LuaTypeManagerImpl`; it answers *what a tag means*, and it returns **strings**.
`LuaCatsTypeDeclarations` answers a disjoint question — *which leaf spells a type name, and is that
spelling a declaration or a use* — and it returns **PSI elements**, because a rename must write one.
Neither reads the other, and no function is moved between them; the two are siblings under the same
"one reader per question" rule, and the name is chosen to say so. Merging them would put a
rename-only slot predicate on the type engine's hot stub path for no caller.

### 2.2 `LuaCatsBaseElement` — the change without which nothing else works

```kotlin
open class LuaCatsBaseElement(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun toString(): String = this.node.elementType.toString()

    override fun getReferences(): Array<PsiReference> =
        ReferenceProvidersRegistry.getReferencesFromProviders(this)

    override fun getReference(): PsiReference? = references.firstOrNull()
}
```

Both clauses are load-bearing and both are measured:

- **`getReferences()`** is what makes a contributor reach a LuaCATS element at all (F3).
- **`getReference()`** is what makes the contributed reference *usable*. With `getReferences()`
  alone, `DR-02` measured `firstNamedType.reference=null getReferences=1 references=0`: the
  reference exists and every consumer that reads `element.reference` — the searcher's inner loop,
  `substituteElementToRename` — sees null.

`getReferences()` must **not** merge `getReference()` the way `LuaBaseElement` does: here the two
would recurse. `LuaBaseElement` can, because its `getReference()` is an independent override on
`LuaNameRefBaseImpl`; no LuaCATS element has one.

**Blast radius.** This changes `getReferences()` for every LuaCATS PSI element, which is why
`REFACT-08-15` gates on the full suite and `risks-and-gaps.md` Risk 1.2 owns it. The other two
registered `psi.referenceContributor`s are `LuaLabelReferenceContributor`, patterned on `LuaLabelRef`
(a Lua element, never matched), and `LuaRequireReferenceContributor`, patterned on
`PlatformPatterns.psiElement()` but returning `PsiReference.EMPTY_ARRAY` unless
`LuaRequireReference.moduleStringOf(element)` is non-null and the element sits under a
`require` `LuaFuncCall`
([LuaRequireReferenceContributor.kt:28-51](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaRequireReferenceContributor.kt)).

### 2.3 `net.internetisalie.lunar.lang.LuaCatsTypeReference`

```kotlin
class LuaCatsTypeReference(element: PsiElement) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength)),
    PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult>
    override fun resolve(): PsiElement?
    override fun isReferenceTo(target: PsiElement): Boolean
    override fun handleElementRename(newElementName: String): PsiElement
}
```

`element` is the **holder** (`LuaCatsNamedType` / `LuaCatsTypeParam` / `LuaCatsGenericType`), never
the NAME leaf: each of those rules is `::= NAME` (`luacats.bnf:201-207`), so the holder's text is
exactly the name and the range is the whole element. The rewrite still goes to the leaf (§3.7).

Sits beside `LuaNameReference` and `LuaLabelReference` in `lang/`, matching where the other two Lua
references live.

### 2.4 `net.internetisalie.lunar.lang.LuaCatsTypeReferenceContributor`

One `PsiReferenceProvider` registered against three patterns —
`PlatformPatterns.psiElement(LuaCatsNamedType::class.java)` and the same for `LuaCatsTypeParam` and
`LuaCatsGenericType`. Modelled on `LuaLabelReferenceContributor`
([LuaLabelReferenceContributor.kt:9-28](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaLabelReferenceContributor.kt)).

```kotlin
getReferencesByElement(element, context) =
    if (LuaCatsTypeDeclarations.useLeafOf(element) == null) PsiReference.EMPTY_ARRAY
    else arrayOf(LuaCatsTypeReference(element))
```

**The gate is `useLeafOf`, not a shape test written here**, and that is §2.1's rule ("every other
component asks it; none re-derives a slot rule") rather than a preference. It subsumes every
exclusion the provider needs — a zero-length element, a declaration slot holder, and a shadowed type
parameter name, all of §3.4 — so there is exactly **one** clause to falsify. Measured: with the guard also written into the
provider, mutation N of `REFACT-08-00-DR-03` removed one copy and the other kept the write correct,
so neither copy on its own had a reachable falsifier. With the single clause, mutation N reddens TC-22
directly.

### 2.5 `net.internetisalie.lunar.lang.insight.LuaCatsTypeReferenceSearcher`

```kotlin
class LuaCatsTypeReferenceSearcher :
    QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    )
}
```

The cats-comment sibling of `LuaCatsTypeNavigation`'s search-free lookup and of
`LuaNameReferenceSearcher`
([LuaNameReferenceSearcher.kt:44-100](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt)),
and it exists for the same reason that one does: `CachesBasedRefSearcher` derives a search text only
for a `PsiFileSystemItem`, a `PsiNamedElement` or a `PsiMetaOwner`
(`CachesBasedRefSearcher.java:26-56`), and a bare LuaCATS `NAME` leaf is none of the three, so its
`text` is null and nothing is scanned.

**Executed, not inferred.** `REFACT-08-00-DR-03` mutation O removes this EP registration and leaves
everything else in place: `ReferencesSearch` then returns `references=0` in every fixture, and TC-25's
rename reports `outcome=RENAMED staleWidget=2 newGadget=0` — the declaration moved and both uses were
left behind. This is the measurement that corrects `REFACT-01`'s `risks-and-gaps.md` line claiming
"no searcher needed". Algorithm in §3.5.

### 2.6 `net.internetisalie.lunar.refactoring.rename.LuaCatsTypeRenameProcessor`

```kotlin
class LuaCatsTypeRenameProcessor : RenamePsiElementProcessor(), DumbAware {
    override fun canProcessElement(element: PsiElement): Boolean
    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement?
    override fun findReferences(
        element: PsiElement,
        searchScope: SearchScope,
        searchInCommentsAndStrings: Boolean,
    ): Collection<PsiReference>
    override fun findCollisions(
        element: PsiElement,
        newName: String,
        allRenames: Map<out PsiElement, String>,
        result: MutableList<UsageInfo>,
    )
    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?,
    )
    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement?
    override fun getQualifiedNameAfterRename(element: PsiElement, newName: String, nonJava: Boolean): String
}
```

**The superclass is `RenamePsiElementProcessor`, not `RenamePsiElementProcessorBase`**, and
**`DumbAware` is not decoration** — both for the reasons `LuaLabelRenameProcessor`'s KDoc records
([LuaLabelRenameProcessor.kt:26-36](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelRenameProcessor.kt)):
`RenamePsiElementProcessor.forElement` casts the extension instance unconditionally, and both
`forElement` and `forPsiElement` skip a processor failing `DumbService.isUsableInCurrentContext`
while `RenameElementAction` stays a `DumbAwareAction`. Claiming the element while indexing is safe
here in the same way it is for `LuaRenameProcessor`: every index-backed step
(`substituteElementToRename`'s resolve, `findReferences`' search) runs before the first write, so an
unavailable index ends the refactoring as a refusal, never as a half-applied rename.

### 2.7 `net.internetisalie.lunar.refactoring.rename.LuaCatsTypeNameInputValidator`

```kotlin
class LuaCatsTypeNameInputValidator : RenameInputValidator {
    override fun getPattern(): ElementPattern<out PsiElement>
    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean
}
```

**The pattern must be narrow, and this is a measured constraint rather than a style choice.**
`RenameInputValidatorRegistry.getInputValidator` returns a non-null `Condition<String>` as soon as
**any** registered validator's pattern accepts the element, and `RenameUtil.isValidName` then returns
that condition's answer and never falls through to `LanguageNamesValidation`
(`RenameUtil.java:383-406`). **`PlatformPatterns.psiElement()` with an early `return true` for
non-cats elements is therefore wrong, however harmless it reads**: measured, the full suite then
reports `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel FAILED — a reserved word must
be rejected as a label rename target`, because `RenameUtil.isValidName(project, label, "end")` has
become `true`. The pattern to write is
`PlatformPatterns.psiElement(LuaCatsElementTypes.NAME).with(<isDeclarationLeaf>)`, and with it the
same run reports 0 failures and `DR-02` P6's Lua-side control element answers `false` for
`parser.node`.

### 2.8 `LuaTargetElementEvaluator` — one clause added

```kotlin
override fun getNamedElement(element: PsiElement): PsiElement? =
    element.takeIf {
        LuaDeclarationSite.kindOf(it) == LuaDeclarationKind.NUMERIC_FOR_VARIABLE ||
            LuaCatsTypeDeclarations.isDeclarationLeaf(it)
    }
```

`TargetElementUtilBase.getNamedElement(element)` consults the language's `TargetElementEvaluatorEx2`
first and only then tries the `PsiNamedElement`-parent fallback
(`TargetElementUtilBase.java:106-126`); `doFindTargetElement` reaches it through
`getNamedElement(element, offsetInElement)` (`:161`, `:246`). The evaluator is registered
`language="Lua"` and a LuaCATS element reports `LuaLanguage`
([LuaCatsElementType.kt:22-26](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsElementType.kt)),
so the same registration serves both.

### 2.9 `LuaRenameCollisionUsageInfo` — reused, not re-declared

`findCollisions` emits the existing `internal` carrier
([LuaRenameConflictDetector.kt:54-60](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictDetector.kt)).
`LuaCatsTypeRenameProcessor` is in the same package, so no visibility change is needed and **no new
carrier is defined**. The conflict rule is small enough to live in `findCollisions` (§3.10); it does
not warrant a detector object of its own the way `LuaRenameConflictDetector`'s four rules or
`LuaLabelConflictDetector`'s scope walk do.

### 2.10 `LuaFindUsagesProvider` — one clause added

```kotlin
override fun canFindUsagesFor(element: PsiElement): Boolean =
    LuaDeclarationSite.kindOf(element) != null || LuaCatsTypeDeclarations.isDeclarationLeaf(element)

override fun getType(element: PsiElement): String =
    LuaDeclarationSite.kindOf(element)?.usageViewType
        ?: if (LuaCatsTypeDeclarations.isDeclarationLeaf(element)) "type" else ""
```

`getType` must be extended with it: `LuaDeclarationKind` has no LuaCATS member and returning `""`
for a claimed element gives the Find Usages window an unlabelled node.

## 3. Algorithms

### 3.1 The five slots, derived from the grammar

`luacats.bnf` is the source. Declarations:

| Tag | Rule | Slot |
| :-- | :--- | :--- |
| `@class` | `classTag ::= '@class' <<ArgKeyword exactKeyword>>? <<ArgType typeName>> [':' parentTypes] description?` (`:91`), `typeName ::= parameterizedName \| NAME` (`:94`) | the `LuaCatsArgType`'s single `NAME` child |
| `@alias` | `aliasTag ::= '@alias' <<ArgName NAME>> <<ArgType type>>? description?` (`:82`) | the `LuaCatsArgName`'s single `NAME` child |

Uses — three rules, each `::= NAME`, so each holder has exactly one `NAME` child:

| Holder | Rule | Reached from |
| :-- | :--- | :--- |
| `LuaCatsNamedType` | `namedType ::= NAME` (`:207`) | every `type` position: `@type`, `@param`, `@return`, `@field`, `@vararg`, `@cast`, `@operator`, a parent type in `@class X : Y`, a union member, an array element, a `fun(…)` argument or return |
| `LuaCatsTypeParam` | `typeParam ::= NAME` (`:203`) | a type argument inside `<…>` — **only when the enclosing `parameterizedName` is itself a use**; `typeParam` is reached from two rules and the other one declares (see below) |
| `LuaCatsGenericType` | `genericType ::= NAME` (`:202`) | the head of `Foo<…>` |

**`typeParam` is a declaration slot as well as a use slot.** It is reached from two rules, not one:

| Rule | Position | Kind |
| :-- | :--- | :--- |
| `parameterizedName ::= genericType '<' typeParam {',' typeParam }* '>'` (`:201`) | inside `Foo<…>` | a **use** where the `parameterizedName` is a type, a **declaration** where it is a class tag's `ArgType` (`---@class Box<T>`) |
| `genericTypeParam ::= <<ArgName typeParam>> ( <<ArgSymbol (':')>> <<ArgType parentType>> )?` (`:117`) | `---@generic T` | always a **declaration**, with function-local scope |

Executed (`REFACT-08-00-DR-04` P-A) — the NAME leaf's parent is `TYPE_PARAM` in every case, and it is
the *grandparent* that separates them:

```
[f1] leaf='T'      line=1  chain=TYPE_PARAM > PARAMETERIZED_NAME > ARG_TYPE > CLASS_TAG   <- ---@class Box<T>
[f1] leaf='T'      line=5  chain=TYPE_PARAM > ARG_NAME > GENERIC_TYPE_PARAM > GENERIC_TYPE_PARAMS > GENERIC_TAG
[f1] leaf='Widget' line=10 chain=TYPE_PARAM > PARAMETERIZED_NAME > DISTINCT_TYPE > ARRAY_TYPE > UNION_TYPE > TYPE
[f1] leaf='Widget' line=13 chain=TYPE_PARAM > PARAMETERIZED_NAME > DISTINCT_TYPE > ARRAY_TYPE > UNION_TYPE > TYPE
```

Lines 1 and 5 are declarations of a type parameter — a differently-scoped name that happens to be
spelled like a type. Lines 10 and 13 (`---@type Box<Widget>`, `---@param p table<string, Widget>`)
are uses. §3.4's `isDeclarationSlotHolder` is the discrimination, and `REFACT-08-17` is the
requirement.

`ArgName ::= <<child>>` / `ArgType ::= <<child>>` (`:43-44`) give those wrappers exactly one child —
the same grammar fact `LuaCatsParamRenamer` already relies on
([LuaCatsParamRenamer.kt:40-42](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaCatsParamRenamer.kt)).

### 3.2 `classDeclarationLeaf(tag)` / `aliasDeclarationLeaf(tag)`

```
classDeclarationLeaf(tag):
  argType := PsiTreeUtil.getChildOfType(tag, LuaCatsArgType) ; null -> return null
  first := argType.node.firstChildNode
  return first.psi if first.elementType == LuaCatsElementTypes.NAME else null

aliasDeclarationLeaf(tag):
  argName := PsiTreeUtil.getChildOfType(tag, LuaCatsArgName) ; null -> return null
  first := argName.node.firstChildNode
  return first.psi if first.elementType == LuaCatsElementTypes.NAME else null
```

**The element-type test is what implements `REFACT-08-09`, and there is no separate guard.**
`typeName ::= parameterizedName | NAME` (`luacats.bnf:94`) gives a class tag's `ArgType` exactly one
child, which is either a `LuaCatsParameterizedName` or a `NAME` leaf — so for `---@class Box<T>` the
`firstChildNode` is `PARAMETERIZED_NAME` and the test already returns null. **Do not add an
explicit `PsiTreeUtil.getChildOfType(argType, LuaCatsParameterizedName) != null` guard in front of
it — it is dead code, and that is measured.** Mutation G of `REFACT-08-00-DR-02` deleted such a
guard and `classDeclarationLeaf` for `---@class Box<T>` was still `null`, while mutation G3 — which
deletes it **and** relaxes the element-type test to `firstChildNode?.psi` — returned
`PARAMETERIZED_NAME`. The element-type test is the clause.

Why the exclusion is needed at all: `LuaCatsTypeNameIndex.Indexer.map` keys a class tag under
`argType.text.trim()`
([LuaCatsTypeNameIndex.kt:86-89](../../../../src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaCatsTypeNameIndex.kt)),
which for `@class Box<T>` is the whole string `Box<T>`. DR-01 measured exactly two such
disagreements over 188 class tags, both `table<K, V>`. Admitting the head as a declaration leaf would
therefore produce a rename whose `declarationLeaves(head)` lookup returns nothing — the caret's own
slot rewritten and nothing else. Refusing is the only correct option without re-keying the index,
and re-keying it changes what `LuaTypeManagerImpl.materializeUnhostedClass` can resolve, which is
outside this feature (`risks-and-gaps.md` Gap 2.2).

**The user-facing refusal is carried by §3.3, not by this function.** A caret on `Box` in
`---@class Box<T>` sits on a NAME leaf whose parent is a `LuaCatsGenericType`, so `isDeclarationLeaf`
is false for it regardless of what this function returns; it is then treated as a *use*, whose
`resolve()` is null because the index holds `Box<T>`. Measured (`DR-02` P16, and again under
mutation G3): `findTargetElement` is null, the platform offers no rename, and the file is
byte-identical.

**The element type is read from the node, not from a generated getter.** `LuaCatsArgType.getText()`
is safe, but a generated `@NotNull` child getter logs a platform error when a partially parsed tag
has no such child — the failure mode REFACT-01 design §3.5 records for `identifierLeafOf`. DR-01
measured 19 of 195 real annotated files carrying at least one `PsiErrorElement`, so partially parsed
tags are the normal case here, not the exception.

### 3.3 `isDeclarationLeaf(element)`

```
if element.node.elementType != LuaCatsElementTypes.NAME    -> false
slot := element.parent ; null -> false
tag  := slot.parent
if slot is LuaCatsArgType and tag is LuaCatsClassTag       -> classDeclarationLeaf(tag) === element
if slot is LuaCatsArgName and tag is LuaCatsAliasTag       -> aliasDeclarationLeaf(tag) === element
otherwise                                                  -> false
```

Written as a **round trip** against §3.2 rather than as a shape enumeration, so it cannot fall behind
it — the idiom `LuaRenameProcessor.receiverSegmentRefusal` uses against
`LuaDeclarationSite.functionNameLeafOf`. It is what excludes, by construction, every residue class
DR-01 measured: a `@param` name and a `@cast` variable (both `LuaCatsArgName`, but under the wrong
tag — 154 occurrences), a `@field` name (`LuaCatsFieldNameDescriptor`, 16), prose
(`LuaCatsDescription`, 50) and a return description (3).

### 3.4 `useHolderOf` / `useLeafOf` / resolution

```
isDeclarationSlotHolder(holder) =
       // 1. the `T` of `---@generic T` (`genericTypeParam ::= <<ArgName typeParam>>`, :117)
       (holder is LuaCatsTypeParam && holder.parent is LuaCatsArgName)
       // 2. anything inside the parameterized name a class tag declares — both the `Box`
       //    head and every `T` parameter of `---@class Box<T>`
       || (holder.parent is LuaCatsParameterizedName
           && holder.parent.parent is LuaCatsArgType
           && holder.parent.parent.parent is LuaCatsClassTag)

shadowedTypeParameterNames(comment) =
       every LuaCatsTypeParam under `comment` for which isDeclarationSlotHolder is true,
       mapped to its text                              // both clauses above, same comment

useHolderOf(element)  = element.parent, if element is a NAME leaf and the parent is one of the
                        three holders of §3.1, is NOT a declaration slot holder, and its text is
                        not in shadowedTypeParameterNames(its containing LuaCatsComment);
                        else null
useLeafOf(holder)     = holder.node.firstChildNode.psi, if holder is one of the three holders, is
                        NOT a declaration slot holder, its text is not shadowed as above, and that
                        node is LuaCatsElementTypes.NAME; else null

LuaCatsTypeReference.declarations() =
    LuaCatsTypeDeclarations.declarationLeaves(holder.text, project, GlobalSearchScope.allScope(project))

declarationLeaves(name, project, scope):
  for virtualFile in FileBasedIndex.getContainingFiles(LuaCatsTypeNameIndex.KEY, name, scope):
      file := PsiManager.findFile(virtualFile) as? LuaFile ; skip if null
      emit every classDeclarationLeaf / aliasDeclarationLeaf in file whose text == name

resolve()      = declarations().firstOrNull()
multiResolve() = declarations().map { PsiElementResolveResult(it) }
```

**Why the exclusion is in the slot reader.** `genericType ::= NAME` and `typeParam ::= NAME` are the
head and the arguments of `Foo<…>` *wherever it appears*, so the same two rules match a genuine use
(`---@type Box<string>`) and a parameterized **declaration** (`---@class Box<T>`) — which
`LuaCatsTypeNameIndex` keys under the whole text `Box<T>`, i.e. a *different* type. `typeParam` is
reached a second way as well, from `genericTypeParam` (`:117`), which is the `---@generic T` form:
a function-local type parameter, never a project type. Both are declarations, and neither may be
rewritten by a rename of a project type that happens to share the spelling.

**Executed** (`REFACT-08-00-DR-04` P-B) — the predicate above (`SPEC`) against the narrower
`holder is LuaCatsGenericType` form (`SHIPPED`), over one fixture carrying both declaration positions
and three use controls:

```
[f1] holder=GENERIC_TYPE text='Box'    line=1  SPEC=DECLARATION SHIPPED=DECLARATION
[f1] holder=TYPE_PARAM   text='T'      line=1  SPEC=DECLARATION SHIPPED=use   <== DISAGREE   ---@class Box<T>
[f1] holder=TYPE_PARAM   text='T'      line=5  SPEC=DECLARATION SHIPPED=use   <== DISAGREE   ---@generic T
[f1] holder=GENERIC_TYPE text='Box'    line=10 SPEC=use SHIPPED=use                          ---@type Box<Widget>
[f1] holder=TYPE_PARAM   text='Widget' line=10 SPEC=use SHIPPED=use
[f1] holder=TYPE_PARAM   text='Widget' line=13 SPEC=use SHIPPED=use                          ---@param p table<string, Widget>
[f1] holder=GENERIC_TYPE text='Box'    line=16 SPEC=use SHIPPED=use                          ---@class Panel : Box<Widget>
[f1] holder=TYPE_PARAM   text='Widget' line=16 SPEC=use SHIPPED=use
```

The two forms disagree on exactly the two declaration positions and agree on every use, which is
what makes `REFACT-08-17`'s mutation reachable: restoring the `holder is LuaCatsGenericType`
conjunct rewrites both.

The exclusion stays narrow by construction — clause 2 requires the whole
`… → ParameterizedName → ArgType → ClassTag` chain — and `DR-03` G3 is its control: a generic head in
a **parent-type** position (`---@class Panel : Box<string>`) is still a use and is still rewritten.

**Why shadowing is needed on top of it.** Excluding the declaration slot is not enough on its own:
`---@generic T` declares `T` for the whole comment, so `---@param v T` in the same comment is a use
of the *type parameter*, not of a project `---@class T`. The same holds inside a parameterized class
tag's comment, where `---@field item T` refers to `Box`'s parameter. Without the shadowing clause a
rename of a project `---@class T` rewrites those tags and silently breaks the generic function.
`LuaCatsComment` is the scope because a `@generic` tag and the tags it governs are siblings under one
comment — **executed** (`REFACT-08-00-DR-04` P-E), with an unrelated comment as the control:

```
[f1] NAMED_TYPE 'T' line=2  commentFound=true commentShadows=[T] SHADOWED=true    ---@class Box<T> / ---@field item T
[f1] NAMED_TYPE 'T' line=6  commentFound=true commentShadows=[T] SHADOWED=true    ---@generic T / ---@param v T
[f1] NAMED_TYPE 'T' line=7  commentFound=true commentShadows=[T] SHADOWED=true    ---@generic T / ---@return T
[f1] NAMED_TYPE 'T' line=10 commentFound=true commentShadows=[]  SHADOWED=false   ---@param w T, no @generic
```

Line 10 is the control: a `T` in a comment that declares no type parameter is an ordinary use and is
still rewritten, so the shadowing clause cannot be widened into a blanket exclusion of the name.

`declarationLeaves` returns a **list**, not a single element: LuaCATS allows a class to be re-opened,
and DR-01 measured 234 declaration slots (188 `@class` + 46 `@alias`) over 188 distinct declared names, 176 of them in scope in the
reach corpus. `resolve()`
taking the first is correct for navigation; `renameElement` uses the whole list (§3.7), which is what
`REFACT-08-03` pins.

The scope is `allScope` deliberately — it is the same scope `LuaTypeManagerImpl` resolves a class
name in
([LuaTypeManagerImpl.kt:322](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt)),
so navigation and type resolution cannot disagree about which declarations exist.

### 3.5 `isReferenceTo` and the searcher

```
isReferenceTo(target) = target.text == holder.text && LuaCatsTypeDeclarations.isDeclarationLeaf(target)
```

**O(1), and that is a requirement rather than an optimisation.** `isReferenceTo` is called once per
candidate use holder inside the searcher's inner loop; re-resolving through the index there would
make the search quadratic in the size of the project. The test is sound because a LuaCATS type name
is **project-global and unscoped** — `LuaTypeManagerImpl` resolves it over `allScope` with no
containing-scope narrowing — so any declaration slot spelling the same name *is* the target's peer.
Measured at scale (`DR-02` P15, over the 195-file DR-01 tree): `parser.object` →
`references=324 files=45 elapsedMs=1457`, matching DR-01's independent census of 324 uses.

```
processQuery(parameters, consumer):
  target := parameters.elementToSearch
  if not isDeclarationLeaf(target) -> return
  name := target.text ; return if empty
  for file in candidateFiles(target, name, parameters.effectiveSearchScope):
      ProgressManager.checkCanceled()
      for holder in (NamedType | TypeParam | GenericType children of file):
          ProgressManager.checkCanceled()
          if holder.text != name -> continue
          reference := holder.reference ; continue if null
          if reference.isReferenceTo(target) and not consumer.process(reference) -> return

candidateFiles(target, name, scope) =
  GlobalSearchScope -> CacheManager.getInstance(target.project)
                          .getFilesWithWord(name, UsageSearchContext.IN_COMMENTS, scope, true)
  LocalSearchScope  -> scope.scope.mapNotNull { it.containingFile }.distinct()
  otherwise         -> emptyList()
```

**`IN_COMMENTS`, not `IN_CODE`**, and the index really does key a dotted name as one word.
`LuaFindUsagesProvider.getWordsScanner` builds a `DefaultWordsScanner` over `LuaSyntax.CommentTokens`
([LuaFindUsagesProvider.kt:22-28](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaFindUsagesProvider.kt)),
so a type name inside a `---@` comment lands in the comment context. Measured over the DR-01 tree:

| word | `getFilesWithWord` `IN_COMMENTS` | `IN_CODE` |
| :-- | ---: | ---: |
| `parser.object` | 46 | 2 |
| `uri` | 46 | 118 |
| `vm.node` | 17 | 41 |
| `scope` | 6 | 14 |

Both loops open with `ProgressManager.checkCanceled()` because both can load PSI: the outer loop
materialises a `PsiFile` per candidate and the inner one expands a `LuaCatsLazyCommentImpl` chameleon
per file — the parse cost `LuaCatsParamRenamer`'s KDoc describes.

### 3.6 `canProcessElement` and `substituteElementToRename`

```
canProcessElement(element) =
    isDeclarationLeaf(element) || useHolderOf(element) != null || useLeafOf(element) != null

substituteElementToRename(element, editor):
  1. if isDeclarationLeaf(element):
  2.     if element.text in BUILTIN_KEYWORDS -> refuse(refactoring.rename.catsBuiltinType)
  3.     outside := outOfProjectDeclarationFiles(element.text, project)
  4.     if outside.isNotEmpty() -> refuse(refactoring.rename.catsLibraryType, element.text, outside.first())
  5.     return element
  6. holder := if (useLeafOf(element) != null) element        // the caret was on the holder
                else useHolderOf(element)                      // the caret was on the NAME leaf
                ; if holder == null -> return null
  7. resolved := (holder.reference as? LuaCatsTypeReference)?.resolve()
  8. return resolved ?: refuse(refactoring.rename.catsUnresolvedType)
```

Step 6 is written as a two-branch `if` because `canProcessElement` admits **both** a NAME leaf and
its holder: `useLeafOf` is non-null only for a holder, `useHolderOf` only for a leaf, and exactly one
of the two answers for any element that reached this point. Neither call is nullable-dereferenced,
and both exclusions of §3.4 — the declaration slot and the shadowed name — are already inside them.

Steps 3-4 are `REFACT-08-16` and §3.11 is their rule; they run **before** the dialog, so a refusal
costs no write and no user input.

Step 2 is `REFACT-08-07`. `builtinType` is a fixed keyword alternative tried **before** `namedType`
inside `simpleType` (`luacats.bnf:205-207`), so every use of a `---@class table` parses as
`LuaCatsBuiltinType` and no holder exists for a provider to match. Measured (`DR-02` P9):
`builtinTypes=[table] namedTypes=[] references=0`. Without step 2 the rename proceeds and moves the
declaration alone — F2's half-apply.

Step 8 covers the caret on a parameterized class head, where `LuaCatsTypeNameIndex` keys the tag
under `Box<T>` and `resolve()` for `Box` is null. Measured (`DR-02` P16): with the shipped code the
platform finds no target at all and `renameElementAtCaret` reports `element not found in file`, so
the file is byte-identical; the message of step 8 is reached only when a use caret's name has no
declaration anywhere.

Refusal is `CommonRefactoringUtil.showErrorHint` plus `null`, the idiom
`LuaRenameProcessor.refuse` and `LuaLabelRenameProcessor.substituteElementToRename` both use.
Headlessly it throws `CommonRefactoringUtil.RefactoringErrorHintException` rather than painting a
balloon, which is what the test cases assert on.

### 3.7 `findReferences` and `renameElement`

```
findReferences(element, searchScope, _) = ReferencesSearch.search(element, searchScope).findAll()

renameElement(element, newName, usages, listener):
  1. declarations := declarationLeaves(element.text, project, projectScope(project))   // §3.11
  2. usageRewrites := usages.mapNotNull { usage ->
         ProgressManager.checkCanceled()
         (usage.reference ?: usage.element?.reference)?.let { { it.handleElementRename(newName) } }
     }
  3. declarationRewrites := declarations.mapNotNull { leaf ->
         (leaf.node as? LeafElement)?.let { { it.replaceWithText(newName) } }
     }
  4. ProgressManager.getInstance().executeNonCancelableSection {
         usageRewrites.forEach { it() }
         declarationRewrites.forEach { it() }
     }
  5. listener?.elementRenamed(element)

LuaCatsTypeReference.handleElementRename(newName):
     leaf := useLeafOf(element) ; if leaf == null -> return element      // nothing to write
     node := leaf.node as? LeafElement ; if node == null -> return element
     node.replaceWithText(newName)
     return element
```

Every property below has a reason:

- **Step 1 runs before any write**, so the declaration set is read from the pre-rename text. Reading
  it afterwards would find nothing, since the name has changed.
- **Step 1's scope is `projectScope`, not `allScope`** — §3.11. `substituteElementToRename` step 4
  has already refused the case where the two differ, so at this point the two sets are equal; the
  narrower scope is what makes that an invariant of the code rather than of the caller.
- **`executeNonCancelableSection` is an instance method** — `ProgressManager.getInstance()
  .executeNonCancelableSection(Runnable)` (`ProgressManager.java:155`), the form
  `LuaRenameProcessor.kt:268` already uses. Written as a static it does not compile.
- **Every rewrite is resolved before the first one is applied** (steps 2-3 build closures; step 4
  applies them), and the whole application is one non-cancelable section. This is REFACT-01 design
  §3.3's rule verbatim, for its reason: a cancellation between the usage writes and the declaration
  writes is a silent half-apply, which is BUG-457 in another costume. The one
  `ProgressManager.checkCanceled()` is in step 2, which writes nothing.
- **Usages before declarations**, matching `RenameUtilBase.doRenameGenericNamedElement`'s own order,
  so no usage's reference is invalidated by a declaration edit.
- **`handleElementRename` returns `element` unwritten rather than throwing when `useLeafOf` is
  null.** It cannot be `!!` (the contract forbids it) and it must not throw: the platform calls it
  once per usage, and one stale usage — a holder invalidated by an earlier write in the same pass —
  would abort a rename already half applied. The reachable case is a `LuaCatsGenericType` whose
  `firstChildNode` is not a `NAME`; §3.5's `isReferenceTo` keeps such a usage out of the set in
  practice, and this branch is what makes that a property of the code rather than of the caller.
- **`LeafElement.replaceWithText` is the rewrite**, the idiom `LuaCatsParamRenamer` already ships and
  `REFACT-01-00-DR-06` validated: it interns text and calls `replaceChild`
  (`LeafElement.java:137-141`), neither parsing nor validating, so a new name the input validator
  accepted cannot be rejected here.

`renameElement` runs inside the platform's own write action (`BaseRefactoringProcessor` documents it
as "called in a command, on EDT, inside a Write Action"), so it opens no `WriteCommandAction`.

### 3.8 New-name validation

```
isInputValid(newName, element, context) =
    newName !in BUILTIN_KEYWORDS && CATS_NAME.matches(newName)

CATS_NAME = ^(?:[A-Za-z_][A-Za-z0-9_.*\-]*|[0-9]+[A-Za-z0-9_.*\-]+)$
```

The regex is `luacats.flex:70-73` transcribed:
`NAME = ({NAME_LEADING}{NAME_TRAILING}*)|({DIGIT}+{NAME_TRAILING}+)` with
`NAME_LEADING = letter|_|unicode-letter` and `NAME_TRAILING = letter|digit|_|unicode-letter|.|-|*`.
The non-capturing group and the anchors outside the alternation are load-bearing: written
`^(A)|(B)$` the alternation binds looser than the anchors and the pattern means `(^A)|(B$)`.

The builtin-keyword clause is the **converse** of §3.6 step 2 and is separately necessary: renaming
some class to `table` would make every future use of it parse as `LuaCatsBuiltinType`, silently
unbinding the type. Measured with the clause deleted (`DR-02` P6 under mutation E):
`isValidName('table')=true`.

Unicode letters are accepted by the flex rule and rejected by this regex's ASCII classes. That is a
deliberate, recorded narrowing — `LuaNamesValidator` makes the same choice for Lua identifiers
([LuaNamesValidator.kt:24](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/LuaNamesValidator.kt))
— and `risks-and-gaps.md` Gap 2.6 owns it.

### 3.9 The non-code search is disabled for this element

```
getElementToSearchInStringsAndComments(element) = null
getQualifiedNameAfterRename(element, newName, nonJava) = newName
```

`RenameDialog.createCheckboxes` adds "Search in comments and strings" unconditionally, and
`RenameUtil.processUsages` guards both non-code branches with `searchForInComments != null`
(`RenameUtil.java:147, 157`) — so returning null disables that route rather than running it against
garbage. That is the correct answer twice over here: the base hook would hand
`ElementDescriptionUtil` a bare `LeafPsiElement` and end at its debug string (REFACT-01 design §2.9),
**and** a text pass over comments would rewrite exactly the prose mentions DR-01 measured and this
feature deliberately excludes (`LuaCatsDescription`, 50 occurrences). `getQualifiedNameAfterRename`
returns `newName` for REFACT-01's reason: the base hook returns null for a non-`PsiNamedElement` and
the null reaches `document.replaceString` as replacement text (`RenameUtil.java:227`; `:226` is the
`LOG.error` above it).

### 3.10 Conflicts

```
findCollisions(element, newName, allRenames, result):
  if not isDeclarationLeaf(element) -> return
  for other in declarationLeaves(newName, project, allScope(project)):
      result += LuaRenameCollisionUsageInfo(other, element, message(refactoring.rename.conflict.catsTypeExists, newName))
```

The anchor is the **rival declaration**, not a usage — the element the user must look at, following
`LuaLabelConflictDetector`'s stated rule
([LuaLabelConflictDetector.kt:25-30](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaLabelConflictDetector.kt)).
Without this, two type declarations merge into one: `LuaTypeManagerImpl.materializeClass` merges the
members of every tag sharing a name, so the user silently gets one type with the union of two
members' sets. Measured (`DR-02` P12):
`CONFLICT ConflictsInTestsException: A LuaCATS type named 'Gadget' is already declared in this project`.

Runs inside `BaseRefactoringProcessor`'s background read action, never the EDT — the same placement
`LuaRenameProcessor.findCollisions` documents.

### 3.11 The resolution scope is not the write scope

```
outOfProjectDeclarationFiles(name, project):
  projectScope := GlobalSearchScope.projectScope(project)
  return declarationLeaves(name, project, GlobalSearchScope.allScope(project))
             .mapNotNull { it.containingFile?.virtualFile }
             .filterNot { projectScope.contains(it) }
             .map { it.presentableUrl }
             .distinct()
```

**The rule.** Resolution (§3.4), `isReferenceTo` (§3.5) and conflict detection (§3.10) read
`GlobalSearchScope.allScope`. The **rewrite** (§3.7 step 1) writes only
`GlobalSearchScope.projectScope`. Where the two sets differ for the name being renamed,
`substituteElementToRename` step 4 **refuses** — it does not narrow.

**Why the two must be different scopes.** `allScope` includes libraries, and Lunar attaches four
`AdditionalLibraryRootsProvider`s (`plugin.xml:528-532`): `PlatformLibraryProvider`,
`LuaLibraryProvider`, `LuaRocksLibraryProvider` and `LuaDefinitionLibraryProvider`. The bundled
runtime stubs those attach declare **non-builtin** class names, so §3.6 step 2's builtin refusal does
not cover them. Reproduce the list:

```bash
grep -rhoE '^\s*---+\s*@class\s+[^ :<]+' src/main/resources --include=*.lua | sed -E 's/.*@class\s+//' | sort -u
```

It prints, among others, `File`, `io`, `os`, `math`, `debug`, `package`, `coroutine`, `utf8`, `bit`,
`bit32`, `struct`, `cjson`, `cmsgpack`, `redis`, `server`, `void`, `self`, `_G` and ~30 `Rockspec*`
names — none of them a `BUILTIN_KEYWORDS` member.

**Why the refusal must be ours and cannot be left to the platform.**
`BaseRefactoringProcessor.ensureElementsWritable` gates writability on the elements of `usages[]`
union `getElementsToWrite(descriptor)` (`BaseRefactoringProcessor.java:424-438`), and
`getElementsToWrite` returns `descriptor.getElements()` (`:842-844`), which for a rename is
`RenameViewDescriptor`'s `myAllRenames.keySet()` (`RenameViewDescriptor.java:26-27`). A declaration
leaf discovered **inside** `renameElement` is in neither set, so the platform's gate passes and the
write is attempted anyway.

**Measured, in each state** (`REFACT-08-00-DR-03`, fixture: a project `---@class File` with two use
sites, against the plugin's own bundled `runtime/standard/lua-5.4/io.lua`):

| State | Outcome |
| :--- | :--- |
| the rule, as specified above | `REFUSED` carrying the offending file; `typesUnchanged=true usesUnchanged=true` |
| **mutation R** — refusal deleted, write scope still `projectScope` | `RENAMED`, **no exception at all**: the project's declaration and both uses move, the library's `---@class File` stays, and one type is silently split in two |
| **mutation R2** — refusal deleted **and** write scope widened to `allScope` | `IncorrectOperationException: Cannot modify a read-only file '…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua'` — thrown *after* `uses.lua` was rewritten (`usesUnchanged=false`), leaving a half-applied rename on disk |

Mutation R is the reason the rule is a refusal rather than a scope narrowing: narrowing alone is the
quietest of the three failures, and Risk 1.1 exists to keep quiet failures out.

**Both mutations are reproducible without the feature.** R and R2 were measured against a prototype
that no longer exists, so `REFACT-08-00-DR-04` re-established their mechanism with a probe that needs
none of this design — it resolves `File` through `LuaCatsTypeNameIndex` and writes the leaves
directly:

```
[f1] File all=2 project=1 outOfProject=[…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua]
[f1]   outOfProject writable=false
[f1] R2 write on out-of-project leaf -> IncorrectOperationException: Cannot modify a read-only file
     '…/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua'. ; libraryTextUnchanged=true
[f1] R  write on in-project leaf -> WROTE ; remainingFileDeclarationsInAllScope=1
[f1] R    survivor 'File' in …/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua
```

That is R's silent split (the project leaf writes, no exception, the library's `File` survives) and
R2's read-only failure (the write the widened scope would attempt), each isolated to one write. What
the probe does **not** reproduce is the *ordering* claim — that R2 throws only after `uses.lua` was
already rewritten — which still rests on DR-03's single run; `risks-and-gaps.md` records that
limit.

**What the rule gives up, stated rather than discovered later.** A type name that any attached
library also declares becomes **unrenameable**, including the project's own declaration of it. With
the bundled stubs alone that is the list above — a user's own `---@class File` cannot be renamed. It
also refuses a rename that would legitimately span a rock-provided or definitions-provided
declaration, even where that file is writable on disk: `projectScope` is chosen over an
`isWritable` test deliberately, because writing into a dependency's stub is not a thing a rename of a
project type should do, and because `projectScope` is the same scope the platform's own
`RenameProcessor` searches usages in by default (`RenameProcessor.java:104`) — so the write set and
the platform's usage set agree by construction. The escape hatch for a user who genuinely wants this
is to rename to a name no library declares; the message names the offending file so the choice is
informed.

**Control.** `REFACT-08-00-DR-03` W4/TC-25 renames a project-only `Widget` under the same rule and
reports `staleWidget=0 newGadget=2`, so the refusal is not a blanket one.

## 4. Registration (`src/main/resources/META-INF/plugin.xml`)

One line per extension point, each beside its existing sibling:

```xml
<psi.referenceContributor
        language="Lua"
        implementation="net.internetisalie.lunar.lang.LuaCatsTypeReferenceContributor"/>

<referencesSearch
        implementation="net.internetisalie.lunar.lang.insight.LuaCatsTypeReferenceSearcher"/>

<renamePsiElementProcessor
        implementation="net.internetisalie.lunar.refactoring.rename.LuaCatsTypeRenameProcessor"/>

<renameInputValidator
        implementation="net.internetisalie.lunar.refactoring.rename.LuaCatsTypeNameInputValidator"/>
```

- `psi.referenceContributor` goes after the two existing ones (`plugin.xml:354-359`);
  `language="Lua"` is correct because `LuaCatsElementType` passes `LuaLanguage` to its
  `IElementType` constructor.
- `referencesSearch` goes after `LuaNameReferenceSearcher` (`plugin.xml:382-383`). It has no
  `language` attribute — the EP is application-level and the searcher gates itself.
- `renamePsiElementProcessor` goes after `LuaLabelRenameProcessor` (`plugin.xml:392-395`). Order is
  immaterial (§1.3) but keeping the rename processors adjacent keeps the disjointness visible.
- `renameInputValidator` is the platform EP `com.intellij.renameInputValidator`
  (`RenameInputValidator.java:19`); Lunar registers none today.

No index registration is added and no `getVersion()` is bumped.

## 5. Threading and cost

| Step | Thread | Index / VFS |
| :--- | :--- | :--- |
| `canProcessElement`, `LuaTargetElementEvaluator.getNamedElement`, `LuaCatsTypeNameInputValidator.getPattern` | EDT | none — PSI shape tests only |
| `substituteElementToRename` | EDT | one `LuaCatsTypeNameIndex` read per rename — the same `getContainingFiles(LuaCatsTypeNameIndex.KEY, …)` call `LuaDocumentationTargetProvider.kt:287` and `LuaCatsTypeNavigation.kt:44` already make. §3.11's check reuses that one read and adds only an in-memory `GlobalSearchScope.contains` per leaf |
| `LuaCatsTypeReference.resolve` (Ctrl+Click) | EDT read action | one index read per action |
| `findReferences` / the searcher / `findCollisions` | `BaseRefactoringProcessor`'s background read action | word index narrowing, then one parse per candidate file |
| `renameElement` | EDT, inside the platform's write action | none |

No component holds a `Project`, `Editor`, `PsiFile` or `VirtualFile` in a field;
`LuaCatsTypeDeclarations` is a stateless object and `LuaCatsTypeReference` holds only the platform's
own `myElement` for the life of one reference.

Measured cost of the heaviest step (`DR-02` P15, 195 real annotated files, busiest name):
`references=324 files=45 elapsedMs=1457`, off the EDT.

## 6. Messages (`src/main/resources/net/internetisalie/lunar/LuaBundle.properties`)

```properties
refactoring.rename.catsBuiltinType=''{0}'' is a LuaCATS builtin type name, so every use of it is parsed as the builtin and not as a reference to this declaration. Renaming it would move this tag and leave every use bound to the old name.
refactoring.rename.catsLibraryType=''{0}'' is also declared outside this project, in ''{1}''. Renaming it here would leave that declaration on the old name and split one type into two, so the rename is declined.
refactoring.rename.catsUnresolvedType=No ''@class'' or ''@alias'' declaring ''{0}'' was found in this project, so its uses cannot be rewritten.
refactoring.rename.conflict.catsTypeExists=A LuaCATS type named ''{0}'' is already declared in this project; renaming would merge the two.
```

Placed with the other `refactoring.rename.*` keys (`LuaBundle.properties:149-164`). The doubled
apostrophes are `MessageFormat`'s escape, matching every neighbouring entry.

## 7. Alternatives considered

### Alternative A — stub the LuaCATS comment

`.agents/AGENTS.md` records stubbing the comment as "the 'correct' but heavy fix" that "would also
unlock Find Usages/Rename on types à la EmmyLua's `PsiNameIdentifierOwner` doc tags". **It is not on
this feature's critical path, and this design does not take it.**

What a rename needs is *declaration lookup* and *use-site enumeration*. `LuaCatsTypeNameIndex`
already gives the first (measured: `REFACT-01-00-DR-04` P2), and the platform's word index already
gives the second (measured: `DR-02` P15, 45 files reached for `parser.object` with no new index).
Stubbing buys neither; it buys fast cross-file lookup *without de-stubbing*, which nothing here
needs, at the cost of a lazy-parseable stubbed element type and pressure on the platform's
`IElementType` registry size limit — the limit `.agents/AGENTS.md` records as a live hazard in this
repo ("instantiating per file/parse exhausts it"). The Find-Usages-and-Rename half it was credited
with is what §2.2-§2.6 deliver without it.

### Alternative B — rewrite through `LuaCatsTypeNameIndex`

Prototyped and rejected by `REFACT-01-00-DR-04` P4: the index maps declaration sites, so the rewrite
moves the `@class` and leaves 68 of 68 uses stale, with `LuaTypeManager.resolveType` and
`LuaCatsDeclaredType.isType` both flipping to false and nothing reported (P9). It is not a cheaper
route to the same result; it is a different, wrong result.

### Alternative C — declare the reference on the generated PSI via a `lua.bnf`/`luacats.bnf` mixin

Grammar-Kit's `mixin=`/`implements=` would put `getReference()` on
`LuaCatsNamedTypeImpl` directly and make §2.2 unnecessary. Rejected: it requires regenerating
`src/main/gen` for the LuaCATS parser and PSI, which this feature otherwise does not touch, and it
would place the reference on three generated classes instead of one hand-written base class —
three places for the next reference contributor to have to find. §2.2 is four lines and mirrors an
override the Lua side already has.

### Alternative D — a `PsiNameIdentifierOwner` on `LuaCatsClassTag` / `LuaCatsAliasTag`

`REFACT-01-00-DR-04`'s sizing table proposed this. Rejected for the same regeneration cost as
Alternative C, and because it is not needed: `RenameUtil` and `PsiElementRenameHandler` accept a
non-`PsiNamedElement` element whenever a rename processor claims it
(`PsiElementRenameHandler.getRenameErrorMessage`: `!hasRenameProcessor && … && !(element instanceof
PsiNamedElement)`), which is precisely how `LuaRenameProcessor` already renames bare Lua IDENTIFIER
leaves. The one thing a `PsiNameIdentifierOwner` would additionally unlock is the platform's
in-place rename template, which is TBD-1.

## 8. Open Questions

None — every question this design opened is either answered above from an executed measurement, or carried as a numbered gap with a de-risking task in `risks-and-gaps.md`.
