---
id: "BUG-531"
title: "On GoLand 2026.1.3 the Stubs index fails to build for Lua files: Lunar's stub element types are not registered through `stubElementTypeHolder`"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-531: the Stubs index fails on 2026.1.3

Seen during BUG-530's live check, on a fresh `runIde261` session (GoLand 2026.1.3, build
GO-261.25134.147, bundled JDK 25.0.3), running Lunar built from `main` at `d26298d2`. The
2026.2.3 run (`runIde`) of the same session did not log it.

## 1. Reproduction
1. Run `runIde261` with a fresh sandbox.
2. Create a LuaRocks library project with File → New → Project… → LuaRocks, or open any project that
   contains a `.lua` file.

## 2. Expected vs Actual Behavior
- **Expected**: Lua files are stub-indexed, and nothing from Lunar appears as SEVERE.
- **Actual**: as soon as the first Lua file is indexed, the IDE logs:
  ```
  SEVERE - #c.i.p.s.IStubElementType - All stub element types should be created before index initialization is complete.
  Please add the class net.internetisalie.lunar.lang.psi.stubs.impl.LuaLocalVarStubElementType with external ID lunar.local.var.decl containing stub element type constants to "stubElementTypeHolder" extension.
  ```
  The same message follows for `LuaFuncStubElementType` (`lunar.func.decl`) and
  `LuaLocalFuncStubElementType`. Then:
  ```
  SEVERE - #c.i.u.i.d.BrokenIndexingDiagnostics - Failed to build index 'Stubs' for file file:///home/builder/bug530-live/mylib261/src/mylib261.lua (id = 789) (file type = Lua)
  ```
  The underlying cause is a null pointer in `StubSerializerEnumerator.getSerializerId`. Navigation
  and every feature that resolves through stubs would then miss that file.

## 3. Context / Environment
- **Relevant Files**: `src/main/resources/META-INF/plugin.xml`, which declares the three
  `<stubIndex>` extensions but no `<stubElementTypeHolder>`.
  `src/main/kotlin/net/internetisalie/lunar/lang/psi/stubs/impl/Lua*StubElementType.kt`.
- **Evidence**: `~/.cache/claude-scratch/lunar/bug-530/idea-runIde261-bug531.log`, lines 1158 to
  1458.
- **Not established**:
  - why 2026.2.3 did not log it;
  - whether it reproduces every time on 2026.1.3, or depends on the order in which classes load;
  - whether released versions before MAINT-21 had it.

  The platform's own fix suggestion is in the message. Plan with `plan-bug`.
