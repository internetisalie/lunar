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
