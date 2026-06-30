---
id: "RUN-02-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "RUN-02"
folders:
  - "[[features/debug/run-02-run-configurations/requirements|requirements]]"
---

# RUN-02: Human Verification Checklists

Manual, in-IDE verification steps for RUN-02 requirements that cannot be asserted by a
headless test (UI surfaces, live process launch, debug session). Run these in the
containerized GoLand over VNC (see the **`verify-in-ide`** skill) or a `./gradlew runIde`
sandbox against the Lua test project at `~/Documents/src/lua/test`.

Mark each item `[x]` when verified; note the IDE build and date.

## TC 10 — RUN-02-02: Settings editor exposes and persists all options

> **Requirement**: [RUN-02-02](requirements.md#functional-requirements) (priority **M**) —
> the settings editor (`LuaRunSettingsEditor`) exposes interpreter, script file, working
> directory, source-path templates, environment, interpreter arguments, and program
> arguments. Covers Test Case **10** in `requirements.md`.

**Preconditions**
- [ ] A registered Lua interpreter exists (RUN-01); if not, add one in
      *Settings → Languages & Frameworks → Lua* (or the interpreter dialog) first.
- [ ] The Lua test project is open and contains at least one `.lua` script
      (e.g. `~/Documents/src/lua/test/main.lua`).

**Steps**
1. [ ] Open *Run → Edit Configurations…* (the Run/Debug Configurations dialog).
2. [ ] Click `+` and choose **Lua** from the add-configuration menu (verifies RUN-02-01 as a
       side effect). The editor form opens.
3. [ ] Confirm **all seven rows are present and editable**:
   - [ ] **Interpreter** — a combo box listing the registered `LuaInterpreter`(s); selecting
         one updates the field.
   - [ ] **Script file** — a file chooser; browse and pick `main.lua`; the path appears.
   - [ ] **Working directory** — a folder chooser; browse and pick the project root.
   - [ ] **Source path templates** — an expandable `;`-separated field; enter
         `./?.lua;./lib/?.lua` and confirm the expand control opens a multi-line view.
   - [ ] **Environment** — the env-vars field with a browse button; add `FOO=bar` and
         confirm it is accepted.
   - [ ] **Interpreter arguments** — a raw command-line field; enter `-W`.
   - [ ] **Program arguments** — a raw command-line field; enter `--flag value`.
4. [ ] Set a recognizable configuration **name** (e.g. `RUN-02 manual check`).
5. [ ] Click **Apply**, then **OK**.

**Expected**
- [ ] No errors or red validation banners while editing or on apply.
- [ ] Reopen *Run → Edit Configurations…* and select the saved configuration: every field
      shows the value entered in step 3 (interpreter, script file, working directory,
      environment, interpreter arguments, program arguments).
- [ ] **Known limitation**: the *Source path templates* field may appear empty on reopen —
      `applyEditorTo` does not currently write `sourcePath` back from the editor field (see
      `risks-and-gaps.md` → *TBD: `applyEditorTo` omits `sourcePath`*, RUN-00-DR-02). The
      field's **presence and editability** is what TC 10 verifies; its apply round-trip is
      tracked separately. Do not fail TC 10 solely on the source-path round-trip until
      RUN-00-DR-02 is resolved.

**Result**
- IDE build: `__________`  ·  Date: `__________`  ·  Outcome: ☐ Pass ☐ Fail
- Notes: `__________`

## See Also
- Requirements: [requirements.md](requirements.md) (Test Case 10)
- Design: [design.md](design.md) (§2.5 `LuaRunSettingsEditor`)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md) (RUN-00-DR-02)
