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
- **Result**: ⬜ Pass / ⬜ Fail
