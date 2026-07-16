---
id: "BUG-376"
title: "Publish Rock API key cannot be changed or cleared once stored"
type: "bug"
parent_id: "BUG"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-376: Publish Rock API key cannot be changed or cleared once stored

## 1. Reproduction

1. Right-click a `.rockspec` → *Publish Rock to LuaRocks…* and enter an API key when prompted —
   it is stored per-server in PasswordSafe.
2. Rotate (or mistype) the key on luarocks.org, then publish again.

The action reuses the stored key without prompting; the upload fails with the server's auth error
in a notification. There is no UI anywhere in the plugin to change or clear the stored key — the
only recovery is digging into the platform credential store manually (or the OS keychain).

## 2. Expected vs Actual Behavior

- **Expected**: a stored secret is manageable — either a settings control ("clear stored API
  key"), or the publish flow re-prompts when the server rejects the key.
- **Actual**: the key is prompted for exactly once per server and then silently reused forever.
  On upload failure the error notification shows stderr but offers no path to re-enter the key.

## 3. Context / Environment

- **Confidence**: high — root-caused in code (the failure UX is inferred from the code path, not
  yet reproduced against a live registry with a bad key).
- **Root cause**: `src/main/kotlin/net/internetisalie/lunar/rocks/publish/PublishRockAction.kt`:
  - `ensureApiKey(...)` (lines 47-58) returns the stored key when present
    (`LuaRocksApiKeyStore.getApiKey(server)?.let { return it }`, line 48) — the password dialog is
    only shown when no key is stored.
  - `upload(...)` (lines 60-81) reports a non-zero exit as a plain error notification (lines
    74-77) with no auth-failure detection or "re-enter key" action.
- The store itself supports clearing (`LuaRocksApiKeyStore.setApiKey(server, null)`,
  `src/main/kotlin/net/internetisalie/lunar/rocks/publish/LuaRocksApiKeyStore.kt:47-50`) — only
  the UI to reach it is missing.

## 4. Other Notes

- **Fix direction** (either or both):
  1. A "Clear stored LuaRocks API key" action/link (e.g. on the LuaRocks settings page, or a
     notification action on publish failure).
  2. Detect an auth failure in the `luarocks upload` output and re-prompt (pre-filling nothing),
     overwriting the stored key on success.
- Per-server keying and the legacy-key fallback (ROCKS-06-07/ROCKS-08-02, TC 8) are documented in
  `LuaRocksApiKeyStore`'s KDoc — any fix must preserve those stable service names.
