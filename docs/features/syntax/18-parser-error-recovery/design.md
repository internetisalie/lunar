---
id: "SYNTAX-18-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SYNTAX-18"
folders:
  - "[[features/syntax/18-parser-error-recovery/requirements|requirements]]"
---

# Technical Design: SYNTAX-18 — Parser Error Recovery for Block Constructs

> **As-built note (2026-07-16):** the `recoverWhile`/`lua_statement_recover` half of this design
> was dropped during implementation — empirically unusable (see risks-and-gaps.md Blockers
> 3.2–3.4 and implementation-plan.md "As-Built Deviations"). What shipped is the pin table of
> §3.1 exactly, with no recovery predicate; sections mentioning `recoverWhile`/
> `lua_statement_recover` describe the superseded plan.

## 1. Architecture Overview

### Current State
`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf` declares the nine block rules with
**no `pin` and no `recoverWhile`**:
- `doStatement ::= DO block END` (`lua.bnf:125`)
- `whileStatement ::= WHILE expr DO block END` (`lua.bnf:129`)
- `repeatStatement ::= REPEAT block UNTIL expr` (`lua.bnf:133`)
- `ifStatement ::= IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END` (`lua.bnf:137`)
- `numericForStatement ::= FOR IDENTIFIER '=' expr ',' expr [',' expr] DO block END` (`lua.bnf:140`)
- `genericForStatement ::= FOR nameList IN exprList DO block END` (`lua.bnf:144`)
- `localFuncDecl ::= LOCAL FUNCTION nameRef funcBody` (`lua.bnf:162`)
- `funcDecl ::= FUNCTION funcName funcBody` (`lua.bnf:174`)
- `globalFuncDecl ::= GLOBAL FUNCTION nameRef funcBody` (`lua.bnf:208`)

The generated `ifStatement` shows the roll-back behavior (`LuaParser.java:677-690`): the rule is a
chain of `result_ = result_ && …`; on any `false` the closing
`exit_section_(builder_, marker_, IF_STATEMENT, result_)` is called with `result_ == false`, which
drops the marker. The tokens consumed so far are then re-parsed by `block`'s `{statement}*` loop,
which cannot match them, so they land in an anonymous error subtree — there is no `LuaIfStatement`
node. `funcBody` is `private` (`lua.bnf:289`) and shared, so a decl that fails inside it likewise
rolls the whole decl back.

Two symptoms follow:
1. Editor features cannot key off a typed partial node — EDITOR-08 Smart Enter had to work around
   this (`docs/features/editor/08-smart-enter/risks-and-gaps.md:16`, R-01; it keys off child tokens
   and the caret leaf via `LuaBlockPairs`, `src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaBlockPairs.kt`).
2. `LuaNameRefElementImpl.getName()` (`LuaBaseElements.kt:72`) does
   `findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)!!.text` — the `!!` NPEs on
   error-recovery nodes (noted at `docs/review.md:154`).

### Prior Art in This Repo
- **Grammar (`lua.bnf`)** — `grep -n 'pin=\|recoverWhile' lua.bnf` returns nothing: no rule uses
  pin/recover today. This design **extends** `lua.bnf` (adds attributes + one predicate rule); it
  does not add a new grammar or a second parser.
- **Generated parser (`src/main/gen/.../LuaParser.java`)** — regenerated, not hand-edited.
- **`LuaBlockPairs`** (`src/main/kotlin/net/internetisalie/lunar/lang/syntax/LuaBlockPairs.kt`) — the
  token/keyword-pair map EDITOR-08/COMP-08/EDITOR-01 use to reason about block openers **without** a
  typed node. Left untouched here; simplifying its consumers to use the new typed nodes is out of
  scope (`risks-and-gaps.md`).
- **`LuaNameRefElementImpl.getName()`** (`LuaBaseElements.kt:69-73`) — existing accessor; this
  design **hardens** it (removes the `!!`), does not replace it.
- **`TestLuaParsingExhaustive`** (`src/test/kotlin/net/internetisalie/lunar/lang/parser/TestLuaParsingExhaustive.kt`)
  — existing data-driven parser test; `doTest(code, expectErrors)` asserts on `PsiErrorElement`
  presence (`:17-25`). **Extended** with a new PSI-shape helper, not replaced. Note `testInvalidSyntax`
  (`:137-149`) already asserts `"if a then"`, `"for i=1 do end"` etc. produce error elements — still
  true after pins (a partial node still carries a `PsiErrorElement`).
- Reference grounding for pin/recover semantics: `~/Documents/src/lua/intellij-community`
  `plugins/sh/core/grammar/sh.bnf` (`:216 block_compound_list … {pin(".*")=1 … recoverWhile=block_compound_list_recover}`,
  `:253 block_compound_list_recover ::= !('{' | '\n' | '}' | do | done | '(' | ')')`,
  `:286 old_arithmetic_expansion_expression ::= expression {pin=1 recoverWhile=…}`,
  `:287 …_recover ::= !(ARITH_SQUARE_RIGHT)`) and `plugins/groovy/…/groovy.bnf:566` (`{ pin = 1 recoverWhile = extends_recovery }`).

### Target State
`lua.bnf` gains a `pin=N` attribute on each of the nine block rules, at the offset that
commits the node after the opener keyword(s). **No `recoverWhile` is used** — implementation
proved it empirically unusable in this grammar (risks-and-gaps.md Blockers 3.2/3.4: it destroys
sibling backtracking on any rule that can fail after its first token, and its junk consumption
materializes an unconstructable `STATEMENT` node). The pinned `exit_section_` alone builds the
typed partial node and reports the error at the missing token; grammar-kit's greedy pinned
continuation absorbs following statements as typed children. The parser is regenerated headlessly
and the `src/main/gen/` delta committed. `LuaNameRefElementImpl.getName()` is hardened to return
`null` instead of `!!`-throwing, and five downstream features keyed to the old error-tree shape
are adapted (Smart Enter, block-closer/enter-handler balance, REPL chunk completion, stub
creation, types visitor).

## 2. Core Components

This feature edits generated + grammar sources; it introduces **one** new grammar rule and edits
**one** Kotlin accessor. No new Kotlin classes/services/extensions are created.

### 2.1 `lua.bnf` block-rule attributes (edited grammar, not a class)
- **Responsibility**: declare `pin` + `recoverWhile` on the nine block rules.
- **Threading**: N/A (grammar source; parsing runs on the platform parser thread).
- **Collaborators**: grammar-kit generator via `generate.sh`.
- **Key change** (illustrative, `ifStatement`):
  ```
  ifStatement ::= IF expr THEN block {ELSEIF expr THEN block}* [ELSE block] END {
      pin = 1
      recoverWhile = lua_statement_recover
      implements = ["net.internetisalie.lunar.lang.psi.LuaBlockParent"]
  }
  ```

### 2.2 `private lua_statement_recover` (new grammar predicate rule)
- **Responsibility**: negative-lookahead predicate; true (keep consuming) while the current token
  is not a statement-boundary token.
- **Threading**: N/A (grammar source).
- **Collaborators**: referenced by `recoverWhile` on all nine block rules.
- **Key change**:
  ```
  private lua_statement_recover ::= !(
      SEMI | IDENTIFIER | '(' | '::' | BREAK | GOTO | DO | WHILE | REPEAT |
      IF | FOR | FUNCTION | LOCAL | GLOBAL | RETURN | END | ELSE | ELSEIF | UNTIL
  )
  ```
  Token literals `'('`, `'::'` map to `LPAREN`/`MARKER` per the token table (`lua.bnf:32,50`);
  keyword tokens are named directly (`lua.bnf:9-30`).

### 2.3 `net.internetisalie.lunar.lang.psi.LuaNameRefElementImpl.getName()` (hardened accessor)
- **Responsibility**: return the identifier text of a name reference, or `null` when absent.
- **Threading**: read (called under PSI read context by resolve/rename/inspection callers).
- **Collaborators**: `LuaElementTypes.IDENTIFIER`, `findChildByType`.
- **Key API** (edit at `LuaBaseElements.kt:71-73`):
  ```kotlin
  override fun getName(): String? =
      findChildByType<PsiElement?>(LuaElementTypes.IDENTIFIER)?.text
  ```

## 3. Algorithms

### 3.1 Pin offset selection
- **Input → Output**: a block rule's token sequence → the integer `pin` offset committing the node.
- **Rule**: pin at the smallest prefix length that unambiguously identifies the rule, so the node is
  committed before the first *optional-in-practice-but-mandatory-in-grammar* token that a user is
  mid-typing. grammar-kit counts pin by top-level sequence element (1-based).
- **Steps** (per rule; the number is the count of leading elements to consume before committing):
  1. `doStatement` — `pin = 1` (commit after `DO`).
  2. `whileStatement` — `pin = 1` (commit after `WHILE`; `expr`/`DO` may be missing).
  3. `repeatStatement` — `pin = 1` (commit after `REPEAT`).
  4. `ifStatement` — `pin = 1` (commit after `IF`).
  5. `numericForStatement` — `pin = 1` (commit after `FOR`).
  6. `genericForStatement` — `pin = 1` (commit after `FOR`).
  7. `localFuncDecl` — `pin = 2` (commit after `LOCAL FUNCTION`; `LOCAL` alone also starts
     `localVarDecl`, `lua.bnf:186`, so pinning at 1 would mis-commit a plain `local x`).
  8. `funcDecl` — `pin = 1` (commit after `FUNCTION`).
  9. `globalFuncDecl` — `pin = 2` (commit after `GLOBAL FUNCTION`; `GLOBAL` alone also starts
     `globalVarDecl`/`globalModeDecl`, `lua.bnf:196,202`).
- **`FOR` ambiguity note (edge, §6)**: `numericForStatement` and `genericForStatement` both start
  `FOR` and are alternatives in `statement ::=` (`numericForStatement` first, `lua.bnf:110-111`).
  With `pin=1` on both, the parser tries `numericForStatement` first; because it is pinned at `FOR`,
  a `for k,v in …` input commits to `numericForStatement` at `FOR` and then fails at `'='` — it will
  NOT backtrack to `genericForStatement`. **Therefore `pin=1` is applied ONLY to the block rules that
  do not share a keyword-ambiguous sibling, and the two `FOR` rules use `pin=2`** so the disambiguating
  token (`'='` for numeric at offset 3, `IN` after `nameList` for generic) is consumed before commit:
  - `numericForStatement` — `pin = 3` (commit after `FOR IDENTIFIER '='`).
  - `genericForStatement` — `pin = 3` (commit after `FOR nameList IN`).
  This supersedes steps 5–6 above for the two `FOR` rules; see §6 "FOR disambiguation". A half-typed
  `for i` (before `=`/`in`) is thus below the pin and still rolls back to `block` recovery — an
  accepted limitation (`risks-and-gaps.md` Gap 2.1), because committing earlier would mis-route the
  two `for` variants.
- **Final pin table**: `do`=1, `while`=1, `repeat`=1, `if`=1, `funcDecl`=1, `localFuncDecl`=2,
  `globalFuncDecl`=2, `numericForStatement`=3, `genericForStatement`=3.

### 3.2 Error-anchor placement (grammar-kit pinned exit)
- **Input → Output**: a pinned rule whose post-pin sub-expression fails → a `PsiErrorElement`.
- **Steps**: grammar-kit generates, for a pinned rule, an `exit_section_(builder_, level_, marker_,
  <ELEMENT_TYPE>, result_, pinned_, <recover-ref>)`. When `pinned_` is true and `result_` is false,
  the section is closed with the element type (node is built) and an error is reported at the builder
  position where the first post-pin token failed. Recovery (§3.3) then runs.
- **Anchor per skeleton** (from where the first missing mandatory token sits): `if x`→before `then`;
  `while c`→before `do`; `for i = 1, n`→before `do`; `for k,v in pairs(t)`→before `do`;
  `repeat`→before `until`; `do`→before `end`; `function foo`→before `(` (inside `funcBody`, §3.4).
- **Edge — empty/EOF**: if the failure is at end of file, the error is reported at EOF offset; the
  node still exists. `block`'s `not_eof` guard (`lua.bnf:98`) prevents infinite recovery loops.

### 3.3 recoverWhile boundary predicate
- **Input → Output**: current token stream position → boolean (true = keep consuming as error text).
- **Steps**: `lua_statement_recover ::= !( <boundary token set> )` (see §2.2). grammar-kit invokes it
  repeatedly after a pinned failure; recovery consumes one token per true evaluation and stops at the
  first token in the boundary set. The boundary set is the union of statement-starters (from
  `statement ::=`, `lua.bnf:100-117`: `SEMI`, `IDENTIFIER`/`LPAREN` via `assignmentStatement`/
  `exprStatement`/`var`, `MARKER` via `label`, `BREAK`, `GOTO`, `DO`, `WHILE`, `REPEAT`, `IF`, `FOR`,
  `FUNCTION`, `LOCAL`, `GLOBAL`) and block terminators (`RETURN` for `finalStatement`, `END`, `ELSE`,
  `ELSEIF`, `UNTIL`).
- **Rules / edge handling**:
  - Stopping at `END`/`ELSE`/`ELSEIF`/`UNTIL` lets an *enclosing* block close correctly, so a nested
    malformed `if` inside a `while … do … end` does not eat the outer `end`.
  - Stopping at `RETURN` means TC 11 (`if x\nreturn 1`) recovers the `if` and parses `return 1` as a
    sibling `finalStatement`.
  - The predicate is a single shared `private` rule; all nine block rules reference it, so there is
    exactly one boundary definition to maintain.
- **Complexity**: O(k) token scan on the error path only, k = tokens until the next boundary.

### 3.4 funcBody-shared failure surfacing
- **Input → Output**: a `funcDecl`/`localFuncDecl`/`globalFuncDecl` whose shared `private funcBody`
  (`lua.bnf:289 = '(' [parList] ')' block END`) fails → error on the pinned owning decl node.
- **Steps**: because the decl rule is pinned (after `FUNCTION`), a failure while parsing the inlined
  `funcBody` (e.g. missing `(`) is reported under the owning decl's `exit_section_`; the decl node is
  still built (satisfying SYNTAX-18-01 for TC 7–9). `funcBody` itself is NOT pinned (it has no element
  type of its own; pinning a shared private rule would double-report), so the single pin on the owner
  is sufficient.

## 4. External Data & Parsing
Not applicable — this feature consumes no CLI/file/network input. Its only "external" artifact is
grammar-kit's own generated `LuaParser.java`, which is produced (not parsed) by `generate.sh` and
reviewed as a git diff.

## 5. Data Flow

### Example 1: `if x` (TC 1, 10)
`configureByText("test.lua", "if x")` → lexer emits `IF IDENTIFIER` → parser enters `ifStatement`,
consumes `IF` (pin=1 committed), parses `expr`→`x`, then `consumeToken(THEN)` fails →
`exit_section_` closes an `IF_STATEMENT` node and reports an error at the position after `x` →
`recoverWhile` sees EOF (a boundary via `not_eof`) and stops → PSI: `LuaFile > LuaIfStatement(IF, expr(x), PsiErrorElement)`.

### Example 2: `if x\nreturn 1` (TC 11)
As above through the pinned `IF_STATEMENT` failure at expected `then`; `recoverWhile` evaluates the
next token `RETURN`, which is in the boundary set → recovery stops immediately (consumes nothing) →
`block`'s `[finalStatement …]` matches `return 1` as a sibling → PSI:
`LuaFile > LuaIfStatement(…error…)`, `LuaFinalStatement(return 1)`.

### Example 3: `for k,v in pairs(t)` (TC 4)
`FOR` consumed; `numericForStatement` tried first, pinned at offset 3 (`FOR IDENTIFIER '='`) —
fails at `,` before reaching pin (offset 2 is `IDENTIFIER`=`k`, offset 3 expects `'='` but sees
`,`), so it is **not** committed and the parser falls through to `genericForStatement`, which parses
`FOR nameList(k,v) IN exprList(pairs(t))` and pins at offset 3 (`IN`); the trailing `do`/`block`/`end`
are absent → `GENERIC_FOR_STATEMENT` node built, error at expected `do`.

## 6. Edge Cases
- **FOR disambiguation** (§3.1 note): both `for` rules pinned at offset 3, past their disambiguating
  token, so a valid `for i=1,n do end` still routes to numeric and `for k,v in t do end` to generic.
  A bare `for i` (no `=`/`in` yet) is below both pins → rolls back to `block` recovery (accepted;
  Gap 2.1). Covered by regression TC 12 (`testValidControlFlow` `for` cases must stay green).
- **`local` / `global` ambiguity**: `localFuncDecl` pin=2 and `globalFuncDecl` pin=2 keep a plain
  `local x`/`global x` from being mis-committed to the func-decl rule (they only commit after
  `FUNCTION`). Regression covered by `testValidAssignments` / attribute cases.
- **Nested malformed block**: `while c do if x end` — inner `if x` fails at `then`, pinned, recovers
  and stops at `end`; the `end` closes the enclosing `while`. No outer-node corruption.
- **Missing `IDENTIFIER` on a recovered `nameRef`**: `function` alone (no name) may produce a
  `nameRef`/`funcName` with no `IDENTIFIER`; `getName()` (§2.3) must return `null` (TC 13).
- **EOF during recovery**: `not_eof` (`lua.bnf:98`) bounds the `block` loop so recovery cannot spin
  at end of input.
- **`testInvalidSyntax` still expects errors**: `"if a then"` (`TestLuaParsingExhaustive.kt:142`),
  `"for i=1 do end"` (`:143`) still carry a `PsiErrorElement` inside the (now typed) partial node, so
  `doTest(..., expectErrors=true)` remains green — the change adds a typed wrapper, it does not remove
  the error.

## 7. Integration Points
No `plugin.xml` change. The parser is already registered via `LuaParserDefinition`; the grammar
header (`lua.bnf:73-87`) already names `parserClass`, `elementTypeHolderClass`, `psiImplUtilClass`,
etc., and none of those change. Regeneration + accessor edit only.

```
# No <extensions> block is added or modified for this feature.
# Regeneration command (local-only, never CI):
.claude/skills/generate-parser/scripts/generate.sh
# Then commit the src/main/gen/ delta by hand.
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| SYNTAX-18-01 | M | §2.1, §3.1, §3.4 |
| SYNTAX-18-02 | M | §3.2 |
| SYNTAX-18-03 | M | §3.1 (pin offsets chosen to not perturb valid parses), §6 |
| SYNTAX-18-04 | M | §2.2, §3.3 |
| SYNTAX-18-05 | M | §2.3 |
| SYNTAX-18-06 | S | §7 (headless generate.sh; no-op over unchanged sources) |

## 9. Alternatives Considered
- **Custom `GeneratedParserUtilBase` hook / hand-written recovery in `LuaParser`**: rejected — the
  parser is generated and hand edits are reverted by `generate.sh`; grammar-kit `pin`/`recoverWhile`
  is the idiomatic mechanism (grounded in `sh.bnf`/`groovy.bnf`).
- **Per-rule bespoke recover predicates**: rejected — one shared boundary predicate is simpler and
  the boundary set is identical for every block rule (next statement or enclosing terminator).
- **Pinning `funcBody` directly**: rejected — it is a shared `private` rule with no element type;
  pinning the owning decl (§3.4) surfaces the error on the right typed node without double-reporting.
- **`pin=1` on both `FOR` rules**: rejected — mis-routes `for k,v in …` (§3.1 note); `pin=3` past the
  disambiguating token is required.

## 10. Open Questions
_None — feature has cleared the planning bar._
