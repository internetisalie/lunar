---
id: "MAINT-41"
title: "41: Enforce the Review's Systemic Patterns Mechanically"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-41: Enforce the review's systemic patterns mechanically

## The gap

The July 2026 codebase review ([[REVIEW-2026-07]], `docs/review.md` §4.6) found that its second-pass
misses were not random: #73, #79, #80 and #87 are instances of the systemic patterns §2 had already
named, sitting in files no reviewer opened. A file-driven review keeps missing them, whereas a
pattern-driven check would catch every site. The review's recommendation, *enforcement, not
re-review*, is not tracked anywhere else.

## Patterns to enforce (from `docs/review.md` §2 and §4.6)

| Pattern | Contract rule | Example finding |
| :-- | :-- | :-- |
| `Logger.error` on an expected, caught failure (e.g. `ExecutionException`) | IDE fatal errors are for defects | #79 |
| PSI or VFS access outside a read action | Rule 1, threading | #73, #87 |
| A `PsiElement` (or `Project`/`Editor`/`VirtualFile`) field in a `data class` or long-lived object | Rule 4, memory | #80 |
| Wildcard imports | Rule 3 (currently debt, BUG-421) | #92 |

## Real fixtures from the third pass (review §6)

| Pattern | Should flag | Should not flag |
| :-- | :-- | :-- |
| Wildcard imports | `LuaCrossFileCompletionScopeProcessor.kt:7` (`lang.psi.*`), `LuaGenerateDocIntention.kt:9` (`lang.psi.*`), `LuaLocalVarStubElementType.kt:3` (`com.intellij.psi.stubs.*`, a platform-package case) | — |
| `Project` held in a long-lived object | `run/LuaSuspendContext.kt:26` (`private val project: Project`), propagated to `LuaStackFrame.kt:31` | `redis/debug/LuaLdbSuspendContext.kt`, its post-contract twin, which holds none. Decide whether session-scoped debugger objects count as long-lived; the post-contract code says yes |
| PSI outside a read action | none | `LuaGenerateDocIntention.invoke` (EDT, write-action intention) |

## Three further checks proposed by the third pass

Each is a unit test, not a lint rule, and each would have caught a P2 of that pass mechanically:

1. **Every offered colour key is applied.** Every `TextAttributesKey` returned by
   `LuaColorSettingsPage.getAttributeDescriptors` must be applied by some highlighter or annotator.
   Flags `LABEL`, `LONGSTRING`, `VAR_SHADOWED`, `REF_UNDEFINED` and `DOC_VALUE` (BUG-521).
2. **Every highlighter key is a token the lexer can emit.** Every key in `LuaSyntaxHighlighter`'s map must be
   a lexer token type: lex a corpus and collect the emitted types, or assert the type is not composite.
   Flags `ATTRIB_NAME`, `LABEL_REF` and `LABEL_NAME` (BUG-521).
3. **Every persisted setting has a reader outside `settings/`.** Every persisted `State` property must be
   read outside the `settings/` package. Flags `enableTypeInference` and `includeAllFieldsInCompletions`
   (BUG-522); after BUG-522 removes them, the fixture is the mutation proof.

## Scope to plan

- **Choose the mechanism.** Options are detekt custom rules, ktlint custom rules, or a Gradle source
  check. Weigh them against the existing ktlint gate, CI (`.gitea` and `.github`), and false
  positives on the platform's sanctioned idioms.
- **Prove each rule is real.** Each rule needs a fixture that it flags and one it doesn't, plus a
  mutation proof that it catches the review's own example.
- **Existing violations:** fix them, or baseline them with an owner.
- **Overlap:** BUG-421 (wildcard imports) and MAINT-36 (#92). Decide whether this subsumes them.

Not planned. Plan with `plan-feature`.
