---
id: "BUG-440"
title: "Quick Doc returns \"No documentation found\" for openresty library members, while love2d works"
type: "bug"
parent_id: "BUG"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-440: Quick Doc finds nothing for `ngx.*`, and the inferred types are degraded too

Found by COMP-09's live IDE verification (2026-08-14) while judging checklist scenario 2.3.
It is filed separately because it is **not** about the type text that scenario is about — it
is the fallback that scenario's verdict silently depends on.

## Reproduce

Enable `LuaCATS/openresty` and `LuaCATS/love2d` from the shipped catalog
(`src/main/resources/definitions/lunar-definitions-catalog.json`; both sha256-verified in the
run below). In a Lua file, complete each receiver and press **Ctrl+Q** on a member.

| Member | Declared as | `Ctrl+Q` result |
| :-- | :-- | :-- |
| `love.getVersion` | `function love.getVersion()` in `love.lua` | ✅ `function love.getVersion() : number, number, number, string`, **with per-return docs** |
| `ngx.status` | `---@field status ngx.http.status_code` in `ngx.lua` | ❌ **"No documentation found."** |
| `ngx.say` | `function ngx.say` in `ngx.lua` | ❌ **"No documentation found."** |

love2d is the control: same mechanism, same catalog, same session — it works. Every openresty
member tried failed.

## The corroborating symptom, which points at the layer

The inferred type after insertion is degraded on exactly the same members:

```lua
local b = ngx.status   -- inlay: nil | string     (declared ngx.http.status_code)
local z = ngx.say      -- inlay: fun(...)         (a real function declaration)
```

So this is **probably not a documentation-rendering defect**. Quick Doc says "No documentation
found" when it has no resolved target to document, and the inlays say the target is not
resolving properly either. **Treat that as a hypothesis and measure it** — find out whether
`resolveGlobal`/`resolveType` returns the openresty declaration at all before touching any doc
provider. This feature has been burned repeatedly by plausible readings of the wrong layer.

A lead worth checking first: `ngx.status`'s declared type is `ngx.http.status_code` — a
**nested-qualified** type name. Nested qualifiers are exactly the shape [[BUG-430]] (`a.b.c = v`
flattens onto the root) and [[BUG-439]] (sibling-file members never enumerated) live in, and
`ngx.lua` mixes all three declaration forms: 28 `---@field`, 44 `function ngx.X`, 78 `ngx.X =`.
Establish which form fails before assuming all of them do.

## MEASURED 2026-08-20 — two of the three symptoms are not Lunar defects

Taken before any fix, as this report demands. The `/implement-bug` pre-requisite is **not met**:
there is no Root Cause and no Fix Strategy here, so this stays a `plan-bug` input. What follows
narrows it.

### 1. The `---@class ngx : table` lead — REFUTED by probe

The obvious structural difference from love2d is that openresty parents its classes on **builtins**
(`ngx : table`, `ngx.thread : thread`, and 5 more), where `love.lua`'s `---@class love` has no parent
and its only parented class inherits a class declared beside it. A light fixture says that is not it:

```
B440-PROBE NoParent         -> LuaClassType supers=[]         all=[say, status]
B440-PROBE WithClassParent  -> LuaClassType supers=[NoParent] all=[extra, say, status]
B440-PROBE WithTableParent  -> LuaClassType supers=[table]    all=[say, status]
B440-PROBE WithThreadParent -> LuaClassType supers=[thread]   all=[status]
```

A builtin parent materializes normally. Recorded so the next attempt does not re-derive it.

### 2. `ngx.status`'s degraded inlay is an OPENRESTY defect, not ours

`---@field status ngx.http.status_code` names a type that **is not declared anywhere in the shipped
library** — `grep -rn "status_code"` over the whole checkout finds only three unrelated hits, in
`ngx/balancer.lua` and `resty/websocket/protocol.lua`, none of them a declaration. Falling back to
`nil | string` for an undefined type name is correct behaviour.

### 3. `ngx.say`'s `fun(...)` inlay is CORRECT

The declaration is literally `function ngx.say(...) end` (`ngx.lua:4219`) — a vararg function. `fun(...)`
is the right rendering, not a degradation.

**So this report's "corroborating symptom, which points at the layer" does not corroborate anything.**
Both inlay observations are explained without a Lunar defect, and the inference drawn from them —
that the target is not resolving — loses its evidence. The Quick Doc failure is still real and still
unexplained; it simply no longer has a second symptom pointing at a layer.

### Where the next attempt should start

`LuaDocumentationTargetProvider.resolveDocumentationTarget` (`:108-126`) resolves through
`reference.resolve()`, so the question is narrowly **whether `ngx.say` resolves at all** — not whether
its type is good. Probe that directly against both libraries before looking at any doc rendering.
Note the declaration-form counts differ sharply between the two files (openresty `ngx.lua`: 28
`---@field`, 83 `function ngx.X`, 78 `ngx.X =`), so establish which form fails rather than assuming
all three do — `ngx.say` is the second form and is the cleanest single case to pin.

## `---@field` HALF FIXED 2026-08-20 — VNC verification still OWED, so this is NOT closed

`catsFieldDocumentationTarget` resolves the receiver's class, looks the member up through
`LuaClassType.resolveMember`, and documents the tag `materializeClass` already recorded as its
`sourceElement`. Resolution is deliberately not made to succeed for a field.

Gates: full suite **green with `-PwithCorpus`**, zero `IMPROVED` lines (correct — a doc-layer change
moves no inspection count). 50/50 in the documentation suite, no regression. Mutation-proved **2/2**.

### Two wrong turns, both worth recording

**1. The first reproduction fixture was wrong, not the code.** It reached the member through a typed
local (`---@type Cfg; local c; c.identity`), which turned the *function control* red too and looked
like a far larger bug. The reported scenario is a **direct global** member access (`ngx.say`), and
with that shape the control is green. **That miss surfaced a separate, unreported defect**: a member
reached through a typed local documents nothing, whichever form declares it. Out of scope here; file
it if it matters.

**2. Reusing `LuaFieldDocumentationTarget` produced a target that rendered nothing.** It reads a
`---@type` tag off the comment plus the comment's own summary — on a `---@class` block both describe
the *class*, not the field — so `computeDocumentation()` returned null, which renders exactly as the
missing target did. `targets == 1` went green while the user-visible behaviour was unchanged. Only
the content assertion caught it, which is why this report's test strategy asks for both.

### Mutation proof, and one discarded result

| mutation | red |
| :-- | :-- |
| remove the `catsFieldDocumentationTarget` branch | both `---@field` tests; both controls stay green |
| return a target for **any** member of the class | `testAnUnknownMemberStillHasNoTarget`, and only it |

The second mutation's **first** attempt reported SURVIVED and was discarded rather than recorded: it
searched `element.containingFile` for a field tag, and the consumer file has none — the tags are in
the library file — so the edit was inert. An inert mutation is INVALID, not evidence of a weak test.

### Why this is `in_progress` and not `done`

**Quick Doc is a user-visible surface and `verify-in-ide` has not been run.** The tests drive the real
provider and assert on produced HTML, which is stronger than a typical unit test, but they do not
prove the platform invokes this provider on Ctrl+Q or that the result renders legibly. Two things are
owed in one VNC session:

1. **The `---@field` fix renders** — `ngx.status` shows its type and prose.
2. **The unresolved `ngx.say` question below.** That half still does not reproduce headlessly and a
   headless green cannot settle it in either direction.

## PLANNED 2026-08-20 — it is not openresty, it is `---@field`

The measurement section above narrowed this to "does `ngx.say` resolve". It does. Probed with the
**real** catalog files (`ngx.lua`, `love.lua`) registered as a library root, driving
`LuaDocumentationTargetProvider.documentationTargets` at the member's own offset:

| case | `reference.resolve()` | doc targets |
| :-- | :-- | --: |
| `ngx.say` — `function ngx.say(...) end` | `LuaFuncDeclImpl` | **1**, with HTML |
| `love.getVersion` — `function love.getVersion() end` | `LuaFuncDeclImpl` | **1**, with HTML |
| `ngx.status` — `---@field status …` | **null** | **0** |
| `LoveConfig.identity` — `---@field identity string` | **null** | **0** |

**The last row is the one that matters.** A love2d `---@field` fails exactly as an openresty one
does, so the library split this report was built on is a red herring: the live checklist happened to
test a **function** on love2d and a **field** on openresty. Every framing above — "love2d is the
control: same mechanism, same catalog, same session — it works", "Blast radius: unmeasured … measure
the catalog rather than generalising from n=2" — is answered: the blast radius is **every `---@field`
member of every library**, and it has nothing to do with which library.

### Root cause

`LuaDocumentationTargetProvider.resolveDocumentationTarget` (`:108-126`) obtains its target through
`reference.resolve()`. A `---@field` member **has no declaration PSI to resolve to** — the field
exists only as a tag inside a LuaCATS comment — so `resolve()` returns null, no target is produced,
and Quick Doc renders "No documentation found".

This is the documented invariant in `AGENTS.md`: *"LuaCATS tags are NOT stubbed — they ride a host
declaration's stub"*, and *"a tag with no host decl is never stub-indexed"*. The same shape already
bit Go to Class for a bare `---@class`, and the fix there was **not** to make resolution work but to
target the **tag's own identifier**: `LuaCatsTypeNameIndex` reads `LuaCatsClassTag.argType` directly
and `LuaCatsTypeNavigation` navigates to the comment identifier. `@field` needs the analogous move.

### Fix strategy

1. **Add a `---@field` branch to `resolveDocumentationTarget`.** When the resolved target is null and
   the element is a member access whose receiver types to a class, look the member up through
   `LuaClassType.resolveMember(name)` — which already returns a `LuaTypeMember` carrying
   `sourceElement`, the tag itself (`LuaTypeManagerImpl.materializeClass` sets
   `sourceElement = member.tag ?: decl`). Document **that** element.
2. **Render from the tag's own comment.** The prose above a `---@field` is the doc text; the type is
   `field.typeName`. No new extraction is needed — `LuaCatsDeclarations.fieldMembers` already parses
   both.
3. Do **not** try to make `reference.resolve()` succeed for a field. That would mint a fake
   declaration and reaches Find Usages, Rename and the type engine; the precedent (`LuaCatsTypeNavigation`)
   deliberately did not go that way.

### The one thing still unexplained — do not drop it

**This report states `ngx.say` failed live, and it does NOT reproduce headlessly.** Quick Doc returns
a target with HTML for it. Either the live observation conflated the two members, or the definitions
mechanism loads libraries differently from `registerLibraryRoot` in a way that breaks the function
case too. **Re-check `ngx.say` in the running IDE (`verify-in-ide`) before closing this**, and treat a
headless green as insufficient for that half. The `---@field` half is settled and reproducible; the
function half is not, in either direction.

### Test strategy

| test | asserts |
| :-- | :-- |
| a `---@field` member yields **one** documentation target with HTML | the defect, red today |
| the rendered doc contains the field's declared type and its prose | it documents the tag, not just anything |
| **control**: `function X.y()` still yields one target | a fix that changed the function path would be caught |
| **control**: an unknown member yields **zero** targets | a fix that returns a target for anything would pass the first three |

Mutation-prove by removing the new branch: the first must go red and both controls must stay green.

## Why it matters beyond one library

COMP-09 deliberately serves index-arm members with **no type text** (design §4.13, declared in
the CHANGELOG). The live verification accepted that trade — but explicitly on the grounds that
**one keystroke restores the signature**. Where Quick Doc returns nothing, the user is left with
a bare name and no way to recover the type: the row reads `status`, the right column is blank,
and `Ctrl+Q` is empty. The trade is only cheap while this works.

That does **not** argue for putting a type back into the index value — it argues for fixing
this. See scenario 2.3's recorded result in
[human-verification-checklists.md](../../completion/09-member-enumeration/human-verification-checklists.md).

## Blast radius: unmeasured

Two libraries were tried. `love2d` works, `openresty` does not. Nothing establishes which of the
other catalog entries behave which way, or whether the split follows declaration form, file
layout, or something else. **Measure the catalog rather than generalising from n=2.**
