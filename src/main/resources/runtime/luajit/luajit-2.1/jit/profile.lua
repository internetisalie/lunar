-- LuaJIT -- a Just-In-Time Compiler for Lua. https://luajit.org/
--
-- Copyright (C) 2005-2026 Mike Pall. All rights reserved.
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in
-- all copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
-- THE SOFTWARE.
--
-- API stubs for Lunar, describing the API documented at https://luajit.org/.

---@meta

---LuaJIT sampling profiler, loaded with require("jit.profile")
local profile = {}

---Starts the profiler; callback receives the thread, the sample count and the VM state
---@param mode string
---@param func fun(thread: thread, samples: integer, vmstate: string)
function profile.start(mode, func) end

---Stops the profiler
function profile.stop() end

---Returns a string with a stack dump of the given thread, or of the current one
---@overload fun(thread: thread, fmt: string, depth: integer): string
---@param fmt string
---@param depth integer
---@return string
function profile.dumpstack(fmt, depth) end

return profile
