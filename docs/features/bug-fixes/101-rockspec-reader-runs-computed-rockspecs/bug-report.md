---
id: "BUG-101"
title: "The package browser runs a downloaded rockspec in a Lua interpreter whenever its fields are built from locals or concatenation"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-101: The package browser runs a downloaded rockspec whenever its fields are computed

## 1. Reproduction
In the package browser, select a rock whose server rockspec computes its fields. For example,
luacheck's rockspec builds `version` and `source.url` from a `local` and string concatenation.
`LuaRocksMetadataService.fromServer` (`rocks/browser/LuaRocksMetadataService.kt:88`) first tries
`RockspecMetadataReader.read`, the literal reader that never runs the file (BUG-58). That reader
returns null for a computed field, so the service falls back to `evaluated()`, which hands the
downloaded file to `RockspecBridge.evaluate`: a real Lua interpreter, run with an empty environment
and a timeout (BUG-50, BUG-68).

## 2. Expected
A rockspec whose fields are built only from string literals, `local` bindings and `..` concatenation
is read without being run. The interpreter is the fallback only for genuinely dynamic files.

## 3. Actual
Every computed rockspec is executed. The empty environment removes the standard library, but
`evaluate` has no trust gate, so an untrusted file can still busy-loop or allocate (for example
`("x"):rep(n)`) in a child process until the timeout.

## 4. Scope
Earlier measurement put 18 of the corpus's 29 computed rockspecs within reach of a reader that
follows `local` bindings and concatenation. Re-measure this when planning.
