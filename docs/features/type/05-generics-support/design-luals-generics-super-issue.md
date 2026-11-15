---
id: "TYPE-05-GENERICS-ANALYSIS"
title: "LuaLS Generics Super-Issue Analysis"
type: "design"
parent_id: "TYPE-05"
folders:
  - "[[features/type/05-generics-support/requirements|requirements]]"
---

# LuaLS generics super-issue, mapped to Lunar

**Tracker:** [LuaLS/lua-language-server#1861](https://github.com/LuaLS/lua-language-server/issues/1861),
"Generics Super Issue", opened 2023-01-30 by carsakiller, pinned, **open**. Last activity on the
thread 2026-05-02; 21 comments. State below is as of **2026-11-15**, read from the GitHub REST API.

The super-issue is a checklist of fifteen issues plus a discussion thread. Six of the fifteen
links point at `sumneko/lua-language-server`; that repository was renamed to
`LuaLS/lua-language-server`, so those numbers are the same issues in the same tracker, not an
archive. Every number and title below was checked against GitHub.

Lunar's generics work is [[TYPE-05]] (same-file bare `T`) and [[TYPE-14]] (the LuaLS closed
baseline plus the constraint-in-body item). TYPE-14 cites this survey's open questions as **Q1–Q9**
(requirements "Out of Scope", risks-and-gaps "Future work"); the numbering is kept stable here.

## How to read the Lunar status

| Status | Meaning |
| :-- | :-- |
| `done (TYPE-14-NN)` | a TYPE-14 requirement covering the shape is `Full` in its requirements status table |
| `partial (… remainder BUG-NN)` | part is delivered; the rest is filed as the named bug |
| `open bug BUG-NN` | not delivered; filed as the named bug |
| `deferred, untracked` | TYPE-14 lists it as out of scope; no bug or roadmap row exists. TYPE-14's stated reason is quoted |
| `open TYPE-NN` | not delivered; filed as the named feature |
| `not yet assessed` | no Lunar document records Lunar's behaviour for this shape |

"Upstream" says whether LuaLS has shipped behaviour for the item, from the LuaLS
[changelog](https://github.com/LuaLS/lua-language-server/blob/master/changelog.md). An item that
LuaLS has shipped and Lunar has not is a **parity gap**.

## What changed upstream

- **PR [#3330](https://github.com/LuaLS/lua-language-server/pull/3330)** (ian-pascoe, merged
  2026-01-19, released in **3.17.0** the same day) closed #1853, #1863 and #1929, and its changelog
  entries also claim fixes for **#723** (generics in `@overload`), **#1000** (`@param self T` returns
  the receiver's type) and **#1170** (inline `fun<T>` in `@field` / `@type`). Those three issues are
  **still open** on the tracker and unticked on the checklist. Treat them as implemented upstream,
  with the issue left open.
- **3.18.0** (2026-04-03): a generic class method typed `@param self list<T>` resolves its return;
  `ipairs(self)` in a generic class method; hover no longer doubles the brackets (`list<<T>>`).
- **3.19.0** (2026-08-07): `@operator` on a generic class substitutes through the instantiation;
  a method-level generic return is no longer overwritten by the receiver class's binding; a class
  inheriting a generic class is a child of that generic's parent.
- **3.19.2** (2026-10-13): a raw generic class's unbound parameters resolve to `any`; a field read
  on a value typed as a constrained generic (`@generic T: C` with `@param self T`) is looked up on
  the constraint, so existing fields stop reporting `undefined-field`.
- **Earlier, still relevant:** 3.13.6 lets a backtick capture carry a prefix or suffix and be
  optional, a union arm or an array element (`` `T`.Cat? ``), and fixed field completion on a
  generic class object (#2196, #2945).
- **PR [#3149](https://github.com/LuaLS/lua-language-server/pull/3149)** (capture the argument's
  *type* as well as a string literal through backticks) is open and unmerged since 2025-04; a
  maintainer argued against adding more special syntax ahead of the v4 rewrite.
- **LuaLS.github.io PR [#78](https://github.com/LuaLS/LuaLS.github.io/pull/78)** (merged 2025-11-13)
  rewrote the wiki's backtick-capture explanation, the documentation half of Q5.

## The checklist issues

"Asks for" is a paraphrase. Dates are the issue's close date on GitHub.

| # | Title (GitHub) | State | Asks for | Upstream | Lunar status | Lunar evidence |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| [#723](https://github.com/LuaLS/lua-language-server/issues/723) | [Feature request] generics in overload | open | Use a function's `@generic` names inside its `@overload` signatures. | shipped 3.17.0 (issue left open) | open [[TYPE-18]] (needs [[TYPE-16]]) | `@overload` never reaches the type engine (`LuaSignatureCopier.kt:118-119`); TYPE-16 models it, TYPE-18 adds generic names |
| [#734](https://github.com/LuaLS/lua-language-server/issues/734) | Support for generic classes | open | Generic classes (`---@class Array<T>`) that instantiate with a type argument. | basic generic classes work (shown in the issue's 2023 comments); issue left open | partial (TYPE-14-08, TYPE-14-10, TYPE-14-14 done; remainder BUG-32, BUG-37) | TYPE-14-09 Partial ([[BUG-32]]); TYPE-14-01 Partial ([[BUG-37]]). A class whose parent is a table literal over `T` (the issue's `Array<T>: { [integer]: T }`) is not recorded |
| [#911](https://github.com/LuaLS/lua-language-server/issues/911) | Problem with generics | open | When two parameters share `T` and one argument is a class, offer that class's fields while typing the other argument's table literal. | not recorded | not yet assessed | TYPE-14-03 binds `T` from arguments; completion inside a literal argument is not recorded |
| [#1000](https://github.com/LuaLS/lua-language-server/issues/1000) | Returning generics with `T<X, Y>` | open | A fluent method typed `@param self T` returns the receiver's concrete type; also a generic applied to arguments, `T<V, _>`. | `@param self T` shipped 3.17.0; `T<V, _>` not recorded | open bug BUG-94 (body); open [[TYPE-19]] (call site) | [[BUG-94]] (`@generic T: C`, `@param self T` reports false errors in the body). The return-to-receiver half is TYPE-19. `T<V, _>` is not yet assessed |
| [#1170](https://github.com/LuaLS/lua-language-server/issues/1170) | feature: support generics in function type format | open | Inline generic function types (`fun<T>(a: T): T`) in `@field`, `@type` and `@overload`. | shipped 3.17.0 for `@field` / `@type` (issue left open) | open [[TYPE-17]] | Q3 below; the `@overload` half is #723 |
| [#1272](https://github.com/LuaLS/lua-language-server/issues/1272) | `generic` inheritance does not work | open | A backtick-captured class returned together with a base (`T\|Rotation`, or `T: Rotation`) should carry the base's members without type-mismatch errors. The maintainer's answer was an intersection type (`T & Rotation`). | not recorded | not yet assessed | TYPE-14-13 (capture) is done. TYPE-14's behaviour rule "a bound wins over a binding for checking, never for inference" is the relevant contract; neither the union nor the bounded form of this shape is recorded |
| [#1322](https://github.com/LuaLS/lua-language-server/issues/1322) | [Question] Prefixing/modifying a generic to get a different class | **closed, not planned, 2023-07-23** | Derive a return class name from a captured name (`"AceGUI"..T`). The maintainer said type operations are not supported. | not planned (the parameter-side prefix of Q5 exists) | not yet assessed | nearest recorded item is Q5's prefixed capture |
| [#1341](https://github.com/LuaLS/lua-language-server/issues/1341) | Cannot infer value type | open (`bug`) | `@generic V: string`, `@param list V[]`, `@return {[V]: true}`: the returned map's key type should follow the argument's element type. | not recorded | not yet assessed | TYPE-14-03 substitutes through `Elem`, `Member` and `Arg` paths; an indexed-key table type in a return is not recorded |
| [#1532](https://github.com/LuaLS/lua-language-server/issues/1532) | How do I document a generic class (JS Promises) | open | A Promise-like generic class whose chained methods carry the value type, and whose `next` introduces a new type. | generic class methods resolve (3.17.0, 3.18.0); method-level type parameters via `fun<T>` | partial (TYPE-14-08 done; remainder BUG-32, TYPE-17) | method-level parameters are Q3, [[TYPE-17]] |
| [#1853](https://github.com/LuaLS/lua-language-server/issues/1853) | Recursive expansion on hover of generic type | **closed 2026-01-19** (PR #3330) | Hover on a self-referential generic class (`store<T>` whose methods take `self: store<T>`) expanded without bound. | fixed 3.17.0 | not yet assessed (needs a probe) | no Lunar record of hover or inlay display for a self-referential generic class. The graph type's `displayName()` prints a table as its class name or `{ ... }` and a function by parameter names only (`LuaGraphType.kt:199-221`), so that path does not nest; the hover and inlay renderers were not read |
| [#1856](https://github.com/LuaLS/lua-language-server/issues/1856) | Generic class instanced in function should return as a concrete type | open | A generic function returning `list<T>` with `T` captured should display as `list<string>`, not the expanded parent. | double-bracket display fixed 3.18.0; issue open | deferred, untracked | TYPE-14 Out of Scope: "display-only; Gap 2.3". TYPE-14-14 returns `Box<string>` with members for a non-literal-parent class |
| [#1863](https://github.com/LuaLS/lua-language-server/issues/1863) | Generic Class Inheritance | **closed 2026-01-19** (PR #3330) | Methods of a generic class reachable on an instance, with return types substituted (`Box<string>:getValue()` returns `string`). | fixed 3.17.0 | partial (TYPE-14-10 done; remainder BUG-32) | [[BUG-32]]: a method using only its class's parameter is `undefined` on an instantiation; TYPE-14-04 Partial (`.` calls, [[BUG-31]]) |
| [#1883](https://github.com/LuaLS/lua-language-server/issues/1883) | Type inferencing of filter style functions | open | `fun(a: T)` plus `table<any, T>`: infer the lambda's `a` from the table argument. | not recorded | open bug BUG-33 | TYPE-14-16 Not Implemented ([[BUG-33]]). The half with no sibling source is TYPE-14 risks-and-gaps Gap 2.2, "out of scope" |
| [#1929](https://github.com/LuaLS/lua-language-server/issues/1929) | cannot extend generic class | **closed 2026-01-19** (PR #3330) | `---@class Bar: Foo<integer>` inherits `Foo`'s members with `integer` substituted. | fixed 3.17.0 | done (TYPE-14-10) | closes [[BUG-420]] |
| [#1935](https://github.com/LuaLS/lua-language-server/issues/1935) | Allow to set named fields for generic class | **closed 2023-02-22** (by the reporter, `question`) | A named `@field` should win over a table-literal parent's indexed type. Closed with a workaround (`---@field [string] number` instead of the literal parent), not a code change. | workaround only | not yet assessed | not a generics shape; no Lunar record |

## Issues linked from the thread since

These cross-reference #1861 but are not on its checklist.

| # | Title (GitHub) | State | Asks for | Upstream | Lunar status | Lunar evidence |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| [#2196](https://github.com/LuaLS/lua-language-server/issues/2196) | Generic class methods don't show up | closed 2023-07-13 (`duplicate`) | A method typed `@param self lanes.Lane<T>` on an instance of `Lane<integer>` is offered and returns `integer`. | completion fixed 3.13.6; return through `self list<T>` fixed 3.18.0 | open [[TYPE-20]] | nearest records are [[BUG-32]] and [[BUG-37]]; neither covers a generic `self` parameter typed with the class, so it is filed separately |
| [#2355](https://github.com/LuaLS/lua-language-server/issues/2355) | Generics don't expand alias types/don't work with other parameters | open | A backtick-captured alias name types a sibling parameter (a callback) or the return. | not recorded | not yet assessed | TYPE-14-13 binds a captured class or primitive; aliases are not recorded |
| [#2470](https://github.com/LuaLS/lua-language-server/issues/2470) | feature: Variadic generic packs and mapping expansion | open | Variadic type-parameter packs (`T...`) mapped over a generic (`Component<T>`). | not recorded | deferred, untracked | Q4 |
| [#2495](https://github.com/LuaLS/lua-language-server/issues/2495) | Using a union of string literals as a table key doesn't force use of the keys | open | `table<'a'\|'b', number>` should reject other keys, like an exact class. | not recorded | not yet assessed | not a generics shape |
| [#2610](https://github.com/LuaLS/lua-language-server/issues/2610) | The type of keys used to index [] tables is not checked | open (`bug`) | Check an index expression's key against `table<K, V>` / `V[]`. | not recorded | not yet assessed | not a generics shape |

## Open questions in the thread

Q-numbers are the ones TYPE-14 cites. "Raised" is the comment that opened the question.

| Q | Raised | Asks for | Upstream | Lunar status | Lunar evidence |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Q1 | tmillr, 2024-01-29; checkraisefold 2024-06/07 | Inside the body, a parameter typed `T` where `T: A` has `A`'s members. | fields looked up on the constraint, 3.19.2 | done (TYPE-14-12) | requirements status row TYPE-14-12 Full. The `@param self T` variant is [[BUG-94]], open |
| Q2 | aiya000, 2025-10-11 (and sharpchen on #1170, 2025-05-23) | Scoped generics: `---@type T` or `---@cast x T[]` inside a generic function's body. | not recorded | deferred, untracked | TYPE-14: "needs a per-function type-parameter scope for `@type`; no upstream design" |
| Q3 | AserJoker, 2024-09-18; brunoti 2025-02-10 | A method-level type parameter independent of the class's (`next<K>(cb: fun(v: T): K): A<K>`). | inline `fun<T>` in `@field` / `@type` shipped 3.17.0 (#1170) | open [[TYPE-17]] | TYPE-14 deferred it as "annotation-syntax extension; LuaLS has none"; LuaLS parses `fun<T>` since 3.17.0 |
| Q4 | CelDaemon, 2024-12-29 and 2025-01-01 | Vararg generics, for `assert`, `xpcall` and `unpack` signatures (`@param ... T`, `@return T ...`). | not recorded (#2470 open) | deferred, untracked | TYPE-14: "parser and engine extension for `...` positions; upstream open" |
| Q5 | nullromo, 2025-11-10; tomlau10, litoj 2025-11 | What backtick capture means: a string-literal argument names the returned class. An advanced form joins the capture to a prefix (`` Object.`T` ``). | documented (LuaLS.github.io #78); prefix and suffix forms supported since 3.13.6 or earlier; argument-type capture is the unmerged PR #3149 | partial (TYPE-14-13 done; remainder [[TYPE-21]]) | plain capture is TYPE-14-13 Full. Prefixed form deferred: "rare; the plain capture (TYPE-14-13) covers the documented use" |
| Q6 | litoj, 2025-11-09 | A generic class whose body is `T\|{preset: string}`, used as a parameter type with `T: BasicOpts`, should expose the bound's fields; hover shows `Preset<<T:BasicOpts>>`. | not recorded | not yet assessed | TYPE-14-12 covers a parameter typed exactly `T`; a bound reached through a generic class argument is not recorded |
| Q7 | FunctionDJ, 2025-12-11 | Capture an array of string literals as the key set of the returned table (`` `T`[] `` → `{ [T]: string }`). | not recorded | deferred, untracked | TYPE-14: "no upstream semantics" |
| Q8 | chismar, 2026-02-16 | A generic function returning `fun(t: T): T`: the returned function's table-literal argument should complete `T`'s fields. | not recorded | not yet assessed | TYPE-14-03 substitutes `Param`/`Return` paths of a returned function type; completion inside a literal argument is not recorded |
| Q9 | Curve, 2026-05-02 | An indexed access type: `@return T[key]` for a `key` parameter. | not recorded | deferred, untracked | TYPE-14: "no upstream semantics" |

The two 2026-01 comments are not questions: ian-pascoe announced PR #3330 (2026-01-12), and the
thread has had no new question since Q9.

## Lunar generics bugs with no item above

| Bug | Status | What | Upstream relation |
| :-- | :-- | :-- | :-- |
| [[BUG-31]] | todo | A generic function called with `.` on a class (`Class.fn(...)`) is not instantiated (TYPE-14-04 remainder). | none on the checklist |
| [[BUG-37]] | todo | A generic class's parameter is not recognised in an in-file method's own annotations (TYPE-14-01 remainder). | none on the checklist; closest is #1863 |
| [[BUG-88]] | todo | An argument outside a union constraint (`f(1)` for `T: string \| boolean \| table`) is not reported. | a LuaLS test expectation, found by the MAINT-55 conformance suite: LuaLS reports it |
| [[BUG-94]] | todo | `@generic T: C` with `@param self T` on a method gives false errors and misses the undefined field. | a LuaLS test expectation (MAINT-55); the 3.19.2 constraint-field change is this shape |
| [[BUG-96]] | todo | A generic-heavy file's snapshot build does not finish within 60 s. | performance, no upstream counterpart |
| [[BUG-34]] | done | A generic body's own return expression leaked its uninstantiated type into the call result. | — |

## References

- Super-issue: https://github.com/LuaLS/lua-language-server/issues/1861
- PR #3330 (merged): https://github.com/LuaLS/lua-language-server/pull/3330
- PR #3149 (open): https://github.com/LuaLS/lua-language-server/pull/3149
- LuaLS changelog: https://github.com/LuaLS/lua-language-server/blob/master/changelog.md
- TYPE-14: [requirements](../14-generics-baseline/requirements.md),
  [risks and gaps](../14-generics-baseline/risks-and-gaps.md)
- LuaCATS: https://github.com/LuaCATS/LuaCATS
