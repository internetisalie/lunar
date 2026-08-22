---
id: "BUG-456"
title: "The debug listener binds all interfaces and offers arbitrary Lua execution to whoever connects first"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-456: `ServerSocket(port)` binds `0.0.0.0`, and the first thing offered is `EXEC`

Found 2026-08-22 by the [[DEBUG-05]] retroactive-requirements agent.

## 1. What happens

`LuaDebuggerController.connect()` opens the debuggee listener with `ServerSocket(serverPort)`. That
constructor binds the **wildcard address** — verified on this project's JDK (corretto-21):
`wildcard = true`. There is no bind-address option, and `clientAddress` is captured only to log.

The protocol offers `EXEC` — evaluate an arbitrary Lua string in the debuggee — and the debuggee
accepts commands from whoever connects. Lunar performs no authentication and no origin check.

## 2. Why this matters, stated carefully

This is exposure, not a remote exploit chain: it requires an attacker with network reach to the
developer's machine on the debug port, during a debug session. On a laptop behind a firewall the
practical risk is low. On a shared network, a corporate VLAN, a CI runner, or any host with the port
forwarded, it is a live path to running code as the developer.

It is worth fixing because **the fix is one argument** and the current behaviour is not a deliberate
choice — it is the default of the constructor that was used.

## 3. Fix strategy

Bind loopback explicitly:

```kotlin
ServerSocket(serverPort, backlog, InetAddress.getLoopbackAddress())
```

Loopback is correct for every use Lunar supports today, because the debuggee is always launched
locally ([[DEBUG-05]] establishes that remote attach does not exist). If remote attach is ever
built, the bind address becomes a user-visible setting and this becomes an explicit, informed
choice rather than an inherited default.

Worth doing at the same time: reject or log a connection from an unexpected origin rather than
silently serving it.

## 4. Test strategy

Assert the bound address is loopback. That is a cheap, non-flaky unit assertion on the socket, and
it pins the property so a later refactor cannot silently widen it again.
