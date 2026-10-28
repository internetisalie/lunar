---
id: "TARGET-11-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "TARGET-11"
folders:
  - "[[features/target/11-neovim-definition-library/requirements|requirements]]"
---

# Verification Checklists: TARGET-11 — Neovim (`vim.*`) Definition Library

Run per the `verify-in-ide` skill, network enabled, with the plugin built from the implementation
tree.

## 1. Live enable

### Scenario 1.1: Enable Neovim and use `vim` (TC 8)
- **Setup**: A Lua project holding `init.lua` = `vim.notify("x")\nlocal buf = vim.api.nvim_get_current_buf()\n`;
  language level 5.1; no definition library enabled.
- **Steps**:
  1. Settings ▸ Languages & Frameworks ▸ Lua ▸ Lua Project ▸ Definition Libraries.
  2. Confirm the *Neovim (vim.\*)* row shows licence `Apache-2.0 AND Vim AND MIT` and an attribution
     link to `https://github.com/neovim/neovim`.
  3. Tick it, press *Apply*, wait for the "Fetching Lua definition libraries" task and indexing to end.
  4. Open `init.lua`. On a new line type `vim.` and invoke completion.
  5. `grep -c 'Error while indexing.*definitions/neovim-' <sandbox>/log/idea.log`.
  6. Delete the `vim.` line typed in step 4, then append the lines `local x = M` and
     `local y = uv` to `init.lua`.
- **Expected**: step 2 as stated; after step 3 the row reads Fetched; in step 4 neither `vim`
  is underlined or struck through, and the completion list contains `notify` and `api`; step 5 prints `0`;
  in step 6 `M` carries `Undeclared variable 'M'` ([[BUG-509]]) and `uv` carries
  `Undeclared variable 'uv'` ([[BUG-510]]).
- **Result**: ✅ Pass, 2026-10-28, GoLand 2026.2.3 `runIde` on the libvirt builder, plugin built from
  `beed0fc4` (Phase 1). Evidence (`$S` = `~/.cache/claude-scratch/lunar/target-11/impl`):
  - Before step 1, with no library enabled, both `vim` references were underlined
    (`$S/shots/08-before.png`).
  - Step 2: the row read `Neovim (vim.*)`, `Not fetched`, `Apache-2.0 AND Vim AND MIT`,
    `https://github.com/neovim/neovim` (`$S/shots/10-step2.png`).
  - Step 3: after *Apply*, the definitions cache held `neovim-779c36969e31080503be2dfccbcc3f999e289606`
    with 128 `.lua` files, and the log shows the indexing task for `Synthetic library 'Lua Definition
    Libraries'`. The open panel kept showing `Not fetched`, because it does not re-read state after
    *Apply*. Reopened, the row read `Fetched` (`$S/shots/20-fetched.png`).
  - Step 4: neither `vim` was underlined or struck through (`$S/shots/13-after.png`). Completion on
    `vim.` listed `notify` (`$S/shots/15-notify.png`) and `api` (`$S/shots/16-api.png`).
  - Step 5: `0`. The whole log also had no `Error while indexing` line and no `ERROR` or `SEVERE`
    entry.
  - Step 6: the Problems view listed `Undeclared variable 'M' :3` and `Undeclared variable 'uv' :4`,
    plus three `Unused local variable` warnings, and nothing on `vim` (`$S/shots/19-problems.png`).
  - TC 8's mutations (reverting BUG-504, BUG-509 or BUG-510) were not run live. DR-10 and DR-11 ran
    their equivalents in tests.
