---
id: FORMAT-02
title: "02: Configurable Code Style"
type: feature
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: FORMAT
folders: ["[[features/formatting/requirements|requirements]]"]
---

# 02: Configurable Code Style

Expose Lunar's formatter as **settings a user can change**: the Lua code-style model, the controls
that edit it, the preview that demonstrates it, and the two places it is persisted — the code-style
scheme XML and `.editorconfig`.

**Boundary with [[FORMAT-01]].** FORMAT-01 owns indentation *behaviour* — what the formatter does
with `INDENT_SIZE`, `CONTINUATION_INDENT_SIZE` and `USE_TAB_CHARACTER`, and which default value Lunar
should ship for Lua. This feature owns whether a user can *reach* those fields at all. A row here is
**Full** when the knob is reachable and its value is delivered to the formatter; it is **not** a claim
that the resulting formatting is correct. Where a field is honoured by the formatter but has no
control, the row is **Not Implemented** *here* while the behaviour row in FORMAT-01/03/04/06 can
stay Full — that asymmetry is the whole reason the two features are separate.

## How these requirements were derived

**Not from Lunar's code.** This file's own git history is the [[BUG-450]] §4 pattern exactly: created
by `5632a81d` as one of 16 bulk placeholder `requirements.md` files for zero-coverage epics, then
given ✅ en masse by `47df3605`, never individually verified. A table read back off
`LuaCodeStyleSettings.kt` would reproduce that failure with more words. The rows come from three
external sources, and Lunar was consulted only afterwards, to assign a status:

1. **The IntelliJ code-style contract**, read in `~/Documents/src/lua/intellij-community`. Every hook
   on `CodeStyleSettingsProvider`, `LanguageCodeStyleSettingsProvider` and `CustomCodeStyleSettings`
   is a question this feature must answer — "we provide it" or "we deliberately do not". The tab set
   is fixed by `LanguageCodeStyleSettingsProvider.SettingsType`
   (`platform/lang-api/.../LanguageCodeStyleSettingsProvider.java:44`): `INDENT_SETTINGS`,
   `SPACING_SETTINGS`, `WRAPPING_AND_BRACES_SETTINGS`, `BLANK_LINES_SETTINGS`, `COMMENTER_SETTINGS`,
   `LANGUAGE_SPECIFIC`. The real constant is `INDENT_SETTINGS` — "Tabs and Indents" is the tab's
   *label*, not the enum.
2. **What a Lua formatter has to offer knobs for**, derived from the language rather than from our
   implementation: indent width and tabs-vs-spaces, continuation indent, spaces around operators and
   after commas, spaces inside a table constructor's braces and inside an index's brackets, space
   before a call's parenthesis, the two parenthesis-free call forms `f"x"` and `f{...}`, blank lines
   between functions, alignment of consecutive assignments and of table fields, the optional `;`, and
   the `:` of a method call.
3. **The repo's own serialization constraint**, recorded in `.editorconfig:15-20`.

**Nothing below was verified by running the IDE.** Claims about which controls render are read from
platform source — cited per row — and from the fact that no test in `src/test/kotlin` constructs
either provider. A unit test can assert that a field exists and that the formatter honours it; it
cannot assert that a control appears in the right tab, or that the preview repaints. Every row whose
subject is a rendered control is marked **needs live verification** and its status should not be
trusted until confirmed through the `verify-in-ide` skill.

## Requirements & Status

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| `FORMAT-02-01` | **A Lua node under Editor \| Code Style** | **M** | **Full** | `LuaCodeStyleSettingsProvider` is registered on `com.intellij.codeStyleSettingsProvider` (`plugin.xml:157`) and overrides `createConfigurable`, so the platform builds one page. Its id comes from `CodeStyleSettings.generateConfigurableIdByLanguage(LuaLanguage)` and its title from `getLanguage().getDisplayName()` = "Lua"; neither is overridden, which is correct. **Needs live verification** — registration is not rendering. |
| `FORMAT-02-02` | **Exactly one Lua page, not two** | **M** | **Full** | Both providers are registered (`plugin.xml:157-160`) and both return `LuaLanguage`. Only `LuaCodeStyleSettingsProvider` overrides `createConfigurable`; `LanguageCodeStyleSettingsProvider.registerSettingsPageProvider` admits a provider to the page set only when that method's declaring class is not the base class, so `LuaLanguageCodeStyleSettingsProvider` contributes options without contributing a second node. |
| `FORMAT-02-03` | **A Lua-specific settings object** | **M** | **Full** | `LuaCodeStyleSettings : CustomCodeStyleSettings(LuaLanguage.id, …)` — tag name `Lua` — created by `LuaCodeStyleSettingsProvider.createCustomSettings`. Carries `WRAP_ARGUMENTS`, `WRAP_TABLE_CONSTRUCTOR`, `ALIGN_CONSECUTIVE_ASSIGNMENTS`, `ALIGN_TABLE_FIELDS`, `WRAP_LONG_COMMENTS`. |
| `FORMAT-02-04` | **The formatter reads the user's object, not a fresh one** | **M** | **Partial** | `LuaFormatBlock.kt:285-287` is `LuaCodeStyleSettings.getInstance(settings) ?: LuaCodeStyleSettings(settings)`. The fallback silently substitutes an all-defaults instance if the custom settings are ever unreachable, so an EP-registration failure would present as "my settings are ignored" rather than as an error — the failure mode the SCHEMA epic hit, and which only live verification caught. Nothing asserts the elvis branch is dead. |
| `FORMAT-02-05` | **Standard tabs present** | **M** | **Partial** | `LuaCodeStyleMainPanel : TabbedLanguageCodeStylePanel`, whose `initTabs` adds Tabs-and-Indents, Spaces, Wrapping-and-Braces and Blank-Lines when a `LanguageCodeStyleSettingsProvider` exists. It adds **no commenter/generation tab** — `CommenterForm` is instantiated only by Java's `CodeStyleGenerationConfigurable` and XML's `GenerationCodeStylePanel`. See `-15`. **Needs live verification.** |
| `FORMAT-02-06` | **Indent width is configurable** | **M** | **Full** | `customizeSettings(INDENT_SETTINGS)` names `IndentOption.INDENT_SIZE`, which `IndentOptionsEditor.showStandardOptions` re-shows. Delivery to the formatter is FORMAT-01's. **Needs live verification.** |
| `FORMAT-02-07` | **Continuation indent is configurable** | **M** | **Partial** | `CONTINUATION_INDENT_SIZE` is named, and `getIndentOptionsEditor()` returns `SmartIndentOptionsEditor(this)`, whose `showStandardOptions` override re-shows the continuation field — so the control should render. Nothing asserts the value reaches the formatter: the only test that sets the field, `TestLuaFormatBlock.testArgs`, carries `@Ignore` and **no `@Test`**, and `BaseDocumentTest` is a plain class whose methods are collected by annotation, so no engine runs it. **Needs live verification.** |
| `FORMAT-02-08` | **Tabs vs. spaces is configurable** | **M** | **Not Implemented** | The epic table names this explicitly ("use of tabs/spaces"). `IndentOptionsEditor.showStandardOptions` begins with `setVisible(false)` — hiding the indent field, the tab-size field and the *Use tab character* checkbox — then re-shows only the options it is passed. Lunar passes `INDENT_SIZE` and `CONTINUATION_INDENT_SIZE` only, so `USE_TAB_CHARACTER` stays hidden. `IndentOptions.USE_TAB_CHARACTER` is honoured by the platform's indent machinery, so the capability exists and only the control is missing; whether the formatter then actually emits tabs is [[FORMAT-01]]'s, and is equally unasserted there. **Needs live verification.** |
| `FORMAT-02-09` | **Tab width is configurable** | **M** | **Not Implemented** | Same single cause as `-08`: `TAB_SIZE` is not named, so the field is hidden by `setVisible(false)` and never re-shown. The epic table names this too. **Needs live verification.** |
| `FORMAT-02-10` | **Smart tabs / keep indents on empty lines** | **C** | **Not Implemented** | `SmartIndentOptionsEditor.setVisible(false)` also hides `SMART_TABS` and `KEEP_INDENTS_ON_EMPTY_LINES`, and neither is named. Both are meaningful for Lua and both become one-line additions once `-08`/`-09` are fixed. |
| `FORMAT-02-11` | **Spaces around operators** | **M** | **Full** | `SPACING_SETTINGS` names the assignment, logical, equality, relational, bitwise, additive, multiplicative, shift and unary options, and `LuaFormatBlock`'s `spacingBuilder` binds each to the matching `LuaSyntax` token set. `TestLuaFormatBlock.testUnaryOperatorSpacing` covers the unary case, which is the one where Lua differs from C-family languages (`not`, `#`, unary `-`, `~`). |
| `FORMAT-02-12` | **Space after comma; inside parentheses, brackets and table braces** | **M** | **Full** | `SPACE_AFTER_COMMA`, `SPACE_WITHIN_PARENTHESES`, `SPACE_WITHIN_BRACKETS` and `SPACE_WITHIN_BRACES` are all named and all bound in the spacing builder. `SPACE_WITHIN_BRACES` is the table-constructor knob; `SPACE_WITHIN_BRACKETS` is the index knob and is regression-covered in both directions by `TestLuaFormatBlock` for [[BUG-382]]. |
| `FORMAT-02-13` | **Wrapping options for calls and table constructors** | **S** | **Full** | `WRAPPING_AND_BRACES_SETTINGS` shows `KEEP_LINE_BREAKS` plus two `showCustomOption` rows bound to `WRAP_ARGUMENTS` / `WRAP_TABLE_CONSTRUCTOR` with `WRAP_OPTIONS`/`WRAP_VALUES`. Behaviour is [[FORMAT-04]]'s; `TestLuaFormattingWave7` sets both fields and asserts the wrap, proving the fields reach `LuaFormatBlock`. **Needs live verification** that the two combo rows render. |
| `FORMAT-02-14` | **Alignment options** | **S** | **Full** | Two further `showCustomOption` rows on the wrapping tab for `ALIGN_CONSECUTIVE_ASSIGNMENTS` / `ALIGN_TABLE_FIELDS`, both defaulting off, both asserted through the formatter by `TestLuaFormattingWave7`. Behaviour is [[FORMAT-05]]'s. **Needs live verification.** |
| `FORMAT-02-15` | **Comment options reachable** | **C** | **Not Implemented** | `customizeSettings(COMMENTER_SETTINGS)` names three commenter options and adds a custom row for `WRAP_LONG_COMMENTS`, but that consumer is only ever driven by `CommenterForm`, which `TabbedLanguageCodeStylePanel` never constructs (see `-05`). The whole block is dead as UI. The platform still honours `LINE_COMMENT_ADD_SPACE` and friends from `CommonCodeStyleSettings`, and `WRAP_LONG_COMMENTS` still drives `LuaCommentWrapPostProcessor`, but a user cannot set either from the Lua page — only through `.editorconfig` (`-22`). |
| `FORMAT-02-16` | **Blank-line options reachable** | **S** | **Not Implemented** | `BLANK_LINES_SETTINGS -> consumer.showStandardOptions()` is called with **no arguments**. `OptionTableWithPreviewPanel.showStandardOptions(String…)` adds only the options it is passed to `myAllowedOptions`, and `createOptionsTree` (line 217) skips every option that is neither custom, nor allowed, nor covered by a prior `showAllStandardOptions()` — so the Lua Blank Lines tab renders with an empty option tree. `BLANK_LINES_AROUND_METHOD` and `KEEP_BLANK_LINES_IN_CODE` *are* read by `LuaFormatBlock` and *are* covered by `TestLuaFormattingWave7.testFunctionSeparationHonorsSetting` / `testKeepMaxBlankLines`, which set them programmatically — precisely the case a unit test cannot distinguish from a working UI. **Needs live verification.** |
| `FORMAT-02-17` | **Only Lua-meaningful options are offered** | **S** | **Full** | The corollary of `-16`. Because every tab uses the explicit-name form rather than `showAllStandardOptions()`, no Java-shaped row (`BLANK_LINES_AFTER_PACKAGE`, `BLANK_LINES_AROUND_FIELD_IN_INTERFACE`, `SPACE_BEFORE_CATCH_LBRACE`, …) can leak onto a Lua tab. That is the right default; `-16` is the cost of over-applying it. |
| `FORMAT-02-18` | **Right margin is configurable for Lua** | **S** | **Not Implemented** | `RIGHT_MARGIN` is a `WrappingOrBraceOption` that `WrappingAndBracesPanel` renders as an ordinary gated option row. Lunar does not name it, so the Lua tab has no *Hard wrap at*. `CodeStyleSettings.getRightMargin(language)` falls back to the global default whenever the language value is `< 0` (`CodeStyleSettings.java:910-918`), so `LuaCommentWrapPostProcessor` and FORMAT-04 silently wrap at the IDE-wide margin. FORMAT-04's own implementation plan assumes "a set `RIGHT_MARGIN`", which today is reachable only from code. `WRAP_ON_TYPING` is absent for the same reason. |
| `FORMAT-02-19` | **Per-tab code samples** | **C** | **Partial** | `getCodeSample(settingsType)` ignores its argument and returns one sample for every tab, read from `codeStyle/preview/codeStyle.lua` through `CodeStyleAbstractPanel.readFromFile` and split on `---`. The contract is per-`SettingsType` precisely so each tab can demonstrate its own options. The single sample is also inherited verbatim from lua-for-idea — its Apache header names Sylvanaar, 2010 — and is mostly filler identifiers; it exercises spacing and indentation but demonstrates no wrapping, alignment or blank-line option. |
| `FORMAT-02-20` | **The preview repaints as options change** | **M** | **Full** | Platform-supplied: `CodeStyleAbstractPanel` reformats the sample through the registered `lang.formatter` (`plugin.xml:402`, `LuaFormattingModelBuilder`) on every change. Lunar contributes nothing and needs to contribute nothing — `createFileFromText` is correctly left unoverridden, so the platform builds the required non-physical, event-disabled file itself. **Needs live verification**; there is no headless way to assert a repaint. |
| `FORMAT-02-21` | **Settings persist across restarts** | **M** | **Full** | `CustomCodeStyleSettings.readExternal`/`writeExternal` round-trip the fields through `DefaultJDOMExternalizer` under the `Lua` tag, with a `DifferenceFilter` so only non-default values are written; a project scheme lands in `.idea/codeStyles/Project.xml` and is shareable via VCS. Lunar overrides neither method nor `getKnownTagNames`, which is correct. **Untested** — nothing in `src/test/kotlin` writes and re-reads a `LuaCodeStyleSettings`, and the serialization tests under `settings/` cover `LuaProjectSettings`, not code style. |
| `FORMAT-02-22` | **Lua custom settings appear in `.editorconfig`** | **S** | **Full** | Free, and unrecognised anywhere in the repo: `LanguageCodeStylePropertyMapper.getSupportedFields` adds each `CustomCodeStyleSettings` as `CodeStyleObjectDescriptor(customSettings, null)`, and a `null` field set means *all* fields (`AbstractCodeStylePropertyMapper.addAccessorsFor`). So `ij_lua_wrap_arguments`, `ij_lua_align_table_fields`, `ij_lua_wrap_long_comments` and the rest are already readable and writable — including `WRAP_LONG_COMMENTS`, which has no UI at all (`-15`). **Needs live verification**; nothing in `src/` or `docs/` mentions `.editorconfig`, so this has never been exercised. |
| `FORMAT-02-23` | **Lua common settings appear in `.editorconfig`** | **S** | **Partial** | Common fields are gated by `getSupportedFields()`, which replays `customizeSettings` across every `SettingsType` — so the export set is exactly the UI set and inherits its holes: no blank-line properties (`-16`), no right margin (`-18`). Indent options are worse than a mirror. `LanguageCodeStylePropertyMapper.getSupportedIndentOptions()` adds `TAB_SIZE`, `USE_TAB_CHARACTER`, `SMART_TABS` and `KEEP_INDENTS_ON_EMPTY_LINES` **only when the provider's `INDENT_SETTINGS` set is empty** — so naming two indent options actively suppresses the fallback that would otherwise have supplied the other four. |
| `FORMAT-02-24` | **Language id for external formats** | **S** | **Full** | `getExternalLanguageId` is not overridden, so it sanitizes `LuaLanguage.getID()` = `"Lua"` to `lua` — the correct value, and the `ij_lua_` prefix in `-22`/`-23` depends on it. |
| `FORMAT-02-25` | **A hook exists for shipping Lua defaults** | **S** | **Not Implemented** | `customizeDefaults(CommonCodeStyleSettings, IndentOptions)` — the supported override, `getDefaultCommonSettings()` being deprecated in its favour — is never implemented, so Lua's defaults arrive by omission rather than by decision. **[[FORMAT-01]] owns the value** (Lua convention vs. the inherited `CodeStyleDefaults`); this row owns only the mechanism, and records the aggravating factor: with `-08`/`-09` unimplemented, a user who disagrees with the inherited default has no control with which to change it. |
| `FORMAT-02-26` | **Field names are a compatibility contract** | **M** | **Full** | A `LuaCodeStyleSettings` field name is public in **two** external formats: `DefaultJDOMExternalizer` writes `<option name="WRAP_ARGUMENTS" …>` into the user's scheme XML, and `CodeStyleFieldAccessor.getPropertyName` derives the `.editorconfig` key from the same `Field.getName()` via `PropertyNameUtil`. Renaming a field therefore discards every saved value *and* breaks every checked-in `.editorconfig`; deleting one orphans the option. The evolution rule is **add fields, never rename them**, and treat a removal as a migration. It is mechanically enforced by `ktlint_standard_property-naming = disabled` in `.editorconfig:20` — that line is load-bearing and must not be "cleaned up". |
| `FORMAT-02-27` | **Space before a call's parenthesis** | **C** | **Not Implemented** | `f ()` vs `f()`. `SPACE_BEFORE_METHOD_CALL_PARENTHESES` and `SPACE_BEFORE_METHOD_PARENTHESES` exist on `CommonCodeStyleSettings` and are named in `SpacingOption`, but neither is offered by `customizeSettings` nor bound in `LuaFormatBlock`'s spacing builder — so whatever the source had survives. |
| `FORMAT-02-28` | **Space before a parenthesis-free call argument** | **C** | **Not Implemented** | `print"hello"` and `setmetatable{...}` are legal calls, and the shipped preview sample even contains `print"hello"`. No platform `SpacingOption` models this, so it needs a new `LuaCodeStyleSettings` field (per `-26`, a genuinely new one — not a rename) plus a spacing rule between the callee and a `STRING` or table argument. Nothing exists today. |
| `FORMAT-02-29` | **Semicolon options** | **C** | **Not Implemented** | Lua's `;` is optional, which makes both spacing and removal legitimate knobs. `LuaBundle.properties:93-94` already defines `codeStyle.spacing.beforeSemicolon` / `afterSemicolon`, and `LuaCodeStyleSettings.kt:92-104` holds the wiring **commented out**, so both keys are dead. The commented code would not have worked either: it passes `SpacingOption.SPACE_BEFORE_SEMICOLON.name` to `showCustomOption(LuaCodeStyleSettings::class.java, …)`, but those are `CommonCodeStyleSettings` fields and `LuaCodeStyleSettings` has no field of either name — `showCustomOption` would look for one there. The correct form is `showStandardOptions(SPACE_BEFORE_SEMICOLON.name, SPACE_AFTER_SEMICOLON.name)` plus spacing-builder bindings. "Remove redundant semicolons" would be a separate custom field. |
| `FORMAT-02-30` | **Spacing around a method-call colon** | **C** | **Not Implemented** | `a:b()` is Lua's only colon. `codeStyle.spacing.aroundColon`, `beforeColon`, `afterColon` and the group label `codeStyle.spacing.groupIndex` are defined in `LuaBundle.properties:89-92` and referenced by nothing in `src/main` — four more dead keys from the same abandoned attempt as `-29`. |
| `FORMAT-02-31` | **Space before comma** | **W** | **Won't** | `SPACE_BEFORE_COMMA` exists and would be mechanical to expose, but `a , b` has no constituency in any Lua style guide, and every knob costs a row on a tab a Lua user has to read. Declined deliberately, not overlooked. |
| `FORMAT-02-32` | **LuaCATS doc-comment settings** | **W** | **Won't** | `getDocCommentSettings` returns `DocCommentSettings.DEFAULTS`. It models a JavaDoc-shaped block comment the platform can enable, disable and reformat; LuaCATS `---@` annotations are line comments whose formatting [[FORMAT-06]] handles directly and whose content is a type language, not prose. Wrapping the wrong abstraction around them would be worse than leaving the default. |
| `FORMAT-02-33` | **Custom accessors for external formats** | **W** | **Won't** | `getAccessor` / `getAdditionalAccessors` exist for settings whose type the default `FieldAccessorFactory` cannot serialize. All five Lua custom fields are `Int` or `Boolean`, which it handles, so overriding either would add code that changes nothing. Revisit only if a list- or map-valued setting is ever added. |
| `FORMAT-02-34` | **A code-style group node** | **W** | **Won't** | `CodeStyleSettingsProvider.getGroup` nests a page under a shared parent, as the JS/TS family does. Lua is a single top-level language with one page; a group of one is noise in the settings tree. |

## Test Cases

| TC | Requirement | Input | Action | Expected |
| :--- | :--- | :--- | :--- | :--- |
| `TC-02-01` | `-04`, `-13` | `f(aaa, bbb, ccc)` | set `WRAP_ARGUMENTS = WRAP_ALWAYS` through `settings.getCustomSettings(LuaCodeStyleSettings::class.java)`, then `CodeStyleManager.reformatText` | one argument per line — proves the *user's* settings object, not a fallback instance, reached `LuaFormatBlock` |
| `TC-02-02` | `-07` | a call whose arguments span lines | `IndentOptions.CONTINUATION_INDENT_SIZE = 4`, reformat | continuation lines indent by 4. Requires **re-enabling `TestLuaFormatBlock.testArgs`** — add `@Test`, drop `@Ignore`. It fails against the current formatter, and that failure is [[FORMAT-01]]'s to fix; only the settings-delivery half of the assertion belongs to this feature |
| `TC-02-03` | `-12` | `local x = t[1]` | `SPACE_WITHIN_BRACKETS = false`, reformat; then `= true` | `t[1]`, then `t[ 1 ]` — already asserted by `TestLuaFormatBlock` |
| `TC-02-04` | `-21` | a `LuaCodeStyleSettings` with `ALIGN_TABLE_FIELDS = true` | `writeExternal` into a JDOM element, then `readExternal` into a fresh instance | the flag round-trips, and the written element contains `<option name="ALIGN_TABLE_FIELDS" …>` — the literal field name, which is what `-26` protects |
| `TC-02-05` | `-26` | the set of public field names on `LuaCodeStyleSettings` | compare against a frozen list held in the test | any rename fails the build with a message naming the migration cost, instead of silently discarding users' saved settings |
| `TC-02-06` | `-06`…`-10`, `-23` | — | `LuaLanguageCodeStyleSettingsProvider().getSupportedFields(SettingsType.INDENT_SETTINGS)` | assert the exact set. Today `{INDENT_SIZE, CONTINUATION_INDENT_SIZE}`; after `-08`/`-09` it must also contain `USE_TAB_CHARACTER` and `TAB_SIZE`. This is the one headless assertion that catches both the hidden controls and the `.editorconfig` suppression, because both read this method |
| `TC-02-07` | `-16` | — | `getSupportedFields(SettingsType.BLANK_LINES_SETTINGS)` | non-empty, and contains `BLANK_LINES_AROUND_METHOD` and `KEEP_BLANK_LINES_IN_CODE` — the two fields `LuaFormatBlock` already reads |
| `TC-02-08` | `-19` | — | `getCodeSample(SPACING_SETTINGS)` vs `getCodeSample(BLANK_LINES_SETTINGS)` | differ, and each is non-empty and parses with no `ERROR_ELEMENT` |
| `TC-02-09` | `-01`, `-02` | — | count the providers from `CodeStyleSettingsProvider.EXTENSION_POINT_NAME` and `LanguageCodeStyleSettingsProvider.getSettingsPagesProviders()` whose language is `LuaLanguage` and which contribute a page | exactly one |

## Verification

**Headless, today.** `TestLuaFormatBlock` and `TestLuaFormattingWave7` — both `BaseDocumentTest` —
are the only tests that touch code style. Between them they cover `-04`, `-11`, `-12`, `-13` and
`-14` *as far as a unit test can*: they set fields directly and assert the reformatted text. Every
`TestLuaFormattingWave7` method carries `@Test` and runs; in `TestLuaFormatBlock`, `testArgs` and
`testLocalVarDecl` carry `@Ignore` and no `@Test`, so `-07` has no live coverage at all.
`StyluaFormattingServiceTest` covers the [[FORMAT-07]] alternative path and asserts nothing here.

**Nothing tests the settings surface itself.** No file under `src/test/kotlin` or
`src/integrationTest/kotlin` names `LuaCodeStyleSettingsProvider`, `LuaLanguageCodeStyleSettingsProvider`,
`customizeSettings` or `getCodeSample` — verified by grep. `TC-02-06` through `TC-02-09` are the
headless gap that leaves; all four are cheap and none needs a UI.

**Live verification is required and has never been done** for `-01`, `-05`, `-06`, `-07`, `-08`,
`-09`, `-13`, `-14`, `-16`, `-20` and `-22`. Every one is a question about a rendered control, a
tab's contents, or a repaint. `TestLuaFormattingWave7` is the standing demonstration of why that
matters: it proves the formatter honours `BLANK_LINES_AROUND_METHOD` while a user, per `-16`, appears
to have no control that sets it. Use the `verify-in-ide` skill — open **Settings | Editor | Code
Style | Lua**, check each tab's option list against the rows above, change one option per tab and
watch the preview, then apply, restart, and confirm the values survived (`-21`).

**Recorded nowhere else in the repo.** `-08`, `-09`, `-15`, `-16`, `-18`, `-23` and `-25` have no bug
report and no roadmap row. `-08` and `-09` contradict the epic table's own text for FORMAT-02
("configure tab size, use of tabs/spaces"), and `-16` leaves FORMAT-03's shipped behaviour
unreachable from the UI. `-29` and `-30` account for six dead `LuaBundle` keys. `-22` is the opposite
kind of surprise: working `.editorconfig` support that no document claims and no one has tried.
