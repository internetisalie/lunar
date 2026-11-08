---
id: "SYNTAX-19"
title: "19: Shebang File-Type Detection"
type: "feature"
parent_id: "SYNTAX"
status: "todo"
priority: "medium"
folders:
  - "[[features/syntax/requirements|syntax]]"
---

# SYNTAX-19: Shebang File-Type Detection

Concept only. It is to be planned through `plan-feature` before any work starts.

## Overview

An extensionless Lua script, such as hererocks' `bin/luarocks` (`#!/…/.lua/bin/lua`), opens as plain
text. Lunar registers its file type by extension (`lua`, `rockspec`) and by name (`.luacheckrc`,
`.busted`) only. The lexer already handles a `#!` first line (`LuaLexer.flex:101`, `SHEBANG`), so the
gap is file-type detection alone.

## Approach

- **Not** the `hashBangs` attribute on `<fileType>`. The platform matches it with
  `FileUtil.isHashBangLine`, a substring search anywhere on the first line. `lua` would then match
  `#!/home/me/src/lua/project/bin/bash`, and with Lua projects usually under a `lua` directory, such
  shell scripts would be common.
- A `FileTypeRegistry.FileTypeDetector` (the pattern `HashBangFileTypeDetector` uses) that:
  1. takes the interpreter's basename, or for `#!/usr/bin/env [-S] X` the first non-option argument;
  2. matches it whole against the Lua interpreters: `lua`, `lua5.1`–`lua5.5`, `lua51`–`lua55`,
     `luajit` with an optional version suffix, `resty` (OpenResty) and `tarantool`;
  3. reads at most a 256-byte prefix, as the platform's detector does.

## Draft Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| SYNTAX-19-01 | Path shebang | M | Not Implemented | `#!/usr/bin/lua`, `#!/x/.lua/bin/lua` and `#!/usr/bin/luajit` open as Lua. |
| SYNTAX-19-02 | `env` shebang | M | Not Implemented | `#!/usr/bin/env lua5.4` and `#!/usr/bin/env -S luajit -O3` open as Lua. |
| SYNTAX-19-03 | No false match | M | Not Implemented | A non-Lua interpreter whose path contains `lua` (`#!/src/lua/bin/bash`) is not typed as Lua. |
| SYNTAX-19-04 | Extensions win | M | Not Implemented | A file the platform already types by extension or name is unchanged. |
| SYNTAX-19-05 | Version hint | C | Not Implemented | A versioned interpreter (`lua5.1`, `luajit`) may suggest the file's language level. |

## Open Questions

- Should Lunar's run configuration replace the run gutter that another plugin's shebang support adds?
- Is the version hint (SYNTAX-19-05) worth a separate feature?
