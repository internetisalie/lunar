---
name: manage-docs
description: Manage frontmatter and structure for documentation files (epics, features, requirements, design documents, etc.) using standardized fields and wikilink folders.
---

# Manage Docs

This skill ensures that all documentation files follow the project's frontmatter conventions, maintaining a consistent hierarchy and navigation structure.

## Frontmatter Standard

All documentation files should include YAML frontmatter with the following fields:

- **id**: (Required) Unique identifier (e.g., "COMP", "COMP-03-03").
- **title**: (Required) The clear, descriptive title of the document.
- **type**: (Optional) Semantic type for hierarchy: `epic`, `feature`, `user-story`, `task`, `design`, `risk`, `qa`, `guide`, `plan`.
- **parent_id**: (Optional) Parent item ID (for hierarchies).
- **status**: (Optional) Mirror of the Saga status: `todo`, `in_progress`, `done`, `blocked`, `planned`.
- **priority**: (Optional) Mirror of the Saga priority: `critical`, `high`, `medium`, `low`.
- **estimate**: (Optional) Story points or estimated effort.
- **tags**: (Optional) A list of tags for categorization.
- **created**: (Optional) Creation date in YYYY-MM-DD format.
- **folders**: (Optional) A list of wikilinks to parent or related documents/folders.

### Patterns

#### 1. Epic Root (`docs/features/<epic>/`)
This directory contains the high-level artifacts for an Epic.

- **Product Requirements (PRD)**
  - **File**: `docs/features/<epic>/<epic>-product-requirements.md`
  - **id**: `<EPIC>-PRD`
  - **folders**: `["[[features/<epic>/requirements|<EPIC>]]"]`
  - **title**: `"Product Requirements"`
  - **type**: "design"
- **Epic Requirements (SRS)**
  - **File**: `docs/features/<epic>/requirements.md`
  - **id**: `<EPIC>`
  - **folders**: `["[[features]]"]`
  - **title**: `"<EPIC>: <Description>"`
  - **type**: "epic"
- **Risks and Gaps**
  - **File**: `docs/features/<epic>/<epic>-risks-and-gaps.md`
  - **id**: `<EPIC>-RISKS`
  - **folders**: `["[[features/<epic>/requirements|requirements]]"]`
  - **title**: `"Design Gaps & De-risking"`
  - **type**: "risk"
- **Overall Implementation Plan**
  - **File**: `docs/features/<epic>/implementation-plan.md`
  - **id**: `<EPIC>-PLAN`
  - **folders**: `["[[features/<epic>/requirements|requirements]]"]`
  - **title**: `"Implementation Plan"`
  - **type**: "plan"
- **Verification Checklists**
  - **File**: `docs/features/<epic>/human-verification-checklists.md`
  - **id**: `<EPIC>-CHECKLIST`
  - **folders**: `["[[features/<epic>/requirements|requirements]]"]`
  - **title**: `"Verification Checklists"`
  - **type**: "qa"
- **User Guide**
  - **File**: `docs/features/<epic>/user-guide.md`
  - **id**: `<EPIC>-GUIDE`
  - **folders**: `["[[features/<epic>/requirements|requirements]]"]`
  - **title**: `"User Guide"`
  - **type**: "guide"

#### 2. Feature / Sub-Feature (`docs/features/<epic>/<XX>-<feature>/`)
This directory contains the detailed specifications and plans for a specific feature within an epic.

- **Feature Requirements (SRS)**
  - **File**: `docs/features/<epic>/<XX>-<feature>/requirements.md`
  - **id**: `<EPIC>-<XX>`
  - **folders**: `["[[features/<epic>/requirements|requirements]]"]`
  - **title**: `"<XX>: <Description>"`
  - **type**: `"feature"`
  - **parent_id**: `<EPIC>`
- **Technical Design (TDD)**
  - **File**: `docs/features/<epic>/<XX>-<feature>/design.md`
  - **id**: `<EPIC>-<XX>-DESIGN`
  - **folders**: `["[[features/<epic>/<XX>-<feature>/requirements|requirements]]"]`
  - **title**: `"Technical Design"`
  - **type**: `"design"`
  - **parent_id**: `<EPIC>-<XX>`
- **Implementation Plan**
  - **File**: `docs/features/<epic>/<XX>-<feature>/implementation-plan.md`
  - **id**: `<EPIC>-<XX>-PLAN`
  - **folders**: `["[[features/<epic>/<XX>-<feature>/requirements|requirements]]"]`
  - **title**: `"Implementation Plan"`
  - **type**: `"plan"`
  - **parent_id**: `<EPIC>-<XX>`


#### 3. Legacy / Simple Feature (`docs/features/<feature>/`)
Used for smaller features or legacy structures.

- **Requirements**: `docs/features/<feature>/requirements.md`
- **Spec**: `docs/features/<feature>/spec/<REQ-ID>-<desc>.md`
- **Design/Plan**: `docs/features/<feature>/<type>.md`

## Workflow

### 1. New Document Creation
When creating a new document:
- Identify its type (Epic, Spec, Design, etc.).
- Determine its parent document for the `folders` field.
- Set a clear `title`.
- Insert the standardized frontmatter at the top of the file.

### 2. Updating Frontmatter
When moving files or restructuring documentation:
- Update the `folders` wikilinks to reflect the new hierarchy.
- Ensure the `title` remains accurate.

### 3. Verification
- Use `grep` to ensure all `.md` files in `docs/` have a `title` in their frontmatter.
- Check that `folders` links are valid wikilinks.
- Run `scripts/align_icons.py` to synchronize `vf_icon` with the current `status`.

## Bundled Resources
- **scripts/align_icons.py**: Automatically aligns `vf_icon` with the `status` field (adds ✅ for "done", removes for others).
