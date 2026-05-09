---
name: summary
description: 'Generate project status reports and summaries from Saga tracking data. Use when asked to create status reports, generate feature summaries, or produce documentation from project tracking data.'
---

# Summary Report Generator

This skill creates formatted project status reports and summaries by querying Saga tracking data and generating markdown documents suitable for documentation or status updates.

## When to Use This Skill

- User asks for a "status report", "feature summary", or "project summary"
- Need to generate reports in `docs/status.md` or similar locations
- Want to create formatted summaries from Saga tracking data
- Need to produce regular status updates for stakeholders

## Step-by-Step Workflow

### 1. Initialize and Query Saga Data
- Call `saga_tracker_dashboard` to get current project statistics
- Parse the response to extract epics, tasks, completion percentages, and recent activity
- Filter out any epics with status "cancelled" from the report

### 2. Generate Report Content
Create a markdown report with the following sections:
- **Header**: Project name and overall completion percentage
- **Progress Table**: Table showing each epic with priority, progress, task counts, and visual completion bars
- **Recent Milestone**: Summary of significant recent accomplishments
- **Timestamp**: Last updated timestamp

### 3. Format the Report
- Use emoji indicators for priority levels (🔴 Critical, 🟡 High, 🟡 Medium, 🟢 Low)
- Create visual completion bars using block characters (█ and ░)
- Sort epics by completion percentage or priority as appropriate
- Include specific details from recent activity when available

### 4. Save to Documentation
- Write the generated report to `docs/status.md`
- Optionally maintain a history of reports if requested

## Example Usage
  
When asked to "generate a status report for the Lunar project":
  
1. Query Saga: `saga_tracker_dashboard`
2. Process response to extract:
    - Overall completion: 43.6%
    - Epic data with task counts and completion rates (excluding cancelled epics)
    - Recent completed tasks (e.g., MAINT-04 completion)
3. Generate formatted markdown
4. Save to `docs/status.md`

### Example Generated Markdown
```markdown
# Lunar Project Feature Summary

**Overall Completion: 43.6%**
(79 of 181 tasks completed across 23 epics)

| Epic | Priority | Progress | Tasks | Completion Bar |
| :--- | :--- | :--- | :--- | :--- |
| **Static Analysis** | 🟡 Medium | **100%** | 5/5 | `██████████` |
| **Documentation** | 🟡 Medium | **100%** | 7/7 | `██████████` |
| **Debugging & Execution** | 🟢 Low | **84.6%** | 11/13 | `████████░░` |
| **Type Inference & Inlay Hint Bug Fixes** | 🔴 High | **100%** | 5/5 | `██████████` |
| **Union Type Fixes** | 🟡 Medium | **100%** | 1/1 | `██████████` |
| **Syntax & Editor** | 🟡 Medium | **68.8%** | 11/16 | `███████░░░` |
| **Code Navigation** | 🟡 Medium | **45.5%** | 5/11 | `█████░░░░░` |
| **Inspections & Diagnostics** | 🟡 Medium | **50%** | 2/4 | `███░░░░░░░` |
| **Refactoring & Intentions** | 🟢 Low | **50%** | 2/4 | `█████░░░░░` |
| **Type System** | 🔴 High | **58.8%** | 20/34 | `███████░░░` |
| **Code Completion** | 🔴 High | **60%** | 3/5 | `█████░░░░░` |
| **Formatting** | 🟡 Medium | **33.3%** | 2/6 | `███░░░░░░░` |
| **Maintenance & Refactoring** | 🟢 Low | **20%** | 2/10 | `██░░░░░░░░` |
| **Tool Inventory Management** | 🔴 High | **0%** | 0/3 | `░░░░░░░░░░` |
| **LuaRocks Integration** | 🔴 High | **0%** | 0/7 | `░░░░░░░░░░` |
| **Bugfixes & Stability** | 🔴 Critical | **0%** | 0/5 | `░░░░░░░░░░` |
| **TOOL-01: Core Tool Registry & Discovery** | 🔴 High | **0%** | 0/7 | `░░░░░░░░░░` |
| **TOOL-02: Project Binding & Environment Integration** | 🔴 High | **0%** | 0/5 | `░░░░░░░░░░` |
| **TOOL-03: UI/UX & Health Monitoring** | 🟡 Medium | **0%** | 0/4 | `░░░░░░░░░░` |
| **MAINT: Symbol Resolution Test De-Risk** | 🔴 High | **0%** | 0/12 | `░░░░░░░░░░` |
| **ROCKS-01: Project Initialization & Setup** | 🔴 High | **0%** | 0/5 | `░░░░░░░░░░` |
| **ROCKS-04: Task Execution & Run Configurations** | 🔴 High | **0%** | 0/3 | `░░░░░░░░░░` |

## Recent Milestone
The **MAINT-04 - Refactor Symbol Resolution (PsiScopeProcessor)** task has been completed, along with all its subtasks, establishing a comprehensive test baseline for symbol resolution refactoring. Several tool-related epics have been created but remain blocked due to dependency on core tool infrastructure.

*Last updated: 2026-05-09T19:25:23-04:00*
```

## Customization Options

The skill can be adapted to:
- Different output locations (not just `docs/status.md`)
- Various report formats (JSON, plain text, etc.)
- Different time ranges for "recent activity"
- Custom grouping or filtering of epics/tasks
- Inclusion of additional metrics (blocked tasks, estimated hours, etc.)

## References

- Saga tracker documentation for available queries
- Markdown formatting guidelines
- Project naming and documentation conventions