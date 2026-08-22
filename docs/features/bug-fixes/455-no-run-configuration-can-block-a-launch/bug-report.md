---
id: "BUG-455"
title: "No Lunar run configuration can block a launch — validation throws the non-fatal exception tier, which the platform catches and ignores"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-455: `RuntimeConfigurationError` appears zero times in `src/`

Found 2026-08-22 by the [[DEBUG-06]] retroactive-requirements agent, derived from the platform
contract before Lunar's code was read.

## 1. Reproduction

1. Create a Lua run configuration with **no interpreter configured** (or one whose path no longer
   exists).
2. Press Run.

## 2. Expected vs actual

- **Expected**: the configuration is marked invalid and Run is refused with a message naming the
  field to fix.
- **Actual**: Run and Debug stay enabled, the launch proceeds, and the failure surfaces later as an
  `ExecutionException` from `startProcess()` — further from the cause and in a different dialog.

## 3. Root cause

The platform has two validation tiers and Lunar throws the wrong one.
`LuaRunConfiguration.checkConfiguration()` throws a **bare `RuntimeConfigurationException`**, and
`RunManagerImpl.canRunConfiguration` treats the tiers differently:

```kotlin
catch (_: RuntimeConfigurationError) { return false }
catch (_: RuntimeConfigurationException) { }
return true
```

Per `RunConfiguration.java`, the bare tier means "non-fatal, execution should still be allowed".
`RuntimeConfigurationError` — the tier that actually blocks — **appears zero times in `src/`**.

So every validation Lunar performs is advisory, including the ones that describe a target that
cannot possibly run.

## 4. Related, same feature, worth fixing together

- **`-07`**: `run/LuaRuntimeResolution.kt` computes `LuaToolHealth(fileExists, executable)` and
  returns a non-null tool regardless. Nothing in `run/` reads `.health` (grep: zero hits), so a
  deleted or non-executable interpreter validates clean. The data for the check is already in hand
  and thrown away.
- **`-15`**: the debug port is never probed. The `ServerSocket` binds *after* the interpreter is
  spawned; the resulting `BindException` is reported with **`log.error`**, so a user
  misconfiguration is raised as an IDE internal error, in a dialog naming neither the port nor the
  field.
- **`-22`**: three different wordings for "no runtime configured" — a hand-rolled literal in
  `LuaRunConfiguration`, another in `LuaTestRunConfiguration`, and the canonical
  `LuaToolResolver.notConfiguredMessage()` that neither calls. Exactly the drift BUG-378 swept.

## 5. Fix strategy

Throw `RuntimeConfigurationError` for conditions that make the target unrunnable — no interpreter,
missing or non-executable interpreter path, missing script, missing working directory — and reserve
the bare exception for genuine warnings. Route every message through
`LuaToolResolver.notConfiguredMessage()` so the three wordings become one.

Probe the debug port before spawning, and report a bind failure as a configuration error naming the
port, not via `log.error`.

## 6. Test strategy

**The existing test cannot catch this**: `assertFailsWith<RuntimeConfigurationException>` passes for
`Error` and `Warning` alike, since both subclass it. Assert the *exact* subclass. See [[BUG-461]].

## 7. Collateral finding, not fixed here

[[RUN-04]]'s `requirements.md` and its `risks-and-gaps.md` Gap 2.1 both state that
`checkConfiguration()` is not overridden. That is false against `main` — the override exists at
`LuaRunConfiguration.kt:275-285`. And the epic table still records `DEBUG-06` as **Full**, which
`-02` refutes.
