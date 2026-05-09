---
name: plan-feature
description: 'Standardized workflow for planning and documenting new product features or epics. Use when asked to "plan a feature", "create a technical design", "break down an epic", or "prepare an implementation plan". Ensures alignment with SDLC standards (PRD, SRS, TDD) and setup of tracking in Saga.'
---

# Plan Feature

This skill provides a structured process for taking a high-level feature request from concept to an implementation-ready state. It focuses on modularity, technical rigor, risk mitigation, and clear tracking.

## When to Use This Skill

- User wants to start a new feature or "Epic".
- Asked to "design" a system or "plan" an implementation.
- Need to break down complex requirements into manageable tasks.
- Preparing documentation for a software engineering task.

## Step-by-Step Planning Workflow

### Step 1: Understand & Align
- Read existing specifications and the Product Requirements Document (PRD).
- Identify key stakeholders, user stories, and high-level acceptance criteria.
- Align the feature with standard SDLC document types:
    - **PRD**: Product Requirements (The "What" and "Why").
    - **SRS**: Software Requirements Specification (Functional details).
    - **TDD**: Technical Design Document (Internal implementation).
    - **Plan**: Implementation roadmap.

### Step 2: Modularize
- Evaluate if the request (Epic) should be split into multiple distinct features.
- Create sub-directories under `requirements/spec/<domain>/<feature-name>/` to house artifacts.
- Rename generic files (e.g., `tool.md`) to specific ones (e.g., `tool-product-requirements.md`).

### Step 3: Define Functional Specs (SRS)
- Create `requirements.md` for each feature.
- Detail the scope (In Scope vs. Out of Scope).
- Define a Requirements Table with unique IDs, priorities, and descriptions.
- Include Test Cases (TCs) with inputs, actions, and expected outputs.

### Step 4: Design Technical Architecture (TDD)
- Create `design.md` for each feature.
- Define Data Models (Enums, Data Classes).
- Outline Storage mechanisms (Settings, DB schemas).
- Specify Services and Integration points.
- Detail implementation specifics like regex patterns, process execution strategies, or environment injection.

### Step 5: Draft Implementation Plans
- Create `implementation-plan.md`.
- Break the implementation into logical **Phases** (e.g., Phase 1: Storage, Phase 2: Logic).
- Assign **MoSCoW** priorities ([Must], [Should], [Could]) to each phase.
- Include specific Verification Tasks (Unit, Integration, Manual tests).

### Step 6: Risk Assessment & De-risking
- Identify technical risks (e.g., platform dependencies) and design gaps.
- Document these in a `risks-and-gaps.md` file.
- Create specific **De-risking Tasks** (DR actions) to prototype or research unknowns early.
- Prepend IDs with `<EPIC>-DR-*`.

### Step 7: Tracker Setup (Saga)
- Initialize the feature in the tracker using `saga_epic_create`.
- **Naming Convention**: Use the format `CODE: Title` for epic titles (e.g., `TOOL: Tool Inventory Management`).
- Create tasks for each implementation phase and de-risking action using `saga_task_create`.
- Use the `priority` field to reflect M/S/C status (High/Medium/Low).
- Add granular subtasks to each task for checklist tracking.
- Apply MoSCoW tags and update task titles with `[Must]`, `[Should]`, etc.

### Step 8: Finalize & Polish
- Resolve "Open Questions" from the PRD and incorporate decisions into the design.
- Create a `human-verification-checklists.md` for manual validation of each task.
- Ensure all artifacts are linked and consistent.

## Artifact Structure

A well-planned feature should have the following folder structure:
```
docs/requirements/spec/<domain>/<feature-name>/
├── requirements.md               # SRS: Functional details & Test Cases
├── design.md                     # TDD: Technical architecture
├── implementation-plan.md        # Phases & prioritized roadmap
├── human-verification-checklists.md # Manual test steps
└── (risks-and-gaps.md)           # Technical risks (at Epic root)
```

### Example: Tool Epic Structure
```
docs/requirements/spec/tool/
├── tool-product-requirements.md      # Epic PRD
├── tool-risks-and-gaps.md            # Epic-level risks & de-risking actions
├── human-verification-checklists.md  # Master checklist for all tasks
├── 01-inventory-management/          # Feature 1: Registry
│   ├── requirements.md
│   ├── design.md
│   └── implementation-plan.md
├── 02-project-binding/               # Feature 2: Binding & PATH
│   ├── requirements.md
│   ├── design.md
│   └── implementation-plan.md
└── 03-ui-and-health-checks/          # Feature 3: UI & Monitoring
    ├── requirements.md
    ├── design.md
    └── implementation-plan.md
```

## Best Practices

- **Atomic Tasks**: Keep tasks small (2-8 hours estimated).
- **MoSCoW Rigor**: Be honest about what is truly a "Must".
- **Self-Verification**: Every implementation phase must have a corresponding verification step.
- **Async Safety**: Always specify background execution for long-running or CLI tasks to avoid UI hangs.
- **VCS Awareness**: Prefer storing project-level settings in files that can be shared via Git (e.g., `.idea/lunar.xml`).
