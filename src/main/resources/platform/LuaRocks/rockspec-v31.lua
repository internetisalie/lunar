---@meta rockspec

-- LuaCATS type definitions for LuaRocks .rockspec files (schema version 3.1).

---@class RockspecDescription
---@field summary?    string             Short one-line description of the package.
---@field detailed?   string             Long-form description of the package.
---@field homepage?   string             URL of the project homepage.
---@field license?    string             SPDX license identifier or license name.
---@field maintainer? string             Name and/or e-mail address of the maintainer.
---@field labels?     table<string, string> Categorisation labels (key-value pairs).
---@field issues_url? string             URL of the issue tracker.
local RockspecDescription = {}

---@class RockspecExternalDep
---@field program? string Name of an external program that must be present.
---@field header?  string Name of a C header file that must be found.
---@field library? string Name of a native library that must be linkable.
local RockspecExternalDep = {}

---@class RockspecExternalDepsMap
---@field [string] RockspecExternalDep External dependency entries.
local RockspecExternalDepsMap = {}

--- External dependencies with optional per-platform overrides.
---@class RockspecExternalDependencies
---@field platforms? table<string, RockspecExternalDepsMap> Per-platform dependency overrides.
local RockspecExternalDependencies = {}

---@class RockspecDependencyMap
---@field [string] string Dependency constraint strings matching `^%s*([a-zA-Z0-9%.%-%_]*/?[a-zA-Z0-9][a-zA-Z0-9%.%-%_]*)%s*([^/]*)$`.
local RockspecDependencyMap = {}

--- Dependencies with optional per-platform overrides.
---@class RockspecDependencies
---@field platforms? table<string, RockspecDependencyMap> Per-platform dependency overrides.
local RockspecDependencies = {}

--- Build-time dependencies with optional per-platform overrides.
---@class RockspecBuildDependencies
---@field platforms? table<string, RockspecDependencyMap> Per-platform dependency overrides.
local RockspecBuildDependencies = {}

--- Test-time dependencies with optional per-platform overrides.
---@class RockspecTestDependencies
---@field platforms? table<string, RockspecDependencyMap> Per-platform dependency overrides.
local RockspecTestDependencies = {}

---@class RockspecSource
---@field url         string                        URL from which the source archive or VCS repository is fetched (required).
---@field md5?        string                        Expected MD5 checksum of the downloaded archive.
---@field file?       string                        Local filename to use when saving the downloaded archive.
---@field dir?        string                        Sub-directory inside the archive that contains the package root.
---@field tag?        string                        VCS tag to check out (git/hg/svn).
---@field branch?     string                        VCS branch to check out.
---@field module?     string                        VCS module name (used by CVS and some other VCS backends).
---@field cvs_tag?    string                        CVS tag.
---@field cvs_module? string                        CVS module name.
---@field platforms?  table<string, RockspecSource> Per-platform source overrides.
local RockspecSource = {}

---@class RockspecBuildInstall
---@field lua?  table<string, any> Lua module files: destination-name → source-path.
---@field lib?  table<string, any> Native library files: destination-name → source-path.
---@field conf? table<string, any> Configuration files: destination-name → source-path.
---@field bin?  table<string, any> Executable scripts: destination-name → source-path.
local RockspecBuildInstall = {}

---@class RockspecBuildPlatform
---@field type?              string                            Build backend for this platform.
---@field install?          RockspecBuildInstall              Additional files to install by category.
---@field copy_directories? table<string, string>             Directories to copy verbatim.
local RockspecBuildPlatform = {}

---@class RockspecBuild
---@field type               string                              Build backend (e.g. `"builtin"`, `"make"`, `"cmake"`, `"command"`).
---@field install?           RockspecBuildInstall               Additional files to install by category.
---@field copy_directories?  table<string, string>              Directories to copy verbatim into the install prefix.
---@field platforms?         table<string, RockspecBuildPlatform> Per-platform build overrides.
---@field [string] any       Additional backend-specific properties.
local RockspecBuild = {}

---@class RockspecHooksPlatform
---@field post_install? string Lua chunk executed after installation on this platform.
local RockspecHooksPlatform = {}

---@class RockspecHooks
---@field post_install? string                              Lua chunk executed after the package is installed.
---@field platforms?    table<string, RockspecHooksPlatform> Per-platform hook overrides.
local RockspecHooks = {}

---@class RockspecDeploy
---@field wrap_bin_scripts? boolean When `true`, LuaRocks wraps bin-scripts with the active Lua interpreter.
local RockspecDeploy = {}

---@class RockspecTestPlatform
local RockspecTestPlatform = {}

---@class RockspecTest
---@field type?       string                          Test runner type.
---@field platforms?  table<string, RockspecTestPlatform> Per-platform test overrides.
---@field [string] any Additional test configuration properties.
local RockspecTest = {}

--- Root class representing a complete LuaRocks rockspec.
---@class Rockspec
---@field rockspec_format?      string                            Rockspec format version (e.g. `"1.0"`, `"3.1"`).
---@field package               string                            Package name (case-insensitive, required).
---@field version               string                            Package version plus rockspec revision (e.g. `"1.2.3-1"`, required).
---@field description?          RockspecDescription               Package metadata (summary, license, homepage, …).
---@field supported_platforms?  table<string, string>             Platforms this rock supports (e.g. `"linux"`, `"!windows"`).
---@field dependencies?         RockspecDependencies              Run-time dependencies.
---@field build_dependencies?   RockspecBuildDependencies         Build-time dependencies.
---@field test_dependencies?    RockspecTestDependencies          Test-time dependencies.
---@field external_dependencies? RockspecExternalDependencies      External system dependencies (headers, libraries, programs).
---@field source                RockspecSource                    Location from which the package source is fetched (required).
---@field build                 RockspecBuild                     Instructions for building and installing the package (required).
---@field hooks?                RockspecHooks                     Lua hooks executed at install time.
---@field test?                 RockspecTest                      Test configuration and runner type.
---@field deploy?               RockspecDeploy                    Deployment options.
local Rockspec = {}
