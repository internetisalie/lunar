---
id: "BUG-420"
title: "A parameterized `@class` parent never resolves, so inheritance through a generic base is lost"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-420: A parameterized `@class` parent never resolves

`---@class Kid : Base<string, number>` records its supertype under the **full** text
`Base<string, number>`. Nothing strips the generic arguments before the lookup, and
`LuaClassNameIndex` is keyed on the plain class name `Base` — so the parent never resolves and
**every member inherited through a parameterized base is missing**.

Found answering MAINT-34's DR-01 (2026-08-07). Filed separately rather than widened into MAINT-34,
whose scope is extraction *parity*, not resolution.

## Measured, not inferred

Throwaway probe (`ProbeMaint34DeRiskTest`, builder, not committed) over:

```lua
---@class ProbeBase
---@field inherited string
local ProbeBase = {}

---@class ProbeKid : ProbeBase<string, number>
---@field own number
local ProbeKid = {}
```

```
PROBE DR-01 kid.superTypes=[ProbeBase<string, number>]      ← the name is intact
PROBE DR-01 kid.members=[own]                               ← `inherited` is MISSING
PROBE DR-01 resolveType("ProbeBase<string, number>") = null ← the name does not resolve
PROBE DR-01 resolveType("ProbeBase") members=[inherited]    ← the parent itself is fine
```

The parent class is perfectly resolvable under its plain name. The only thing standing between
`ProbeKid` and its inherited members is the unstripped `<string, number>`.

## Not the same bug as BUG-402

BUG-402 is that the **stub** path splits `Base<string, number>` on `,` into two fragments
(`Base<string`, `number>`) while the AST path keeps it whole — a parity defect, fixed by MAINT-34-02.
This bug is what remains **after** that fix: both paths then agree on one correct name, and that one
correct name still does not resolve. MAINT-34-02 converts two nonsense names into one accurate
unresolved one; it never claimed to close this.

Ordering matters only in that this is measurable today on the AST path, which already behaves the
way MAINT-34-02 will make universal.

## Fix direction — the repo has already solved this once

`LuaCatsDocumentationRenderer.parentClassName` strips the arguments before looking a parent up, and
quick-doc's inherited-field walk has worked all along because of it:

```kotlin
private fun parentClassName(argType: LuaCatsArgType): String =
    argType.text.substringBefore('<').trim()
```

The type engine needs the same treatment where a supertype reference is resolved. Two things to
settle before implementing, neither obvious from the outside:

- **Where to strip.** Stripping at *extraction* would discard the arguments from the stub and from
  `superTypes`, losing information a real generics implementation needs later — quick-doc gets away
  with it because it only ever wants the name. Stripping at *resolution* keeps
  `superTypes[0].name == "Base<string, number>"` displayable while making the lookup succeed. The
  latter is preferred; it is also the one that does not change the serialized shape.
- **`LuaClassType.typeParameters` already exists** (`LuaStructuredTypes.kt:7`) and is unused on this
  path. Whether this bug is the moment to start populating it, or whether it should stay a name-only
  strip until something actually substitutes type arguments, is a scoping decision — the cheap strip
  restores inheritance, which is the user-visible loss.

## Impact

Low. Parameterized `@class` parents are rare in the corpus, and the failure is silent-and-partial
(members missing) rather than an error. Recorded because it is a *known* incorrect result with a
known fix, which is exactly the kind of thing that otherwise gets rediscovered a year later.
