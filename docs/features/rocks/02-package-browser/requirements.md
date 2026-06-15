---
id: ROCKS-02
title: "02: Package Browser"
type: feature
parent_id: ROCKS
status: "planned"
priority: "medium"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-02: Package Browser

## Scope
The Package Browser provides an IDE-native interface for exploring, searching, and managing LuaRocks packages from remote and local repositories. It aims to reduce context switching by bringing the `luarocks search` and package exploration workflow directly into the IDE.

### In Scope
- **Remote Search**: Searching by package name, version, or keyword using the `luarocks search` CLI.
- **Split-View UI**: A list of search results on the left and a detailed preview (README, metadata, license) on the right.
- **Repository Management**: Filtering results by repository (e.g., LuaRocks.org, custom manifests).
- **Package Installation**: One-click install/uninstall for selected packages.
- **Manifest Caching**: Local caching of repository manifests to speed up search and provide basic offline support.
- **Version Selection**: Dropdown to select specific versions of a package for installation.

### Out of Scope
- Direct editing of remote rockspecs (read-only in browser).
- Managing binary builds for architectures other than the host machine.

## Syntax/Behavior

### Search Logic
- Leverages the project-bound `luarocks` binary (see `TOOL-02`).
- Supports fuzzy matching and regex patterns for advanced discovery.
- Filters results based on the current Lua version compatibility (using `--lua-version` flag).

### Package Preview
- Fetches and renders package descriptions and homepages.
- Displays license information clearly to ensure compliance.
- Links to external documentation or source repositories.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **ROCKS-02-01** | **Search Interface** | **Must** | **Pending** | User can search for packages by name or keyword via a dedicated search bar. |
| **ROCKS-02-02** | **Split-View Browser** | **Must** | **Pending** | Display search results and package metadata side-by-side. |
| **ROCKS-02-03** | **Remote Integration** | **Must** | **Pending** | Fetch package data from the default LuaRocks.org repository. |
| **ROCKS-02-04** | **Install/Uninstall Actions** | **Must** | **Pending** | Provide buttons to install/uninstall packages directly from the browser. |
| **ROCKS-02-05** | **Manifest Caching** | **Should** | **Pending** | Cache remote manifests locally to improve search performance. |
| **ROCKS-02-06** | **Version Picker** | **Should** | **Pending** | Allow users to select a specific version from the available remote versions. |
| **ROCKS-02-07** | **Offline Mode** | **Could** | **Pending** | Allow browsing and installing from local cache or filesystem if offline. |

## Test Cases

### TC-ROCKS-02-01: Basic Package Search
- **Input**: Enter "inspect" in the search bar.
- **Action**: IDE executes `luarocks search inspect`.
- **Expected Output**: A list of packages matching "inspect" appears in the results pane.

### TC-ROCKS-02-02: Metadata Preview
- **Input**: Select the "inspect" package from search results.
- **Action**: Preview pane fetches metadata.
- **Expected Output**: Displays summary, version list, license (MIT), and homepage URL.

### TC-ROCKS-02-03: Version Selection & Install
- **Input**: Select version "3.1.0" for package "inspect" and click "Install".
- **Action**: IDE runs `luarocks install inspect 3.1.0`.
- **Expected Output**: Package is installed locally; success notification shown.

### TC-ROCKS-02-04: Search Porcelain Parse (unit — design §4.1)
- **Input** (mock `luarocks search --porcelain inspect` stdout):
  ```
  inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito
  inspect 3.1.3-0 src https://luarocks.org/manifests/kikito
  inspect 3.1.2-0 rockspec https://luarocks.org/manifests/kikito
  ```
- **Action**: Parse via `LuaRocksSearchService` parser.
- **Expected Output**: 2 collapsed entries — `inspect 3.1.3-0` and `inspect 3.1.2-0`; the
  version picker for `inspect` lists `["3.1.3-0","3.1.2-0"]`.

### TC-ROCKS-02-05: Show Porcelain Parse (unit — design §4.2)
- **Input** (mock `luarocks show --porcelain inspect` stdout):
  ```
  package	inspect
  version	3.1.3-0
  summary	Human-readable representation of Lua tables
  license	MIT
  homepage	https://github.com/kikito/inspect.lua
  dependency	lua	>= 5.1
  ```
- **Action**: Parse via `LuaRocksMetadataService`.
- **Expected Output**: `LuaRockMetadata(name="inspect", version="3.1.3-0", summary=…,
  license="MIT", homepage="https://github.com/kikito/inspect.lua",
  dependencies=["lua >= 5.1"], …)`.

### TC-ROCKS-02-06: Cache Staleness (unit — design §2.5)
- **Input**: `put("inspect", results, t0)`; then `get("inspect", t0 + 299_000)` and
  `get("inspect", t0 + 301_000)`.
- **Action**: Query the cache.
- **Expected Output**: the first `get` returns the cached results; the second returns null
  (stale, TTL 300 000 ms).
