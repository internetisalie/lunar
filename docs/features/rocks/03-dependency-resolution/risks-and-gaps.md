---
id: "ROCKS-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-03"
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/03-dependency-resolution/requirements|requirements]]"
---

# Risks & Design Gaps: Dependency Resolution (ROCKS-03)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `ROCKS-03-R-01` | **Comparator parity** | Medium | §3.1/§3.2 mirror LuaRocks `core/vers.lua` (deltas + zero-pad + revision). Parity validated by `ROCKS-03-DR-01` against real version strings. |
| `ROCKS-03-R-02` | **Bridge execution cost** | Medium | One `capture` per rockspec; resolution runs on a pooled thread with a 10 s timeout per call; results not retained on EDT. Cache later if needed. |
| `ROCKS-03-R-03` | **Interpreter absence** | Medium | If no Lua interpreter is configured, `RockspecBridge.read` returns null and the tool window shows a configure-interpreter empty state. |
| `ROCKS-03-R-04` | **Gson on classpath** | Low | If `com.google.gson` is not bundled, add it to `build.gradle.kts` (`ROCKS-03-DR-02`). |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `ROCKS-03-G-01` | **System trees** | v1 reads only project-local `lua_modules`/`.luarocks`; global/system trees deferred. | `ROCKS-03-DR-01` |
| `ROCKS-03-G-02` | **Manifest fallback** | Directory enumeration assumes the standard `rocks-<X.Y>/<pkg>/<version>/` layout. | `ROCKS-03-DR-03` |
| `ROCKS-03-G-03` | **`lua` pseudo-dep display** | Whether to hide the `lua >= 5.x` node. | `ROCKS-03-DR-04` |

## De-risking Tasks (DR)

- [ ] `ROCKS-03-DR-01`: Validate `LuaRocksVersion` against a corpus of real LuaRocks versions
      (`scm-1`, `dev-1`, `3.1-0`, `2.2.0-1`, `1.0beta2`); decide whether to support system trees.
- [ ] `ROCKS-03-DR-02`: Confirm `com.google.gson` is on the IntelliJ platform classpath; else
      add the dependency. Owner: implementer, Phase 2.
- [ ] `ROCKS-03-DR-03`: Add a `manifest`-parsing fallback if directory enumeration proves
      insufficient on some LuaRocks layouts.
- [ ] `ROCKS-03-DR-04`: Decide UI treatment of the `lua` pseudo-dependency node.
- [ ] `ROCKS-03-DR-05`: Verify the bridge end-to-end after the §2.6a exporter fix
      (`ipairs(names)`) against a real rockspec; assert the `dependencies` array shape.
- [ ] `ROCKS-03-DR-06`: Verify the Phase-2 relocation of `src/main/lua/**` to
      `src/main/resources/lua/` packages the scripts and `LuaRocksBridgeFiles.ensureExtracted`
      resolves them at runtime in the sandbox IDE.
