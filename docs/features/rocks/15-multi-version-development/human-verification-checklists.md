---
id: "ROCKS-15-HVC"
title: "Human Verification Checklists"
type: "qa"
parent_id: "ROCKS-15"
folders:
  - "[[features/rocks/15-multi-version-development/requirements|requirements]]"
---

# Human Verification Checklists: ROCKS-15

Run in the containerized GoLand over VNC (see the `verify-in-ide` skill). Requires `hererocks`
installed in the container (`pip install hererocks`) and network access for provisioning. Provision
at least two envs (e.g. PUC 5.3 and PUC 5.4) via ROCKS-14 Create, or via the batch action below.

## Verification results — 2026-07-04 (GoLand 2026.1.3, sandbox `runIde` on the gce-builder VM)

Verified live over VNC. An initial pass exposed a real integration defect cluster (live
create/detect/batch updated only ROCKS-14's legacy field, so envs did not surface in the switcher
until a project reload, and detection re-fired every reopen). Fixed in commit `ea5fb1aa`
(set-aware activation for all live paths + detection skip via `resolveAllEnvs()` + widget refresh)
and **re-verified live — PASS**.

- ✅ **Widget** — reads `No Lua env` on an empty set; shows the active env's versioned label
  (`PUC 5.1` / `PUC 5.3`) once envs exist.
- ✅ **Batch provisioning** — *Provision Version Matrix* with `5.1, 5.3` created
  `.lua-matrix/PUC-5.1` (5.1.5) + `.lua-matrix/PUC-5.3` (5.3.6) and **both appeared in the switcher
  immediately, no reload** (the primary fix; ROCKS-15-05).
- ✅ **Switcher popup** — lists every env with the active one checked, plus *Add environment…*.
- ✅ **Active switch** — selecting `PUC 5.1` updated the status-bar label live and rebound the
  interpreter + LUAROCKS tool (ROCKS-15-02/03).
- ✅ **Migration** — a legacy single-env project opens with that env migrated into the set + active.
- ✅ **Run Test Matrix** — action surfaces under Tools ▸ Lua Environment and gracefully reports
  *"No rockspec discovered for the matrix"* when the project has no rockspec (no crash / no process).
- ✅ **Detection skip-via-set** — an env already in the set is not re-offered (unit-tested +
  code-verified; the legacy-field re-fire is fixed).

Not driven to a populated grid live (needs a working rockspec fixture across versions): the
**Run Test Matrix tool-window results table**. Its per-env command construction, pass/fail
aggregation, and one-task-per-env concurrency are unit-covered (`MatrixRunnerTest`, TC-7/TC-8).

## Migration & set (ROCKS-15-01)

- [ ] A project provisioned under ROCKS-14 (single env) opens with that env present in the switcher
      and marked active — the legacy descriptor was migrated, nothing lost.
- [ ] After provisioning a second env, both appear in `.idea/lunar.xml` under `hererocksEnvs`.

## Version switcher widget (ROCKS-15-02/03)

- [ ] A "Lua Environment" widget in the status bar shows the active env label (e.g. "PUC 5.4").
- [ ] Clicking it opens a popup listing every env, the active one marked, plus "Add environment…".
- [ ] Selecting a different env updates the status-bar label and the project interpreter (Settings ▸
      interpreter) to that env's `bin/lua`; a LuaRocks operation now uses that env's `luarocks`.
- [ ] "Add environment…" opens the ROCKS-14 Create dialog.
- [ ] With no envs, the widget reads "No Lua env".

## Test matrix (ROCKS-15-04)

- [ ] Tools ▸ Lua Environment ▸ **Run Test Matrix…** offers a command (make/test/build) and runs it
      against every env on background tasks; the IDE stays responsive.
- [ ] The "Lua Matrix" tool window shows one row per env with PASS/FAIL and exit code.
- [ ] Selecting a row shows that env's captured luarocks output.
- [ ] A rockspec that fails on one Lua version but passes on another yields a mixed PASS/FAIL grid.
- [ ] With no envs, the action is disabled (or reports "no environments").

## Batch provisioning (ROCKS-15-05)

- [ ] **Provision Version Matrix…** accepts a base dir and a list of {flavor, version} rows.
- [ ] Running it provisions one env per row under `<base>/<flavor>-<version>` and adds each to the
      switcher on success; failures surface per-row error balloons without blocking the others.
