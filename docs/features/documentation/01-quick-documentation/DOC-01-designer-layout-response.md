---
id: "DOC-01-RESPONSE"
title: "Designer Layout Response"
type: "spec"
parent_id: "DOC-01"
status: "done"
priority: "medium"
folders:
  - "[[features/documentation/01-quick-documentation/requirements|requirements]]"
---

# Designer Layout Response

This implementation follows the JetBrains **Modern `DocumentationTarget`** standard. In this approach, the IDE provides the outer container (padding, background, and scrolling), while the plugin provides the HTML payload divided into three logical blocks: **Definition**, **Content**, and **Sections**.

### 1. The HTML Template
This is the structure `LuaDocumentationRenderer.kt` should generate. It uses standard platform CSS classes that the IDE already knows how to style.

```html
<!-- THE DEFINITION BLOCK: Syntax highlighted signature -->
<div class='definition'>
    <pre>
<span style="color: #cc7832;">function</span> <span style="color: #ffc66d; text-decoration: line-through;">old_add</span>&lt;T : <span style="color: #a9b7c6;">number</span>&gt;(
    a: <span style="color: #a9b7c6;">T</span>, 
    b: <span style="color: #a9b7c6;">T</span>
): <span style="color: #a9b7c6;">T</span>
    </pre>
</div>

<!-- THE CONTENT BLOCK: Markdown rendered here -->
<div class='content'>
    <p>Adds two numbers together. This function is <b>deprecated</b> since version 1.2.0. 
    Use <a href="psi_element://new_add"><code>new_add</code></a> instead.</p>
</div>

<!-- THE SECTIONS BLOCK: Metadata table -->
<table class='sections'>
    <!-- DEPRECATED SECTION -->
    <tr>
        <td valign='top' class='section'><p>Deprecated:</p></td>
        <td valign='top'><p>Use <code>new_add()</code> instead. This will be removed in 2.0.</p></td>
    </tr>
    
    <!-- PARAMETERS SECTION -->
    <tr>
        <td valign='top' class='section'><p>Parameters:</p></td>
        <td valign='top'>
            <p><code>a</code> &ndash; The first value to sum.</p>
            <p><code>b</code> &ndash; The second value to sum.</p>
        </td>
    </tr>

    <!-- FIELDS SECTION (Using Field Icons + Overlays) -->
    <tr>
        <td valign='top' class='section'><p>Fields:</p></td>
        <td valign='top'>
            <p>
                <icon src='AllIcons.Nodes.Field'/> <!-- Base Icon -->
                <icon src='AllIcons.Nodes.C_private'/> <!-- Overlay -->
                <code>id</code> : <code>string</code> &ndash; Internal identifier.
            </p>
        </td>
    </tr>

    <!-- RETURNS SECTION -->
    <tr>
        <td valign='top' class='section'><p>Returns:</p></td>
        <td valign='top'><p>The sum of <code>a</code> and <code>b</code>.</p></td>
    </tr>
</table>
```

---

### 2. The CSS "Secret Sauce"
The IDE injects its own CSS, but you should use these inline styles or specific class names to ensure consistency with the **Documentation-Specific Palette**:

```css
/* Note: These are implicitly applied by the IDE, 
   but useful for your Designer's Figma Specs */

.definition {
    padding: 4px 12px;
    background-color: #3b3c3d; /* Darcula Doc Header Background */
    border-bottom: 1px solid #4e5052;
}

.content {
    padding: 8px 12px;
    line-height: 1.4;
}

.sections {
    padding: 0 12px 8px 12px;
    border-spacing: 0 4px;
}

.section {
    color: #909090; /* Muted header color */
    padding-right: 16px;
    white-space: nowrap;
    font-weight: bold;
}

code {
    background-color: #45474a; /* Slightly lighter than background */
    font-family: "JetBrains Mono", monospace;
    font-size: 90%;
}
```

#### Color Scheme

Visual Reference Key for Designers
* Background (Body): #2B2D30 (New UI Darcula)
* Header Background: #313438
* Border/Separator: #4E5157
* Primary Text: #CED0D6
* Muted/Header Text: #868A91
* Keyword (function/class): #CC7832
* Variable/Identifier: #A9B7C6
* Instance Field: #9876AA
* Hyperlink: #589DF6

---

### 3. Comparison with Other Plugins

| Feature | **Kotlin Plugin** | **Angular/TS Plugin** | **Lunar (Proposed)** |
| :--- | :--- | :--- | :--- |
| **Header Style** | Minimalist; no background color change. | Often includes a "Location" breadcrumb. | **Bold Definition** with subtle background block for clear separation. |
| **Field Icons** | Uses Property icons (purple 'p'). | Uses Field icons (blue 'f'). | **Field Icons with visibility overlays** (standard for LuaCATS). |
| **Overloads** | Signature + "and 2 more..." link. | Signature concatenation. | **Concatenated list** (best for dynamic Lua typing). |
| **Deprecation** | "Deprecated" tag at the top. | Striking through the signature only. | **Signature Strikethrough + Warning Section** (Maximum visibility). |

---

### 4. Rendered Mockup (Visual Representation)

#### **Darcula Theme (New UI)**
> **`function`** ~~`old_add`~~ **`<T : number>(a: T, b: T): T`**  *(Definition Block)*
> ────────────────────────────────────────────────────────
> Adds two numbers together. This function is **deprecated**. *(Content Block)*
>
> **Parameters:**  
> `a` — The first value to sum.  
> `b` — The second value to sum.
>
> **Fields:**  
> 🔒 `id` : `string` — Internal identifier. *(Sections Block)*
>
> **Returns:**  
> The sum of `a` and `b`.

#### **Implementation Tip for `DocumentationTarget`**
When implementing the `computeDocumentation()` method, you will return a `DocumentationResult`. Ensure that the `DocumentationResult.documentation(String html)` call receives the concatenated string of the three divs above.

*   **Icons:** Use `IconManager.getInstance().getIcon("path", AllIcons::class.java)` to resolve icons, but in the HTML string, you refer to them via `<icon src='...'>` which the platform's `ImageResolver` will handle if correctly configured.
*   **Overlays:** The platform handles overlays in code. For HTML, you may need to generate a combined image or use a small `<table>` to align the visibility icon next to the field icon.