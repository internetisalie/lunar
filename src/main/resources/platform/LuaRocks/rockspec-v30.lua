---@meta rockspec

-- LuaCATS type definitions for LuaRocks .rockspec files (schema version 3.0).

---@class RockSpecDescription
---@field summary?    string   Short one-line description of the package.
---@field detailed?   string   Long-form description of the package.
---@field homepage?   string   URL of the project homepage.
---@field license?    string   SPDX license identifier or license name.
---@field maintainer? string   Name and/or e-mail address of the maintainer.
---@field labels?     string[] Categorisation labels used on the LuaRocks website.
---@field issues_url? string   URL of the issue tracker.
local RockSpecDescription = {}

--- A dependency list is either a plain array of constraint strings …
---@alias RockSpecDependencies string[]|RockSpecDependenciesMap

--- … or an object that maps platform names to nested dependency lists.
---@class RockSpecDependenciesMap
---@field platforms? table<string, RockSpecDependencies> Per-platform dependency overrides.
local RockSpecDependenciesMap = {}

---@class RockSpecExternalDep
---@field program? string Name of an external program that must be present.
---@field header?  string Name of a C header file that must be found.
---@field library? string Name of a native library that must be linkable.
local RockSpecExternalDep = {}

---@class RockSpecSource
---@field url         string                        URL from which the source archive or VCS repository is fetched.
---@field md5?        string                        Expected MD5 checksum of the downloaded archive.
---@field file?       string                        Local filename to use when saving the downloaded archive.
---@field dir?        string                        Sub-directory inside the archive that contains the package root.
---@field tag?        string                        VCS tag to check out (git/hg/svn).
---@field branch?     string                        VCS branch to check out.
---@field module?     string                        VCS module name (used by CVS and some other VCS backends).
---@field cvs_tag?    string                        CVS tag.
---@field cvs_module?  string                       CVS module name.
---@field platforms?  table<string, RockSpecSource> Per-platform source overrides.
local RockSpecSource = {}

---@class RockSpecBuildModuleSpec
---@field sources?   string[] C/C++ source files to compile.
---@field defines?   string[] Preprocessor macro definitions (e.g. `"FOO=1"`).
---@field incdirs?   string[] Directories to add to the compiler include path.
---@field libraries? string[] Libraries to link against (without the `lib` prefix or extension).
---@field libdirs?   string[] Directories to add to the linker library search path.
local RockSpecBuildModuleSpec = {}

--- A single module entry is a source path string, a list of source paths,
--- or a detailed build spec object.
---@alias RockSpecBuildModule string|string[]|RockSpecBuildModuleSpec

---@class RockSpecBuildInstall
---@field lua?  table<string, string> Lua module files: destination-name → source-path.
---@field lib?  table<string, string> Native library files: destination-name → source-path.
---@field conf? table<string, string> Configuration files: destination-name → source-path.
---@field bin?  table<string, string> Executable scripts: destination-name → source-path.
local RockSpecBuildInstall = {}

---@class RockSpecBuild
---@field type              string                               Build backend (e.g. `"builtin"`, `"make"`, `"cmake"`, `"command"`).
---@field modules?          table<string, RockSpecBuildModule>   Map of Lua/C module names to their build instructions.
---@field install?          RockSpecBuildInstall                 Additional files to install by category.
---@field copy_directories? string[]                             Directories to copy verbatim into the install prefix.
---@field platforms?        table<string, RockSpecBuild>         Per-platform build overrides.
local RockSpecBuild = {}

---@class RockSpecHooksPlatform
---@field post_install? string Lua chunk executed after installation on this platform.
local RockSpecHooksPlatform = {}

---@class RockSpecHooks
---@field post_install? string                              Lua chunk executed after the package is installed.
---@field platforms?    table<string, RockSpecHooksPlatform> Per-platform hook overrides.
local RockSpecHooks = {}

---@class RockSpecDeploy
---@field wrap_bin_scripts? boolean When `true`, LuaRocks wraps bin-scripts with the active Lua interpreter.
local RockSpecDeploy = {}

--- Root class representing a complete LuaRocks rockspec.
---@class RockSpec
---@field rockspec_format?       string                              RockSpec format version (e.g. `"1.0"`, `"3.0"`). Defaults to `"1.0"`.
---@field package                string                              Package name (case-insensitive).
---@field version                string                              Package version plus rockspec revision, e.g. `"1.2.3-1"`.
---@field description?           RockSpecDescription                 Package metadata (summary, license, homepage, …).
---@field supported_platforms?   string[]                            Platforms this rock supports (e.g. `"linux"`, `"!windows"`).
---@field dependencies?          RockSpecDependencies                Run-time dependencies as constraint strings or a platform map.
---@field external_dependencies? table<string, RockSpecExternalDep> External system dependencies (headers, libraries, programs).
---@field source                 RockSpecSource                      Location from which the package source is fetched.
---@field build                  RockSpecBuild                       Instructions for building and installing the package.
---@field hooks?                 RockSpecHooks                       Lua hooks executed at install time.
---@field deploy?                RockSpecDeploy                      Deployment options.
local RockSpec = {}
