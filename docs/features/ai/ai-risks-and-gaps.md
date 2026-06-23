---
id: "AI-RISKS"
title: "AI Epic: Risks & Gaps"
type: "risk"
status: "todo"
parent_id: "AI"
folders:
  - "[[features/ai/requirements|requirements]]"
---

# AI Epic: Risks & Gaps

## Critical Risks

### Risk 1.1: Security & Arbitrary Code Execution
- **Impact**: An AI agent could generate and execute malicious Lua code on the host machine.
- **Likelihood**: High
- **Mitigation**: Route all custom execution and terminal commands through `com.intellij.mcpserver.util.checkUserConfirmationIfNeeded`. This prompts a modal validation dialog to the developer unless "Brave Mode" is explicitly checked in Settings.

### Risk 1.2: Hard Classpath Dependency Failure
- **Impact**: If the `com.intellij.mcpServer` plugin is missing or disabled in the host IDE, Lunar will fail to load.
- **Likelihood**: High (since older or non-Ultimate IDE versions might lack the MCP plugin).
- **Mitigation**: Register all MCP toolsets inside an optional configuration file (`lunar-mcp.xml`) loaded only when the `com.intellij.mcpServer` plugin is active.

## Design Gaps

### Gap 2.1: None identified
- **Question**: All configuration decisions are pinned down.
- **Leaning**: None.
- **Resolved by**: N/A.

## Technical Debt & Future Work
- **TBD: Remote LuaRocks registries** — The current scope restricts package operations to local/project-bound trees. Exposing credentials and remote server settings is deferred.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|---|---|---|---|
| AI-00-DR-01 | Create a basic sandbox setup and test registering a dummy `McpToolset` to verify optional plugin class-loading boundaries. | Risk 1.2 | todo |
