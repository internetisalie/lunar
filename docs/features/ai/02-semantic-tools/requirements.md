---
id: "AI-02"
parent_id: "AI"
type: "feature"
status: "todo"
priority: "high"
folders:
  - "[[features/ai/requirements|requirements]]"
title: "AI-02: Semantic Context Toolset"
---

# AI-02: Semantic Context Toolset (Type & Symbol Expositions)

**Requirement**: Read-only MCP tools exposing Lunar's semantic engines — type inference,
symbol resolution, indexes, documentation search, and diagnostics — to external AI agents,
including a diagnostics-delta quality gate for agent edit loops.
**Priority**: Should
**Status**: Not Implemented

---

## Overview

Agents currently understand Lua projects by grepping text. Lunar's PSI, type graph, and
seven indexes answer the same questions precisely and cheaply. This feature is almost pure
exposure of existing engines: each tool is a thin adapter over an existing service, wired
through the AI-01 registration infrastructure (`lunar-mcp.xml`, loaded only when
`com.intellij.mcpServer` is active).

All tools in this feature are **read-only**: no execution-confirmation dialog (Brave Mode
is irrelevant), no file mutation, no subprocesses.

### Toolset

| Tool | Backing engine | Returns |
|---|---|---|
| `lua_project_brief()` | TARGET settings, interpreter SDK, ROCKS discovery, source roots, test configs | Structured project summary: target platform/version, language level, interpreter, source roots, direct rock dependencies, test framework, entry points |
| `lua_type_at(file, line, column)` | Type-graph snapshot | Inferred type (display name + LuaCATS-syntax rendering), narrowed vs declared |
| `lua_signature(file, line, column)` | Type engine + LuaCATS PSI | Params (names/types/optionality), returns, overloads, `@deprecated`, doc comment |
| `lua_resolve(name, context_file)` | Reference resolution + stub indexes | Declaration site(s) with file/line and kind (local/global/class member/stdlib) |
| `lua_find_usages(file, line, column, max)` | Find-usages + read/write classifier | Usage list with read/write flags, capped with explicit truncation marker |
| `lua_class_info(class_name)` | `LuaClassNameIndex` + `resolveMember` + hierarchy | Fields, methods (with signatures), supertypes, known subtypes |
| `lua_module_deps(file?)` | `LuaFileBindingsIndex` require extraction | Require-graph edges for one file or the project (capped) |
| `lua_docs_search(query, max)` | DOC-06 full-text documentation index | Ranked matches with symbol, snippet, file/line |
| `lua_diagnostics(file)` | Inspections + type checks + luacheck | Structured list: inspection id, severity, range, message, quick-fix availability |
| `lua_diagnostics_delta(file, baseline)` | Same, plus baseline store | New/resolved/unchanged diagnostics vs a baseline token from a prior call |

**The quality-gate loop** (`lua_diagnostics` → edit → `lua_diagnostics_delta`) is the
highest-value element: an agent can ask "did my edit make this file semantically worse?"
and receive a precise answer no text-based linter can give. Baselines are session-scoped
tokens (opaque, capped count, LRU-evicted) — no persistence.

## Acceptance Criteria

- [ ] All tools registered via the AI-01 optional-plugin mechanism (`lunar-mcp.xml`);
      Lunar loads normally when the MCP plugin is absent
- [ ] No tool in this feature triggers the execution-confirmation dialog (read-only class)
- [ ] All PSI/index access runs in cancellable read actions on background threads — never
      the EDT; long queries respect a per-call time budget and return partial results with
      a truncation marker rather than hanging the MCP request
- [ ] Every tool returns structured (JSON-serializable) results with stable field names;
      types rendered in LuaCATS syntax (the annotation language agents already know)
- [ ] Result caps on all list-returning tools (usages, deps, search) with explicit
      `truncated: true` + total-count fields — no silent truncation
- [ ] `lua_project_brief` degrades gracefully: absent interpreter/rocks/tests yield
      explicit nulls with reason strings, not errors
- [ ] `lua_diagnostics` includes native inspections, type-engine errors, and luacheck
      results (when configured), each tagged with its source
- [ ] `lua_diagnostics_delta` correctly classifies new / resolved / unchanged against the
      baseline token; unknown or expired baseline returns a full-list fallback flagged
      `baseline_missing: true`
- [ ] Position parameters accept 1-based line/column (agent/editor convention) and are
      converted correctly; out-of-range positions produce a structured error, not an
      exception
- [ ] Unit tests per tool over fixture projects; one end-to-end test driving the toolset
      through the MCP server round-trip (per the AI-00-DR-01 sandbox pattern)
