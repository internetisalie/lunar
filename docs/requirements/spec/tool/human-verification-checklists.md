# Human Verification Checklists: Tool Inventory Management (TOOL Epic)

This document provides step-by-step verification procedures for humans (developers/QA) to validate the implementation of each TOOL task.

---

## TOOL-01: Core Tool Registry & Discovery

### TOOL-01 Phase 1: Data Models & Storage (#136)
- [ ] **Code Review**: Verify `LuaTool` is a data class and `LuaToolType` is an exhaustive enum.
- [ ] **State Inspection**: Open `lunar.xml` after adding a tool (mocked if UI isn't ready) and verify the XML structure is clean.
- [ ] **Persistence**: Restart the IDE and verify (via debugger or logs) that the `toolInventory` list is correctly reloaded.

### TOOL-01 Phase 2: Validation Logic (#137)
- [ ] **CLI Execution**: Verify that calling the validator on a valid `luarocks` path returns the correct semantic version.
- [ ] **Regex Verification**: Test the regex against non-standard but valid version strings (e.g., dev builds or alpha releases).
- [ ] **Error Handling**: Verify that a non-executable file at a valid path returns a clear "Invalid Executable" state.

### TOOL-01 Phase 3: Discovery Service (#138)
- [ ] **Auto-Detect**: Verify that tools in standard locations (e.g., `/usr/local/bin/luarocks`) are found automatically.
- [ ] **Environment PATH**: Ensure the service scans the current system `PATH` correctly.
- [ ] **Permissions**: Verify the service doesn't crash if it encounters a directory it doesn't have read access to.

### TOOL-01 Phase 4: Core Management Service (#139)
- [ ] **Registration**: Verify that registering the same tool path twice results in a single entry or an update, not a duplicate.
- [ ] **Lifecycle**: Verify that unregistering a tool removes it from the inventory and clears any global bindings using that tool.

---

## TOOL-02: Project Binding & Environment Integration

### TOOL-02 Phase 1: Binding Storage (#140)
- [ ] **Project vs Global**: Verify that `lunar.xml` at the project level (`.idea/lunar.xml`) stores bindings separately from application-level settings.

### TOOL-02 Phase 2: Resolution Logic (#141)
- [ ] **Inheritance**: Verify that if a project binding is set to "None/Inherit", the global default is returned.
- [ ] **Override**: Verify that setting a project-specific tool correctly overrides a different global default.

### TOOL-02 Phase 3: Run Configuration Integration (#142)
- [ ] **Process Environment**: Launch a Lua script. Use an external tool (like `ps` or `Process Explorer`) to verify the child process has the tool directory in its `PATH`.
- [ ] **Execution**: Verify that a script calling `os.execute("luarocks ...")` uses the version bound in the IDE.

### TOOL-02 Phase 4: Terminal Integration (#143)
- [ ] **Terminal PATH**: Open a new Integrated Terminal. Run `echo $PATH` (POSIX) or `$env:Path` (Windows) and verify the tool directory is at the *front* of the list.
- [ ] **Session Isolation**: Open two different projects. Verify each terminal has the correct `PATH` for its respective project binding.

---

## TOOL-03: UI/UX & Health Monitoring

### TOOL-03 Phase 1: Global Inventory UI (#144)
- [ ] **Usability**: Verify the "Add" button opens the native file picker.
- [ ] **Visual Feedback**: Verify that the table shows a warning icon for tools that failed validation.
- [ ] **Interactive**: Verify that double-clicking an entry allows editing the name or path.

### TOOL-03 Phase 2: Project Binding UI (#145)
- [ ] **Context**: Verify the project settings panel only shows tool types relevant to the current project.
- [ ] **Selection**: Verify the dropdown correctly lists all tools of that type from the global inventory.

### TOOL-03 Phase 3: Health Monitoring (#146)
- [ ] **Background Check**: Delete a registered tool from the filesystem. Wait for the background interval (or restart) and verify the UI marks the tool as "Missing".
- [ ] **Recovery**: Restore the file and verify the UI updates the status to "OK" automatically.

### TOOL-03 Phase 4: Notifications (#147)
- [ ] **Alerting**: Open a project with a bound tool that is now missing. Verify a non-modal notification balloon appears with a "Fix..." link.

---

## TOOL-DR: De-risking Tasks

### TOOL-DR-01: Prototype Terminal PATH injection (#148)
- [ ] **Cross-Shell Test**: Verify the prototype works in Bash, Zsh, CMD, and PowerShell.
- [ ] **Override Test**: Verify the injected path persists even if the user's `.zshrc` or `.bashrc` modifies the `PATH`.

### TOOL-DR-02: Define OS-specific filename patterns (#149)
- [ ] **Windows Review**: Verify `luarocks.bat` and `luarocks.exe` are both included in the search patterns.
- [ ] **POSIX Review**: Verify no-extension and `.sh` variants are considered where applicable.

### TOOL-DR-03: Verify map serialization in settings (#150)
- [ ] **Stress Test**: Populate a map with 50+ entries and non-standard characters in the keys. Verify the resulting XML is valid and correctly escaped.

### TOOL-DR-04: Implement Async process wrapper (#151)
- [ ] **Thread Safety**: Use the "Internal Actions" -> "UI Freeze Detector" or simply watch the UI while running a slow discovery. Verify the "spinning wheel" cursor never appears.
