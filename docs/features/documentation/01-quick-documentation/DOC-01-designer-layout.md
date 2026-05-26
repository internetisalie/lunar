---
id: "DOC-01-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "DOC-01"
status: "done"
priority: "medium"
folders:
  - "[[features/documentation/01-quick-documentation/01-quick-documentation|requirements]]"
---

# Designer Layout Brief: Lunar Quick Documentation

## 1. Project Overview
**Lunar** is a Lua language plugin for IntelliJ Platform IDEs. This document defines the visual design requirements for the **Quick Documentation popup (Ctrl+Q)**, ensuring it aligns with the modern JetBrains "New UI" (ExpUI) and platform standards.

---

## 2. Platform & Technical Specifications
*   **Target UI:** JetBrains **New UI (ExpUI)**.
*   **Implementation API:** `DocumentationTarget` (Modern).
*   **Rendering Engine:** Swing-based HTML with `MarkdownUtil.convertToHtml` support.
*   **Color Palette:** **Documentation-Specific Palette** (e.g., `DocumentationComponent.COLOR`). High contrast ratios suitable for popups.
*   **Themes:** Full support for **Light** and **Darcula**.

---

## 3. Visual Language & Typography

### Typography
*   **Body Text:** Theme default sans-serif (Inter/Segoe UI/San Francisco). Line height: **1.4**.
*   **Code Blocks:** **JetBrains Mono**. Line height: **1.2**.
*   **Links:** Standard platform blue/purple. **No icons** for inline links. Underline on hover only.

### Special Decorations
*   **Deprecation:** Use **STRIKEOUT** treatment (strikethrough line + muted grey text).
*   **Inheritance:** Italicized text or "Inherited from [Type]" labels in muted colors.

---

## 4. Iconography Mapping
Where icons are required, use stock **IntelliJ SDK Platform Icons (`AllIcons`)**.

| Element | Icon Key | Note |
| :--- | :--- | :--- |
| **Function** | `AllIcons.Nodes.Function` | Used in Definition Header |
| **Class/Table** | `AllIcons.Nodes.Class` | Used in Definition Header |
| **Field** | `AllIcons.Nodes.Field` | Used in Sections list |
| **Private** | `AllIcons.Nodes.C_private` | Visibility Overlay on Field icon |
| **Protected** | `AllIcons.Nodes.C_protected` | Visibility Overlay on Field icon |
| **Public** | `AllIcons.Nodes.C_public` | Visibility Overlay on Field icon |

---

## 5. Layout Architecture
The documentation is structured into three distinct vertical blocks.

### Block 1: Definition (The Header)
*   **Background:** Subtle contrasting background (Darcula: `#3b3c3d` / Light: `#f2f2f2`).
*   **Content:** Icon + Monospace syntax-highlighted signature.
*   **Behavior:** Long signatures should wrap intelligently. Overloads should be stacked vertically in a single scrolling view.

### Block 2: Content (Description)
*   **Content:** Standard Markdown rendering.
*   **Indentation:** 12px horizontal padding.

### Block 3: Sections (Metadata)
Use a two-column table format (`DocumentationMarkup.SECTIONS_TABLE`).
*   **Parameters:** `name` (bold) + `description`.
*   **Fields:** Standard field icon with visibility overlay + `name` : `type` + `description`.
*   **Returns:** Type + description.

---

## 6. Implementation Reference (HTML/CSS)

```html
<!-- Example: Deprecated Function with Parameters -->
<div class='definition'>
    <pre>
<span style="color: #cc7832;">function</span> <span style="color: #909090; text-decoration: line-through;">old_add</span>(a: <span style="color: #a9b7c6;">number</span>, b: <span style="color: #a9b7c6;">number</span>): <span style="color: #a9b7c6;">number</span>
    </pre>
</div>

<div class='content'>
    <p>This function adds two numbers. <b>Note:</b> Use the new API instead.</p>
</div>

<table class='sections'>
    <tr>
        <td valign='top' class='section'><p>Parameters:</p></td>
        <td valign='top'>
            <p><code>a</code> &ndash; The first number.</p>
            <p><code>b</code> &ndash; The second number.</p>
        </td>
    </tr>
    <tr>
        <td valign='top' class='section'><p>Returns:</p></td>
        <td valign='top'><p>The sum of a and b.</p></td>
    </tr>
</table>
```

## 7. Designer Deliverables
1.  **Layout 1: Function Documentation** (Standard parameters and returns).
2.  **Layout 2: Class/Type Documentation** (Showing a list of fields with visibility icons/overlays).
3.  **Layout 3: Overloaded Function** (Multiple signatures stacked in one view).
4.  **Layout 4: Deprecated State** (Visualizing the grey strikethrough effect).
5.  **Themes:** Mockups for both **Light** and **Darcula** modes.

## 8. Reference Comparisons
*   **Field Management:** Modeled after **Angular/TypeScript** plugins (explicit Field icons).
*   **Signature Display:** Modeled after **Kotlin** (clean, syntax-highlighted definition blocks).
*   **Overload Display:** Modeled after **Python** (concatenated scrolling list).