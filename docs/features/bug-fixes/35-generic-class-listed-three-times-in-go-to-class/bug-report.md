---
id: "BUG-35"
title: "Go to Class lists a generic class three times"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-35: Go to Class lists a generic class three times

Found running [[TYPE-14]]'s human checklist, scenario 3.2 (GoLand 2026.2.3, libvirt builder).

## 1. Reproduction
One project file, `s31.lua`:
```lua
---@class Box<T>
---@field value T
---@field items T[]
local Box = {}
---@type Box<string>
local b
---@generic T
---@param v T
---@return Box<T>
local function make(v) end
```
Navigate → Class (Ctrl+N), type `Box`.

## 2. Expected vs Actual Behavior
- **Expected**: one `Box` entry.
- **Actual**: three `Box` entries, all from `s31.lua`, and each one lands on line 1. A non-generic
  class in the same project (`---@class Box13` `local Box13 = {}`) is listed once.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `lang/navigation/LuaNavigationItem.kt`, `lang/navigation/LuaCatsTypeNavigation.kt`.

## 4. Root Cause
The count of three comes from GoLand, not from the Lua file. Nothing about generics causes it.

**What was measured.** Live, in the same `runIde` sandbox and project as the report, with temporary
logging in `LuaCatsTypeNavigation`, a single Ctrl+N `Box` search showed:
- `processNames` ran once and enumerated `Box` once;
- `processElements("Box")` ran three times, and `processElements("Box13")` once;
- a non-generic `---@class Base` (`s21.lua`) was also fetched three times while typing `B`.

Logging every `gotoClassContributor` showed the source: GoLand's `com.goide.go.GoTypeContributor`
enumerates the name `Box` twice, and Lunar's contributor once. The other two contributors
(`LspGoToClassContributor`, `JavaScriptClassContributor`) enumerate it zero times.

**Mechanism.**
1. The platform does not merge duplicate names. `DefaultChooseByNameItemProvider.getAllNames` keeps
   every name every contributor enumerates. `processByNames` then calls
   `ContributorsBasedGotoByModel.getElementsByName` once per entry. That method asks every
   contributor whose own name set contains the name. So `Box` is looked up three times, and our
   contributor answers each time.
2. Each answer is a new `LuaNavigationItem`, built at
   [LuaCatsTypeNavigation.kt:50](../../../../src/main/kotlin/net/internetisalie/lunar/lang/navigation/LuaCatsTypeNavigation.kt).
3. Search Everywhere removes repeats through its `searchEverywhereResultsEqualityProvider`s
   (`SeEqualityChecker` in the 2026.2 implementation). `PsiElementsEqualityProvider` matches only
   items that are PSI or `PsiElementNavigationItem`. `TrivialElementsEqualityProvider` uses
   `equals`. [LuaNavigationItem.kt:21](../../../../src/main/kotlin/net/internetisalie/lunar/lang/navigation/LuaNavigationItem.kt)
   is neither of the first two and has identity `equals`. No provider matches, and each repeat is
   shown.

Go's own items for `Box` are outside project scope, so none of them are listed. Only the Lua item is
repeated.

**Origin: the defect has been there since NAV-03 (`6bc0967f`). TYPE-14 Phase 5 made it visible for
`Box`.** The test below was run on `3e957ef9` (before Phase 5), with two stand-in contributors
enumerating each name:
- `---@class Box<T>` was listed once, as `Box<T>`, because no Go type is named `Box<T>`;
- `---@class Base` was listed three times.

Phase 5 keys a parameterized class on its head
([LuaCatsTypeNameIndex.kt:88](../../../../src/main/kotlin/net/internetisalie/lunar/lang/indexing/LuaCatsTypeNameIndex.kt),
`classHeadName`). That moved `Box` onto a name Go also declares.

**The report's open questions:**
- *Does a lone `---@class Box<T>` list once?* In a light fixture, with no other contributor, yes:
  once for a lone tag, with a `local`, with `@field`s, and in the full reproduction file. A lone tag was
  not tried in GoLand. The repeat depends only on the name, so it should repeat the same way there.
- *Do `---@type Box<string>` / `---@return Box<T>` contribute entries?* No. The index and the
  navigation read only `@class` and `@alias` tags. The reproduction file has one class tag. Adding
  either reference leaves the count at one in the fixture.

## 5. Fix Strategy
Give `LuaNavigationItem` value equality: `equals`/`hashCode` on its declaration PSI and its name.
The icon follows from the declaration's kind, so it is not compared. The repeated lookups then
return equal items, and `TrivialElementsEqualityProvider` collapses them. Search Everywhere wraps
an item with its presentation, and the presentation of equal items is equal too.

Rejected: implementing `PsiElementNavigationItem`. `PSIPresentationBgRendererWrapper` would then
draw and navigate from the raw tag PSI and drop the item's name, icon and location. Dropping
repeated names inside the contributor is not possible either, because each repeat comes from a
separate `processElementsWithName` call.

## 6. Test Strategy
`LuaGotoContributorTest.testGotoClassListsAClassOnceWhenAnotherContributorEnumeratesItsName`:
1. Registers two foreign Go-to-Class contributors that enumerate `Box` and own nothing, which is
   what `GoTypeContributor` does.
2. Runs the real class search (`myFixture.getGotoClassResults`) and asserts that the platform
   returns `Box` more than once.
3. Passes the results through the registered results-equality providers, the way Search Everywhere
   does, and asserts that `Box` remains once.

Before the fix the test fails with *Box is listed once: [s31.lua, s31.lua, s31.lua] expected:<1>
but was:<3>*. That is the reported symptom. It passes after the fix, and fails the same way when the
fix is reverted.

Checked in GoLand 2026.2.3 with the same project as the report. Searching `Box` in Go to Class now
lists `Box` once, plus `Box13`; searching `Base` lists `Base` once, plus `PBase`. Selecting `Box`
opens the `---@class Box<T>` line.
