---
id: "MAINT-27-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-27"
folders:
  - "[[features/maint/27-luacats-doc-correctness/requirements|requirements]]"
---

# Technical Design: MAINT-27 — LuaCATS Doc & Lexer Correctness

## 1. Architecture Overview

### Current State

Ten defects, coalesced from `docs/review.md` (re-verified 2026-07-17), across five
`luacats/**` sites plus one generated-lexer regeneration. All are corrective edits to
existing components — no new PSI types, indexes, or extension points.

| Finding | File (verified) | Line(s) | Defect |
| :--- | :--- | :--- | :--- |
| #19 | `luacats/lang/lexer/luacats.flex` | 72, 77 | `CODE=` / `STRINGD=` negated char classes match `\n`; an unclosed backtick/quote consumes across newlines |
| #66 | `luacats/lang/lexer/luacats.flex` | 68, 70–71 | `HIGH_ASCII=[\x80-\xff]` under `%unicode` — names above U+00FF (CJK/Cyrillic) abort tag lexing |
| §3 dead | `luacats/lang/lexer/luacats.flex` | 80, 187–192 | `COMMENT_END` state declared, no rules; `TAG_OVERLOAD` block unreachable (`@overload` routes to `TAG_TYPE`, line 109) |
| #35 | `luacats/lang/doc/LuaCatsDocumentationRenderer.kt` | 112, 135, 202, 214, 246, 266, 282, 367, 386, 434 | Raw type text passed to `buildTypeLink` — `table<string, integer>`, `fun(x): boolean` break popup HTML and the `psi_element://` href |
| #57 | `LuaCatsDocumentationRenderer.kt` | 203–207 | `---@type T` local renders as `class T` (wrong keyword) |
| #36 | `LuaCatsDocumentationRenderer.kt` | 405–407, 502–506 | `lookupParentComment` keys on `parentTypes.text` (whole list `"A, B"`); bare `--- @class Parent` (no host decl, absent from `LuaClassNameIndex`) never found |
| #67 | `LuaCatsDocumentationRenderer.kt` | 405–425 | Inheritance rendering is single-level — grandparent fields never appear |
| #37 | `LuaCatsDocumentationRenderer.kt` | 313–316 | Alias `Values:` section gated on `enumTagList`; the union-alias form (`---@alias X` + `---\| "v"`) has no `@enum` → never renders |
| #38 | `luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt` | 27–129 (all getters) | Getters use recursive `PsiTreeUtil.findChildrenOfType`, diverging from the generated direct-children contract |
| #72 | `luacats/lang/syntax/LuaCatsAnnotator.kt` | 23–31, 56–61 | `LuaCatsNamedType` under an `ArgType`-of-`classTag`/`aliasTag` special case is unreachable per the bnf; would mis-highlight an alias target if it fired |

The `#38` root cause, grounded in the grammar: `luacats.bnf` line 38 defines
`comment ::= commentLine*` and line 39 `private commentLine ::= DASHES (anyTag | typeOption | description)?`.
Because `commentLine` is a **private** rule it produces no PSI node, so every tag and every
top-level `description` is a **direct child** of the `comment` node. The lazy element IS that
`comment` node: `LuaBaseElements.kt:125-135` (`LUACATS_COMMENT.parseContents`) returns
`root.firstChildNode`, and `root ::= comment` (bnf line 37). The generated
`LuaCatsCommentImpl.getDescriptionList()` (`src/main/gen/.../impl/LuaCatsCommentImpl.java:62-64`)
correctly uses `PsiTreeUtil.getChildrenOfTypeAsList(this, …)` (direct children only). The
hand-written `LuaCatsLazyCommentImpl.kt` overrides every getter with recursive
`findChildrenOfType`, so descriptions nested inside each tag (`classTag ::= … description?`,
bnf line 90; every `*Tag` has a trailing `description?`) leak into `getDescriptionList()`,
duplicating text in `LuaCatsSummary`/`collectDescriptionText` and skewing `isDocCommentEmpty`.

### Prior Art in This Repo

All work extends existing components; nothing is duplicated:

- **`LuaCatsDocumentationRenderer`** (`luacats/lang/doc/LuaCatsDocumentationRenderer.kt`) — the
  sole doc renderer; **edited in place** (six of the ten findings). No parallel renderer exists
  (`grep -rl "class.*Documentation.*Renderer" src/main/kotlin` → only this + `LuaDocumentationRenderer`).
- **`buildTypeLink(typeName: String)`** (`lang/doc/LuaDocumentationRenderer.kt:206-213`) — the
  shared type-linking helper; **reused**, called with an already-escaped/predicate-gated string
  (#35). It internally uses `DocumentationManagerUtil.createHyperlink` and `codeFragment`
  (`LuaDocumentationRenderer.kt:196-204`, which already wraps text via `HtmlChunk.text` — HTML-safe).
- **`LuaCatsTypeNameIndex`** (`lang/indexing/LuaCatsTypeNameIndex.kt:37-81`, `KEY` at line 79) and
  **`LuaCatsTypeNavigation`** (`lang/navigation/LuaCatsTypeNavigation.kt:26-56`) — the file-based
  index + resolver that already reach **bare** `@class`/`@alias` tags (no host decl). **Reused** as
  the #36 fallback for parent-comment lookup. `lookupParentComment` currently uses only
  `StubIndex`/`LuaClassNameIndex` (`LuaClassNameIndex.KEY`, line 504), which misses bare classes.
- **`LuaCatsLazyCommentImpl`** (`luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt`) — hand-written
  lazy comment; **edited in place** (#38). The generated `LuaCatsCommentImpl.java` is the
  correct-contract reference to mirror.
- **`LuaCatsAnnotator`** (`luacats/lang/syntax/LuaCatsAnnotator.kt`) — the sole LuaCATS annotator;
  **edited in place** (#72 dead-branch removal).
- **`luacats.flex`** (`luacats/lang/lexer/luacats.flex`) — the LuaCATS lexer grammar; **edited**
  then regenerated via `.claude/skills/generate-parser/scripts/generate.sh` (#19, #66, §3 dead code).

No new files are created. No `plugin.xml` change is required (see §7).

### Target State

Each defect is fixed at its verified site. The lexer edits are regenerated headlessly (the
pipeline is proven by BUG-361, commit `0566cfbc`, which edited `lua.flex`/`lua.bnf` successfully).
The renderer gains an escaping helper and a chain-walking fields collector. The lazy comment's
getters switch to the direct-children idiom. The annotator's dead branches are removed after a
fixture proves current behavior.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.luacats.lang.lexer.luacats.flex` (regenerated → `_LuaCatsLexer.java`)
- **Responsibility**: tokenize LuaCATS comment text.
- **Threading**: lexer runs inside the platform's parse (read action); no direct threading concern.
- **Collaborators**: `LuaCatsLexer.kt` wraps the generated `_LuaCatsLexer`; consumed by
  `LuaCatsParser` via `LUACATS_COMMENT.parseContents` (`LuaBaseElements.kt:128-133`).
- **Edits** (macro/state definitions, §3.1):
  - `CODE={BACKTICK}[^`]+{BACKTICK}` → `CODE={BACKTICK}[^`\r\n]+{BACKTICK}` (line 72, #19).
  - `STRINGD={QUOTE}[^\"]*{QUOTE}` → `STRINGD={QUOTE}[^\"\r\n]*{QUOTE}` (line 77, #19).
  - `HIGH_ASCII=[\x80-\xff]` → `UNICODE_LETTER=[:letter:]`; `NAME_LEADING`/`NAME_TRAILING`
    (lines 70–71) substitute `{UNICODE_LETTER}` for `{HIGH_ASCII}` (#66).
  - Remove `COMMENT_END` from the `%state` list (line 80) and delete the entire `<TAG_OVERLOAD>`
    block (lines 187–192) (§3 dead code). `@overload` already routes to `TAG_TYPE` (line 109),
    so no `yybegin` targets `TAG_OVERLOAD`.

### 2.2 `net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationRenderer` (edited)
- **Responsibility**: render Quick Documentation HTML for LuaCATS-annotated elements.
- **Threading**: called inside `runReadAction` on a background documentation thread (existing
  contract — see `LuaCatsDocumentationRendererTest` which wraps calls in `runReadAction`).
- **Collaborators**: `buildTypeLink`, `codeFragment`, `LuaCatsTypeNameIndex`,
  `LuaCatsTypeNavigation`, `StubIndex`/`LuaClassNameIndex`, `LuaCatsComment` PSI.
- **New private helpers**:
  ```kotlin
  // §3.2 — escape + gate hyperlinking to simple identifiers
  private fun renderTypeText(rawType: String): String

  // §3.3 — the "simple identifier" predicate
  private fun isSimpleIdentifier(text: String): Boolean

  // §3.4 — resolve a parent @class comment by individual name (stub index → LuaCatsTypeNameIndex)
  private fun resolveClassComment(project: Project, className: String): LuaCatsComment?

  // §3.5 — walk the inheritance chain collecting field tags with a cycle guard
  private fun collectInheritedFieldTags(
      project: Project,
      classTag: LuaCatsClassTag,
  ): List<LuaCatsFieldTag>
  ```
- **Edited methods**: every `buildTypeLink(x.text)` call site listed in §1 becomes
  `buildTypeLink(...)` fed a value produced by `renderTypeText` **or** a direct escaped-code
  render (§3.2 decides which). `buildVariableSignature` (#57) splits the `class`/`local :`
  keyword decision. `buildFieldsSection`/`lookupParentComment` are replaced by the §3.4/§3.5
  helpers. `buildSectionsBlock`'s `LuaCatsAliasTag` arm (line 313) gates on
  `comment.typeOptionList.isNotEmpty()` instead of `enumTagList` (#37).

### 2.3 `net.internetisalie.lunar.luacats.lang.psi.impl.LuaCatsLazyCommentImpl` (edited)
- **Responsibility**: lazy-parseable `comment` PSI node exposing typed tag lists.
- **Threading**: PSI read.
- **Collaborators**: mirrors generated `LuaCatsCommentImpl.java`.
- **Edit**: every `get*TagList()` / `getDescriptionList()` / `getTypeOptionList()` body changes
  from `PsiTreeUtil.findChildrenOfType(this, X::class.java).toList()` to
  `PsiTreeUtil.getChildrenOfTypeAsList(this, X::class.java)` (direct children — §3.6). This is a
  pure body swap on all 26 getters; no signatures change. (Caching decision: §3.6 — no cache; see
  Alternatives §9.)

### 2.4 `net.internetisalie.lunar.luacats.lang.syntax.LuaCatsAnnotator` (edited)
- **Responsibility**: semantic highlighting of LuaCATS PSI.
- **Threading**: annotator runs on the highlighting thread (read action) — platform-managed.
- **Edit**: after the §3.7 fixture proves current behavior, remove the `LuaCatsNamedType`
  parent-of-`ArgType`-of-`classTag`/`aliasTag` special case (lines 24–30 collapse to a single
  `highlight(holder, element, LuaCatsHighlight.TYPE)`) and delete the `LuaCatsElementTypes.NAME`
  else-branch (lines 56–61), which is unreachable for the same grammar reason.

## 3. Algorithms

### 3.1 Lexer containment (#19, #66) + dead-state removal (§3)
- **Input → Output**: LuaCATS comment char stream → `IElementType` token stream.
- **Steps** (macro edits only; JFlex mechanics unchanged):
  1. `CODE` and `STRINGD` negated classes add `\r` and `\n` to their exclusion set, so an
     unclosed backtick/quote no longer matches past end-of-line; the `.` / `{NL}` rules then
     recover the line (`{NL}` at flex line 88 already resets to `YYINITIAL`).
  2. Replace `HIGH_ASCII=[\x80-\xff]` with `UNICODE_LETTER=[:letter:]` (a JFlex predefined class,
     Unicode-aware under `%unicode`; grounded in `intellij-community`
     `xml/xml-parser/.../_XmlLexer.flex:70` `ALPHA=[:letter:]`). Update `NAME_LEADING` and
     `NAME_TRAILING` (flex lines 70–71) to reference `{UNICODE_LETTER}` in place of `{HIGH_ASCII}`.
  3. Delete `COMMENT_END` from the `%state` declaration (line 80) and the entire `<TAG_OVERLOAD>`
     block (lines 187–192).
- **Rules / edge handling**: `[:letter:]` includes ASCII letters, so `ALPHA_LOWER`/`ALPHA_UPPER`
  become redundant in the NAME macros but are left untouched (still referenced by `DIAGNOSTIC`);
  only the `HIGH_ASCII` alternative is replaced. No token or PSI element type is added/removed, so
  `luacats.bnf` and `LuaCatsTokenTypes.kt`/`LuaCatsElementType.kt` need no edit.
- **Regen**: run `.claude/skills/generate-parser/scripts/generate.sh` (see §5 data-flow and the
  implementation plan preconditions). The script (verified `generate.sh:89-98`) also regenerates
  `luacats.bnf` (step 4b) and re-runs JFlex on `luacats.flex` (step 5b); the bnf regen must be a
  **no-op** on our flex-only change — see the Alternatives note §9 and DR-01.
- Also remove `TAG_OVERLOAD` from the `%state` declaration list (luacats.flex:82) when
  deleting its rule block — leaving it is a harmless orphaned constant, but removing it keeps
  the state list honest.


### 3.2 Type-text HTML escaping + hyperlink gating (#35, #57)
- **Input → Output**: raw tag type text (`String`, e.g. `table<string, integer>`, `fun(x): boolean`,
  `Player`) → HTML-safe fragment (`String`).
- **Steps** (`renderTypeText`):
  1. `val escaped = com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(rawType)`.
  2. If `isSimpleIdentifier(rawType)` (§3.3) → return `buildTypeLink(rawType)` (existing helper;
     it links a primitive/type name and its `createHyperlink` builds a valid `psi_element://` href
     because the name has no HTML-special or structural chars).
  3. Else → return `codeFragment(LuaCatsHighlight.TYPE, rawType)`. `codeFragment`
     (`LuaDocumentationRenderer.kt:196-204`) already wraps its argument in `HtmlChunk.text(...)`,
     which HTML-escapes; so the structured type is rendered as escaped, non-linked code. (Step 1's
     `escaped` is used only for the plain-text-attribute fallback paths in §1 sites that append
     type text directly, e.g. the `(<type>)` spans at renderer lines 367/386/434 — those append
     `renderTypeText(type)` instead of `buildTypeLink(type)`.)
- **Rules / edge handling**: empty string → `codeFragment` of `""` (harmless empty span).
  `renderTypeText` never emits a raw unescaped angle bracket, so the popup HTML and href stay valid.
- **#57 keyword split** (`buildVariableSignature`, renderer lines 199–219): the current `else if
  (classTag != null || typeTag != null)` branch collapses both to `class`. Split it:
  - `classTag != null` → keep `class <name>` (+ parent chain via §3.4/§3.5).
  - `classTag == null && typeTag != null` → render
    `local <varName> : <renderTypeText(typeTag.argType.text)>`, where `varName =
    element.attNameList.firstOrNull()?.text ?: "variable"` and the keyword uses
    `codeFragment(LuaHighlight.KEYWORD, "local")`.

### 3.3 Simple-identifier predicate (#35)
- **Input → Output**: `String` → `Boolean`.
- **Definition**: `isSimpleIdentifier(text)` returns true iff `text` matches the regex
  `^[\p{L}_][\p{L}\p{N}_.]*$` — i.e. a Lua/LuaCATS dotted name: a leading Unicode letter or
  underscore, then Unicode letters, digits, underscores, or dots (dotted module names like
  `http.Client` are still simple). Any occurrence of `<`, `>`, `(`, `)`, `{`, `}`, `[`, `]`, `|`,
  `,`, `:`, whitespace, `"`, `'`, or `` ` `` makes it non-simple (structured type → escaped code,
  not a link). Matches the linkable subset `buildTypeLink` can form a valid href from.
- **Rules / edge handling**: empty string → false (regex requires ≥1 leading char). This predicate
  is the sole authority for "what is linkable"; no other heuristic is introduced.

### 3.4 Parent `@class` comment resolution (#36)
- **Input → Output**: (`Project`, single parent class name `String`) → `LuaCatsComment?`.
- **Steps** (`resolveClassComment`):
  1. `val stubDecl = StubIndex.getElements(LuaClassNameIndex.KEY, className,
     project, GlobalSearchScope.allScope(project), LuaLocalVarDecl::class.java).firstOrNull()`.
     If non-null, return `stubDecl.catsComment` (host-decl form; unchanged from today).
  2. Else (bare `--- @class Parent`, no host decl) fall back to `LuaCatsTypeNameIndex`:
     for each `virtualFile` in
     `FileBasedIndex.getInstance().getContainingFiles(LuaCatsTypeNameIndex.KEY, className, scope)`,
     load `PsiManager.getInstance(project).findFile(virtualFile) as? LuaFile`, then for each
     `tag` in `PsiTreeUtil.findChildrenOfType(luaFile, LuaCatsClassTag::class.java)` whose
     `PsiTreeUtil.getChildOfType(tag, LuaCatsArgType::class.java)?.text?.trim() == className`,
     return `PsiTreeUtil.getParentOfType(tag, LuaCatsComment::class.java)`. (This mirrors
     `LuaCatsTypeNavigation.processElements`, `LuaCatsTypeNavigation.kt:41-47`.)
  3. If neither index yields a match, return `null`.
- **Rules / edge handling**: `className` is a single, trimmed name — the **caller** iterates
  `classTag.parentTypes?.argTypeList` (each is a `LuaCatsArgType`, verified
  `LuaCatsParentTypes.getArgTypeList()` → `getChildrenOfTypeAsList`,
  `src/main/gen/.../impl/LuaCatsParentTypesImpl.java:32-34`) and calls `resolveClassComment` per
  name; it never passes the whole `"A, B"` list text. Generic parents (`Base<T>`) resolve by their
  simple name via `isSimpleIdentifier`-style trimming: take the substring before the first `<`.

### 3.5 Inheritance-chain field collection (#67, #36)
- **Input → Output**: (`Project`, the element's `LuaCatsClassTag`) → ordered `List<LuaCatsFieldTag>`
  of inherited fields (direct-class fields are collected separately by the existing
  `comment.fieldTagList`).
- **Steps** (`collectInheritedFieldTags`, breadth-first):
  1. `val visited = mutableSetOf<String>()` seeded with the starting class name
     (`classTag.argType.text.trim()`).
  2. `val queue: ArrayDeque<String>` initialized from the starting `classTag.parentTypes` names
     (via §3.4's per-`ArgType` name extraction, first-`<`-trimmed).
  3. While the queue is non-empty **and** `visited.size <= 64` (depth bound): pop `name`; if
     `name in visited` continue; add to `visited`; `ProgressManager.checkCanceled()`; resolve
     `parentComment = resolveClassComment(project, name)` (§3.4); if non-null, append
     `parentComment.fieldTagList` to the result and enqueue that comment's own
     `classTagList.firstOrNull()?.parentTypes` names (grandparents).
  4. Return the accumulated list (may contain duplicate field names across levels; the renderer
     lists them all under "Inherited Fields:", nearest-ancestor first by BFS order).
- **Rules / edge handling**: the `visited` set (a local, not a field) is the **cycle guard** —
  `@class A : B`, `@class B : A` terminates after visiting {A, B}. The `<= 64` cap is a hard
  depth ceiling against pathological chains. Empty/absent `parentTypes` → empty result → the
  existing `hasInheritedFields` check (renderer line 407) is false and no section renders.
- **Complexity**: O(chain length) index lookups, bounded by 64; each lookup is a `StubIndex` or
  `FileBasedIndex` query (fast, no full-tree scan beyond the resolved parent files).

### 3.6 Direct-children getter contract (#38)
- **Input → Output**: (lazy `comment` node, target `LuaCats*` class) → `List<T>` of **direct**
  children only.
- **Steps**: each getter body becomes
  `return PsiTreeUtil.getChildrenOfTypeAsList(this, X::class.java)`.
- **Rules / edge handling**: because tags and top-level descriptions are direct children of the
  `comment` node (bnf `private commentLine`, §1), this returns exactly the generated
  `LuaCatsCommentImpl` contract. Descriptions nested inside a tag are no longer surfaced by
  `getDescriptionList()`, fixing the duplicate-summary and `isDocCommentEmpty` skew. **No caching**
  is added: the comment is a `LazyParseablePsiElement`; the platform's PSI walk is already fast and
  the result is invalidated by normal PSI change events — a manual cache would need bespoke
  invalidation and risks staleness (see §9). `getChildrenOfTypeAsList` iterates one level of
  siblings (O(children)), which is strictly cheaper than the current recursive walk.

### 3.7 Annotator dead-branch verify-then-drop (#72)
- **Protocol** (order matters — write the fixture FIRST):
  1. Add a highlighting test (`LuaCatsAnnotatorTest`) with fixtures `---@class Foo : Bar` and
     `---@alias Mode Player` that assert the **current** highlight keys the annotator produces for
     the class name / alias target tokens (via `doHighlighting()`, the existing pattern).
  2. Confirm from the grammar (already verified) that `classTag ::= '@class' … <<ArgType typeName>>`
     with `typeName ::= parameterizedName | NAME` (bnf lines 90, 93) — the class name under a
     classTag's `ArgType` is a raw `NAME`/`parameterizedName`, **never** a `LuaCatsNamedType`; and
     `namedType ::= NAME` (bnf line 196) only appears under `type`/`simpleType`, reached by
     `parentTypes`/`@type`/`@param`/alias **target**. So the `LuaCatsNamedType` branch (annotator
     lines 24–30) is unreachable for classTag, and for an alias target it would wrongly key the
     target as `NAME` instead of `TYPE`.
  3. Remove the special case; re-run the fixture; assert highlighting is unchanged (proving the
     branch was dead) or corrected (alias target now `TYPE`). Also remove the unreachable
     `LuaCatsElementTypes.NAME` else-branch (lines 56–61).

## 4. External Data & Parsing

No CLI / network / external-file input. The only externally-shaped input is the LuaCATS comment
text, tokenized by the existing lexer grammar (§3.1); its format is the LuaCATS annotation syntax
already encoded in `luacats.flex` / `luacats.bnf`. No new parse format is introduced.

## 5. Data Flow

### Example 1: `@type` local doc (#35, #57)
`---@type table<string, integer>\nlocal m = {}` → user hovers `m` →
`LuaDocumentationTargetProvider` → `renderDoc(LuaLocalVarDecl)` → `buildVariableSignature`:
`classTag == null`, `typeTag != null` → emits
`local m : ` + `renderTypeText("table<string, integer>")` → not simple → `codeFragment` with
HTML-escaped `table&lt;string, integer&gt;`. Popup HTML is valid; no broken href. (Before:
`class table<string, integer>` with a corrupt link.)

### Example 2: grandparent fields (#67, #36)
`---@class C : B`, `---@class B : A`, `---@class A` with `---@field id integer` on `A` →
`buildFieldsSection` for `C` → `collectInheritedFieldTags(project, cTag)` → BFS visits
`B` (via stub or `LuaCatsTypeNameIndex`), then `A`, collecting `A.id` → "Inherited Fields:" lists
`id`. (Before: only `B`'s fields, and only if `B` had a host decl.)

### Example 3: union-alias values (#37)
`---@alias Mode "r"|"w"` (no `@enum`) → hover the alias tag → `buildSectionsBlock`
`LuaCatsAliasTag` arm gates on `comment.typeOptionList.isNotEmpty()` → renders "Values:" `"r"`,
`"w"`. (Before: gated on `enumTagList`, empty → no values.)

### Example 4: lexer containment (#19)
`---@param x `unclosed backtick\n---@param y number` → the `CODE` rule no longer spans the
newline; line 1 lexes `x` + a bad/text token for the stray backtick, line 2 (`{NL}` → `YYINITIAL`)
lexes `@param y number` cleanly. (Before: the backtick consumed through `y number`.)

## 6. Edge Cases

- **Alias target that is a bare class name** (`---@alias Mode Player`): after #72, `Player` under
  the alias `ArgType` highlights as `TYPE` (via the general `LuaCatsNamedType → TYPE` arm), the
  correct kind.
- **Self-inheriting class** (`---@class A : A`): §3.5 `visited` guard terminates immediately.
- **`@class C : Base<T>`**: §3.4 trims at the first `<`, resolving `Base`.
- **Unicode class name** (`---@class 名前`): after #66, `名前` lexes as a single `NAME`.
- **`---@type` with a simple type** (`---@type Player`): `isSimpleIdentifier("Player")` true →
  `local m : ` + linked `Player`.
- **Empty comment** (`--- \nlocal x`): after #38, `getDescriptionList()` returns only the top-level
  description, so `isDocCommentEmpty` is accurate.

## 7. Integration Points

No `plugin.xml` change. All edited components are already registered:

- `LuaCatsDocumentationRenderer` is invoked through the existing
  `LuaDocumentationTargetProvider` (registered `<platform.backend.documentation.targetProvider>`).
- `LuaCatsAnnotator` is already registered as an `<annotator language="Lua">`.
- `LuaCatsLexer`/`luacats.flex` output feeds the existing `LUACATS_COMMENT` lazy element type.
- `LuaCatsTypeNameIndex` is already a registered `<fileBasedIndex>`.

Verification that no registration is needed:

```text
# no new EP, index, or annotator is added — grep confirms all four are pre-registered
grep -n "LuaCatsAnnotator\|LuaCatsTypeNameIndex\|DocumentationTargetProvider" src/main/resources/META-INF/*.xml
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-27-01 | M | §2.1, §3.1 |
| MAINT-27-02 | M | §2.2, §3.2, §3.3 |
| MAINT-27-03 | S | §2.2, §3.4, §3.5 |
| MAINT-27-04 | S | §2.2 (§3 alias arm), §5 Ex.3 |
| MAINT-27-05 | S | §2.3, §3.6 |
| MAINT-27-06 | C | §2.4, §3.7 |

## 9. Alternatives Considered

- **#38 caching (`CachedValuesManager` vs lazy backing field vs none)**: rejected both caches.
  A `CachedValuesManager` cache keyed on the comment PSI needs a dependency/modification tracker;
  `getChildrenOfTypeAsList` is already O(direct children) and the comment is small, so a cache adds
  invalidation risk (stale lists after re-parse of the lazy element) for no measurable win. A lazy
  backing field on the mutable `LazyParseablePsiElement` would survive re-parse incorrectly. Chosen:
  **no cache**, pure direct-children read (§3.6).
- **#35 escaping (escape-everywhere vs link-only-simple)**: escaping the raw type and *still*
  hyperlinking structured types would produce invalid `psi_element://` hrefs (they contain `<`, `(`).
  Chosen: escape everywhere, **link only simple identifiers** (§3.2/§3.3).
- **#66 (`[:letter:]` vs explicit `\p{L}` vs widening `\x80-\xff`)**: `[:letter:]` is the JFlex
  predefined, Unicode-aware class used across `intellij-community` lexers under `%unicode`; widening
  the byte range cannot cover astral planes. Chosen: `[:letter:]`.
- **luacats.bnf regen**: `generate.sh` regenerates the bnf unconditionally (step 4b). Since #19/#66/§3
  are lexer-only (no token or element-type change), the bnf regen must produce an empty
  `git diff src/main/gen/.../luacats/lang/psi`. This is asserted, not assumed (DR-01).

## 10. Open Questions

_None — feature has cleared the planning bar._
