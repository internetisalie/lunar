---
id: "BUG-506"
title: "`definition_pins.library_globals` omits the root of `function a.b()`, which Lunar treats as a global declaration, so the attribution classifier calls a library's own drop UNEXPLAINED"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-506: the corpus attribution classifier and Lunar disagree on what a library declares

Found by [[TARGET-11]]'s prototype (DR-05). Tooling only. It does not gate TARGET-11; [[MAINT-39]]'s
`neovim` corpus pin needs it. **Depends on [[BUG-509]]**, which changes what Lunar treats as declared
(§1); the rule in §5 is the post-BUG-509 one. `$S` = `~/.cache/claude-scratch/lunar/target-11`.

## 1. What Lunar treats as a declaration (executed)

Library root holding one file, consumer `local a = vim.x`, `LuaUndeclaredVariableInspection` on
(`Target11ShapeProbeTest.testDeclares*`, `$S/results/run11-declares/shapes.txt`):

| Library file | `Undeclared variable 'vim'` hits |
| :-- | --: |
| `function vim.x() end` | 0 |
| `function vim.api.x() end` | 0 |
| `local vim = {}` / `function vim.x() end` / `return vim` | 0 |
| `_G.vim = _G.vim or {}` | 1 |
| `vim.x = 1` | 1 |
| `function vim:x() end` | 1 |
| `local unrelated = 1` / `return unrelated` (control) | 1 |

So the root of a column-zero `function R.<…>(` declaration is a global to Lunar; `_G.R = …` and a
method declaration `function R:x()` are not. An indented one is too (`if true then` /
`  function I.f() end` / `end` declares `I`; [[BUG-509]] §1, `h-base`). **After [[BUG-509]]** a root
that its own file declares `local` (the third row) is no longer a global (BUG-509 §4, `l-509fix`).

## 2. What the classifier counts (executed)

`library_globals` (`tooling/corpus/definition_pins.py:146-154`, via `declared_globals` at `:137`) matches only
`FUNCTION_DECLARATION = ^function\s+(NAME)\s*\(` and `^(NAME)\s*=(?!=)` (`:35-36`); MAINT-37 design
§3.6 step 2 and its TC 27 (`function M.f() end` → nothing) pin that rule. On the pinned Neovim
archive (`python3 -I $S/globals_probe.py …`, `$S/globals-probe.out`): `vim=False`.

`attribute_delta.py` on tallies derived from TARGET-11's control and library sweeps of lazy.nvim
(`bash $S/attr-probe.sh`, `$S/attr-probe.out`; the refresh lines are synthetic, every count measured):

```
| inspection.LuaUndeclaredVariable | 537 | 7 | -530 | UNEXPLAINED | vim:-530 |
exit=1
```

The drop is exactly what the library should cause; the classifier rejects it because `vim` is not
in its name set.

## 3. Expected vs actual

- **Expected:** `library_globals` returns the names Lunar treats as declared by the library.
- **Actual:** the root of a dotted function declaration is never counted.

## 4. Root cause

`FUNCTION_DECLARATION` (`definition_pins.py:35`) requires `(` straight after the name, so
`function vim.x(` never matches. MAINT-37 chose that rule on purpose (design §3.6 step 2); §1 shows
it disagrees with Lunar.

## 5. Fix strategy (executed in scratch, `$S/r2/pins/`)

- `definition_pins.py:35`: `FUNCTION_DECLARATION = re.compile(r"^function\s+([A-Za-z_][A-Za-z0-9_]*)\s*[.(]")`.
  A dotted name contributes its root; a method `function R:x()` still contributes nothing, matching §1.
- A dotted root the same file declares `local` contributes nothing, matching §1 after BUG-509. Add
  `LOCAL_DECLARATION = re.compile(r"^\s*local\s+(?:function\s+)?([A-Za-z_][A-Za-z0-9_]*(?:\s*,\s*[A-Za-z_][A-Za-z0-9_]*)*)")`
  and `file_locals(text) -> set[str]` (every name in each match's comma list, stripped); in
  `declared_globals`, compute `locals_here = file_locals(text)` once, add a `FUNCTION_DECLARATION`
  match only `if not (line[match.end() - 1] == "." and match.group(1) in locals_here)`, and test
  `GLOBAL_ASSIGNMENT` on every line separately. Executed in scratch (`$S/r3/pins/`, diff against
  `$S/r2/pins/definition_pins.py` is exactly these lines).
- Indented declarations stay uncounted: the rule remains column-zero, so an indented genuine root
  such as §1's `I` is missed. Counting it needs the scope analysis BUG-509 does in Kotlin; the
  classifier only has to explain a drop, and a missed name surfaces as UNEXPLAINED, not as a silent
  error.
- MAINT-37 documents that pin the old rule change with it:
  - `docs/features/maint/37-pinned-definition-libraries/design.md` §3.6 step 2: the regex above, and
    the sentence "dotted names (`function M.f()`) … never match" becomes "a dotted name
    (`function M.f()`) contributes its root `M`; a method (`function M:f()`) contributes nothing".
  - MAINT-37 requirements TC 27 (`requirements.md:272`): expected `{"describe", "context", "M"}`;
    mutation column adds "keep `\(` (→ no `M`)".
  - MAINT-37 requirements §"MAINT-37-06 / -07" globals block: unchanged. Re-extracted with the new
    rule (`python3 -I $S/r2/fixed_globals.py $S/r3/pins $S/r3/catalog-with-neovim.json $S/dl-catalog`,
    `$S/r3/pins/post509-globals.out`), `busted` and `luassert` (`match mock stub`) equal the shipped
    rule's sets, so MAINT-37's recorded classifications stand. `neovim` gives
    `M _ENV _G g name performResume response vim`.

## 6. Test strategy and Done (executed in scratch)

In `tooling/corpus/test_definition_pins.py`, `LibraryGlobalsTest`:

- TC 27's expected set becomes `{"describe", "context", "M"}`.
- New `test_BUG_506_a_dotted_function_declares_its_root_and_a_method_does_not`: archive member
  `n/runtime/lua/vim/shared.lua` = `function vim.list_extend(dst, src) end`,
  `function vim.api.nvim_get_current_buf() end`, `function obj:method() end`,
  `_G.shadow = _G.shadow or {}`, `field.x = 1` (one per line); `library_globals(archive, "n/runtime/lua")`
  equals `{"vim"}`.
- New `test_BUG_509_a_local_root_is_not_a_global`: archive member `n/runtime/lua/m.lua` =
  `local M = {}`, `function M.f() end`, `local a, log = 1, {}`, `function log.g() end`,
  `function vim.x() end`, `return M` (one per line); `library_globals(archive, "n/runtime/lua")`
  equals `{"vim"}`.

| Tree | Result | Evidence |
| :-- | :-- | :-- |
| fixed rule | TC 27 and `test_BUG_506_…` pass; full `test_definition_pins.py` `OK` | `$S/r2/pins/fixed-full.out` |
| mutant: keep `\s*\(` | TC 27 and `test_BUG_506_…` fail (`M`, `vim` missing) | `$S/r2/pins/mutant-revert.out` |
| mutant: `\s*[.(:]` | the new test fails (`obj` added) | `$S/r2/pins/mutant-colon.out` |
| fixed rule plus the local exclusion | all three tests pass; full file `OK` (8 tests) | `$S/r3/pins/fixed-full.out` |
| mutant: drop the local exclusion | only `test_BUG_509_…` fails (`log`, `M` added) | `$S/r3/pins/mutant-nolocal.out` |

**Done:** [[BUG-509]] is merged; the tests above pass in `python3 -m unittest` from `tooling/corpus/`;
every mutant in the table has been observed red; and the MAINT-37 edits in §5 are made. No TARGET-11 code and no corpus
sweep is needed.

## 7. Verification of the delivered fix (2026-10-28)

§5's rule and local exclusion, and §6's two tests, are in `tooling/corpus/`. `python3 -m unittest
test_definition_pins` passes. Each mutant was observed red: keeping `\s*\(` fails TC 27,
`test_BUG_506_…` and `test_BUG_509_…`; `\s*[.(:]` fails `test_BUG_506_…` (`obj`); dropping the local
exclusion fails `test_BUG_509_…`. MAINT-37 design §3.6 step 2 and TC 27 carry the new rule.
