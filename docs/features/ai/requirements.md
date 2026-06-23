---
id: "AI"
title: "AI: AI-Assisted Development"
type: "epic"
status: "todo"
priority: "high"
folders:
  - "[[features]]"
---

# AI-Assisted Development Requirements (`AI`)

Lunar provides structured integration with external AI coding assistants using the Model Context Protocol (MCP) and exposure of its rich internal type/dependency systems.

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`AI-01`](01-mcp-server/requirements.md) | **MCP Server Integration** | **M** | **todo** | Declarative toolsets for dependency resolution and safe code verification. |

---

## Motivation
Modern developers heavily use AI agents. Exposing Lunar's LuaRocks and execution models programmatically enables these agents to construct valid codebases without leaving the editor context.
