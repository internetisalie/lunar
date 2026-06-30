---
id: "AI-01-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "AI-01"
folders:
  - "[[features/ai/01-mcp-server/requirements|requirements]]"
---

# AI-01: Risks & Gaps

## Critical Risks

### Risk 1.1: Temp file leak from `execute_lua_in_console`
- **Impact**: Accumulation of stale `.lua` files in `PathManager.getTempDir()` if JVM crashes mid-call.
- **Likelihood**: Low
- **Mitigation**: `finally` block deletes the temp file. OS-level temp cleanup catches JVM-crash leftovers. Temp filenames include a UUID prefix (`lunar_mcp_`) to avoid collisions.

### Risk 1.2: `luarocks install --local` on a project without a tree
- **Impact**: `luarocks install --local` fails if no local tree exists yet (no prior `luarocks init` or `luarocks make`).
- **Likelihood**: Medium (first-time project setup)
- **Mitigation**: The tool returns stderr text; the LLM can see "no local tree" and suggest `luarocks init` or `luarocks make` first. Document this in tool descriptions.

## Design Gaps

### Gap 2.1: `LuaToolManager` integration for binary resolution
- **Question**: When should `LuaRocksSearchService` and `LuaRocksCli` switch to `LuaToolManager.getEffectiveTool()`?
- **Leaning**: ROCKS wave 2 (ROCKS-06: Project LuaRocks Environment) will own this. Until then, `LuaRocksSettings.executablePath` is sufficient.
- **Resolved by**: Deferred decision; tracked in AI-01-05 / ROCKS-06.

### Gap 2.2: Global rock visibility in `luarocks_list`
- **Question**: `LuaRocksTreeLocator` only sees project-local trees. How to surface system-global installs?
- **Leaning**: Merge with `luarocks list --porcelain` or `luarocks show --home` per package.
- **Resolved by**: AI-01-05 (Could) requirement.

## Technical Debt & Future Work
- **TBD: `LuaToolManager` binary resolution** — switch `LuaRocksSearchService` and `LuaRocksCli` to use `LuaToolManager.getEffectiveTool(project, LUAROCKS)?.path ?: LuaRocksSettings.executablePath` once ROCKS wave 2 lands.
- **TBD: `isExperimental() = false`** — after validating tool descriptions with real LLM sessions, flip to stable.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|---|---|---|---|
| AI-01-DR-01 | Verify `com.intellij.mcpServer` plugin ID is present in GoLand 2026.1.3 (the test IDE) | Risk of missing dependency at test time | todo |
| AI-01-DR-02 | Verify `checkUserConfirmationIfNeeded` is accessible from outside the `com.intellij.mcpserver` package (API visibility) | Risk of compilation failure | todo |
| AI-01-DR-03 | Confirm `LuaRocksTreeLocator.installedRocks()` works correctly in a headless test environment (no project files on disk) | Test strategy risk | todo |