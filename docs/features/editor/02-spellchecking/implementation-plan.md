---
id: "EDITOR-02-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "EDITOR-02"
folders:
  - "[[features/editor/02-spellchecking/requirements|requirements]]"
---

# EDITOR-02: Implementation Plan

Precondition: `design.md` has cleared the bar (tokenizer dispatch §3.1, string decoding §3.2,
identifier suppression §3.3/§2.4, quick-fix reuse §3.4, LuaCATS prose §3.5, plugin.xml §7 all
specified). Each task names the class/file it creates and the design section it realizes.

## Phases

### Phase 1: Comment spellchecking (strategy skeleton + comment routing) [Must]
- **Goal**: Lua line/block comments spellcheck as plain text; strategy registered; keywords /
  operators / numbers suppressed by fall-through.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.spellcheck.LuaSpellcheckingStrategy` extending
        `com.intellij.spellchecker.tokenizer.SpellcheckingStrategy`, `DumbAware` — realizes design
        §2.1 + §3.1 (dispatch steps 1–5, 8; string/identifier branches temporarily fall through to
        `EMPTY_TOKENIZER` until Phases 2–3).
  - [ ] Register `<spellchecker.support language="Lua" implementationClass="…LuaSpellcheckingStrategy"/>`
        in `src/main/resources/META-INF/plugin.xml` — realizes design §7.
  - [ ] Add LuaCATS prose tokenizer `catsCommentTokenizer` (`Tokenizer<LuaCatsComment>`) inside
        `LuaSpellcheckingStrategy` visiting `LuaCatsDescription` / `LuaCatsElementTypes.COMMENT`
        children only — realizes design §3.5.
- **Exit criteria**: TC-1 (comment typo highlighted), TC-6 (shebang not flagged), TC-7 (LuaCATS
  prose flagged, tag/type names not) pass.

### Phase 2: String literal spellchecking [Should]
- **Goal**: words inside single/double/long-bracket strings spellcheck; escapes and delimiters
  handled so typo ranges map to source.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.spellcheck.LuaStringTokenizer` extending
        `com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer<PsiElement>`, implementing the
        §3.2 delimiter-strip table + `CodeInsightUtilCore.parseStringCharacters` escape path —
        realizes design §2.2 + §3.2.
  - [ ] Wire the `StringLiteralTokens` branch (design §3.1 step 6) in `LuaSpellcheckingStrategy`
        to return the `LuaStringTokenizer` instance.
- **Exit criteria**: TC-2 (double-quoted typo), TC-3 (long-bracket `[==[…]==]` typo at correct
  range) pass.

### Phase 3: Identifier spellchecking + suppression + quick fixes [Should]
- **Goal**: declaration names split (camelCase/snake_case) and spellchecked with Rename/change-to/
  save-to-dictionary fixes; stdlib globals, keywords, and LuaCATS type tokens suppressed.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.spellcheck.LuaSpellcheckSuppressions` (object) with
        `isSuppressed(name, project)` combining `LuaStandardGlobals.contains(name, level)`,
        `LuaKeywords.isReserved(name)`, and the `CATS_TYPES` set; level via
        `LuaProjectSettings.getInstance(project).state.languageLevel` — realizes design §2.4.
  - [ ] Create `net.internetisalie.lunar.lang.spellcheck.LuaIdentifierTokenizer` extending
        `Tokenizer<LuaNameDeclElement>` implementing §3.3 (nameIdentifier → suppress check → offset
        → `consumeToken(..., useRename=true, IdentifierSplitter)`) — realizes design §2.3 + §3.3.
  - [ ] Wire the `LuaNameDeclElement` branch (design §3.1 step 7) in `LuaSpellcheckingStrategy`.
  - [ ] Confirm no override of `getRegularFixes` — quick fixes come from
        `getDefaultRegularFixes` per design §3.4 (verification-only task).
- **Exit criteria**: TC-4 (identifier typo + Rename fix), TC-5 (suppressed `local pairs`),
  TC-8 (change-to + save-to-dictionary offered on a comment typo) pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| EDITOR-02-01 Comment spellcheck | M | Phase 1 |
| EDITOR-02-02 String literal spellcheck | S | Phase 2 |
| EDITOR-02-03 Identifier spellcheck | S | Phase 3 |
| EDITOR-02-04 Quick fixes | S | Phase 3 |
| EDITOR-02-05 Suppression | C | Phase 1 (LuaCATS prose §3.5) + Phase 3 (stdlib/keyword) |

## Verification Tasks

Tests use `BasePlatformTestCase` + `myFixture`, mirroring `LuaCatsAnnotatorTest`. Enable the
platform spellcheck inspection with `myFixture.enableInspections(GrazieSpellCheckingInspection::class.java)`
(the `com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection` bundled in the test IDE — see
DR-03) and assert with `<TYPO descr="Typo: In word '…'">…</TYPO>` markers via
`myFixture.checkHighlighting(true, false, true)`. This is the real-flow DoD gate (drives the
platform machinery, asserts the user-visible highlight range).

- [ ] `LuaSpellcheckingStrategyTest.commentTypoHighlighted` — `-- helo world` flags `helo`
      (covers TC-1).
- [ ] `LuaSpellcheckingStrategyTest.shebangNotFlagged` — `#!/usr/bin/lua\n` no typo (TC-6).
- [ ] `LuaSpellcheckingStrategyTest.luacatsProseFlaggedTypeNamesNot` — `---@class Buildr\n---helo`
      flags `helo` (prose) but not `Buildr`/`class` (TC-7).
- [ ] `LuaSpellcheckingStrategyTest.doubleQuotedStringTypo` — `local s = "helo"` flags `helo` (TC-2).
- [ ] `LuaSpellcheckingStrategyTest.longBracketStringTypoRange` — `local s = [==[helo]==]` flags
      `helo` at the correct inner offset (TC-3).
- [ ] `LuaSpellcheckingStrategyTest.identifierTypoWithRename` — `local recieveBuffer = 1` flags
      `recieve`; assert Rename intention present via `myFixture.filterAvailableIntentions("Rename")`
      / change-to variant (TC-4, TC-8).
- [ ] `LuaSpellcheckingStrategyTest.suppressedStdlibRedeclaration` — `local pairs = 1` no typo (TC-5).
- [ ] Run `human-verification-checklists.md` in the IDE over VNC (verify-in-ide skill): typo squiggle
      in a comment, string, and identifier; Alt+Enter offers Rename / Change to / Save to dictionary.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Comment spellchecking + strategy + LuaCATS prose | todo | Must |
| Phase 2: String literal spellchecking | todo | Should |
| Phase 3: Identifier spellchecking + suppression + quick fixes | todo | Should |
