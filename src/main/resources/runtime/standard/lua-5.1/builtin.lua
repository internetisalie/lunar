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

---The type nil has one single value, **nil**, whose main property is to be
---different from any other value; it usually represents the absence of a useful value.
---@class nil

---The type boolean has two values, **false** and **true**. Both **nil** and
---**false** make a condition false; any other value makes it true.
---@class boolean

---The type number uses two internal representations, or two subtypes, one
---called integer and the other called float. Lua has explicit rules about
---when each representation is used, but it also converts between them
---automatically as needed. Standard Lua uses 64-bit integers and
---double-precision (64-bit) floats.
---@class number

---The type string represents immutable sequences of bytes.
---Lua is 8-bit clean: strings can contain any 8-bit value, including embedded
---zeros. Almost any Lua value can be converted to a string using tostring()
---@class string

---Lua can call (and manipulate) functions written in Lua and functions
---written in C. Both are represented by the type function.
---@class function

---The type userdata is provided to allow arbitrary C data to be stored in
---Lua variables. A userdata value represents a block of raw memory. There are
---two kinds of userdata: full userdata, which is an object with a block of
---memory managed by Lua, and light userdata, which is simply a C pointer value.
---Userdata has no predefined operations in Lua, except assignment and identity
---test. By using metatables, the programmer can define operations for full
---userdata values. Userdata values cannot be created or modified in Lua, only
---through the C API.
---@class userdata

---The type thread represents independent threads of execution and is used to
---implement coroutines. Lua threads are not related to operating-system threads.
---Lua supports coroutines on all systems, even those that do not support
---threads natively.
---@class thread

---The type table implements associative arrays, that is, arrays that can have
---as indices not only numbers, but any Lua value except nil and NaN. Tables can
---be heterogeneous; that is, they can contain values of all types (except nil).
---Tables are the sole data-structuring mechanism in Lua; they can be used to
---represent ordinary arrays, lists, symbol tables, sets, records, graphs, trees, etc.
---@class table

---Any of the built-in Lua types
---@class any

---Return type for functions that don't return a value
---@class void

---Represents 'self' in method definitions
---@class self


---The global environment is represented by an ordinary Lua table
---@class _G
_G = {}

---Lua version string (e.g., "Lua 5.1")
_VERSION = "Lua 5.1"

---Returns a string describing the type of its only argument
---Possible return values: "nil", "boolean", "number", "string", "table", "function", "thread", "userdata"
---@param v any
---@return '"nil"'|'"boolean"'|'"number"'|'"string"'|'"table"'|'"function"'|'"thread"'|'"userdata"'
function type(v) end

---Converts argument e to a number with optional base
---@overload fun(e: string): number|nil
---@overload fun(e: string, base: integer): number|nil
---@param e string
---@param base? integer
---@return number|nil
function tonumber(e, base) end

---Converts a value to a string
---@param v any
---@return string
function tostring(v) end

---Prints its arguments using tostring
---@vararg any
function print(...) end

---Calls function f with arguments, catching errors
---Returns true + results on success, false + error message on error
---@overload fun(f: function): boolean, ...
---@param f function
---@vararg any
---@return boolean success, ...
function pcall(f, ...) end

---Like pcall but with custom error handler
---@param f function
---@param err function error handler
---@vararg any
---@return boolean success, ...
function xpcall(f, err, ...) end

---Raises an error with message at given level
---@param message any
---@param level? integer
function error(message, level) end

---Controls garbage collection behavior
---@overload fun(opt: '"stop"'): nil
---@overload fun(opt: '"restart"'): nil
---@overload fun(opt: '"collect"'): nil
---@overload fun(opt: '"count"'): integer, integer
---@overload fun(opt: '"step"', arg: integer): boolean
---@overload fun(opt: '"setpause"', arg: integer): integer
---@overload fun(opt: '"setstepmul"', arg: integer): integer
---@param opt string "stop"|"restart"|"collect"|"count"|"step"|"setpause"|"setstepmul"
---@param arg? integer
---@return any
function collectgarbage(opt, arg) end

---Returns next key-value pair from table
---When called with nil, returns first key
---@param t table
---@param index? any
---@return any key, any value
function next(t, index) end

---Returns iterator for key-value pairs
---@param t table
---@return function
function pairs(t) end

---Returns iterator for array elements (with integer indices)
---@param t table
---@return function
function ipairs(t) end

---Gets the length of a table without invoking __len metamethod
---@param t table
---@return integer
function rawlen(t) end

---Gets a value from table by key without invoking metamethods
---@param t table
---@param key any
---@return any
function rawget(t, key) end

---Sets a value in table by key without invoking metamethods
---@param t table
---@param key any
---@param value any
function rawset(t, key, value) end

---Loads Lua code from reader function
---@param reader fun(): string|nil
---@param chunkname? string
---@return function|nil, string|nil
function load(reader, chunkname) end

---Loads Lua code from file
---@overload fun(): function
---@overload fun(filename: string): function|nil
---@param filename? string
---@return function|nil, string|nil
function loadfile(filename) end

---Loads and executes Lua code from string (deprecated, use load+pcall)
---@deprecated
---@param chunk string
---@param chunkname? string
---@return function|nil, string|nil
function loadstring(chunk, chunkname) end

---Executes string chunk (deprecated)
---@deprecated
---@param chunk string
function dostring(chunk) end

---Selects arguments after index
---@param index integer|'"#"'
---@vararg any
---@return ...
function select(index, ...) end

---Calls error if value is false; otherwise returns all arguments
---@overload fun(v: any): any
---@param v any
---@param message? string
---@return any
function assert(v, message) end

---Opens and executes file as Lua chunk
---Returns all values returned by chunk, or nil on error
---@param filename? string
---@return any
function dofile(filename) end

---Gets the function environment (Lua 5.1 only)
---@overload fun(): table
---@param f integer|function
---@return table
function getfenv(f) end

---Gets the metatable of object
---Returns nil if no metatable or __metatable field
---@param object any
---@return table|nil
function getmetatable(object) end

---Declares a module and returns its environment
---Sets it in package.loaded and returns the table
---@param name string
---@vararg any
---@return table
function module(name, ...) end

---Compares two values for equality without invoking __eq
---@param v1 any
---@param v2 any
---@return boolean
function rawequal(v1, v2) end

---Sets the function environment (Lua 5.1 only)
---@param f integer|function
---@param table table
---@return function
function setfenv(f, table) end

---Sets the metatable for a table
---Can be prevented by __metatable field
---@param table table
---@param metatable table|nil
---@return table
function setmetatable(table, metatable) end

---Unpacks list[i], list[i+1], ..., list[j]
---Equivalent to table.unpack in Lua 5.2+
---@param list table
---@param i? integer
---@param j? integer
---@return ...
function unpack(list, i, j) end
