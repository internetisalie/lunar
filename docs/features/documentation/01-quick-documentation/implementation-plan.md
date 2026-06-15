---
id: DOC-01-PLAN
title: "Implementation Plan"
type: plan
parent_id: DOC-01
status: "done"
priority: "medium"
folders:
  - "[[features/documentation/01-quick-documentation/requirements|requirements]]"
---

# DOC-01 Quick Documentation Implementation Plan

## Overview

This plan guides refactoring `LuaCatsDocumentationRenderer.kt` to implement the modern three-block Quick Documentation structure specified in `DOC-01-designer-layout-response.md`. The implementation will be incremental, testable, and preserve all existing functionality while adding new features.

**Target Files:**
- `src/main/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRenderer.kt` (main refactor)
- `src/main/kotlin/net/internetisalie/lunar/lang/doc/LuaDocumentationRenderer.kt` (minor updates)

**Strategy:** Refactor existing implementation (faster than rewrite, preserves working logic)

---

## Phase 1: Preparation & Infrastructure

### Step 1.1: Create Test Infrastructure

**Goal:** Establish automated verification before making changes

**Actions:**
1. Create test file: `src/test/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRendererTest.kt`
2. Implement baseline test cases:
   - Test function documentation rendering (with @param, @return)
   - Test class documentation rendering (with @class, @field)
   - Test variable documentation rendering (with @type)
   - Test deprecated item rendering (with @deprecated)
   - Test enum documentation rendering (with @enum)

**Test Structure:**
```kotlin
class LuaCatsDocumentationRendererTest : BaseDocumentTest() {
    
    fun testFunctionDocumentation() {
        configureByText("""
            ---@param name string Player name
            ---@param age number Player age
            ---@return Player player The created player
            function createPlayer(name, age) end
        """.trimIndent())
        
        val element = myFixture.elementAtCaret
        val doc = LuaCatsDocumentationRenderer.renderDoc(element)
        
        // Verify structure contains three blocks
        assertContains(doc, "<div class='definition'>")
        assertContains(doc, "<div class='content'>")
        assertContains(doc, "<table class='sections'>")
        
        // Verify content
        assertContains(doc, "createPlayer")
        assertContains(doc, "name")
        assertContains(doc, "string")
    }
    
    // Additional test methods...
}
```

**Verification:**
- Run `./gradlew test --tests "*LuaCatsDocumentationRendererTest*"`
- All tests should FAIL initially (expected - we haven't refactored yet)
- Document baseline failures for comparison after refactoring

---

### Step 1.2: Extract Constants

**Goal:** Centralize HTML structure constants for maintainability

**Actions:**
1. Open `LuaCatsDocumentationRenderer.kt`
2. Add companion object with constants at top of class:

```kotlin
companion object {
    // Three-block structure constants
    private const val DEFINITION_START = "<div class='definition'><pre>"
    private const val DEFINITION_END = "</pre></div>"
    
    private const val CONTENT_START = "<div class='content'>"
    private const val CONTENT_END = "</div>"
    
    private const val SECTIONS_START = "<table class='sections'>"
    private const val SECTIONS_END = "</table>"
    
    // Section row template
    private const val SECTION_HEADER_CELL = "<tr><td valign='top' class='section'><p>"
    private const val SECTION_SEPARATOR = "</td><td valign='top'>"
    private const val SECTION_END = "</td></tr>"
    
    // Icon rendering (for future use)
    private const val ICON_START = "<icon src='"
    private const val ICON_END = "'/>"
}
```

**Verification:**
- File compiles without errors
- No behavior change yet (constants not used)

---

## Phase 2: Refactor HTML Structure

### Step 2.1: Update Main Render Function

**Goal:** Replace old header/footer approach with three-block structure

**Current Code (lines 24-31):**
```kotlin
fun renderDoc(element: LuaPsiElement): String? {
    val sb = StringBuilder()
    when (element) {
        is LuaCatsFunctionDef -> renderFunctionDoc(element, sb)
        is LuaCatsClassDef -> renderClassDoc(element, sb)
        is LuaCatsVariableDef -> renderVariableDoc(element, sb)
        else -> return null
    }
    return sb.toString()
}
```

**New Code:**
```kotlin
fun renderDoc(element: LuaPsiElement): String? {
    val definitionBlock = buildDefinitionBlock(element) ?: return null
    val contentBlock = buildContentBlock(element)
    val sectionsBlock = buildSectionsBlock(element)
    
    return buildString {
        append(DEFINITION_START)
        append(definitionBlock)
        append(DEFINITION_END)
        
        if (contentBlock.isNotEmpty()) {
            append(CONTENT_START)
            append(contentBlock)
            append(CONTENT_END)
        }
        
        if (sectionsBlock.isNotEmpty()) {
            append(SECTIONS_START)
            append(sectionsBlock)
            append(SECTIONS_END)
        }
    }
}
```

**Verification:**
- File compiles (will have errors - stub methods don't exist yet)
- Structure is clear and matches specification

---

### Step 2.2: Implement buildDefinitionBlock()

**Goal:** Create syntax-highlighted signature block

**Actions:**
1. Add new function after renderDoc():

```kotlin
private fun buildDefinitionBlock(element: LuaPsiElement): String? {
    return when (element) {
        is LuaCatsFunctionDef -> buildFunctionSignature(element)
        is LuaCatsClassDef -> buildClassSignature(element)
        is LuaCatsVariableDef -> buildVariableSignature(element)
        else -> null
    }
}

private fun buildFunctionSignature(funcDef: LuaCatsFunctionDef): String {
    val sb = StringBuilder()
    
    // Add deprecation styling if present
    if (funcDef.deprecatedTag != null) {
        sb.append("<s>")
    }
    
    // Function keyword
    sb.append("<span style='color: #CC7832;'>function</span> ")
    
    // Function name
    val name = funcDef.name ?: "anonymous"
    sb.append("<b>").append(name).append("</b>")
    
    // Parameters
    sb.append("(")
    val params = funcDef.paramTags
    params.forEachIndexed { index, param ->
        if (index > 0) sb.append(", ")
        sb.append(param.nameIdentifier?.text ?: "")
    }
    sb.append(")")
    
    if (funcDef.deprecatedTag != null) {
        sb.append("</s>")
    }
    
    return sb.toString()
}

private fun buildClassSignature(classDef: LuaCatsClassDef): String {
    val sb = StringBuilder()
    
    if (classDef.deprecatedTag != null) {
        sb.append("<s>")
    }
    
    sb.append("<span style='color: #CC7832;'>class</span> ")
    sb.append("<b>").append(classDef.name ?: "").append("</b>")
    
    // Add parent class if exists
    classDef.classTag?.parentType?.let { parent ->
        sb.append(" <span style='color: #CC7832;'>:</span> ")
        sb.append(parent.text)
    }
    
    if (classDef.deprecatedTag != null) {
        sb.append("</s>")
    }
    
    return sb.toString()
}

private fun buildVariableSignature(varDef: LuaCatsVariableDef): String {
    val sb = StringBuilder()
    
    if (varDef.deprecatedTag != null) {
        sb.append("<s>")
    }
    
    val name = varDef.name ?: "variable"
    sb.append("<b>").append(name).append("</b>")
    
    varDef.typeTag?.type?.let { type ->
        sb.append(": ").append(type.text)
    }
    
    if (varDef.deprecatedTag != null) {
        sb.append("</s>")
    }
    
    return sb.toString()
}
```

**Verification:**
- Compile and run tests
- Check that definition block appears at top of rendered documentation
- Verify deprecated items show strikethrough (`<s>` tag)

---

### Step 2.3: Implement buildContentBlock()

**Goal:** Extract description text from documentation comments

**Actions:**
1. Add function to extract description:

```kotlin
private fun buildContentBlock(element: LuaPsiElement): String {
    val description = when (element) {
        is LuaCatsFunctionDef -> element.description
        is LuaCatsClassDef -> element.description
        is LuaCatsVariableDef -> element.description
        else -> null
    }
    
    return description?.let { 
        // Simple markdown-style rendering
        // Convert line breaks to <p> tags
        it.trim().split("\n\n")
            .joinToString("") { para -> "<p>$para</p>" }
    } ?: ""
}
```

**Verification:**
- Run tests
- Check that description appears in content block
- Verify paragraph formatting

---

### Step 2.4: Implement buildSectionsBlock()

**Goal:** Create metadata table (Parameters, Returns, Fields, etc.)

**Actions:**
1. Add section building function:

```kotlin
private fun buildSectionsBlock(element: LuaPsiElement): String {
    val sb = StringBuilder()
    
    when (element) {
        is LuaCatsFunctionDef -> {
            buildParamSection(element, sb)
            buildReturnSection(element, sb)
            buildSeeSection(element, sb)
            buildDeprecatedSection(element, sb)
        }
        is LuaCatsClassDef -> {
            buildFieldsSection(element, sb)
            buildSeeSection(element, sb)
            buildDeprecatedSection(element, sb)
        }
        is LuaCatsVariableDef -> {
            buildTypeSection(element, sb)
            buildSeeSection(element, sb)
            buildDeprecatedSection(element, sb)
        }
    }
    
    return sb.toString()
}

private fun buildParamSection(funcDef: LuaCatsFunctionDef, sb: StringBuilder) {
    val params = funcDef.paramTags
    if (params.isEmpty()) return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("Parameters:")
    sb.append(SECTION_SEPARATOR)
    
    params.forEach { param ->
        val name = param.nameIdentifier?.text ?: ""
        val type = param.type?.text ?: "any"
        val desc = param.description ?: ""
        
        sb.append("<p><code>").append(name).append("</code>")
        sb.append(" <span style='color: #A9B7C6;'>(").append(type).append(")</span>")
        if (desc.isNotEmpty()) {
            sb.append(" - ").append(desc)
        }
        sb.append("</p>")
    }
    
    sb.append(SECTION_END)
}

private fun buildReturnSection(funcDef: LuaCatsFunctionDef, sb: StringBuilder) {
    val returns = funcDef.returnTags
    if (returns.isEmpty()) return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("Returns:")
    sb.append(SECTION_SEPARATOR)
    
    returns.forEach { ret ->
        val type = ret.typeList?.text ?: "any"
        val name = ret.nameIdentifier?.text
        val desc = ret.description
        
        sb.append("<p><span style='color: #A9B7C6;'>").append(type).append("</span>")
        if (name != null) {
            sb.append(" <code>").append(name).append("</code>")
        }
        if (desc != null) {
            sb.append(" - ").append(desc)
        }
        sb.append("</p>")
    }
    
    sb.append(SECTION_END)
}

private fun buildFieldsSection(classDef: LuaCatsClassDef, sb: StringBuilder) {
    val fields = classDef.fieldTags
    if (fields.isEmpty()) return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("Fields:")
    sb.append(SECTION_SEPARATOR)
    
    fields.forEach { field ->
        val name = field.nameIdentifier?.text ?: ""
        val type = field.type?.text ?: "any"
        val desc = field.description ?: ""
        
        sb.append("<p><code>").append(name).append("</code>")
        sb.append(" <span style='color: #A9B7C6;'>(").append(type).append(")</span>")
        if (desc.isNotEmpty()) {
            sb.append(" - ").append(desc)
        }
        sb.append("</p>")
    }
    
    sb.append(SECTION_END)
}

private fun buildTypeSection(varDef: LuaCatsVariableDef, sb: StringBuilder) {
    val typeTag = varDef.typeTag ?: return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("Type:")
    sb.append(SECTION_SEPARATOR)
    sb.append("<p><code>").append(typeTag.type?.text ?: "any").append("</code></p>")
    sb.append(SECTION_END)
}

private fun buildSeeSection(element: LuaPsiElement, sb: StringBuilder) {
    val seeTags = when (element) {
        is LuaCatsFunctionDef -> element.seeTags
        is LuaCatsClassDef -> element.seeTags
        is LuaCatsVariableDef -> element.seeTags
        else -> emptyList()
    }
    
    if (seeTags.isEmpty()) return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("See Also:")
    sb.append(SECTION_SEPARATOR)
    
    seeTags.forEach { see ->
        val reference = see.nameIdentifier?.text ?: ""
        sb.append("<p><code>").append(reference).append("</code></p>")
    }
    
    sb.append(SECTION_END)
}

private fun buildDeprecatedSection(element: LuaPsiElement, sb: StringBuilder) {
    val deprecatedTag = when (element) {
        is LuaCatsFunctionDef -> element.deprecatedTag
        is LuaCatsClassDef -> element.deprecatedTag
        is LuaCatsVariableDef -> element.deprecatedTag
        else -> null
    } ?: return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("<span style='color: #FF6B68;'>⚠ Deprecated:</span>")
    sb.append(SECTION_SEPARATOR)
    sb.append("<p>").append(deprecatedTag.description ?: "This item is deprecated").append("</p>")
    sb.append(SECTION_END)
}
```

**Verification:**
- Run tests
- Verify all sections render correctly
- Check that deprecated section shows warning icon (⚠) and red color

---

## Phase 3: Icon Support (Optional Enhancement)

### Step 3.1: Add Icon Rendering for Fields

**Goal:** Display field visibility icons (public/private/protected)

**Note:** This requires platform integration and may not work in plain HTML. Skip if complexity is too high for initial implementation.

**Actions:**
1. Update `buildFieldsSection()` to include icon references:

```kotlin
private fun buildFieldsSection(classDef: LuaCatsClassDef, sb: StringBuilder) {
    val fields = classDef.fieldTags
    if (fields.isEmpty()) return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("Fields:")
    sb.append(SECTION_SEPARATOR)
    
    fields.forEach { field ->
        val name = field.nameIdentifier?.text ?: ""
        val type = field.type?.text ?: "any"
        val desc = field.description ?: ""
        
        // Add icon based on visibility
        val visibility = field.visibility ?: "public"
        val iconPath = when (visibility) {
            "private" -> "AllIcons.Nodes.C_private"
            "protected" -> "AllIcons.Nodes.C_protected"
            else -> "AllIcons.Nodes.Field"
        }
        
        sb.append("<p>")
        sb.append(ICON_START).append(iconPath).append(ICON_END)
        sb.append(" <code>").append(name).append("</code>")
        sb.append(" <span style='color: #A9B7C6;'>(").append(type).append(")</span>")
        if (desc.isNotEmpty()) {
            sb.append(" - ").append(desc)
        }
        sb.append("</p>")
    }
    
    sb.append(SECTION_END)
}
```

**Verification:**
- Build and test in IDE
- Check if icons appear (may require platform-specific rendering)
- If icons don't work, remove icon code and document as future enhancement

---

## Phase 4: Enum Support

### Step 4.1: Add Enum Rendering

**Goal:** Handle @enum tag with value listing

**Actions:**
1. Add enum check to `buildDefinitionBlock()`:

```kotlin
private fun buildDefinitionBlock(element: LuaPsiElement): String? {
    return when (element) {
        is LuaCatsFunctionDef -> buildFunctionSignature(element)
        is LuaCatsClassDef -> buildClassSignature(element)
        is LuaCatsVariableDef -> buildVariableSignature(element)
        is LuaCatsEnumDef -> buildEnumSignature(element)  // NEW
        else -> null
    }
}

private fun buildEnumSignature(enumDef: LuaCatsEnumDef): String {
    val sb = StringBuilder()
    sb.append("<span style='color: #CC7832;'>enum</span> ")
    sb.append("<b>").append(enumDef.name ?: "").append("</b>")
    return sb.toString()
}
```

2. Add enum values section:

```kotlin
private fun buildEnumValuesSection(enumDef: LuaCatsEnumDef, sb: StringBuilder) {
    val values = enumDef.enumValues
    if (values.isEmpty()) return
    
    sb.append(SECTION_HEADER_CELL)
    sb.append("Values:")
    sb.append(SECTION_SEPARATOR)
    
    values.forEach { value ->
        val name = value.nameIdentifier?.text ?: ""
        val desc = value.description ?: ""
        
        sb.append("<p><code>").append(name).append("</code>")
        if (desc.isNotEmpty()) {
            sb.append(" - ").append(desc)
        }
        sb.append("</p>")
    }
    
    sb.append(SECTION_END)
}
```

3. Update `buildSectionsBlock()` to include enum case:

```kotlin
when (element) {
    // ... existing cases ...
    is LuaCatsEnumDef -> {
        buildEnumValuesSection(element, sb)
        buildSeeSection(element, sb)
    }
}
```

**Verification:**
- Create test with @enum tag
- Verify enum values appear in sections

---

## Phase 5: Color Theme Integration

### Step 5.1: Replace Hardcoded Colors

**Goal:** Use EditorColorsManager for theme-aware colors

**Current Issue:** Colors are hardcoded (e.g., `#CC7832`, `#A9B7C6`)

**Actions:**
1. Import required classes:

```kotlin
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
```

2. Create color helper function:

```kotlin
private fun getColorHex(attribute: TextAttributesKey): String {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val attributes = scheme.getAttributes(attribute)
    val color = attributes?.foregroundColor ?: return "#A9B7C6" // fallback
    return String.format("#%02X%02X%02X", color.red, color.green, color.blue)
}
```

3. Update signature building to use dynamic colors:

```kotlin
private fun buildFunctionSignature(funcDef: LuaCatsFunctionDef): String {
    val sb = StringBuilder()
    val keywordColor = getColorHex(DefaultLanguageHighlighterColors.KEYWORD)
    
    if (funcDef.deprecatedTag != null) {
        sb.append("<s>")
    }
    
    sb.append("<span style='color: ").append(keywordColor).append(";'>function</span> ")
    // ... rest of function
}
```

**Verification:**
- Test in both Light and Darcula themes
- Verify colors adapt correctly
- If too complex, document as future enhancement and keep hardcoded colors

---

## Phase 6: Testing & Validation

### Step 6.1: Run Automated Tests

**Actions:**
1. Run all tests: `./gradlew test --tests "*LuaCatsDocumentationRendererTest*"`
2. Verify all tests PASS
3. Check test coverage (aim for >80% of renderer code)

**Expected Results:**
- ✅ Function documentation renders with three-block structure
- ✅ Class documentation shows fields section
- ✅ Variable documentation shows type section
- ✅ Deprecated items show strikethrough and warning section
- ✅ Enum types show values section

---

### Step 6.2: Manual Verification

**Actions:**
1. Build plugin: `./gradlew build`
2. Run test IDE: `./gradlew runIde`
3. Open test project: `/home/mini/Documents/src/lua/test`
4. Create test files with various annotations:

**Test File 1: Function Documentation**
```lua
---@param x number The X coordinate
---@param y number The Y coordinate
---@return number distance The calculated distance
function calculateDistance(x, y)
    return math.sqrt(x*x + y*y)
end
```

**Test File 2: Class Documentation**
```lua
---@class Player
---@field name string The player's name
---@field score number The player's score
---@field private health number The player's health
local Player = {}
```

**Test File 3: Deprecated Function**
```lua
---@deprecated Use newFunction() instead
---@param value string Input value
---@return string result Processed result
function oldFunction(value)
    return value:upper()
end
```

**Test File 4: Enum**
```lua
---@enum LogLevel
local LogLevel = {
    DEBUG = 1,   -- Detailed debug information
    INFO = 2,    -- General information
    WARN = 3,    -- Warning messages
    ERROR = 4    -- Error messages
}
```

5. For each test file:
   - Place cursor on function/class/variable name
   - Press Ctrl+Q (or Cmd+Q on Mac)
   - Verify Quick Documentation popup shows:
     - ✅ Definition block at top with syntax highlighting
     - ✅ Content block with description (if present)
     - ✅ Sections table with appropriate rows
     - ✅ Strikethrough for deprecated items
     - ✅ Warning section for deprecated items
     - ✅ Proper formatting and spacing

---

### Step 6.3: Screenshot Documentation

**Actions:**
1. Take screenshots of each test case showing Quick Documentation popup
2. Save to `docs/features/documentation/design/screenshots/`
3. Compare with designer mockups to verify alignment

---

## Phase 7: Cleanup & Documentation

### Step 7.1: Remove Old Code

**Actions:**
1. Review `LuaCatsDocumentationRenderer.kt` for unused code
2. Remove old helper functions that are no longer needed
3. Remove any commented-out code from refactoring

**Verification:**
- Run `./gradlew ktlintCheck` to ensure code quality
- Run `./gradlew test` to ensure nothing broke

---

### Step 7.2: Update Documentation

**Actions:**
1. Add KDoc comments to public functions:

```kotlin
/**
 * Renders Quick Documentation for LuaCATS-annotated elements.
 * 
 * Returns HTML with three-block structure:
 * 1. Definition block: Syntax-highlighted signature
 * 2. Content block: Markdown-rendered description
 * 3. Sections table: Metadata (params, returns, fields, etc.)
 * 
 * @param element The PSI element to document (function, class, or variable)
 * @return HTML string for documentation popup, or null if element is not supported
 */
fun renderDoc(element: LuaPsiElement): String? { ... }
```

2. Update `docs/features/documentation/01-quick-documentation.md`:
   - Mark DOC-01 status as "Full" (fully implemented)
   - Add implementation notes section
   - Reference this implementation plan

---

## Phase 8: Final Validation

### Step 8.1: Full Test Suite

**Actions:**
1. Run full test suite: `./gradlew test`
2. Run linting: `./gradlew ktlintCheck`
3. Build plugin: `./gradlew build`
4. Verify no warnings or errors

---

### Step 8.2: Integration Testing

**Actions:**
1. Test with real Lua projects
2. Verify documentation works with:
   - Complex type annotations (unions, arrays, generics)
   - Multi-line descriptions
   - Multiple @param and @return tags
   - Nested classes
   - Overloaded functions

---

## Success Criteria

Implementation is complete when:

- [ ] All automated tests pass (`./gradlew test`)
- [ ] Manual verification shows correct three-block structure
- [ ] Deprecated items show strikethrough and warning
- [ ] All section types render correctly (params, returns, fields, see, deprecated)
- [ ] Enum types show value listings
- [ ] Code passes linting (`./gradlew ktlintCheck`)
- [ ] Plugin builds without errors (`./gradlew build`)
- [ ] Documentation appears correctly in test IDE
- [ ] No regression in existing functionality

---

## Troubleshooting

### Issue: Tests fail with NullPointerException

**Solution:** Ensure test uses `BaseDocumentTest` and proper fixture setup. Check that PSI element is resolved correctly.

### Issue: HTML doesn't render correctly in popup

**Solution:** Verify HTML structure matches platform expectations. Check browser dev tools in IDE (Help → Diagnostic Tools → Debug Log Settings, add `#com.intellij.codeInsight.documentation`).

### Issue: Colors don't adapt to theme

**Solution:** Use `EditorColorsManager` API instead of hardcoded hex values. See Phase 5.1.

### Issue: Icons don't appear

**Solution:** Icon rendering may require additional platform integration. Document as future enhancement and proceed without icons.

---

## Estimated Effort

- Phase 1 (Preparation): 1-2 hours
- Phase 2 (HTML Structure): 2-3 hours
- Phase 3 (Icons): 1-2 hours (optional)
- Phase 4 (Enums): 1 hour
- Phase 5 (Colors): 1-2 hours (optional)
- Phase 6 (Testing): 2-3 hours
- Phase 7 (Cleanup): 1 hour
- Phase 8 (Validation): 1 hour

**Total: 10-15 hours** (can be reduced by skipping optional phases)

---

## Notes for Haiku

- Follow phases sequentially - do not skip ahead
- Run tests after each major change to catch regressions early
- If stuck on a phase, document the issue and move to next phase
- Prioritize core functionality (Phases 1, 2, 4, 6) over enhancements (Phases 3, 5)
- Ask for help if PSI APIs are unclear - IntelliJ Platform documentation is extensive
- Keep commits small and focused on single phase/step
- Use `log.warn()` for debugging (appears in IDE logs), remove before final commit

---

**Last Updated:** 2026-04-19  
**Author:** Copilot (for Haiku execution)
