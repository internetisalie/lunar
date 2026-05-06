---
name: implement-feature
description: Guided workflow for implementing new software features. Use when you need to research, specify, implement, and verify a new feature from start to finish, ensuring documentation and testing are integrated into the lifecycle.
---

# Implement Feature

This skill provides a structured, senior-engineer-led workflow for delivering high-quality features. It emphasizes the "Research -> Strategy -> Execution" lifecycle with mandatory documentation and verification phases.

## Workflow

### 1. Research & Specification
Before writing code, establish a clear source of truth for what the feature should do.
- **Task**: Create a detailed requirements document in `docs/requirements/spec/`.
- **Naming**: Use the project's ID convention (e.g., `syntax-XX-name.md`).
- **Structure**:
  - **Scope**: What is and isn't included.
  - **Syntax/Behavior**: Formal definition of the feature.
  - **Requirements Table**: List specific deliverables with IDs (e.g., `FEAT-01-01`), Priority (M/S/C/F), and Description.
  - **Test Cases**: Concrete examples with expected inputs and outputs.
- **Integration**: Link this new specification in the relevant parent requirement document (e.g., `docs/requirements/syntax-editor.md`).

### 2. Status Analysis
Map the existing codebase against your new requirements.
- **Task**: Use `grep_search`, `glob`, and `read_file` to find existing fragments or related infrastructure.
- **Update**: Populate an "Implementation Status" section in the specification or parent requirement document. Mark each requirement ID as `Implemented`, `Partial`, `Pending`, or `Future`.

### 3. Strategy & Implementation
Formulate a plan and execute it surgically.
- **Task**: For each `Pending` or `Partial` requirement that isn't blocked:
  1. **Plan**: Define the files to change and the test approach.
  2. **Act**: Apply targeted changes. Adhere strictly to existing project conventions and Kotlin/Java/Lua idioms.
  3. **Validate**: Create or update automated tests. A change is incomplete without verification.
- **Lifecycle**: Resolve sub-tasks through iterative **Plan -> Act -> Validate** cycles.

### 4. Verification & Review
Ensure the feature is robust and maintainable.
- **Task**: Run all relevant project tests (e.g., `./gradlew test`).
- **Linter**: Run project-specific quality checks (e.g., `./gradlew ktlintCheck`).
- **Exceptions**: Review the idea.log after a user verification session, checking for any unexpected exceptions, errors, or output.
- **Refinement**: Review your own implementation. Look for:
  - Opportunities for abstraction.
  - Performance bottlenecks.
  - Missing edge cases in tests.
  - High-value improvements (e.g., better error messages, improved completion).

### 5. Final Documentation
- **Task**: Update all requirement documents to reflect the final `Implemented` status.
- **Examples**: Add an example in the `examples` folder of the test project demonstrating the new feature, if possible.
- **TODOs**: If any items were deferred (e.g., marked as `Future`), ensure they are captured in the project's primary `GEMINI.md` or a global TODO list.

## Guidelines
- **Atomic Commits**: If requested to commit, keep them atomic (one feature/fix per commit).
- **Concise Documentation**: Keep requirements focused on behavior and constraints.
- **Empirical Reproduction**: For bug-fix components, always reproduce the failure with a test before fixing.
- **Idiomatic Code**: Prioritize standard language features and library usage over custom logic.
