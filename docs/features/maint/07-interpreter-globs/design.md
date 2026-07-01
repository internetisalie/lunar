---
id: "MAINT-07-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-07"
folders:
  - "[[features/maint/07-interpreter-globs/requirements|requirements]]"
---

# Technical Design: MAINT-07 — Interpreter Search Path Globs

## 1. Architecture Overview

### Current State
`LuaInterpreterService.findInterpreters()` (`LuaInterpreterService.kt:18`) builds the
interpreter list as:

```kotlin
(if (SystemInfo.isWindows) PATHS_WINDOWS else PATHS_UNIX)
    .map { pathFromEnvVarString(it) }      // String -> Path (env-var substitution + Path.of)
    .flatMap { searchPath -> find(searchPath) }   // Path -> List<LuaInterpreter>
```

`find(directoryName: Path)` (`:30`) resolves the entry as a **single** directory via
`directoryName.directoryAsVirtualFile()` (`:168`) and scans its children for each family's
executable. It already supports globbing of the **executable name** (`isGlob(exeName)` branch,
`:37`), but the **directory path itself** is always literal. Consequences:

- `PATHS_WINDOWS` (`:149`) hand-enumerates `C:\Program Files\Lua 5.1` … `Lua 5.4` and carries
  `// TODO: Search Path Globs` (`:148`).
- `pathFromEnvVarString` calls `Path.of(substituteEnvVars(it))` (`:112`). On Windows,
  `Path.of` rejects `*`/`?` with `InvalidPathException`, so a glob directory spec cannot even
  be represented today.

### Prior Art in This Repo
Searched `src/main` for interpreter discovery, glob, and path-search components:

- **`LuaInterpreterService`** (`platform/LuaInterpreterService.kt`) — the discovery service.
  `findInterpreters` (`:18`), `find(Path)` (`:30`), `validate` (`:60`), `identify` (`:74`),
  `substituteEnvVars` (`:116`), `pathFromEnvVarString` (`:112`), `PATHS_UNIX`/`PATHS_WINDOWS`
  (`:132`/`:149`). **This design EXTENDS this file** — adds directory-path expansion and
  refactors the `findInterpreters` pipeline; `find`, `validate`, `identify` are unchanged.
- **Glob primitives** — `isGlob` (`:209`), `matchesGlob` (`:213`), `patternFromGlob` (`:219`),
  all top-level functions in the same file. **REUSED** for segment matching (identical glob
  semantics to the existing executable-name matching); no new glob engine is introduced.
  These functions are also consumed by `LuaInterpreterFamily.find` (`LuaInterpreter.kt:160`),
  confirming they are the repo's shared glob helpers.
- **`directoryAsVirtualFile()`** (`:168`) — `Path -> VirtualFile?` directory lookup; **REUSED**
  unchanged by `find`.
- **Executable-name globbing** — `LuaInterpreterFamily.find(product, executableName)`
  (`LuaInterpreter.kt:157`) and the `isGlob(exeName)` branch of `find` (`:37`). A separate,
  pre-existing mechanism; **NOT extended or duplicated** here (this design globs *directories*,
  that one globs *executable names*).
- **`PathConfiguration` / `SourcePathPattern`** (`lang/path/SourcePathPattern.kt`) — module
  `package.path` resolution. Unrelated subsystem; **NOT touched**.
- No existing directory-glob expander found (grep for `newDirectoryStream`, `PathMatcher`,
  `expandSearchPath`, `expandGlob` in `src/main` → none).

### Target State
Insert a pure **glob-expansion** step between env-var substitution and `find`. The pipeline
becomes String → String (env) → `List<Path>` (glob expansion) → `List<LuaInterpreter>` (find).
Non-glob specs pass through as a single literal `Path` (unchanged behavior); glob specs are
expanded segment-by-segment against the real filesystem into the set of existing directories.

```
findInterpreters
  └─ for each PATHS_* entry:
       substituteEnvVars(entry): String
         └─ expandSearchPath(spec): List<Path>          ← new
              └─ expandSegment(base, segment): List<Path> ← new (private)
                   └─ patternFromGlob(segment)            ← reused
       find(dir): List<LuaInterpreter>                    ← unchanged
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.platform.expandSearchPath` (new top-level function, `LuaInterpreterService.kt`)
- **Responsibility**: Expand one env-substituted, absolute search-path spec into the list of
  existing directories it denotes, expanding any glob directory segments.
- **Threading**: Called on the discovery pooled/background thread (from `findInterpreters`,
  itself run inside `newProjectBackgroundTask`, `LuaInterpretersTable.kt:151`). Performs
  `java.nio.file` I/O only; no EDT, no read/write action.
- **Collaborators**: `isGlob` (`:209`), `patternFromGlob` (`:219`), the private
  `expandSegment`, `ProgressManager.checkCanceled()`.
- **Key API**:
  ```kotlin
  fun expandSearchPath(spec: String): List<Path>
  ```

### 2.2 `net.internetisalie.lunar.platform.expandSegment` (new private top-level function, `LuaInterpreterService.kt`)
- **Responsibility**: Given a base directory and one path segment, return the base's matching
  child directories (a single resolved child for a literal segment; all name-matching child
  directories for a glob segment).
- **Threading**: same background thread as §2.1.
- **Collaborators**: `isGlob`, `patternFromGlob`, `java.nio.file.Files`.
- **Key API**:
  ```kotlin
  private fun expandSegment(base: Path, segment: String): List<Path>
  ```

### 2.3 `net.internetisalie.lunar.platform.LuaInterpreterService.findInterpreters` (edit, `:18`)
- **Responsibility**: Refactor the pipeline to keep entries as `String` through env-var
  substitution, then fan out through `expandSearchPath` before `find`.
- **Threading**: unchanged (background).
- **Collaborators**: `substituteEnvVars` (`:116`), `expandSearchPath`, `find` (`:30`).
- **Key API**:
  ```kotlin
  fun findInterpreters(): List<LuaInterpreter>
  ```
- **Note**: `pathFromEnvVarString` (`:112`) is removed (its `Path.of` step moves into
  `expandSearchPath`, which must not call `Path.of` on glob strings — see §3.1 / §6).

### 2.4 `PATHS_WINDOWS` (edit, `:149`)
Replace the eight hand-enumerated entries with two globs and delete the TODO:
```kotlin
val PATHS_WINDOWS: Array<String> = arrayOf(
    "C:\\Program Files\\Lua 5.*",
    "C:\\Program Files (x86)\\Lua 5.*",
)
```

## 3. Algorithms

### 3.1 `expandSearchPath(spec: String): List<Path>`
- **Input → Output**: an absolute, env-substituted directory spec → the list of existing
  directories it denotes (empty if none; a single literal `Path` if `spec` has no glob).
- **Steps**:
  1. If `!isGlob(spec)` → `return listOf(Path.of(spec))`. (Do **not** filter for existence;
     `find` checks that downstream. Preserves MAINT-07-02.)
  2. Split into raw segments: `val rawSegments = spec.split('/', '\\')`.
     (String split — **not** `Path.of(spec)** — because `Path.of` rejects `*`/`?` on Windows.)
  3. Seed the frontier and choose the first segment index by anchor type:
     - `spec.startsWith("/")` (Unix absolute; `rawSegments[0] == ""`): `frontier = listOf(Path.of("/"))`, start at index 1.
     - `rawSegments[0]` matches `^[A-Za-z]:$` (Windows drive): `frontier = listOf(Path.of(rawSegments[0] + "\\"))`, start at index 1.
     - otherwise (relative base): `frontier = listOf(Path.of(rawSegments[0]))`, start at index 1.
  4. For each `segment` from the start index to `rawSegments.lastIndex`:
     - `if (segment.isEmpty()) continue` (handles `//` and trailing separators).
     - `ProgressManager.checkCanceled()`.
     - `frontier = frontier.flatMap { expandSegment(it, segment) }`.
     - `if (frontier.isEmpty()) return emptyList()` (early-out; nothing further can match).
  5. `return frontier`.
- **Rules / edge handling**: absolute spec assumed (§6). Result contains only existing
  directories (by construction of `expandSegment`). Depth is bounded by segment count — no
  recursion, so no symlink-cycle risk.
- **Complexity**: `O(Σ children(dir))` over visited directories; one `DirectoryStream` per
  glob segment per frontier directory.

### 3.2 `expandSegment(base: Path, segment: String): List<Path>`
- **Input → Output**: a base directory + one segment → matching child directories.
- **Steps**:
  1. If `!isGlob(segment)`:
     - `val child = base.resolve(segment)`
     - `return if (Files.isDirectory(child)) listOf(child) else emptyList()`.
  2. Else (glob segment):
     - `val pattern = patternFromGlob(segment)` (`:219`).
     - `try { Files.newDirectoryStream(base).use { stream -> stream.filter { Files.isDirectory(it) && pattern.matcher(it.fileName.toString()).matches() }.sortedBy { it.fileName.toString() } } }`
     - `catch (e: IOException) { LOG.debug("Cannot list $base: ${e.message}"); emptyList() }`.
- **Rules / edge handling**: `Files.newDirectoryStream(base)` throws `NoSuchFileException`
  (an `IOException`) if `base` does not exist, and `AccessDeniedException` on permission
  failure — both caught → `emptyList()` (MAINT-07-05). `sortedBy { fileName }` gives ascending
  name order (MAINT-07-04). Non-directory entries (files) are excluded by `Files.isDirectory`.
- **Complexity**: `O(children(base) · log children(base))` for the sort.

## 4. External Data & Parsing
This feature consumes no CLI/text/network input. It reads **filesystem directory listings**
via `java.nio.file.Files.newDirectoryStream` (structured `Path` entries, not text) and matches
simple names against `java.util.regex.Pattern` produced by the existing `patternFromGlob`
(`:219`), whose translation table is: `*` → `.*`, `?` → `.`, `.` → `\.`, `\` → `\\`, every
other char literal, anchored `^…$`. No new parser is introduced.

## 5. Data Flow

### Example 1: Windows install discovery (MAINT-07-01 / -03)
Given `C:\Program Files\Lua 5.1` and `C:\Program Files\Lua 5.4` on disk:
1. `findInterpreters` → entry `"C:\\Program Files\\Lua 5.*"`.
2. `substituteEnvVars` → unchanged (no `${…}`).
3. `expandSearchPath` → glob → segments `["C:", "Program Files", "Lua 5.*"]`; seed
   `Path.of("C:\\")`, start index 1. `"Program Files"` (literal) → `C:\Program Files`;
   `"Lua 5.*"` (glob, `^Lua 5\..*$`) → `[C:\Program Files\Lua 5.1, C:\Program Files\Lua 5.4]`.
4. Each directory → `find(dir)` → scans for `lua.exe` etc. → interpreters identified via `identify`.

### Example 2: Literal Unix path, no regression (MAINT-07-02)
Given entry `"/usr/local/bin"`:
1. `substituteEnvVars` → `"/usr/local/bin"`.
2. `expandSearchPath` → `!isGlob` → `listOf(Path.of("/usr/local/bin"))`.
3. `find` resolves it via `directoryAsVirtualFile()` exactly as before.

### Example 3: Env-var + mid-segment glob (MAINT-07-06)
Given entry `"${HOME}/lua*/bin"` with `HOME=/home/me` and dirs `/home/me/lua5.4/bin`:
1. `substituteEnvVars` → `"/home/me/lua*/bin"`.
2. `expandSearchPath` → segments `["", "home", "me", "lua*", "bin"]`; seed `Path.of("/")`.
   `home`, `me` literal; `lua*` glob → `[/home/me/lua5.4]`; `bin` literal → `[/home/me/lua5.4/bin]`.
3. `find(/home/me/lua5.4/bin)`.

## 6. Edge Cases
- **`Path.of` on a Windows glob** — never called on a glob string; `expandSearchPath` uses
  `split('/', '\\')` and only calls `Path.of` on the anchor and on literal `resolve` steps.
- **Non-existent base directory** — `Files.newDirectoryStream` throws `NoSuchFileException`
  → caught → `emptyList()` (TC 7).
- **Glob matches nothing** — filtered stream is empty → `emptyList()`; early-out stops the walk (TC 8).
- **I/O / permission error** — `AccessDeniedException` (an `IOException`) caught → `emptyList()`.
- **Files vs directories** — `Files.isDirectory` excludes regular files at every segment (TC 1).
- **Trailing / doubled separators** — empty segments skipped in step 4.
- **Regex-unsafe glob chars** — `patternFromGlob` special-cases only `* ? . \`; a glob segment
  containing other regex metacharacters (e.g. `+`, `(`) would pass through literally into the
  regex. **Accepted limitation**: none of the in-scope default patterns (`Lua 5.*`, `lua*`,
  `lua5?`) contain such characters; broadening the escape table is out of scope. Literal
  segments (e.g. `Program Files (x86)`) never reach `patternFromGlob`, so their `(`/`)` are safe.
- **Relative spec** — `expandSearchPath` assumes absolute input; a relative glob whose first
  segment is itself a glob is unsupported (out of scope — all repo search paths are absolute).

## 7. Integration Points
No `plugin.xml` change. `LuaInterpreterService` is registered via its `@Service(Service.Level.APP)`
annotation (`LuaInterpreterService.kt:15`); grep of `src/main/resources/META-INF` for
`LuaInterpreterService` / `platform.Lua` returned no `<applicationService>` entry, confirming no
declarative registration exists or is needed. The new functions are plain Kotlin additions to an
existing file. No new indexes, settings keys, or extension points.

```xml
<!-- plugin.xml — no change required for MAINT-07 -->
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-07-01 | M | §2.1, §2.2, §3.1, §3.2 |
| MAINT-07-02 | M | §3.1 step 1 |
| MAINT-07-03 | M | §2.4 |
| MAINT-07-04 | S | §3.2 step 2 (`sortedBy`) |
| MAINT-07-05 | S | §3.1 step 4 (early-out), §3.2 (`catch IOException`) |
| MAINT-07-06 | C | §2.3 (`substituteEnvVars` before `expandSearchPath`), §5 Example 3 |

## 9. Alternatives Considered
- **Java NIO `PathMatcher` / `Files.walk` with a `glob:` matcher** — rejected: `**`/brace
  semantics differ from the repo's `patternFromGlob`, and `Files.walk` would recurse arbitrarily
  deep instead of matching exactly the declared segments. Reusing `patternFromGlob` keeps glob
  semantics identical to the existing executable-name matching.
- **Globbing inside `find` via VFS `children`** — rejected: `find(Path)` receives a single
  directory and the anchor of a glob spec may not be VFS-known; segment expansion is cleaner as a
  pure `java.nio.file` step that feeds concrete directories to the unchanged `find`.
- **Keep hand-enumerated Windows paths** — rejected: defeats the feature and leaves the standing
  `// TODO: Search Path Globs`.

## 10. Open Questions

_None — feature has cleared the planning bar._
