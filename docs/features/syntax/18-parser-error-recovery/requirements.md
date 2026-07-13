---
id: SYNTAX-18
title: "18: Parser Error Recovery for Block Constructs"
type: feature
status: "planned"
priority: "medium"
parent_id: SYNTAX
folders:
  - "[[features/syntax/requirements|requirements]]"
---

# SYNTAX-18: Parser Error Recovery for Block Constructs

## Overview
Lunar's grammar (`src/main/kotlin/net/internetisalie/lunar/lang/psi/lua.bnf`) declares **no
`pin` or `recoverWhile`** on any block rule. A half-written block construct — `if x` (no
`then`/`end`), `while c`, `for i = 1, n`, `for k,v in pairs(t)`, `function foo`, `repeat` —
therefore fails its rule and rolls the whole statement back to an anonymous error tree instead
of building a partial `LuaIfStatement`/`LuaWhileStatement`/etc. node with a localized error.
This feature adds grammar-kit `pin` + `recoverWhile` to the nine block rules so a partial typed
PSI node is built for every half-written skeleton, with the error attached at the missing token
(the absent `then`/`do`/`end`), and hardens PSI accessors that today assume a complete node.
Parent epic: SYNTAX.

## Scope

### In Scope
- Add `pin` (after the opener keyword sequence) to the nine block rules in `lua.bnf`:
  `doStatement` (`lua.bnf:125`), `whileStatement` (`:129`), `repeatStatement` (`:133`),
  `ifStatement` (`:137`), `numericForStatement` (`:140`), `genericForStatement` (`:144`),
  `localFuncDecl` (`:162`), `funcDecl` (`:174`), `globalFuncDecl` (`:208`).
- Add a shared `recoverWhile` predicate rule so recovery stops at the next statement/block
  boundary (a statement-starter or block-terminator token).
- Regenerate the parser via the headless `.claude/skills/generate-parser/scripts/generate.sh`
  and commit the resulting `src/main/gen/` delta by hand.
- Harden `getName()` on `net.internetisalie.lunar.lang.psi.LuaNameRefElementImpl`
  (`LuaBaseElements.kt:72`) against a missing `IDENTIFIER` child, which becomes reachable once
  partial function-decl nodes are built.
- Extend `TestLuaParsingExhaustive` (`src/test/kotlin/net/internetisalie/lunar/lang/parser/`)
  with PSI-shape assertions for each half-written skeleton.

### Out of Scope
- Adding `pin`/`recoverWhile` to non-block rules (expressions, `tableConstructor`, `funcBody`
  argument lists, assignment/var lists). Deferred — see `risks-and-gaps.md` Technical Debt.
- Simplifying the EDITOR-08 Smart-Enter fixers (`net.internetisalie.lunar.lang.smartenter`)
  to key off the now-typed partial PSI. Downstream effect only; tracked in `risks-and-gaps.md`.
- Any change to lexer (`lua.flex`), token set, or element-type holder.
- Quick-fixes or annotator messages for the recovered error (highlighting localization is a
  by-product of where the platform paints the `PsiErrorElement`, not a new annotator).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| SYNTAX-18-01 | **Partial node per block kind** | M | Each half-written block skeleton (`if`/`while`/`for`(numeric & generic)/`do`/`repeat`/`function`/`local function`/`global function`) builds the corresponding typed PSI node (`LuaIfStatement`, `LuaWhileStatement`, `LuaNumericForStatement`, `LuaGenericForStatement`, `LuaDoStatement`, `LuaRepeatStatement`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaGlobalFuncDecl`) rather than rolling back to a bare error tree under `block`. |
| SYNTAX-18-02 | **Localized error placement** | M | The `PsiErrorElement` for a half-written block is emitted at the first missing/unexpected token inside the construct (e.g. the absent `then` for `if x`, absent `do` for `while c`, absent `end` for `if x then`), not as a whole-statement error spanning the opener keyword. |
| SYNTAX-18-03 | **No regression to well-formed parsing** | M | Every currently-valid construct in `TestLuaParsingExhaustive` continues to parse with zero `PsiErrorElement`s and produces byte-identical PSI structure (same element types, same tree shape) as before the pin change. |
| SYNTAX-18-04 | **Bounded recovery at statement boundary** | M | Recovery for a failed block rule consumes tokens only up to (not including) the next statement-starter or block-terminator token, so a following well-formed statement parses normally as a sibling and is not swallowed into the error node. |
| SYNTAX-18-05 | **Robust name accessor on partial nodes** | M | `LuaNameRefElementImpl.getName()` returns `null` (never throws) when the recovered node has no `IDENTIFIER` child. |
| SYNTAX-18-06 | **Deterministic regeneration** | S | Re-running `generate.sh` over the edited `lua.bnf` produces a stable, reviewable `src/main/gen/` diff; a second run over unchanged sources is a no-op (`git diff src/main/gen` empty). |

## Detailed Specifications

### SYNTAX-18-01: Partial node per block kind
For each opener token the parser must be *pinned* after consuming the minimal opener prefix, so
the enclosing `exit_section_` marks the node with its real element type even when a later
mandatory token (`then`/`do`/`end`/`until`) is absent. Pin offset per rule (design §3.1):

| Rule | Pinned after | Rationale |
|------|--------------|-----------|
| `doStatement` | `DO` (offset 1) | opener is a single keyword |
| `whileStatement` | `WHILE` (offset 1) | commit as soon as `while` seen |
| `repeatStatement` | `REPEAT` (offset 1) | opener is a single keyword |
| `ifStatement` | `IF` (offset 1) | commit as soon as `if` seen |
| `numericForStatement` | `FOR IDENTIFIER '='` (offset 3) | shared `FOR` prefix; pin=3 past the disambiguating `'='` (see §3.1 note) |
| `genericForStatement` | `FOR nameList IN` (offset 3) | shared `FOR` prefix; pin=3 past the disambiguating `IN` (see §3.1 note) |
| `localFuncDecl` | `LOCAL FUNCTION` (offset 2) | `LOCAL` alone also starts `localVarDecl`; pin after `FUNCTION` |
| `funcDecl` | `FUNCTION` (offset 1) | opener is a single keyword |
| `globalFuncDecl` | `GLOBAL FUNCTION` (offset 2) | `GLOBAL` alone also starts `globalVarDecl`/`globalModeDecl`; pin after `FUNCTION` |

### SYNTAX-18-02: Localized error placement
grammar-kit's pinned `exit_section_` reports the error at the current builder offset when the
first post-pin sub-expression fails. Expected error anchor per skeleton (design §3.2 / §6):
- `if x` → error at expected `then` (after `x`).
- `if x then` → error at expected `end` (at EOF/boundary).
- `while c` → error at expected `do`.
- `for i = 1, n` → error at expected `do`.
- `for k,v in pairs(t)` → error at expected `do`.
- `repeat` → error at expected `until`.
- `function foo` → error at expected `(` (inside shared `funcBody`, see §3.3).
- `do` → error at expected `end`.

### SYNTAX-18-04: Bounded recovery at statement boundary
The `recoverWhile` predicate consumes tokens while they are **not** a boundary token. Boundary
token set (statement-starters ∪ block-terminators), grounded from `statement ::=` (`lua.bnf:100`)
and the block terminators:

`SEMI IDENTIFIER LPAREN MARKER BREAK GOTO DO WHILE REPEAT IF FOR FUNCTION LOCAL GLOBAL RETURN END ELSE ELSEIF UNTIL`

Recovery therefore stops before the next statement or before an enclosing block's terminator, so
`if x <newline> return 1` recovers the `if` node and parses `return 1` as a sibling `finalStatement`.

## Behavior Rules
- Pins are placed on the **rule that owns the element type**; `funcBody` is `private`
  (`lua.bnf:289`) and shared by `funcDecl`/`localFuncDecl`/`globalFuncDecl`/`funcDef`, so the
  paren/`END` failure inside `funcBody` surfaces on the pinned owning decl node (design §3.3).
- `recoverWhile` is a single shared `private` predicate rule referenced by all nine block rules.
- The recovered `PsiErrorElement` is what the platform's error highlighter paints; no new
  annotator is registered.
- `getName()` must degrade to `null`, matching the `PsiNamedElement` contract, never `!!`-throw.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | SYNTAX-18-01 | `if x` | `configureByText` | `PsiTreeUtil.findChildOfType(file, LuaIfStatement::class.java)` is non-null |
| 2 | SYNTAX-18-01 | `while c` | `configureByText` | a `LuaWhileStatement` node exists |
| 3 | SYNTAX-18-01 | `for i = 1, n` | `configureByText` | a `LuaNumericForStatement` node exists |
| 4 | SYNTAX-18-01 | `for k,v in pairs(t)` | `configureByText` | a `LuaGenericForStatement` node exists |
| 5 | SYNTAX-18-01 | `repeat` | `configureByText` | a `LuaRepeatStatement` node exists |
| 6 | SYNTAX-18-01 | `do` | `configureByText` | a `LuaDoStatement` node exists |
| 7 | SYNTAX-18-01 | `function foo` | `configureByText` | a `LuaFuncDecl` node exists |
| 8 | SYNTAX-18-01 | `local function foo` | `configureByText` | a `LuaLocalFuncDecl` node exists |
| 9 | SYNTAX-18-01 | `global function foo` | `configureByText` | a `LuaGlobalFuncDecl` node exists |
| 10 | SYNTAX-18-02 | `if x` | `configureByText`; find the `PsiErrorElement` | the error's `textOffset` is at or after the end of `x` (not at the `if` keyword start, offset 0) |
| 11 | SYNTAX-18-04 | `if x\nreturn 1` | `configureByText` | both a `LuaIfStatement` and a sibling `LuaFinalStatement` (`return 1`) exist; the `return` is NOT inside the if node |
| 12 | SYNTAX-18-03 | every case in `testValidControlFlow`, `testValidAssignments`, `testValidExpressions`, `testVarargsCoverage` | `configureByText` | zero `PsiErrorElement`s (unchanged) |
| 13 | SYNTAX-18-05 | `function foo` | build the `LuaFuncDecl`, take its `nameRef` child, call `getName()` on a `nameRef` with no identifier (e.g. a recovered empty ref) | returns `null`, no `KotlinNullPointerException` |
| 14 | SYNTAX-18-02 | `while c` | find the `PsiErrorElement` | error offset is at/after the end of `c` |
| 15 | SYNTAX-18-06 | edited `lua.bnf` | run `generate.sh` twice | second run leaves `git diff src/main/gen` empty |

## Acceptance Criteria
- [ ] SYNTAX-18-01: all nine skeletons build their typed partial node (TC 1–9).
- [ ] SYNTAX-18-02: error is localized to the missing token, not the opener (TC 10, 14).
- [ ] SYNTAX-18-03: `TestLuaParsingExhaustive` valid-case suites are unchanged and green (TC 12).
- [ ] SYNTAX-18-04: a following statement parses as a sibling (TC 11).
- [ ] SYNTAX-18-05: `getName()` returns `null` on a partial node (TC 13).
- [ ] SYNTAX-18-06: regeneration is deterministic and a no-op over unchanged sources (TC 15).
- [ ] `generate.sh` output committed; full unit suite green via `gce-builder run test`.

## Non-Functional Requirements
- **Threading**: parsing runs under the platform's parser thread; no EDT work is added. PSI
  reads in tests use the fixture's read context. No new services/coroutines.
- **Memory**: no new long-lived references; the change is purely in generated parsing logic
  and one accessor. No `Project`/`Editor`/`PsiFile` retention introduced.
- **Element-type registry**: no new `IElementType` singletons are created (pins reuse the
  existing nine element types); the registry-size limit is not touched.
- **Performance**: `recoverWhile` adds a bounded token scan only on the error path; well-formed
  input is unaffected (pin/recover code runs only after a mandatory token fails).

## Dependencies
- `.claude/skills/generate-parser/scripts/generate.sh` and the local grammar-kit **2023.3.2**
  jar in `tooling/parser-gen/` (see `tooling/parser-gen/README.md`); regeneration is local-only,
  never CI.
- Consumed by (downstream, not blocking): EDITOR-08 Smart Enter (`net.internetisalie.lunar.lang.smartenter`).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
