---
name: implement-feature
description: Guided workflow for implementing new software features. Use when you need to research, specify, implement, and verify a new feature from start to finish, ensuring documentation and testing are integrated into the lifecycle.
---

# Implement Feature

This skill provides a structured, senior-engineer-led workflow for delivering high-quality features. It emphasizes the "Research -> Strategy -> Execution" lifecycle with mandatory documentation and verification phases.

## Workflow

### 0. Orchestration & Tracking
- **Topic Update**: Call `update_topic` at the start of the feature implementation and whenever transitioning between major phases (e.g., Research to Execution).
- **Project Init**: Ensure the tracker is initialized with `mcp_saga_tracker_init`.
- **Task Management**: Create an Epic if one doesn't exist, and break down the feature into individual Tasks (`mcp_saga_task_create`) that map to the requirements defined in Phase 1.
- **Change Recording**: Throughout all phases, record key decisions, changes, and progress in Saga using appropriate commands (`mcp_saga_note_save`, `mcp_saga_task_update`, etc.) to maintain an auditable trail.

### 1. Research & Specification
Before writing code, establish a clear source of truth for what the feature should do.
- **Task**: Create a detailed requirements document in `docs/requirements/spec/<domain>/<feature-name>/`.
- **Naming**: Use the feature name for the directory (e.g., `01-inventory-management`) and name the file `requirements.md`.
- **Structure**:
  - **Scope**: What is and isn't included.
  - **Syntax/Behavior**: Formal definition of the feature.
  - **Requirements Table**: List specific deliverables with IDs (e.g., `FEAT-01-01`), Priority (M/S/C/F), and Description.
  - **Test Cases**: Concrete examples with expected inputs and outputs.
- **Integration**: Link this new specification in the relevant parent requirement document (e.g., `docs/requirements/syntax-editor.md`).
- **Tracking**: Save the requirements document location and key decisions as a Saga note using `mcp_saga_note_save` for traceability.

### 2. Status Analysis
Map the existing codebase against your new requirements.
- **Task**: Use `grep_search`, `glob`, and `read_file` to find existing fragments or related infrastructure.
- **Update**: Populate an "Implementation Status" section. Mark each requirement ID using standardized classifications from `.instructions.md`:
  - **Full**: Fully implemented, verified, and integrated.
  - **Partial**: Implemented but missing sub-requirements or edge cases.
  - **Not Implemented**: In-scope but not yet started.
  - **Future Work**: Deliberately backlogged with no immediate intention.
- **Tracking**: Save the analysis results and status classifications as a Saga note using `mcp_saga_note_save` to document the starting point.

### 3. Strategy & Implementation
Formulate a plan and execute it surgically.
- **Task**: For each `Not Implemented` or `Partial` requirement:
  1. **Plan**: Define the files to change and the test approach. Update the Task status to `in_progress`. Record the plan in Saga using `mcp_saga_note_save` or by updating the task description.
  2. **Act**: Apply targeted changes. Adhere to project conventions and Kotlin/Java/Lua idioms. Record the changes made (e.g., file paths and summaries) in Saga.
  3. **Validate (Surgical)**: Immediately after editing a file, run `mcp_idea_get_file_problems` to catch errors. Use `mcp_idea_build_project` for incremental compilation checks. Record any issues found and fixed.
  4. **Validate (Behavioral)**: Create or update automated tests. A change is incomplete without verification. Record the test cases created/updated and their results.
- **Delegation**: If a task involves more than 3 files or repetitive steps, delegate it to the `generalist` sub-agent.
- **Lifecycle**: Resolve sub-tasks through iterative **Plan -> Act -> Validate** cycles.

### 4. Verification & Review
Ensure the feature is robust and maintainable.
- **Advanced Debugging**: If diagnosing complex issues, follow the "Advanced Debugging Workflow" from `.instructions.md`:
  1. Add diagnostic logging (`log.warn()`).
  2. Run `runIde` and reproduce in the sandbox.
  3. Analyze `idea.log` (located at `build/idea-sandbox/GO-*/log/idea.log`).
- **Strategic Re-evaluation**: If a fix fails >3 times, stop. Re-read the task, list assumptions, and propose a different architectural approach.
- **Full Test Suite**: Run `./gradlew test` and `./gradlew ktlintCheck`.
- **Refinement**: Look for opportunities for abstraction, performance improvements, and missing edge cases.
- **Tracking**: Record the verification steps, test results, and any issues found during verification in Saga using `mcp_saga_note_save` or by updating relevant tasks.

### 5. Final Documentation
- **Task**: Update all requirement documents to reflect the final `Implemented` status.
- **Examples**: Add an example in the `examples` folder of the test project demonstrating the new feature, if possible.
- **Future Work**: Capture deferred items as `Future Work` in requirement docs and the primary `.instructions.md`.
- **Comprehensive Tracking**: Create a final summary note in Saga using `mcp_saga_note_save` that documents the entire feature implementation process, including key decisions, changes made, and verification results.

### 6. Commit
- **Task**: Commit changes if explicitly requested.
- **Review**: Ensure commit messages explain "why", follow conventional formats, and that the commit remains atomic.
- **Tracking**: Record the commit information (hash, message, files changed) in Saga using `mcp_saga_note_save` or by linking the commit to relevant tasks.

### 6. Commit
- **Task**: Commit your changes to the feature branch, ensuring all tests pass and code quality checks are met.
- **Review**:
  - Ensure the commit message is clear and concise, following the conventional commit format.
  - Verify that the commit does not introduce any new warnings or errors.
  - Check that the codebase remains clean and follows the project's coding standards.
  - If the change is a bug fix, ensure the test case reproduces the failure.
  - If the change is a new feature, ensure it aligns with the project's roadmap and user needs.
  - If the change is a refactoring, ensure it simplifies the code without changing its behavior.
  - If the change is a performance improvement, ensure it does not regress existing functionality and is benchmarked against the previous implementation.
  - If the change is a security fix, ensure it does not introduce new vulnerabilities and is reviewed by a security expert.
  - If the change is a documentation update, ensure it is clear, concise, and accurate.
  - If the change is a style update, ensure it maintains consistency with the project's style guide and does not introduce new issues.
  - If the change is a build system update, ensure it does not break existing workflows and is thoroughly tested.
  - If the change is a dependency update, ensure it does not introduce new vulnerabilities and is reviewed by a security expert.
  - If the change is a configuration update, ensure it does not break existing functionality and is tested against the previous configuration.

## Guidelines
- **Atomic Commits**: If requested to commit, keep them atomic (one feature/fix per commit).
- **Concise Documentation**: Keep requirements focused on behavior and constraints.
- **Empirical Reproduction**: For bug-fix components, always reproduce the failure with a test before fixing.
- **Idiomatic Code**: Prioritize standard language features and library usage over custom logic.
