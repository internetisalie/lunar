---
id: "BUG-477"
title: "Quick Documentation renders nothing for a LuaCATS-commented function"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-477: Quick Documentation renders nothing for a LuaCATS-commented function

<kbd>Ctrl+Q</kbd> on any function carrying a `---` LuaCATS comment shows
**"No documentation found."** — no signature, no description, no `@param`/`@return` table, no
deprecated marker. A plain `--` comment on the same shape renders correctly, so the failure is
specific to the LuaCATS branch of the renderer.

This is most of what the annotation support is *for*: a user who writes `---@param`/`---@return`
sees strictly less in Quick Doc than one who writes an ordinary comment.

## Found by

[[NAV-13]]'s human verification (`human-verification-checklists.md`, HV-8 step 4). NAV-13 is **not**
the cause — the same failure reproduces on a plain `local function` with no colon call anywhere, and
at a declaration with no reference involved. It is recorded there with its controls.

## Reproduction

Sandbox GoLand 2026.1.3 on the builder VM, plugin loaded (`Loaded custom plugins: lunar`), driven
live 2026-09-03.

| # | Fixture | Caret | Observed |
| :-- | :-- | :-- | :-- |
| 1 | `local t = {}` / `---@deprecated gone` / `function t:m() end` / `t:m()` | the `t:m()` call site | "No documentation found." |
| 2 | `local u = {}` / `---Adds two numbers together.` / `---@param a number` / `---@return number` / `function u:add(a) return a end` / `u:add(1)` | the `u:add(1)` call site | "No documentation found." |
| 3 | as #2 | **the declaration's own `add`** | "No documentation found." |
| 4 | `---@deprecated gone` / `local function f() end` / `f()` | **the declaration's own `f`** | "No documentation found." |
| 5 | **control** — `-- Adds two numbers together.` / `local function plainadd(a) return a end` / `plainadd(1)` | the call site | **renders**: `local function plainadd(a)` above "Adds two numbers together." |

Row 5 is what makes this a defect rather than a broken harness: Quick Doc works, the popup opens, and
`LuaPlainDocumentationRenderer` produces both a signature and a description in the same session.

Rows 3 and 4 rule out any reference-resolution involvement: no call site, no colon, caret on the
declaration's own identifier.

## Root cause (located, not yet confirmed by execution)

`LuaDocumentationRenderer.renderCommentOwnerDocumentation`
([:75-89](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationRenderer.kt))
branches on `element.catsComment` **first** and returns immediately:

```kotlin
val catsComment = element.catsComment
if (catsComment != null) {
    LuaCatsDocumentationRenderer.render(sb, element, catsComment)
    return
}
val comment = element.getComment()
if (comment != null) {
    LuaPlainDocumentationRenderer.render(sb, element, comment)
    return
}
```

`renderFullDocumentation` then returns **null** when `sbContent` is empty
([:55-73](../../../../src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationRenderer.kt)),
and null is what the platform renders as "No documentation found." So the cats branch is being taken
and is appending nothing.

That the renderer *can* produce content for these inputs is not in doubt —
`LuaCatsDocumentationRenderer` has an explicit `@deprecated` path that wraps the signature in `<s>`
([:113-130](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRenderer.kt))
and emits a section through `buildDeprecatedSection`, defaulting to `"This item is deprecated"` when
the tag has no description
([:593-600](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRenderer.kt)).
For fixture 1 the expected popup is a struck-through `function t:m()` above a red
**⚠ Deprecated: gone**.

**The unconfirmed step is which of the two is true**: `element.catsComment` is non-null and
`LuaCatsDocumentationRenderer.render` appends nothing, or the dispatch inside `render` matches no
branch for these element types. `render` is documented as dispatching on `LuaFuncDecl` /
`LuaLocalFuncDecl`, and both fixtures are one of those, so the second is the likelier of the two and
neither is established. **Start by executing that dispatch, not by reading it** — the `.agents/`
memory on this repo records type-engine and renderer claims failing exactly there.

## Fix strategy

Not decided — locate first. Two candidates, in order:

1. `LuaCatsDocumentationRenderer.render` matches no branch (or an inner build step returns early) for
   a `LuaFuncDecl`/`LuaLocalFuncDecl` whose comment carries only tags. Fix the dispatch.
2. `element.catsComment` is non-null for comments the cats renderer cannot handle, so the plain
   branch is never reached. Then the fix is a fallback: if the cats renderer appended nothing, fall
   through to `LuaPlainDocumentationRenderer` rather than returning.

A regression test belongs in `src/test/kotlin/net/internetisalie/lunar/lang/doc/`, asserting the
**rendered HTML** rather than the target class — the target is produced correctly in every fixture
above, and asserting on it is exactly what would let this bug through. [[NAV-13]] design §7 measured
`documentationTargets` going `n=0 → n=1` and that measurement is sound; it simply does not see the
content.

## Surface

User-visible IDE surface (the Quick Documentation popup), so the fix needs a `verify-in-ide` pass in
addition to a unit test — the unit test asserts the HTML string, the live pass confirms it renders.
