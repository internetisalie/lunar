---
id: "BUG-393"
title: "Valid LDoc annotations report syntax errors in doc comments"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-393: Valid LDoc annotations report syntax errors in doc comments

Lunar's LuaCATS comment parser rejects three constructs that are ordinary
[LDoc](https://lunarmodules.github.io/ldoc/), producing visible `PsiErrorElement`s inside `---`
comments on code that is otherwise clean. Nothing is wrong with the Lua; the errors are entirely
within documentation.

## 1. Reproduction

```lua
--- @param[opt=false] explicit boolean  When auto_close is false, set true to close the DB.
function M.closeDB(explicit) end

--- @func callback(v1, v2)
function M.arrayContains(t, v, cb) end

--- @param array Lua table (every value must match the type of `array` and support comparison)
function M.binarySearch(array, value) end
```

Each of the three doc comments reports a parse error.

## 2. Expected vs Actual Behavior

- **Expected**: doc comments Lunar cannot fully model degrade to unparsed prose. An unrecognised
  tag or an unmodelled description token is a *documentation* concern, never a syntax error.
- **Actual**: three distinct errors —

| Construct | Reported error |
|---|---|
| `@param[opt=false] explicit boolean` | `'...' or NAME expected, got '['` |
| `@func callback(v1, v2)` | *(empty description)* |
| `` @param array … `value` … `` | ``DASHES, NAME, NUMBER, STRING, SYMBOL or zzz expected, got '`value`'`` |

## 3. Context / Environment

- **Confidence**: high — found by sweeping upstream KOReader `v2026.07.2` with the MAINT-33
  machinery, and each construct is reproducible standalone.
- **Discovered**: 2026-08-03, in a one-off KOReader sweep. KOReader was **not** retained in the
  corpus (see MAINT-33 risks-and-gaps — 10.2 min sweep time blows the runtime budget), so **there is
  no ratchet guarding this**; it is a point-in-time finding, not a tracked metric.
- The four errors were `frontend/cachesqlite.lua:89` and `frontend/util.lua:322,388,408`.

### Root cause, by construct

- **Backtick code spans.** `luacats.flex:57,72` do lex them —
  ``CODE={BACKTICK}[^`\r\n]+{BACKTICK}`` — but the resulting token is not accepted inside a
  `@param` description, so the tag rule fails on prose it should be ignoring.
- **`@func`.** An LDoc-only tag with no LuaCATS equivalent. Unknown tags should be inert, not errors.
- **`@param[opt=default]`.** LDoc's bracketed modifier marks an optional parameter and may carry a
  default. The `@param` rule expects `'...'` or a NAME immediately, and sees `[`.

**Not a `----` problem.** An earlier reading of this blamed four-dash comments, on the grounds that
`LuaLexer.kt:98` gates on `text.startsWith("---")`, which `----` also satisfies. That is wrong:
`luacats.flex:45` defines `DASHES=---+`, so three-or-more dashes is deliberately a doc comment, and
`----` lines parse fine when their contents are otherwise acceptable.

**Unexplained detail:** `util.lua:366` carries the same backticked `@param` text as `:388` and
`:408` but was **not** reported. Worth understanding before fixing — it suggests the trigger is
contextual rather than purely the token.

## 3a. Resolution (2026-08-04)

**Fixed. Two of the three reported constructs were real; the third was not.**

Reproduced first, with `LuaCatsLdocToleranceTest` over the §1 snippets:

| Construct | Reproduced? | Error observed |
|---|---|---|
| `@param[opt=false] explicit boolean` | **yes** | `'...' or NAME expected, got '['` |
| `` @param array … `array` … `` | **yes** | ``DASHES, NAME, NUMBER, STRING, SYMBOL or zzz expected, got '`array`'`` |
| `@func callback(v1, v2)` | **no** | parses clean on current code |

`@func` never reaches the parser as a tag: it is absent from the `TAG` regex, so `<COMMENT_START>`'s
`.` rule emits `@` as TEXT and switches to `COMMENT_DATA`, where the rest of the line is TEXT — i.e.
prose, which is the desired behaviour already. The report's "(empty description)" entry was an
artefact of how the original sweep surfaced the error, not a third defect. A `@return` backtick was
also checked and is fine, because `TAG_RETURN` has no `{CODE}` rule so no CODE token is produced.

**The fix, in `luacats.bnf`:**

1. `description` now accepts `CODE`. `TAG_PARAM` *does* lex backtick spans (`luacats.flex:190`), so
   the rule was refusing a token its own lexer produces.
2. A new `ldocParamTag ::= '@param' description`, listed after `paramTag` in `anyTag`. A well-formed
   LuaCATS `@param` still matches `paramTag` and yields real PSI; anything else falls through to
   prose instead of erroring.

**Why a separate rule rather than an alternative inside `paramTag`.** The obvious shape —
`paramTag ::= '@param' (paramSpec | description)` — was implemented first and rejected: it makes
`LuaCatsParamTag.getArgType()` `@Nullable`, which broke eight call sites across the type engine,
the stub builders and the doc renderer. A sibling element type keeps that contract `@NotNull` and
confines the change.

Recovering the *name and type* from `@param[opt=false] name type` is not possible without a lexer
change: `=` is not a token in the `TAG_PARAM` state, so everything from `=` onward is already TEXT
before the parser runs. Prose is the honest representation; that was not attempted here.

**Verification**: `LuaCatsLdocToleranceTest` (5 cases, including a regression guard that well-formed
tags still parse into real PSI). Full suite 2341 tests / 0 failures; ktlint clean. The parser and
lexer were regenerated headlessly and `src/main/gen` committed.

## 4. Other Notes

- **Priority is a judgement call.** These are false errors on valid input, which is normally
  high-severity — but they are confined to doc comments, affect no Lua semantics, and no corpus
  project currently in the ratchet uses LDoc. Filed medium; raise it if LDoc support is ever a
  stated goal, since KOReader is one of the largest Lua codebases in existence and annotates this
  way throughout.
- **Fix direction**: make tag-rule failure *recoverable* — an unparsed tag should degrade to prose
  rather than emit an error element. That single change addresses all three cases and every LDoc
  construct not yet enumerated, which is the more durable fix than adding three grammar rules.
- **Verification**: unit tests over the three snippets in §1, asserting no `PsiErrorElement`. A
  corpus ratchet is not available unless KOReader is re-admitted.
