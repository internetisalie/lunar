---
id: "BUG-498"
title: "A class merged with a same-named stdlib class gets its members in a different order from one JVM to the next"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-498: a merged `---@class File` is not deterministic across JVMs

Found by [[TYPE-12]]'s DR-09, while recording golden type renderings from the unmodified engine.

## Reproduction

A file that declares a class with the same name as a class in the bundled standard-library stubs:

```lua
---@class File
local File = {}
---@param fmt? string
---@return string
function File:read(fmt) end
function File:close() end
```

The bundled `runtime/standard/lua-5.x/io.lua:29` also declares `---@class File`. Render the inferred
type of `local File`, including its members, in several separate test JVMs.

## Expected

The same members, in the same order, every time.

## Actual

Executed on `40ae01de` (the TYPE-12 planning branch, engine unchanged from `e762dc74`), libvirt
builder, a throwaway golden harness. The type names are identical across JVMs. The deep rendering,
which includes the member order, is not:

```
golden shapes/fileUnion.golden: line 9:
  expected '(15,30)|LuaLocalVarDeclImpl|{ ... } | File|5b2b446e729c9856'
  actual   '(15,30)|LuaLocalVarDeclImpl|{ ... } | File|cd5e4ba67529a21c'
```

Across the 8 test JVMs after the recording, 5 produced the other rendering, and it was the same
alternative each time. Only the `[elements]` section of that file moved, on 15 lines. Its errors,
globals and file return type were identical. Thirty other golden files, none of which shares a
class name with the stubs, were byte-identical in every run. Evidence:
`dr09-3-verify.log` and `out-*/type-golden/actual/shapes/fileUnion.golden` in the TYPE-12 scratch
directory.

## Root cause (read, not yet instrumented)

`LuaTypeManagerImpl.materializeClass` iterates its `decls` in the order the index returns them and
fills `membersMap`, a `LinkedHashMap`, so member order follows declaration order. With one
declaration in the project and one in the stubs, that order appears to vary between JVMs.

The same loop is **last write wins** for a member both declarations carry (its own comment says
so). So the observed reordering is the harmless half. If the two declarations disagree on a
member's type, **which type wins** can vary in the same way. That is unmeasured, and it is the
reason this is a bug rather than a cosmetic ordering difference.

## Fix strategy

Make the declaration order deterministic before merging, for example by sorting `decls` by a stable
key (project before library, then file path, then offset), and state which side wins on a member
conflict. First confirm by instrumentation that the index order is the varying input.

## Test strategy

A test that declares a stub-colliding class with a member whose type disagrees with the stub, asserts
the merged member type and order, and runs in fresh JVMs. Repeat the DR-09 harness run on several
JVMs and require byte-identical renderings.
