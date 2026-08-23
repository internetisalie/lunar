---
id: "BUG-467"
title: "A `require` literal whose module name begins with a delimiter character is mis-sliced on file rename"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-467: `DELIMITER_CHARS` membership over-consumes a module name's own first characters

Found 2026-08-23 by the [[REFACT-01]] Phase 5 reviewer, reproduced end to end.

## 1. What happens

`LuaRequireReference` measures a string literal's opening delimiter run by scanning for the first
character that is **not** in `DELIMITER_CHARS`:

```kotlin
val openIndex = oldLiteral.indexOfFirst { it !in DELIMITER_CHARS }
```

`DELIMITER_CHARS` necessarily contains `=`, because a long bracket is `[==[`. So a module name whose
*body* begins with `"`, `'`, `[` or `=` has those characters counted as part of the delimiter, and the
slice is taken from the wrong offset. Reproduced:

```lua
require("=m6")   -- rename m6.lua -> helpers.lua
require("=helpers66")   -- actual result
```

The open index lands on `m`, so the module reads as `m` rather than `=m6`, and the reconstructed
literal keeps the stray `=` and re-appends the mis-measured tail.

## 2. Why this is `low`, not higher

Three things bound it, and they are why Phase 5's review reported rather than failed on it:

- **It is design §3.7 step 2's own approved algorithm**, not an implementation slip — the design
  specified the delimiter-run measurement this way.
- **It needs a module name no real Lua module has.** `require("=m6")` is legal Lua and legal on disk,
  but `=` is not a character any package manager or `package.path` convention produces.
- **The failure is visible, not silent.** The result is an *unresolved* require — the user sees a
  broken reference — rather than one silently rebound to a different real module. That is the
  distinction that keeps it out of the [[BUG-457]] / [[BUG-466]] class.

It shares a root with the pre-existing `moduleNameOf` `trim`, which has the same
membership-vs-position confusion.

## 3. Fix strategy

Measure the delimiter run **by position**, not by set membership: a Lua string literal opens with
exactly one of `"`, `'`, or `[` followed by *n* `=` then `[`. Parse that prefix as a grammar rather
than consuming any leading character that happens to be in the set. The same correction applies to
`moduleNameOf`.

## 4. Test strategy

The fixture is the bug: `require("=m6")` renamed to `helpers.lua` must produce `require("=helpers")`.
Mutation that must turn it red: restore the `indexOfFirst { it !in DELIMITER_CHARS }` form.

Note the trap — a fixture whose module name starts with an ordinary letter passes under **both** the
correct and the broken implementation, so the fixture's *first character* is the test. This is the
same shape as [[BUG-463]] §5, where a fixture rooted at `/` stays green under the mutation.
