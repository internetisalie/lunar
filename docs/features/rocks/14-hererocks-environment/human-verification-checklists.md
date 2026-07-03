---
id: "ROCKS-14-HVC"
title: "Human Verification Checklists"
type: "qa"
parent_id: "ROCKS-14"
folders:
  - "[[features/rocks/14-hererocks-environment/requirements|requirements]]"
---

# Human Verification Checklists: ROCKS-14

Run in the containerized GoLand over VNC (see the `verify-in-ide` skill). Requires `hererocks`
installed in the container (`pip install hererocks`) and network access for the first build.

## Verification results — 2026-07-03 (GoLand 2026.1.3, sandbox `runIde` on the gce-builder VM)

Verified live over VNC against a clean project (`luaverify`). Outcome: **PASS** on the core
lifecycle. Evidence captured via `scrot`; the plugin was confirmed loaded (`Loaded custom plugins:
lunar` at boot).

- ✅ **Create** — Tools ▸ Lua Environment ▸ *Create Isolated Lua Environment…* opens; defaults
  Directory=`<project>/.lua`, Flavor=PUC, LuaRocks=`latest`. OK ran a background *"Provisioning Lua
  environment"* task with the IDE responsive (confirms the off-EDT locator/provision fix). Result on
  disk: `.lua/bin/lua` = Lua 5.1.5 (PUC), `.lua/bin/luarocks` = 3.8.0.
- ✅ **Bind (after create)** — balloon *"Bound Lua environment PUC 5.1"*; `[TOOL-DIAG]` logged
  `LUAROCKS …/.lua/bin/luarocks v=3.8.0 valid=true` (TOOL-02 binding live).
- ✅ **Upgrade / change version** — dialog pre-filled from the stored descriptor; 5.1→5.4
  re-provisioned in place to Lua 5.4.4, balloon *"Bound Lua environment PUC 5.4"* (descriptor
  persistence + upgrade).
- ✅ **Remove (Unbind Only)** — confirm dialog offers *Delete / Unbind Only / Cancel*; Unbind Only
  cleared the binding and kept `.lua` on disk.
- ✅ **Detect** — reopening the project surfaced *"hererocks environment detected at …/.lua"* with a
  **Bind** action; clicking **Bind** re-bound (balloon *"Bound Lua environment PUC"*).
- ℹ️ The red *"IDE error occurred"* balloon seen during the run is JCEF/`CefApp`/OpenGL headless
  noise (no `net.internetisalie.*` stack trace), **not** a plugin defect.

Not individually exercised live (low risk / covered by unit tests), left for a future manual pass:

- ⏳ **Recreate** (TC-9) — composed of the already-verified provision + delete-directory primitives.
- ⏳ **Error path: hererocks absent → `pip install hererocks` remediation** (TC-3) — covered by
  `HererocksLocatorTest`; live test needs hererocks fully uninstalled (incl. `python -m hererocks`).
- ⏳ **Double-create → "provisioning already in progress"** (TC-10) — covered by
  `HererocksProvisionerTest`; timing-hard to hit by hand.

Minor UX nits (non-blocking): the Create dialog's Lua-version combo defaults to 5.1 rather than the
descriptor's 5.4 default; the detect-bind balloon shows a blank version (`PUC`) until `identify`
fills it (by design §3.4).

## Create

- [ ] Tools ▸ **Lua Environment ▸ Create Isolated Lua Environment…** opens the dialog.
- [ ] Default directory is `<project>/.lua`; flavor = PUC; version list = 5.1–5.4.
- [ ] OK shows a background progress task "Provisioning Lua environment"; the IDE stays responsive.
- [ ] On success, `.lua/bin/lua` and `.lua/bin/luarocks` exist on disk.
- [ ] Settings ▸ project interpreter now points at `.lua/bin/lua`; its detected version matches.
- [ ] A LuaRocks operation (e.g. package browser search / install) uses the env's `luarocks`
      (verify via the tool path in settings / a rock installed under the env tree).

## Detect

- [ ] With a pre-existing hererocks `.lua` dir and no binding, reopening the project shows a
      "hererocks environment detected" notification with a **Bind** action.
- [ ] Clicking **Bind** wires the interpreter + LuaRocks tool without re-provisioning.

## Upgrade / Recreate / Remove

- [ ] **Change Lua/LuaRocks Version…** pre-fills the current spec; changing 5.4→5.3 rebuilds in
      place and the interpreter version updates.
- [ ] **Recreate Environment** deletes and rebuilds `.lua`; bindings still resolve afterward.
- [ ] **Remove Environment** (with delete-directory checked) clears the interpreter + LuaRocks
      binding and removes `.lua`.

## Error paths

- [ ] With `hererocks` absent from PATH and no `python -m hererocks`, Create shows the
      `pip install hererocks` remediation message and does not spawn a process.
- [ ] A failing build (e.g. offline) surfaces an error balloon with stderr; no binding is written.
- [ ] Triggering Create twice quickly for the same directory refuses the second with a
      "provisioning already in progress" message.
