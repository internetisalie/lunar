---
id: "ROCKS-02-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "ROCKS-02"
priority: "medium"
folders:
  - "[[features/rocks/02-package-browser/requirements|requirements]]"
---

# Risks & Design Gaps: Package Browser (ROCKS-02)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `ROCKS-02-R-01` | **Porcelain format drift** | Low | The space/tab field formats are from luarocks source (`search.lua`, `cmd/show.lua`); parsers skip malformed lines. Pinned by parse unit tests (§4). |
| `ROCKS-02-R-02` | **Network latency** | Medium | Search runs under `Task.Backgroundable` with a 15 s timeout; the TTL cache serves repeat queries. |
| `ROCKS-02-R-03` | **Install side effects** | Medium | Install/uninstall run with a 120 s timeout and surface stderr via notification; cache invalidated only on success. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `ROCKS-02-G-01` | **Tree scoping of install** | Whether install targets the project `lua_modules` (`--tree`) or the global tree. | `ROCKS-02-DR-01` |
| `ROCKS-02-G-02` | **Repository filter** | Repository/manifest filtering (ROCKS-02 mentions it) is not yet designed beyond the namespace field. | `ROCKS-02-DR-02` |

## De-risking Tasks (DR)

- [ ] `ROCKS-02-DR-01`: Decide the default install tree (project `--tree lua_modules` vs
      global) and whether to expose a toggle; align with ROCKS-03 tree location.
- [ ] `ROCKS-02-DR-02`: Validate `luarocks search --porcelain` field stability across luarocks
      3.x and confirm namespace handling for custom servers; design the repository filter.
