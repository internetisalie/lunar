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

---DEBUG LIBRARY
---@class debug
debug = {}

---Enters an interactive mode with the user, running each string that the user enters
---A line containing only the word cont finishes this function
function debug.debug() end

---Returns the current hook settings of the thread: the hook function, the hook mask and the hook count
---@param thread? thread
---@return function|nil, string, integer
function debug.gethook(thread) end

---Returns a table with information about a function: f is a function or a stack level
---what selects the fields: "n" (name), "S" (source), "l" (currentline), "u" (upvalues), "t" (istailcall),
---"f" (func), "L" (activelines), "r" (ftransfer, ntransfer); returns nil when the level is out of range
---@overload fun(f: integer|function, what?: string): table|nil
---@param thread thread|integer|function
---@param f? integer|function|string
---@param what? string
---@return table|nil
function debug.getinfo(thread, f, what) end

---Returns the name and the value of the local variable with index loc of the function at level f
---Returns nil when there is no local with that index; with a function f, returns only parameter names
---@overload fun(f: integer|function, loc: integer): string|nil, any
---@param thread thread|integer|function
---@param f integer|function
---@param loc? integer
---@return string|nil name, any value
function debug.getlocal(thread, f, loc) end

---Returns the metatable of the given value, or nil if it does not have a metatable
---@param value any
---@return table|nil
function debug.getmetatable(value) end

---Returns the registry table
---@return table
function debug.getregistry() end

---Returns the name and the value of the upvalue with index up of the function f
---Returns nil if there is no upvalue with the given index
---@param f function
---@param up integer
---@return string|nil name, any value
function debug.getupvalue(f, up) end

---Returns the n-th user value (default 1) associated to the userdata u, plus false if u does not have that value
---@param u any
---@param n? integer
---@return any, boolean
function debug.getuservalue(u, n) end

---Sets the given function as a hook; with no arguments, turns the hook off
---mask: "c" (call), "r" (return), "l" (line); count > 0 also calls the hook after every count instructions
---@overload fun(): nil
---@overload fun(hook: function, mask: string, count?: integer): nil
---@param thread? thread|function
---@param hook? function|string
---@param mask? string|integer
---@param count? integer
function debug.sethook(thread, hook, mask, count) end

---Assigns value to the local variable with index loc of the function at level level
---Returns nil if there is no local with that index, otherwise the name of the local
---@overload fun(level: integer, loc: integer, value: any): string|nil
---@param thread thread|integer
---@param level integer
---@param loc any
---@param value? any
---@return string|nil
function debug.setlocal(thread, level, loc, value) end

---Sets the metatable for the given value to table (which can be nil); returns value
---@param value any
---@param table table|nil
---@return any
function debug.setmetatable(value, table) end

---Assigns value to the upvalue with index up of the function f
---Returns the name of the upvalue, or nil if there is no upvalue with the given index
---@param f function
---@param up integer
---@param value any
---@return string|nil
function debug.setupvalue(f, up, value) end

---Sets value as the n-th user value (default 1) associated to the full userdata udata
---Returns udata, or nil if the userdata does not have that value
---@param udata userdata
---@param value any
---@param n? integer
---@return userdata|nil
function debug.setuservalue(udata, value, n) end

---Returns message with a traceback of the call stack appended; a non-string, non-nil message is returned as is
---level (default 1, or 0 for a thread) tells at which level to start the traceback
---@overload fun(message?: any, level?: integer): string
---@param thread? any
---@param message? any
---@param level? integer
---@return string
function debug.traceback(thread, message, level) end

---Returns a unique identifier (as light userdata) for the upvalue numbered n from function f
---@param f function
---@param n integer
---@return userdata
function debug.upvalueid(f, n) end

---Makes the n1-th upvalue of the Lua closure f1 refer to the n2-th upvalue of the Lua closure f2
---@param f1 function
---@param n1 integer
---@param f2 function
---@param n2 integer
function debug.upvaluejoin(f1, n1, f2, n2) end
