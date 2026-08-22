---
id: "BUG-453"
title: "Lunar parses luacheck's human formatter instead of its documented editor interface — ANSI escapes leak into annotations, and a project-configured formatter yields a silent false-clean"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-453: the integration was written against a competitor's regex, not luacheck's contract

Found 2026-08-22 by the [[ANALYSIS-01]], [[ANALYSIS-04]] and [[ANALYSIS-05]] agents. All three
converged on it; ANALYSIS-01 also found the provenance that explains it.

**luacheck's `docsrc/cli.rst` ends with a section titled "Stable interface for editor plugins and
tools"** — a contract written for integrations exactly like this one, guaranteed since 0.11.0. It
prescribes `--formatter plain`. Lunar has never passed it.

## 1. Reproduction

**A — escapes in annotation text.** Open any file with an unused local. Hover the warning.

**B — silent false-clean.** Put `formatter = "JUnit"` (or `quiet = 3`) in the project's
`.luacheckrc`. Open a file with obvious problems.

## 2. Expected vs actual

- **A expected**: the tooltip reads `unused variable helper`. **Actual**: stray control characters
  surround the highlighted name, because the escape byte survives stripping.
- **B expected**: warnings appear, or Lunar reports that it cannot read the configured format.
  **Actual**: **zero warnings are extracted and the file appears clean.** No error, no banner.

## 3. Root cause

Two flags that were never sent, and one regex that is wrong.

**`--formatter plain` is absent.** luacheck's default formatter is the human-readable one, whose
shape is not contractual. When a project configures any other formatter, `LINE_PATTERN` matches
nothing and `Problems(emptyList())` is indistinguishable from a clean file. With `quiet = 3` only a
summary is emitted, same outcome.

**`--no-color` is absent**, and luacheck's `format.lua` decides colour from `not utils.is_windows`
**without testing for a TTY** — so on Linux and macOS every run emits escapes into the pipe.

**The stripper omits the escape byte** (`analysis/luacheck/LuaCheckInvoker.kt`):

```kotlin
private val ANSI_PATTERN = Regex("\\[[;\\d]*m")
```

It removes the bracket sequence and leaves the escape character itself. Measured: three stray
control characters land in the message, on most 1xx/2xx/3xx warnings. The pattern also over-matches
— a message quoting a bracketed token loses part of it.

## 4. Provenance — why it looks like this

`DEFAULT_ARGS = arrayOf("--codes", "--ranges")` and the parse regex are **character-for-character
IntelliJ-Luanalysis's 2017 `LuaCheckInvoker`**. The integration was seeded from a competitor rather
than from luacheck's editor contract, which is the common cause of all three defects here.

Luanalysis never hit the colour bug because luacheck's `color_support` is `not is_windows or
ANSICON`, and it was a Windows-tested plugin. The defect was latent in the copied code and became
live on this project's platforms.

## 5. Fix strategy

Send `--formatter plain --no-color --codes --ranges`, per the documented interface. That makes the
output contractual, removes the escapes at the source, and fixes B outright.

**Keep the ANSI stripper anyway**, with the escape byte included — belt and braces for a luacheck
build that colours regardless, and cheap.

Related and worth folding in while here: the code (e.g. `W211`) is parsed but never carried onto
`Problem`, so every finding renders at one severity — see [[BUG-452]] for the error/warning split
this unblocks.

## 6. Test strategy

Assert on **real luacheck output**, captured from the vendored binary, not hand-written samples.
`LuaCheckInvokerClassifyTest` TC12 hand-writes plain-formatter output that production never
requests — a test that passes because it feeds the parser the format the parser expects, rather
than the format luacheck actually emits. See [[BUG-461]].
