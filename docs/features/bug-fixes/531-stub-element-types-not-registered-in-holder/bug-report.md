---
id: "BUG-531"
title: "The Stubs index fails to build for Lua files: Lunar's stub element types are not registered through `stubElementTypeHolder`"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-531: the Stubs index fails for Lua files

First seen during BUG-530's live check, on a fresh `runIde261` session (GoLand 2026.1.3, build
GO-261.25134.147), running Lunar built from `main` at `d26298d2`. It is not specific to 2026.1:
GoLand 2026.2.3 fails the same way when the same order of events occurs.

## 1. Reproduction
Deterministic on both GoLand 2026.1.3 (`runIde261`) and 2026.2.3 (`runIde`):
1. Open a project that contains a Go file (`go.mod` + `main.go`) and no Lua file. Its Go stubs
   initialize the stub serializers before any Lunar parser class has been loaded.
2. Once indexing finishes, add a `.lua` file to the project from outside the IDE and run
   *Synchronize* (`Ctrl+Alt+Y`).

The BUG-530 session reached the same order through *File → New → Project… → LuaRocks*, whose
generator writes the first `.lua` file after the IDE has started.

Opening a project whose `.lua` files exist at startup, or one with no other stub-indexed files,
usually does not reproduce it: Lunar's classes then load before the serializers initialize.

## 2. Expected vs Actual Behavior
- **Expected**: Lua files are stub-indexed, and nothing from Lunar appears as SEVERE.
- **Actual**: when the first Lua file is indexed, the IDE logs, three times (one per type):
  ```
  SEVERE - #c.i.p.s.IStubElementType - All stub element types should be created before index initialization is complete.
  Please add the class net.internetisalie.lunar.lang.psi.stubs.impl.LuaLocalVarStubElementType with external ID lunar.local.var.decl containing stub element type constants to "stubElementTypeHolder" extension.
  ```
  The other two are `LuaFuncStubElementType` (`lunar.func.decl`) and `LuaLocalFuncStubElementType`
  (`lunar.local.func.decl`). The file's stub build then fails:
  - on 2026.1.3, `Failed to build index 'Stubs' for file …` caused by
    `NullPointerException: … "idValue" is null at StubSerializerEnumerator.getSerializerId`;
  - on 2026.2.3, `UnhandledException: No ID found for serializer LOCAL_VAR_DECL
    (…LuaLocalVarStubElementType), external id:lunar.local.var.decl` from the assertion in the
    same method.

  A file in this state is missing from every stub index, so Go to Symbol, Go to Class and
  everything else that resolves through `LuaGlobalDeclarationIndex`, `LuaClassNameIndex` or
  `LuaAliasIndex` miss its declarations for the rest of the session.

## 3. Context / Environment
- **Relevant Files**: `src/main/resources/META-INF/plugin.xml` registered the three `<stubIndex>`
  extensions but no `<stubElementTypeHolder>`.
  `src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaStubElementTypes.kt` holds the three types.
- **Evidence**: `~/.cache/claude-scratch/lunar/bug-530/idea-runIde261-bug531.log`, lines 1157 to
  1470, and the red and green runs under `~/.cache/claude-scratch/lunar/bug-531/`.

## 4. Root Cause
The platform assigns stub serializer IDs once, from the stub element types that exist when it
initializes the serializers. `SerializationManagerImpl.doInitSerializedUnderLock` (both builds):
1. loads every `stubElementTypeHolder` holder (`IStubElementType.loadRegisteredStubElementTypes`);
2. registers every `IStubElementType` then present in the `IElementType` registry;
3. sets `IStubElementType.lazyExternalIds`, after which any newly constructed `IStubElementType`
   logs the SEVERE above (`IStubElementType.<init>`).

A type created later never gets an ID, so `StubSerializerEnumerator.getSerializerId` finds none
when the stub tree is serialized.

Lunar's three stub types are `val`s of the Kotlin object `LuaStubElementTypes`. Nothing loads that
object until the generated `LuaElementTypes` interface initializes. That happens at
`LuaElementTypes.java:11`, `LuaStubElementTypes.getStubElementType("ARGS")`, and is first reached
from `LuaLexer.<init>`. `ParserDefinition.getFileNodeType`, which the platform calls before
initializing the serializers, returns `LuaFileElementType` and does not touch them. So whether the
types exist in time depends on whether some Lunar code happened to construct a lexer or parser
earlier. Both logs show the same first loader when they fail: `IdIndexFilter.acceptFileType` →
`LuaFindUsagesProvider.getWordsScanner` → `new LuaLexer()`, while scheduling the new file for
incremental indexing.

**Why 2026.2.3 seemed unaffected.** It is not. `javap` of both platforms' jars shows
`IStubElementType` is byte-identical between 261.25134.147 and 262.10968.67. The only difference
in `SerializationManagerImpl.doInitSerializedUnderLock` is that 262 calls
`StubElementRegistryServiceImpl.ensureStateLoaded()`, which runs `stubElementRegistryExtension`
registrations. Lunar has none, so this does not affect Lunar. Under the reproduction above, 2026.2.3
logs the same three SEVERE messages, and a class-load trace
(`-XX:LogClassLoadingCauseFor=…LuaStubElementTypes`) shows the same first loader. BUG-530's
2026.2.3 run happened to load Lunar's lexer before the serializers initialized.

**Earlier releases.** `plugin.xml` has never registered a holder; `git log -S stubElementTypeHolder`
finds it only in a committed test log. The defect therefore exists in every release since stub
indexing arrived (DOC-06, `ae27af92`), on every supported IDE build. It does not depend on which
platform the plugin was compiled against.

## 5. Fix Strategy
Register `LuaStubElementTypes` declaratively in `plugin.xml`:
```xml
<stubElementTypeHolder class="net.internetisalie.lunar.lang.psi.LuaStubElementTypes"/>
```
Without `externalIdPrefix`, `StubElementTypeHolderEP.initializeOptimized` force-initializes the
holder class (`Class.forName(name, true, loader)`). That runs the Kotlin object's static
initializer and constructs the three types before `lazyExternalIds` is set.

`externalIdPrefix` is **not** used: it promises that the holder is an interface whose field names,
prefixed, equal each type's external ID. That would make the platform read the fields lazily
instead of loading the class. Lunar's holder is an object, and its IDs (`lunar.local.var.decl`)
are not `prefix + "LOCAL_VAR_DECL"`. Using the prefix would mean renaming the external IDs, and
with them every persisted stub, for a startup-time saving on three fields.

No element type is added or moved; the types stay static singletons in the same holder.

## 6. Test Strategy
- `LuaStubElementTypeHolderTest` (light fixture) asserts:
  - the `stubElementTypeHolder` EP contains `LuaStubElementTypes` with no prefix;
  - every Lua `IStubElementType` in the `IElementType` registry is a static constant of a
    registered holder, so a future stub type created outside the holder fails the test.

  Both tests fail without the `plugin.xml` entry.
- No light test reproduces the platform's ordering. In the test application, Lunar's types already
  exist when the serializers initialize, which is why the suite never logged the SEVERE. The red
  and green evidence is the live run in §1 on both IDEs: before the fix, three SEVERE messages
  and a failed stub build; after it, neither, and Go to Symbol finds `M.run` in the added file.
