# Implementation Plan: Project Initialization & Setup (ROCKS-01)

## Phase 1: Templates & Resources [Must]
- [ ] Add \`.luacheckrc\` and \`.stylua.toml\` template files to project resources.
- [ ] Implement \`LuaRocksScaffolder.generateSetupLua(version)\`.

## Phase 2: CLI Integration [Must]
- [ ] Implement \`LuaRocksScaffolder.init(projectPath, luaVersions)\` using \`GeneralCommandLine\`.
- [ ] Add logic to update \`.gitignore\` automatically.

## Phase 3: Project Wizard [Must]
- [ ] Implement \`LuaRocksProjectGenerator\` for the "New Project" dialog.
- [ ] Create the UI for Template selection (Library vs. Plugin).
- [ ] Link the wizard to the \`LuaRocksScaffolder\`.

## Verification Tasks
- [ ] **Integration Test**: Scaffolding test that runs \`init\` and asserts file existence.
- [ ] **Manual Test**: Create a new "Neovim Plugin" project and verify the structure.
