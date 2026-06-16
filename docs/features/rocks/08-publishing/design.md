---
id: ROCKS-08-DESIGN
title: Publishing Design
type: design
parent_id: ROCKS-08
status: done
---

# Technical Design: Publishing

Publishing lets a developer push the current project's `.rockspec` to
[luarocks.org](https://luarocks.org) directly from the IDE, by driving the
`luarocks upload` CLI. It reuses the binary path and notification group already
established by ROCKS-04, and stores the upload API key in the platform credential
store rather than any persisted settings XML.

## 1. Architecture Overview

| Component | Responsibility |
|---|---|
| `net.internetisalie.lunar.rocks.publish.PublishRockAction` | `DumbAwareAction` invoked from the project-view / editor popup on a `.rockspec`. Resolves the target rockspec, ensures an API key, then runs the upload in the background. |
| `net.internetisalie.lunar.rocks.publish.RockUploadCommand` | Pure builder: given the executable, rockspec path, API key, and a `--force` flag, produces the `GeneralCommandLine` arg list. Headlessly testable — no IDE, network, or real key. |
| `net.internetisalie.lunar.rocks.publish.LuaRocksApiKeyStore` | Thin wrapper over `PasswordSafe` using a stable `CredentialAttributes`. Reads/writes/clears the luarocks.org upload API key. |

- **Binary**: reuse `LuaRocksSettings.getInstance().executablePath` (ROCKS-04) — no
  second binary-path setting.
- **Process**: `LuaProcessUtil.capture(GeneralCommandLine, timeout)` under
  `Task.Backgroundable` (off the EDT).
- **Reporting**: the existing `notification.group.lunar.luarocks` group.
- **Icon**: `LuaIcons.ROCKET`.

## 2. API key storage (PasswordSafe)

The API key is a secret, so it is **never** written to `lunar.xml`. It is stored via
`com.intellij.ide.passwordSafe.PasswordSafe.instance` keyed by a stable
`CredentialAttributes`:

```kotlin
val serviceName = generateServiceName("Lunar LuaRocks", "luarocks.org API key")
val attributes = CredentialAttributes(serviceName)
```

- `LuaRocksApiKeyStore.getApiKey()` → `PasswordSafe.instance.getPassword(attributes)`.
- `LuaRocksApiKeyStore.setApiKey(key)` → `PasswordSafe.instance.setPassword(attributes, key)`
  (passing `null`/blank clears it).

`generateServiceName` is the platform-recommended way to namespace the key; the
constant subsystem/key strings live in `LuaRocksApiKeyStore` so the key is stable
across releases.

## 3. Action flow

1. `update(...)`: enabled + visible only when the selected `VirtualFile` is a
   `.rockspec` (gating via `CommonDataKeys.VIRTUAL_FILE` extension check) and a
   project is present.
2. `actionPerformed(...)`:
   a. Resolve the rockspec `VirtualFile`.
   b. Read the API key from `LuaRocksApiKeyStore`. If absent/blank, prompt with
      `Messages.showPasswordDialog`; persist the entered key. Cancel aborts.
   c. Run a `Task.Backgroundable`: assemble the command via
      `RockUploadCommand.build(exe, rockspecPath, apiKey)`, execute with
      `LuaProcessUtil.capture(..., UPLOAD_TIMEOUT_MS)`.
   d. On exit code 0 → INFORMATION notification ("Published <name> to LuaRocks").
      Non-zero → ERROR notification with trimmed stderr. (A 401/invalid-key stderr
      is surfaced verbatim so the user knows to re-enter the key.)

## 4. Command form

The documented luarocks upload CLI is:

```
luarocks upload <path/to/file.rockspec> --api-key=<KEY>
```

`RockUploadCommand.build` emits exactly `[exe, "upload", rockspecPath, "--api-key=<key>"]`,
with an optional `--force` appended when re-uploading an existing version. The API
key is passed via the `--api-key=` flag form (single token) so it is not split.

## 5. Testing

- `RockUploadCommandTest` — asserts the assembled arg list for the basic and
  `--force` cases (no key value leaks beyond the flag form).
- `LuaRocksApiKeyStoreTest` — asserts the stable credential service name / key
  constant (so the storage key cannot drift silently between releases).
- `PublishRockActionGatingTest` — asserts `isRockspec` accepts `*.rockspec` and
  rejects `.lua` / extensionless files.

None require a live network, a real API key, or a running IDE upload.
