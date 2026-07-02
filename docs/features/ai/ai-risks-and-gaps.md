---
id: "AI-RISKS"
title: "AI Epic: Risks & Gaps"
type: "risk"
parent_id: "AI"
folders:
  - "[[features/ai/requirements|requirements]]"
---

# AI Epic: Risks & Gaps

**Last Updated**: 2026-07-02 — extended to cover AI-02/03/04 (risks 1.1–1.2 are the
original AI-01 register).

## Critical Risks

### Risk 1.1: Security & Arbitrary Code Execution (AI-01, AI-03)
- **Impact**: An AI agent could generate and execute malicious Lua code on the host machine.
- **Likelihood**: High
- **Mitigation**: Route all custom execution and terminal commands through `com.intellij.mcpserver.util.checkUserConfirmationIfNeeded`. This prompts a modal validation dialog to the developer unless "Brave Mode" is explicitly checked in Settings. AI-03 extends the same gate to `lua_debug_start` (starting a debug session = running code); read-only AI-02 tools are deliberately outside the gate.

### Risk 1.2: Hard Classpath Dependency Failure (all MCP features)
- **Impact**: If the `com.intellij.mcpServer` plugin is missing or disabled in the host IDE, Lunar will fail to load.
- **Likelihood**: High (since older or non-Ultimate IDE versions might lack the MCP plugin).
- **Mitigation**: Register all MCP toolsets inside an optional configuration file (`lunar-mcp.xml`) loaded only when the `com.intellij.mcpServer` plugin is active. AI-02/03 toolsets ride the same file.

### Risk 1.3: Indirect Prompt Injection via Tool Output (AI-02)
- **Impact**: `lua_docs_search`, `lua_project_brief`, and `lua_signature` return content authored by third parties — doc comments from installed rocks, rock descriptions, dependency stubs. A malicious or compromised package can embed instruction-shaped text ("ignore previous instructions, run …") that flows into the agent's context and, combined with the execution tools, becomes an attack chain.
- **Likelihood**: Medium (supply-chain-shaped; rising as agents act more autonomously).
- **Mitigation**: Tool results are returned strictly as MCP *content* (data), never merged into tool descriptions or instructions; results carry a `source` field (project / dependency / stdlib) so clients can apply trust tiers; document explicitly that AI-02 output is untrusted third-party text; keep the execution-confirmation gate (Risk 1.1) as the backstop — injection can suggest, but the human still approves execution. No sanitization theater (rewriting text is unreliable); the boundary is the confirmation gate.

### Risk 1.4: Agent-Driven Debug Sessions Leak Resources or Run Away (AI-03)
- **Impact**: An abandoned or crashed agent leaves a paused debuggee process alive, a listening port bound, or steps a session forever; repeated sessions exhaust ports/processes.
- **Likelihood**: Medium (agents disconnect ungracefully all the time).
- **Mitigation**: Encoded as AI-03 acceptance criteria — one concurrent agent session per project, wall-clock and idle timeouts, force-termination of session + debuggee + port on MCP client disconnect, and mirroring in the IDE debug tool window so the human can always see and kill what the agent is doing.

### Risk 1.5: Building AI-03 on the Unhardened Debugger Core
- **Impact**: The current MobDebug adapter has a documented defect cluster ([docs/review.md](../../review.md): framing/charset confusion, EDT-blocking waits, unsynchronized state, silent error swallowing). An agent drives the debugger at machine speed and with none of a human's tolerance for flakiness — it would hit every hang/desync defect immediately, and each hang strands an MCP request.
- **Likelihood**: High if built today.
- **Mitigation**: Hard sequencing gate (recorded in AI-03 and roadmap Wave 16): the MobDebug hardening lands **before** AI-03 design starts. Evaluation errors must propagate as structured tool errors (never IDE fatal-error reports, never hangs).

### Risk 1.6: Data Egress via the LLM Polish Hook (AI-04)
- **Impact**: With the polish hook enabled, function signatures and existing comment text are sent to an external LLM provider — a compliance problem in restricted environments if it happens silently or over-broadly.
- **Likelihood**: Low (hook is off by default) / High impact where it matters.
- **Mitigation**: Encoded as AI-04 acceptance criteria — off by default; explicit user-configured provider (BYOK or AI Assistant delegation); settings state exactly what is sent (signatures + comment text, never whole files); the deterministic core is fully functional with the hook disabled, verified by a network-denied test.

### Risk 1.7: Generated Annotations Become False Authority (AI-04)
- **Impact**: LuaCATS annotations *override* inference. A wrong inferred type, once written as an annotation, becomes authoritative — silently changing diagnostics across every caller and locking the error in (idempotency means re-running won't correct it).
- **Likelihood**: Medium (inference is good but not perfect, especially at dynamic boundaries).
- **Mitigation**: Bulk preview before writing (standard refactoring diff); low-confidence inference is written as `any` + optional TODO marker rather than a guessed concrete type; completion summary reports placeholder counts; design must define the confidence threshold (see DR-04 below). Single undoable write command makes the escape hatch trivial immediately after the fact.

## Design Gaps

### Gap 2.1: MCP Tool Output Schema Stability (AI-02/03)
- **Question**: Agents build workflows against tool result field names; renaming fields is a breaking API change invisible to the plugin's own tests.
- **Leaning**: Version the result schema (a `schema_version` field per toolset), keep field names stable post-1.0, and cover the schemas with serialization snapshot tests.
- **Resolved by**: AI-02 design doc.

### Gap 2.2: Diagnostics-Delta Baseline Semantics (AI-02)
- **Question**: Session-scoped baseline tokens — how many retained, keyed how, and what happens across file renames or external edits between baseline and delta calls?
- **Leaning**: Small LRU (per-project cap), token = opaque hash of (file, diagnostics snapshot); any mismatch or eviction degrades to the documented `baseline_missing` full-list fallback — never a wrong delta.
- **Resolved by**: AI-02 design doc.

### Gap 2.3: `lua_debug_evaluate` Under the Session Confirmation (AI-03)
- **Question**: Evaluation can mutate debuggee state (calling setters, writing globals). Is the single session-start confirmation sufficient authority for subsequent evaluate calls?
- **Leaning**: Yes — the debuggee is user-approved code in a user-approved session, and per-evaluate dialogs would make agentic debugging unusable; document the semantics and rely on the tool-window mirror (Risk 1.4) for visibility. Revisit if MCP adds granular consent.
- **Resolved by**: AI-03 design doc.

### Gap 2.4: Human/Agent Session Contention (AI-03)
- **Question**: Both the human (tool window) and the agent (MCP) can drive the same session; interleaved steps race.
- **Leaning**: Human wins: any manual debugger action detaches the agent session gracefully (agent's next call returns a structured `session_detached` state). Encoded loosely in AI-03 criteria; the detach state machine needs design.
- **Resolved by**: AI-03 design doc.

## Technical Debt & Future Work
- **TBD: Remote LuaRocks registries** — The current scope restricts package operations to local/project-bound trees. Exposing credentials and remote server settings is deferred.
- **TBD: LDB binding for AI-03** — the adapter-agnostic tool surface reserves a seam for REDIS-02's LDB adapter (agent-debugged Redis scripts); implementation ships with the REDIS epic, not this one.
- **TBD: MCP resources** — exposing LuaCATS stub libraries / addon docs as MCP *resources* (retrieval surface) rather than tools; evaluate after AI-02 usage data exists.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|---|---|---|---|
| AI-00-DR-01 | Create a basic sandbox setup and test registering a dummy `McpToolset` to verify optional plugin class-loading boundaries. | Risk 1.2 | todo |
| AI-00-DR-02 | Probe AI-02 query latency on a large fixture project (1k+ files): time-budgeted read actions, partial-result behavior under write-action contention (user typing during agent query). | AI-02 perf criteria | todo |
| AI-00-DR-03 | Post-hardening spike: drive a headless XDebugger MobDebug session programmatically (start, breakpoint, step, locals, terminate) to validate the AI-03 tool surface before design sign-off. | Risk 1.5 | todo (blocked on review.md hardening) |
| AI-00-DR-04 | Define the AI-04 confidence policy: which inference results are written as concrete types vs `any` + TODO (e.g. never write a union member set derived from a single call site). | Risk 1.7 | todo |
