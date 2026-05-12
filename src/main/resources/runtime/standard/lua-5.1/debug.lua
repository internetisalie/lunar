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

---@class debug
debug = {}

---Returns information about function at stack level
---what controls info: "S" (source), "l" (line), "u" (upvalues), "n" (name), "f" (function)
---@param level integer
---@param what? string
---@return table
function debug.getinfo(level, what) end

---Returns name of local variable at given level and index
---First argument is index 1
---@param level integer
---@param local integer
---@return string|nil
function debug.getlocal(level, local) end

---Sets value of local variable at given level and index
---Returns variable name or nil if out of range
---@param level integer
---@param local integer
---@param value any
---@return string|nil
function debug.setlocal(level, local, value) end

---Returns function at stack level
---@param level integer
---@return function|nil
function debug.getfunc(level) end

---Sets debug hook function
---mask: "c" (call), "r" (return), "l" (line), "y" (count)
---count used with "y" option
---Nil hook disables hook
---@param func? fun(...any): any
---@param mask string
---@param count? integer
function debug.sethook(func, mask, count) end

---Returns current hook, its mask, and count
---@return fun|nil, string, integer|nil
function debug.gethook() end

---Sets environment of function (affects global variable access)
---Returns the function
---@param func function
---@param env table
---@return function
function debug.setfenv(func, env) end

---Returns environment of function
---@param func function
---@return table
function debug.getfenv(func) end

---Returns string representation of stack trace
---message: optional error message to prepend
---level: stack level to start from (default 1)
---@overload fun(): string
---@overload fun(message: string): string
---@param message? string
---@param level? integer
---@return string
function debug.traceback(message, level) end
