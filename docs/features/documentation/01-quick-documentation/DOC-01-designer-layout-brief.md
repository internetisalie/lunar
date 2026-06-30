---
id: "DOC-01-BRIEF"
title: "Designer Layout Brief"
type: "spec"
parent_id: "DOC-01"
priority: "medium"
folders:
  - "[[features/documentation/01-quick-documentation/requirements|requirements]]"
---

# DOC-01 Quick Documentation: Designer Layout Brief

## Project Overview
**Lunar** is a Lua language plugin for IntelliJ Platform IDEs (IntelliJ IDEA, PyCharm, CLion, etc.). This brief defines visual design requirements for the Quick Documentation popup (Ctrl+Q), which displays formatted information about Lua symbols.

---

## Design Requirements

### 1. Platform Integration
**Critical**: All layouts must integrate tightly with IntelliJ Platform's built-in design system:

- **Reuse existing IntelliJ components** wherever possible
- **Follow IntelliJ color schemes** (supports Light, Darcula, High Contrast themes)
- **Match IntelliJ typography** and spacing conventions
- **Use standard IntelliJ UI elements** (dividers, sections, links, code blocks)

### 2. Reference Materials

#### IntelliJ Platform Style Guides
- **Official Documentation UI Guidelines**: https://plugins.jetbrains.com/docs/intellij/documentation.html
- **DocumentationMarkup API**: Used for section headers and separators (see implementation below)
- **EditorColorsManager**: Provides theme-aware syntax highlighting colors

#### Screenshot Examples from JetBrains Core Plugins
Browse these built-in language plugins for visual reference:
- **Kotlin Plugin**: `Ctrl+Q` on Kotlin functions shows well-formatted parameter/return documentation
- **Java Plugin**: Standard reference for class documentation with inherited members
- **Python Plugin**: Good example of type formatting and parameter descriptions
- **TypeScript Plugin**: Shows modern type annotation rendering

To capture reference screenshots:
1. Open IntelliJ IDEA with Kotlin/Java/Python projects
2. Place caret on a function, class, or variable
3. Press `Ctrl+Q` (or `F1` on Mac)
4. Screenshot the popup for layout reference

#### Local Code References
Implementation files that define current rendering logic:
- `src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationRenderer.kt`
- `src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRenderer.kt`
- `src/main/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsHighlight.kt`

LuaCATS annotation grammar (defines all tag types):
- `src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf`

---

## Layout Specifications

### Layout 1: Function Documentation

**Use Case**: Displaying documentation for Lua functions with LuaCATS type annotations.

**Source Annotation Example**:
```lua
--- Adds two numbers and returns the sum.
--- This function supports both integers and floating-point numbers.
--- @generic T : number
--- @param a T The first number to add.
--- @param b T The second number to add.
--- @return T sum The sum of a and b.
function add(a, b)
    return a + b
end
```

**Visual Elements to Display**:

1. **Definition Header** (top section, monospace font):
   ```
   function add<T : number>(
       a : T,
       b : T
   ) : T
   ```
    - Syntax highlighting: `function` keyword in purple/blue
    - Generic type parameters in angle brackets `<T : number>` with operator colors
    - Parameter names in local variable color
    - Type annotations in type color (e.g., teal/cyan)
    - Return type after `) :` separator

2. **Horizontal Divider** (1px gray line)

3. **Description Section** (regular body text, Markdown-formatted):
   ```
   Adds two numbers and returns the sum.
   This function supports both integers and floating-point numbers.
   ```
    - Support **bold**, *italics*, `inline code`, code blocks, lists
    - Use theme-appropriate text color
    - Line spacing: 1.2-1.4em

4. **Parameters Section**:
    - **Section Header**: "Parameters" (bold, slightly larger, with separator line below)
    - Each parameter:
      ```
      a : T
          The first number to add.
      b : T
          The second number to add.
      ```
    - Parameter name in monospace, local variable color
    - Type in monospace, type color
    - Description indented below, regular font

5. **Returns Section**:
    - **Section Header**: "Returns" (bold, with separator)
    - Return value:
      ```
      sum : T
          The sum of a and b.
      ```
    - Return name (if present) or `_` placeholder in monospace
    - Type in type color
    - Description indented below

**Spacing & Margins**:
- Top padding: 8px
- Section spacing: 12px between sections
- Parameter/return item spacing: 6px
- Description indent: 20px (matches `.body { text-indent: 20px; }`)
- Bottom padding: 8px

**Interactive Elements**:
- Type names (e.g., `T`, `number`, custom types) should be **clickable links** (blue underline on hover)
- Clicking navigates to type definition or its documentation

---

### Layout 2: Class/Type Documentation

**Use Case**: Displaying documentation for Lua classes or type aliases defined with LuaCATS annotations.

**Source Annotation Example**:
```lua
--- Represents a 2D point in Cartesian coordinates.
--- @class Point : Serializable
--- @field x number The X coordinate.
--- @field y number The Y coordinate.
--- @field private id string Internal identifier.
local Point = {}
```

**Visual Elements to Display**:

1. **Definition Header**:
   ```
   class Point : Serializable
   ```
    - `class` keyword highlighted
    - Class name as clickable type link
    - Parent class (if present) after `:` separator, also clickable

2. **Horizontal Divider**

3. **Description Section**:
   ```
   Represents a 2D point in Cartesian coordinates.
   ```

4. **Fields Section**:
    - **Section Header**: "Fields"
    - Each field:
      ```
      x : number
          The X coordinate.
      y : number
          The Y coordinate.
      ```
    - Scope modifiers (`private`, `protected`, `public`) shown as badges or prefixes if present
    - Field name in property/field color
    - Type as clickable link

5. **Inherited Fields Section** (if parent class exists):
    - **Section Header**: "Inherited" (or "Inherited from Serializable")
    - Same field formatting as above
    - Grayed out or italicized to indicate inheritance

**Special Cases**:
- **Enum types** (using `@enum`):
  ```
  --- @enum (key) Status
  --- | 'pending' # Waiting to start
  --- | 'running' # Currently executing
  --- | 'done' # Completed
  ```
  Display as:
  ```
  enum Status
  
  Values:
      'pending' — Waiting to start
      'running' — Currently executing
      'done' — Completed
  ```

---

### Layout 3: Variable Documentation

**Use Case**: Documenting local or global variables with type annotations.

**Source Annotation Example**:
```lua
--- The user's display name.
--- Defaults to "anonymous" if not set.
--- @type string
local username = "anonymous"
```

**Visual Elements to Display**:

1. **Definition Header**:
   ```
   local username : string
   ```
    - `local` keyword (if local variable)
    - Variable name
    - Type annotation

2. **Horizontal Divider**

3. **Description Section**:
   ```
   The user's display name.
   Defaults to "anonymous" if not set.
   ```

---

### Layout 4: Overloaded Functions

**Use Case**: Functions with multiple signatures (using `@overload` tag).

**Source Annotation Example**:
```lua
--- Finds an element in a list.
--- @overload fun(list: table, value: any): number
--- @overload fun(list: table, predicate: fun(item: any): boolean): number
--- @param list table The list to search.
--- @param matcher any|function The value or predicate function.
--- @return number|nil index The index if found, nil otherwise.
function find(list, matcher)
    -- implementation
end
```

**Visual Elements to Display**:

1. **Primary Signature** (from `@param`/`@return` tags)
2. **Overloads Section**:
    - **Section Header**: "Overloads"
    - List each overload signature:
      ```
      fun(list: table, value: any): number
      fun(list: table, predicate: fun(item: any): boolean): number
      ```
    - Each as clickable to show that overload's details

---

### Layout 5: Deprecated or Versioned Items

**Use Case**: Show deprecation warnings or version compatibility.

**Source Annotation Example**:
```lua
--- @deprecated Use `newFunction()` instead.
--- @version <5.3
--- Old implementation, removed in Lua 5.3+.
function oldFunction()
end
```

**Visual Elements to Display**:

1. **Warning Badge** at top:
   ```
   ⚠️ DEPRECATED: Use newFunction() instead.
   ```
    - Yellow/orange background
    - Strikethrough on function name in signature

2. **Version Badge**:
   ```
   🏷️ Version: <5.3
   ```
    - Gray background, small text

---

## Color Palette (Theme-Aware)

All colors must adapt to IntelliJ's active theme. Use `EditorColorsManager` API to fetch:

| Element | Light Theme | Darcula Theme | Highlight Key |
|---------|-------------|---------------|---------------|
| Keyword | `#0033B3` | `#CC7832` | `LuaHighlight.KEYWORD` |
| Local Variable | `#000000` | `#A9B7C6` | `LuaHighlight.VAR_LOCAL` |
| Type Name | `#20999D` | `#6897BB` | `LuaCatsHighlight.TYPE` |
| Operator | `#000000` | `#A9B7C6` | `LuaHighlight.OPERATORS` |
| String | `#067D17` | `#6A8759` | `LuaHighlight.STRING` |
| Number | `#1750EB` | `#6897BB` | `LuaHighlight.NUMBER` |
| Comment | `#8C8C8C` | `#808080` | `LuaHighlight.LINE_COMMENT` |
| Link (Hyperlink) | `#589DF6` | `#589DF6` | Platform default |

**Do not hardcode colors**. Visual designs should show examples for both Light and Darcula themes.

---

## Typography

- **Code/Monospace**: JetBrains Mono, Consolas, Monaco, or theme default monospace font
- **Body Text**: Theme default sans-serif (usually Inter, San Francisco, or Segoe UI)
- **Size**: Match platform defaults (typically 13-14px for body, 12px for code)
- **Line Height**: 1.4 for body text, 1.2 for code blocks

---

## Popup Dimensions & Behavior

- **Default Width**: 400-600px (responsive to content)
- **Max Height**: 800px (scrollable if exceeds)
- **Padding**: 12px top/bottom, 16px left/right
- **Border**: 1px solid theme border color, 4px rounded corners
- **Shadow**: Subtle drop shadow (4px blur, 20% opacity)

**Responsive Behavior**:
- Popup repositions to avoid clipping outside screen bounds
- Long type signatures wrap intelligently (after commas, not mid-identifier)

---

## Accessibility

- **Keyboard Navigation**: All links must be keyboard-accessible (Tab to focus, Enter to activate)
- **Screen Readers**: Semantic HTML structure (`<h2>` for section headers, `<ul>` for lists)
- **High Contrast Mode**: Test all layouts in High Contrast theme

---

## Implementation Notes for Designers

1. **Use IntelliJ's DocumentationMarkup API**:
    - Section headers: `DocumentationMarkup.SECTION_HEADER_START` / `SECTION_SEPARATOR`
    - Code blocks: Wrap in `<pre>` tags with syntax-highlighted `<font color="...">` spans
    - Body text: Use `<div class="body">` for indented descriptions

2. **HTML Generation**:
    - Current implementation generates HTML strings
    - Use inline styles sparingly; prefer CSS classes matching IntelliJ defaults

3. **Markdown Support**:
    - Descriptions use `org.intellij.markdown` parser
    - Support CommonMark syntax (headings, lists, code blocks, links)

4. **Dynamic Content**:
    - Type links generate via `DocumentationManagerUtil.createHyperlink()`
    - Hover previews for types (future enhancement)

---

## Deliverables

Please provide:
1. **Mockups for all 5 layout types** (Light & Darcula themes)
2. **Hover states** for interactive elements (type links, "See Also" links)
3. **Scrolling behavior** for long content (sticky section headers optional)
4. **Responsive layouts** for varying content lengths
5. **Spacing specification sheet** (margins, paddings, line heights in px)

**File Formats**: Figma files preferred, or PNG/PDF with annotations.

---

## Questions?

If you need clarification on:
- **LuaCATS tag syntax** → See `luacats.bnf` grammar file
- **Existing IntelliJ styles** → Reference Kotlin/Java plugin screenshots
- **Color values** → Run IntelliJ and inspect theme colors via developer tools
- **Interactive behaviors** → Reference Java Quick Documentation (Ctrl+Q on any Java method)

Contact: [Your contact info]
