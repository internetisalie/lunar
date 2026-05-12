-- MIT License
--
-- Copyright © 1994–2025 Lua.org, PUC-Rio.
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in all
-- copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
-- SOFTWARE.

---@meta

---PACKAGE LIBRARY
---@class package
package = {}

---Loadable module names list
---Maps module name to loaded module
---@type table
package.loaded = {}

---Searchers for module loading
---Array of functions that search for modules
---Each searcher takes modname and returns loader or nil
---@type fun[]
package.searchers = {}

---Paths for searching modules
---Semicolon-separated list of module paths
---Each path can contain ? placeholder for module name
---@type string
package.path = ""

---Paths for searching C modules
---Semicolon-separated list of C module paths
---Each path can contain ? placeholder for module name
---@type string
package.cpath = ""

---Preloaded modules table
---Modules that are already loaded before require
---@type table
package.preloaded = {}

---Loads and returns a module
---Searches for a module using package.searchers
---Returns the module (from package.loaded or loaded by searcher)
---@param modname string
---@return any
function require(modname) end

---Dynamically loads a C library
---libname: path to C library
---funcname: name of function to initialize (optional)
---Returns function or nil + error message
---Not available on all platforms
---@overload fun(libname: string): function|nil, string|nil
---@param libname string
---@param funcname? string
---@return function|nil, string|nil
function package.loadlib(libname, funcname) end

---Makes all globals accessible from module environment
---Simplifies module interface but pollutes namespace
---Deprecated in Lua 5.2+
---@deprecated
---@param m table
function package.seeall(m) end

