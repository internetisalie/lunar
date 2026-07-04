# Change Log

All notable changes to the Lunar Lua IDE plugin are documented in this file.

## [1.0.0-SNAPSHOT] - In Development

### Runtime & Platform Support
- **Target Selection**: Introduced comprehensive project environment selection with platform and version granularity.
- **New Platforms**: Added explicit support for **LuaJIT**, **Redis (5/6/7)**, **Tarantool**, **OpenResty (NGX)**, and **Pandoc**.
- **Dynamic Standard Libraries**: Automatic resolution of platform-specific library definitions (e.g., Redis globals, LuaJIT-specific functions) based on the selected target.
- **Environment-Aware Luacheck**: Luacheck now dynamically adjusts its analysis standard (`--std`) to match the active project environment.
- **Legacy Migration**: Automated migration of existing project settings to the new environment-aware data model.

### Core Language Support
- **Lua Grammar & Parsing**: Full Lua 5.1-5.4 syntax support with AST-based PSI model
- **Lexer & Tokenization**: Comprehensive lexical analysis with proper handling of comments, strings, and operators
- **Syntax Highlighting**: Color scheme configuration with semantic highlighting for syntax elements

### Documentation & Type Hints
- **Markdown Comments**: Plain comment documentation with Markdown support (no tag parsing)
- **LuaCATS Support**: Modern type annotation system with type hints, overloads, and generics
- **Inlay Hints**: Display inferred types for variables as inline editor hints
- **Type Inference Engine**: Cubic biunification constraint-based type analysis
- **Implicit Class Fields**: `@class` types now include fields discovered from assignments (`ClassName.field = …` and `self.field = …` inside methods), not only `@field` tags — so those members appear in completion and resolution (explicit `@field` still takes precedence)
- **Canonical Union Types**: Union types are normalized at construction — nested unions flattened, members de-duplicated, `T | any` simplified to `any`, and members sorted — for stable type display and comparison
- **Union Mismatch Diagnostics**: When a table value fails against a union type, the error names the closest-matching member and its specific missing field (e.g. `closest match 'Point': missing field 'y'`) instead of a generic message
- **Quick Documentation Resolution Fix**: Quick documentation (and Go-to) for a dotted member access no longer falls back to an unrelated top-level symbol that merely shares the short name — e.g. `package.path` previously showed the doc for `path.rockspec_name_from_rock` from a LuaRocks `path` module, because the declaration index is keyed by receiver and a bare `path` lookup returned every `path.*` function. A member segment now resolves only through its qualified name; when none is documented, nothing is shown rather than an arbitrary wrong symbol.
- **Member Reference Resolution Fix**: Resolving a dotted member access `a.b` (Go-to-declaration / Find Usages) no longer returns every `b.*` function of an unrelated module. Because the declaration index is keyed by receiver, a bare member-name lookup treated `b` as a receiver; member segments now resolve only through the receiver-qualified name.
- **Member Field Navigation & Quick Doc (NAV-12)**: `Go to Declaration` and Quick Documentation on a dotted field access (`package.path`, `obj.field`) now resolve to the `receiver.field = value` declaration, via a new qualified-name member-field index (`LuaMemberFieldIndex`). Field assignments aren't stubbed, so they were previously unreachable; keying by the full `receiver.field` keeps them from colliding with unrelated same-short-name module symbols. Quick documentation renders the field's `---@type` and doc comment (choosing the documented declaration over a bare re-assignment), so `package.path` shows its description instead of "No documentation found". Completes `NAV-01-03` (Table Fields → Full).

### IDE Features
- **Interactive Lua Console (REPL)**: A new **Tools → Lua Console** action launches the project's Lua interpreter as an interactive REPL in a Lua-highlighted console (completion and syntax highlighting in the input). Incomplete chunks (e.g. an open `function`/`if`/`do`/`(`/long-string) are detected by a client-side trial parse and switch to multi-line entry until the chunk closes; command history persists across sessions, stdout/stderr are visually distinct, and interpreter output is unbuffered. Reports a notification when no project interpreter is configured.
- **Method Separators**: Horizontal separator lines are drawn above function/method declarations when the IDE's "Show method separators" setting (Editor | General | Appearance) is enabled.
- **Convert String Quotes Intention**: An Alt+Enter intention cycles the string literal under the caret between single quotes, double quotes, and long-bracket form, preserving the runtime value (escaping/unescaping and raising the bracket level as needed)
- **Invert 'if' Statement Intention**: An Alt+Enter intention negates the condition of an `if … then … else … end` statement and swaps its `then` / `else` branch bodies, preserving behaviour (relational operators are flipped, a `not X` condition is unwrapped, and any other condition is wrapped as `not (…)`); offered only when the statement has an `else` branch and no `elseif`
- **Rename Validation**: The Rename refactoring now rejects new names that are Lua reserved keywords (e.g. `local`, `goto`, `end`) or are not syntactically valid Lua identifiers (e.g. `1var`, `a-b`)
- **Navigation**: Symbol resolution and cross-file references
- **Go to Type (bare `@class`/`@alias`)**: Go to Class / Go to Symbol now find bare `--- @class` and `--- @alias` declarations (pure type-level forms with no following `local`), not only types attached to a local declaration
- **Read/Write Access**: variable references are classified as read vs. write — distinct highlight colors for the variable under the caret, and Read/Write grouping in Find Usages
- **Create from Usage Intentions**: Alt+Enter on an undeclared name now offers to create its declaration — *Create local variable* turns an undeclared assignment target (`x = 1`) into a `local` declaration (`local x = 1`), and *Create function* generates a `local function name(arg1, …, argN) end` stub above the enclosing statement when an undeclared name is called (`myFunc(1, 2)`), with one parameter per positional argument
- **Structure View**: Outline view of file structure
- **Code Completion**: Intelligent completion for variables, functions, and members
- **Type-Inferred Member Completion**: Completing after `.`/`:` now suggests a receiver's inferred members (fields and methods, with icons), including inherited `@class` members, `self` inside methods, and members exposed through `setmetatable`'s `__index`
- **Postfix Templates**: Type an expression followed by `.if`/`.not`/`.var`/`.for`/`.forp`/`.fori`/`.ifnot`/`.nil`/`.notnil`/`.return`/`.print` and press Tab to rewrite it into the matching statement (e.g. `ready.not` → `not ready`, `getUser().var` → `local value = getUser()` with an editable name)
- **Live Templates**: Built-in abbreviation templates for common Lua constructs — `fun`/`lfun`/`if`/`ifel`/`while`/`repeat`/`fori`/`forip`/`forp`/`loc`/`req`/`mod` — plus Surround-With templates (`if`/`for`/`do`/`function`) via Ctrl+Alt+T. Templates are now code-aware and no longer expand inside strings, comments, or numeric literals.
- **Refactoring**: Label refactoring support
- **Introduce Variable**: extract a selected expression into a `local <name> = <expr>` before the enclosing statement and replace the occurrence, with a name suggestion, inline rename, and a this-occurrence/all-occurrences chooser when the expression repeats
- **Variable Name Suggestions**: smart, context-aware variable names derived from the right-hand-side expression — surfaced in the Rename popup and the Introduce Variable name — stripping accessor/factory prefixes (`get`/`set`/`create`/`build`/`new`/`make`/`find`/`load`) when followed by an uppercase letter (`getUser()` → `user`), and resolving method-call callees (`obj:getName()` → `name`)
- **Safe Delete**: deleting a local/parameter/global/label declaration first searches for usages — removes it silently when unused, or shows the standard "usages found" conflict dialog when references remain
- **Block Auto-close on Enter**: pressing Enter after a block opener (`then`/`do`/`function`/`repeat` and table `{`) inserts the matching `end`/`until`/`}` on the next line and opens an indented body line. A balance check now fixes a correctness bug where a redundant `end` was appended even when the block was already closed; full opener coverage spans `if`/`while`/numeric & generic `for`/bare `do`/`function`/`repeat`/table literals; and pressing Enter between an already-matched opener and its terminator indents a blank body line without inserting a duplicate.
- **Code Style**: Settings for indentation, spacing, and formatting
- **Formatter Fix**: Unary `not` now keeps a space before its operand when reformatting (`not x`); previously it collapsed to the distinct identifier `notx`. Symbolic unary operators (`-`, `#`, `~`) remain tight.
- **Blank-Line Management**: Reformat now drives blank lines between function definitions from the standard *Blank Lines* code-style settings (`BLANK_LINES_AROUND_METHOD`), caps runs of blank lines between statements at *Keep blank lines* (`KEEP_BLANK_LINES_IN_CODE`), and ensures a whole-file reformat ends the file with exactly one trailing newline.
- **Expression Wrapping**: New *Call arguments* and *Table constructor* wrapping options (Do not wrap / Wrap if long / Chop down if long) wrap long argument lists and table constructors at the right margin.
- **Alignment**: Optional *Align consecutive assignments* and *Align table field values* code-style options line up the `=` across a run of assignments and across a table constructor's fields (both off by default).
- **Comment Formatting**: Optional *Wrap long comments at right margin* hard-wraps over-long `--` line comments onto continuation `--` lines on reformat, preserving word boundaries and leaving LuaCATS doc comments (`---@…`) untouched.
- **Run Configurations**: Lua script execution and debugging support
- **Breakpoint Debugging**: DBGp protocol support for remote debugging

### Analysis & Quality Tools
- **Luacheck Integration**: Static analysis integration
- **Type Checking**: Constraint-based type validation with error reporting
- **Inspections**: Type assignability and return type mismatch detection
- **Duplicate Diagnostic Fix**: Type errors are no longer reported twice (doubled Problems-panel rows and hover tooltips). The engine surfaced each error through both a redundant whole-file annotator and the inspections; the stale annotator was removed so every diagnostic is surfaced exactly once. The return-vs-assignability split now classifies an error anchored anywhere inside a `return` statement as return-related (previously only its direct child), so the two inspections fully partition all errors without the annotator.
- **Undeclared-Variable Inspection**: Flags reads of names that resolve to nothing — respecting locals, parameters, loop variables, file/project globals, the per-version standard library, an "Additional Globals" allowlist, and `---@diagnostic`/`-- luacheck: ignore` suppression comments
- **For-loop Variable Resolution**: `for` loop variables now resolve correctly within the loop body (navigation, completion, and inspections no longer treat them as undeclared)

### Project Features
- **Platform Libraries**: Lua standard library definitions and type information
- **Project Settings**: Language level configuration (Lua 5.1-5.4)
- **Application Settings**: Interpreter detection and workspace configuration
- **Isolated Lua Environments (hererocks)**: Detect, create, upgrade, recreate, and remove a self-contained hererocks Lua+LuaRocks environment from Tools ▸ Lua Environment. On project open, an existing environment is detected with a one-click **Bind**; provisioning runs on a background task and binds the produced `bin/lua` as the project interpreter and `bin/luarocks` as the LuaRocks tool, so every downstream LuaRocks feature transparently targets the isolated env.
- **Multi-Version Rocks Development (ROCKS-15)**: Maintain a *set* of hererocks environments per project with an active-version switcher in the status bar — pick the active Lua/LuaRocks env from the popup (or add a new one), and the interpreter + LuaRocks binding repoint to it instantly. Existing ROCKS-14 single-environment settings migrate automatically into the new set on load. Adds a **Run Test Matrix** action that runs the rockspec build/test command against every provisioned environment and reports per-version results in a tool window, plus a **Provision Version Matrix** action to provision a whole matrix of Lua versions in one step.
- **Env Binding Off the EDT (fix)**: Binding a Lua environment — after provisioning, via the one-click **Bind** notification, or via the status-bar version switch — now runs its `luarocks --version` and `lua -v` toolchain probes on a background task instead of the UI thread. Previously each of these paths executed the external processes synchronously on the EDT, tripping the platform's `Synchronous execution on EDT` (`OSProcessHandler#checkEdtAndReadAction`) diagnostic and briefly stalling the UI. The bind still marshals its interpreter/tool settings changes back to the EDT internally. *Verified live in GoLand: a provision→bind that logged three such violations before the fix now logs zero.*

### Architecture
- **Bipartite Type Graph**: O(n³) incremental reachability for type constraints
- **Scope Binding**: Lexical scope chains with proper shadowing and function scoping
- **Annotation Support**: Full LuaCATS @type, @param, @return injection
- **Type Caching**: CachedValuesManager integration for efficient type resolution
