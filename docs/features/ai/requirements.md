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
| [`AI-02`](02-semantic-tools/requirements.md) | **Semantic Context Toolset** | **S** | **todo** | Read-only MCP tools over the type engine, indexes, doc search, and diagnostics — including the diagnostics-delta quality gate for agent edit loops. |
| [`AI-03`](03-debugger-toolset/requirements.md) | **Debugger Toolset** | **C** | **todo** | Agent-driven debug sessions (breakpoints, stepping, locals, evaluate) over the debug adapters. Gated on the MobDebug hardening from [docs/review.md](../../review.md). |
| [`AI-04`](04-annotation-generator/requirements.md) | **LuaCATS Annotation Generator** | **S** | **todo** | Bulk-generate annotations from type inference (dynamic→typed migration assistant) + deterministic EmmyDoc→LuaCATS converter; optional off-by-default LLM polish. |

---

## Direction

Lunar does not build AI features that think (no chat UI, no completion model, no bundled
LLM vendor — full-line completion is closed to third-party languages); it builds the
semantic substrate that makes anyone's AI think better about Lua. Two customers: external
agents (via MCP: AI-01/02/03) and the human in the editor (AI-04, whose core is
deterministic inference with AI as optional garnish). Execution-capable tools go through
the JetBrains confirmation layer (Brave Mode); read-only tools are unconfirmed by design.

## Motivation
Modern developers heavily use AI agents. Exposing Lunar's LuaRocks and execution models programmatically enables these agents to construct valid codebases without leaving the editor context.
