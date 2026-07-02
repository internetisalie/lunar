---
id: "MAINT-19-PLAN"
title: "MAINT-19: Implementation Plan"
type: "plan"
priority: "low"
parent_id: "MAINT-19"
folders:
  - "[[features/maint/19-platform-syntax-migration/requirements|requirements]]"
---

# MAINT-19: Implementation Plan

Serial feature (the roadmap flags it "Serial: lexer/parser + `.flex`/`.bnf` regen"). Phases 2 and 4
are **human-in-the-loop** JFlex regeneration handoffs and are modelled as their own steps — the agent
must pause and hand off, not assume automated generation.

## Phase 0 — Baseline capture [Must]

Goal: freeze the current token behavior so the change can be proven byte-identical.

1. Run the lexer suites to confirm green baseline:
   `tooling/gce-builder/gce-builder.sh run "test --tests *TestLuaLexer* --tests *TestLuaCatsLexer*"`.
2. Record the exact constant list from both `.java` interfaces (`grep -n 'IElementType\|TokenSet'`
   `LuaTokenTypes.java` / `LuaCatsTokenTypes.java`) — this is the port checklist.

**Verification:** baseline suites pass; constant checklist captured (73 Lua = 71 `LuaElementType` + `BAD_CHARACTER` + `WHITE_SPACE`; 11 LuaCATS + 1 TokenSet).

## Phase 1 — Kotlin token holders [Must]

Goal: create the two Kotlin `object`s; delete the Java interfaces.

1. Create `src/main/kotlin/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.kt` as an `object` with
   one `@JvmField val` per constant from `LuaTokenTypes.java`, translating initializers per
   `design.md` §Data model (`new LuaElementType("s")` → `LuaElementType("s")`; platform constants
   verbatim; debug-name strings byte-for-byte).
2. Create `src/main/kotlin/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.kt`
   likewise, including the `LUACATS_TOKENS` `TokenSet`.
3. Delete `src/main/java/net/internetisalie/lunar/lang/lexer/LuaTokenTypes.java` and
   `src/main/java/net/internetisalie/lunar/luacats/lang/lexer/LuaCatsTokenTypes.java`.

**Verification (compile will still FAIL until Phase 2 — expected):** the *Kotlin* consumers must
resolve — `LuaLexer.kt`, `LuaCatsLexer.kt`, `LuaCatsAnnotator.kt`, `LuaParserDefinition.kt` compile
against the new `object`s with zero source edits (member access `LuaTokenTypes.X` is preserved). The
only remaining unresolved references at this point are the *generated Java* lexers still declaring
`implements LuaTokenTypes` — fixed in Phase 2/3.

## Phase 2 — `.flex` edits + regeneration handoff [Must] (HUMAN-IN-THE-LOOP)

Goal: rewire the generated lexers off interface-inheritance onto the Kotlin holder's static members.

Agent step (2a):
1. Edit `lua.flex`: add `import static net.internetisalie.lunar.lang.lexer.LuaTokenTypes.*;` to the
   header; change `%implements FlexLexer, LuaTokenTypes` → `%implements FlexLexer`.
2. Edit `luacats.flex`: add
   `import static net.internetisalie.lunar.luacats.lang.lexer.LuaCatsTokenTypes.*;`; change
   `%implements FlexLexer, LuaCatsTokenTypes` → `%implements FlexLexer`.

**HANDOFF (2b):** Agent pauses and hands off to the human. The human runs the **JFlex Generator** on
`lua.flex` and `luacats.flex` in IntelliJ (or the JFlex portion of
`.claude/skills/generate-parser/scripts/generate.sh`), producing regenerated
`src/main/gen/net/internetisalie/lunar/lang/lexer/_LuaLexer.java` and
`src/main/gen/net/internetisalie/lunar/luacats/lang/lexer/_LuaCatsLexer.java`, and commits
`src/main/gen/`. (No `lua.bnf` / `luacats.bnf` regeneration — the parser is untouched.)

**Verification:** `grep 'implements' _LuaLexer.java _LuaCatsLexer.java` shows only `FlexLexer` (no token
interface); each generated file's header contains the new `import static`; the transition-table body
is otherwise unchanged (diff limited to the class declaration + import line).

## Phase 3 — Full build & test [Must]

Goal: prove the whole module compiles and behavior is unchanged.

1. `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the two new `.kt` files
   (match surrounding style; do not mass-reformat).
2. `tooling/gce-builder/gce-builder.sh run build` — compile + plugin verify + `:checkStatus`.
3. `tooling/gce-builder/gce-builder.sh run test` — full unit suite.

**Verification (maps to requirements):**
- TC-01/TC-02: `find src/main/java -name 'LuaTokenTypes.java' -o -name 'LuaCatsTokenTypes.java'` → empty.
- TC-03: `.flex` `%implements` lines name only `FlexLexer`.
- TC-04: generated lexers compile; no Java token interface remains.
- TC-05/06/07/09: `TestLuaLexerExhaustive`, `TestLuaLexer`, `TestLuaCatsLexer` pass **unmodified**
  (no test-source edits allowed — a required test edit means the token identity drifted).
- TC-08: `git diff --name-only` does not include `LuaLexer.kt`, `LuaCatsLexer.kt`,
  `LuaCatsAnnotator.kt`, `LuaParserDefinition.kt`.

## Phase 4 — Docs & status [Should]

1. Set this feature's `status: planned` only after Step 9 review passes; on completion set `done`.
2. Regenerate status: `python3 scripts/gen_status.py` (CLAUDE.md: `run build` also runs
   `:checkStatus`, so `docs/status.md` must be in sync).
3. Mark `MAINT-19` `done` in `docs/roadmap.md` and confirm the MAINT-01 out-of-scope note is satisfied.

## Deferred (NOT this feature)

Full `com.intellij.platform.syntax` migration — tracked as DR `MAINT-19-00-1` in `risks-and-gaps.md`.
