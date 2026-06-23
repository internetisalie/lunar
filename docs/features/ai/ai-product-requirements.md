---
id: "AI-PRD"
title: "Product Requirements: AI-Assisted Development"
type: "spec"
status: "todo"
parent_id: "AI"
folders:
  - "[[features/ai/requirements|requirements]]"
---

# AI-Assisted Development — Product Requirements

## Feature Overview
As AI-assisted coding tools (such as Cursor, Claude Desktop, Windsurf, and GitHub Copilot) become standard developer environments, they require structured, programmatic access to the host IDE's context. 

The **AI-Assisted Development (AI)** Epic provides specialized integration layers that expose Lunar's custom Lua support (dependency management and execution verification) directly to external AI agents using the industry-standard **Model Context Protocol (MCP)**. This enables AI agents to develop, test, and maintain Lua projects with IDE-level awareness.

## Goals & Non-Goals
- **Goals**:
  - Provide an MCP server integration layer within Lunar.
  - Enable AI agents to perform project-bound LuaRocks tasks (search, install, list, build) programmatically.
  - Allow AI agents to safely verify Lua scripts and run configurations directly from the workspace.
  - Enforce strict security boundaries (Brave Mode checks) for code execution.
- **Non-Goals**:
  - Implementing a standalone MCP server runtime (we rely on JetBrains' built-in MCP server).
  - Supporting global LuaRocks environment modifications (restricted to project-bound environments).
  - Automating full Git operations via MCP (delegated to JetBrains' built-in VCS toolsets).

## Key Use Cases

### Use Case 1: Dependency Scaffolding and Installation
As a developer using an AI agent to build a Lua project, I want the agent to install needed libraries (e.g., `busted` for testing) without requiring me to drop to the terminal. The agent queries the LuaRocks repository, creates a rockspec, and runs installation tasks, which the IDE automatically indexes.

### Use Case 2: Code Execution and Test Verification
As an agent writing a Lua script, I want to verify that the script compiles and runs correctly. I trigger execution via the MCP toolset. The IDE pops up a consent dialog to protect my local machine, executes the script inside the project SDK environment upon approval, and returns stdout/stderr to my context.

## Functional Scope

| Feature | Capability | Priority |
|---|---|---|
| AI-01 | **MCP Server Integration** | M |
| AI-02 | **Type & Symbol Expositions** | S |

## Benefits
- **Frictionless Development**: AI agents can inspect, compile, and run code without user intervention.
- **Security & Transparency**: Prevents unauthorized terminal escapes by routing all execution through JetBrains' permission layers.
- **Ecosystem Standard**: Adheres to the JetBrains built-in MCP architecture, ensuring forward-compatibility.
