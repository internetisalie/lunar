---
id: TARGET-09-DESIGN
parent_id: TARGET-09
type: design
folders:
  - "[[features/target/09-addon-auto-detection/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: TARGET-09 — Definition-library Auto-detection

## 1. Architecture Overview

Four pieces, each independently testable, none touching the network:

```
lunar-definitions-catalog.json        LuaDefinitionEntry.detectionPatterns   (data, §2.1)
        │
        ▼
LuaPatternTranslator                  Lua pattern → java.util.regex          (pure, §3.2)
        │
        ▼
LuaAddonDetector                      file text → suggestible entry          (pure, §3.3)
        │
        ▼
LuaAddonNotificationProvider          editor banner → LuaDefinitionLibraryEnabler.apply  (§3.4)
```

The last arrow is the only one with side effects, and it is the existing TARGET-08 entry point —
this feature adds no fetch, no settings write beyond a dismissal list, and no background task.

Prior art this **models but does not duplicate**:
[`LuaToolEditorNotificationProvider`](../../../../src/main/kotlin/net/internetisalie/lunar/toolchain/health/LuaToolEditorNotificationProvider.kt)
(`:37`) is the same banner shape for toolchain health, on a different subject. The two coexist;
neither is extended.

## 2. Data Model

### 2.1 `LuaDefinitionEntry.detectionPatterns`

`src/main/kotlin/net/internetisalie/lunar/definitions/LuaDefinitionCatalog.kt:55` — add one field:

```kotlin
data class LuaDefinitionEntry(
    // … existing fields, unchanged …
    val requires: List<String>,
    /** Upstream LuaLS `config.json` `words`, verbatim. Empty means "never auto-detected". */
    val detectionPatterns: List<String> = emptyList(),
)
```

Loader (`LuaDefinitionCatalogLoader.kt:94`, beside `requires`):

```kotlin
detectionPatterns = entry.optStringArray("detectionPatterns") ?: emptyList(),
```

`optStringArray` already exists at `LuaDefinitionCatalogLoader.kt:132`.

**One tightening is required.** `requireStringArray` (`:129`) validates elements with
`runCatching { it.asString }`, and Gson's `asString` coerces a JSON number to its digits without
throwing — so `[42]` parses as `["42"]`. Give it the same typed check `requireString` already uses
at `:112`:

```kotlin
private fun JsonObject.requireStringArray(field: String): List<String> =
    requireArray(field).map {
        if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) corrupt("field '$field' has a non-string element")
        it.asString
    }
```

This also tightens the existing `urls` and `requires`, which is intended: a numeric URL was never
valid, and the silent coercion was latent corruption.

**Catalog data** — add to `src/main/resources/definitions/lunar-definitions-catalog.json`, copied
verbatim from each addon's upstream `config.json` `words` (provenance in
[risks-and-gaps.md](risks-and-gaps.md) §Curation):

| Entry | `detectionPatterns` |
|---|---|
| `luassert` | `["require[%s%(\"']+luassert[%)\"']"]` |
| `love2d` | `["love%.%w+"]` |
| `busted` | *(omit — upstream declares no `words`)* |

`busted` having no pattern is not a gap to paper over: it is reached as `luassert`'s dependent
through the existing `requires` edge once luassert is suggested and enabled.

### 2.2 `LuaProjectSettings.State.dismissedDefinitionLibraries`

`src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt`, beside
`enabledDefinitionLibraries` (`:87`):

```kotlin
/**
 * TARGET-09-05: catalog ids the user has told us never to suggest for this project. Stored in
 * `.idea/lunar.xml` so the decision is shared with the team, exactly as the enable list is.
 */
var dismissedDefinitionLibraries: MutableList<String> = mutableListOf()
```

Plus, on `LuaProjectSettings` itself, next to `enabledDefinitionLibraries` (`:159`):

```kotlin
val dismissedDefinitionLibraries: List<String>
    get() = state.dismissedDefinitionLibraries

/** TARGET-09-05. Idempotent; no roots change, so no refresh is published. */
fun dismissDefinitionLibrary(id: String) {
    if (id in state.dismissedDefinitionLibraries) return
    state.dismissedDefinitionLibraries = (state.dismissedDefinitionLibraries + id).toMutableList()
}
```

No `notifyDefinitionRootsChanged()` call: dismissing changes no roots.

## 3. Components

### 3.1 Why the patterns are bundled, not read from the fetched tree

The obvious source is the addon's own `config.json`, which ships in every tarball. It cannot be
used: `rootPrefix` extracts `<repo>/library` only
(`LuaArchiveExtractor.kt:25`, `removePrefixPath`), so the manifest never reaches disk — and more
fundamentally, **detection has to work before the library is fetched**. Its entire purpose is to
suggest fetching it. Reading the trigger out of the artefact it is meant to trigger the download of
is circular.

So the patterns are curated into the bundled catalog, like every other field. This supersedes the
2026-08-04 note in TARGET-08's risks-and-gaps that called "retain `config.json`" the cheap
prerequisite — retaining it is still worthwhile for *post-fetch* concerns (`Lua.runtime.version`),
but it is not this feature's dependency and TARGET-09 does not need it.

### 3.2 `LuaPatternTranslator` — the algorithm

`net.internetisalie.lunar.definitions.detect.LuaPatternTranslator` (new, `object`):

```kotlin
object LuaPatternTranslator {
    /** The regex equivalent of [luaPattern], or null when it uses a construct we do not support. */
    fun toRegex(luaPattern: String): String?

    /** [toRegex] compiled with DOTALL, or null. Lua `.` matches newlines; Java's does not. */
    fun compile(luaPattern: String): Pattern?
}
```

Single left-to-right scan with one boolean, `inClass`. Emit to a `StringBuilder`. Return null the
moment an unsupported construct appears.

**Escape sets.** Lua pattern metacharacters are `^ $ * + ? . ( ) [ ] % -`. Java regex
metacharacters are `\ ^ $ . | ? * + ( ) [ ] { }`. The difference — special in Java, literal in Lua —
is **`{ } | \`**, and each must be emitted escaped.

**Character classes.** `%<letter>`:

| Lua | Emitted outside a class | Emitted inside `[...]` |
|---|---|---|
| `%a` | `[A-Za-z]` | `A-Za-z` |
| `%d` | `[0-9]` | `0-9` |
| `%l` | `[a-z]` | `a-z` |
| `%s` | `[ \t\n\x0B\f\r]` | ` \t\n\x0B\f\r` |
| `%u` | `[A-Z]` | `A-Z` |
| `%w` | `[A-Za-z0-9]` | `A-Za-z0-9` |
| `%x` | `[0-9A-Fa-f]` | `0-9A-Fa-f` |
| `%p` | `[\p{Punct}]` | `\p{Punct}` |
| `%c` | `[\x00-\x1F]` | `\x00-\x1F` |
| `%g` | `[\x21-\x7E]` | `\x21-\x7E` |
| `%A %D %L %S %U %W %X %P %C %G` | the same set negated, e.g. `%S` → `[^ \t\n\x0B\f\r]` | **unsupported → null** |

**Escapes.** `%` followed by a **non-alphanumeric** character is that character, literally: emit
`Pattern.quote`-equivalent escaping (a backslash before it). So `%.` → `\.`, `%(` → `\(`,
`%%` → `%`.

**Unsupported → null** (the whole pattern is discarded, not partially translated):
`%b` (balanced match), `%f` (frontier), `%1`–`%9` (back-references), a negated class shorthand
inside a `[...]`, and an empty capture `()` (Lua position capture).

**The remaining specials.**

| Lua | Rule |
|---|---|
| `.` | emit `.` — combined with `Pattern.DOTALL` this matches Lua's "any character" |
| `^` | anchor only at index 0; anywhere else emit `\^` |
| `$` | anchor only at the final index; anywhere else emit `\$` |
| `*` `+` `?` | emit unchanged |
| `-` | outside a class it is Lua's **lazy zero-or-more**: emit `*?`. Inside a class it is a literal or range: emit unchanged |
| `(` `)` | emit unchanged (group); `()` with nothing between is a position capture → null |
| `[` | open class: `inClass = true`; a following `^` is negation and is emitted; a `]` immediately after `[` or `[^` is a literal and is emitted as `\]` |
| `]` | closes the class (`inClass = false`) |

**Matching semantics.** LuaLS asks "does this text mention the library", so callers use
`Matcher.find()`, never `matches()`. Compile once with `Pattern.DOTALL`; **no**
`CASE_INSENSITIVE` — Lua patterns are case-sensitive.

**Worked example** (catalog data, so this must be right):

```
require[%s%("']+luassert[%)"']
  →  require[ \t\n\x0B\f\r\("']+luassert[\)"']
```

finds in `require("luassert")` and `require 'luassert'`; does not find in `requireluassert`.
Note it also does not find `require[[luassert]]` — the upstream class contains no `[`. That is
upstream's fidelity, deliberately preserved; we do not "improve" an addon's declared trigger.

```
love%.%w+   →   love\.[A-Za-z0-9]+
```

finds in `love.graphics.draw()`; does not find in `loveX`.

### 3.3 `LuaAddonDetector`

`net.internetisalie.lunar.definitions.detect.LuaAddonDetector` (new, `@Service(Service.Level.PROJECT)`):

```kotlin
@Service(Service.Level.PROJECT)
class LuaAddonDetector(private val project: Project) {
    /** The first catalog entry this text should trigger a suggestion for, or null. */
    fun detect(text: CharSequence): LuaDefinitionEntry?

    companion object { fun getInstance(project: Project): LuaAddonDetector }
}
```

Algorithm, in order — cheapest exclusions first:

1. Load the catalog via `LuaDefinitionCatalogLoader.load()`, wrapped in `runCatching { }.getOrNull()`
   and returning null on failure, matching `LuaDefinitionLibraryProvider.catalogOrNull()`
   (`LuaDefinitionLibraryProvider.kt:52` neighbourhood).
2. Read `LuaProjectSettings.getInstance(project)` once: `enabledDefinitionLibraries` and
   `dismissedDefinitionLibraries` into `Set<String>`.
3. Walk `catalog.libraries` **in catalog order** (TARGET-09-04: first match wins, deterministic).
   Skip an entry when its id is enabled, dismissed, session-suppressed (§3.5), or its
   `detectionPatterns` is empty.
4. For each surviving entry, `find()` each of its compiled patterns against `text`; the first entry
   with any hit is returned.

**Compilation is cached, not per file** (TARGET-09-07). A project-level
`CachedValuesManager.getManager(project).createCachedValue` keyed on
`PsiModificationTracker.getInstance(project)` holds `Map<String, List<Pattern>>` (entry id →
compiled patterns), built once per catalog load. This mirrors `LuaTypeManagerImpl`'s `typeCache`
(`LuaTypeManagerImpl.kt:25`). A pattern that fails translation is dropped from the list and logged
once, at `warn`, naming the entry id and the pattern.

No PSI, no VFS, no index, no I/O: `detect` takes the text and returns a data object.

### 3.4 `LuaAddonNotificationProvider`

`net.internetisalie.lunar.definitions.ui.LuaAddonNotificationProvider` (new):

```kotlin
class LuaAddonNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>?
}
```

Body, mirroring `LuaToolEditorNotificationProvider.collectNotificationData` (`:39`):

1. `if (file.fileType != LuaFileType) return null` — `net.internetisalie.lunar.lang.LuaFileType`.
2. Read the document text via `FileDocumentManager.getInstance().getDocument(file)?.charsSequence`;
   null → return null. (The document, not `file.contentsToByteArray()` — no disk read on this path.)
3. `val entry = LuaAddonDetector.getInstance(project).detect(text) ?: return null`.
4. Return a `Function { fileEditor -> panel }` building an `EditorNotificationPanel` with
   `EditorNotificationPanel.Status.Info`, text
   `"This file uses ${entry.displayName}. Enable its type definitions?"`, and three action labels:

| Label | Action |
|---|---|
| `Enable` | `LuaDefinitionLibraryEnabler(project).apply(settings.enabledDefinitionLibraries + entry.id)` then `EditorNotifications.getInstance(project).updateAllNotifications()` |
| `Not now` | `LuaAddonDetector.getInstance(project).suppressForSession(entry.id)` then update notifications |
| `Never for this project` | `LuaProjectSettings.getInstance(project).dismissDefinitionLibrary(entry.id)` then update notifications |

`LuaDefinitionLibraryEnabler.apply` already writes the enable list first and dispatches the fetch to
a background task with its own failure balloon (`LuaDefinitionLibraryEnabler.kt:62`), so **this
class performs no fetch and needs no progress handling of its own**.

The whole body is wrapped in `runCatching { }.onFailure { LOG.warn(...) }.getOrNull()`, exactly as
the toolchain provider does at `:45` — a banner must never break the editor.

### 3.5 Session suppression

"Not now" must not persist. `LuaAddonDetector` holds
`private val sessionSuppressed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()` with
`fun suppressForSession(id: String)`, consulted in step 3 of §3.3. It dies with the project service,
which is the intended lifetime.

## 4. Registration

`src/main/resources/META-INF/plugin.xml`, beside the existing provider at `:640`:

```xml
<editorNotificationProvider implementation="net.internetisalie.lunar.definitions.ui.LuaAddonNotificationProvider" />
```

No new notification group: the only balloon on this path is the enabler's existing failure balloon
on `notification.group.lunar.tools` (`plugin.xml:679`). No new service registration — both
`@Service(Service.Level.PROJECT)` classes are found by annotation. No startup activity, by design:
detection is per-file and lazy.

## 5. Threading

`collectNotificationData` runs on a read-compatible background thread and this implementation does
no I/O, so the engineering-contract §1 obligation is met by construction. The three action labels
run on the EDT; `apply` and `dismissDefinitionLibrary` are both cheap state writes, and `apply`
marshals its own fetch to a background task (BUG-396 established that the settings page must not
touch disk on the EDT — the same rule holds here, and is why the detector never stats the cache).

## 6. Open Questions

None — the one genuine product decision (what happens when detection fires) is resolved in requirements §2 as **suggest, never fetch**, and the two implementation unknowns are tracked as TARGET-09-00-DR-01/-02 in [risks-and-gaps.md](risks-and-gaps.md) rather than left to the implementer.
