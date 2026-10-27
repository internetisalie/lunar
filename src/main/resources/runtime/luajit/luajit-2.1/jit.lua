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

---LuaJIT JIT-compiler control
---@class jitlib
jit = {}

---LuaJIT version string, e.g. "LuaJIT 2.1.1791988261"
---@type string
jit.version = ""

---LuaJIT version as a number
---@type integer
jit.version_num = 0

---Target OS name: "Windows", "Linux", "OSX", "BSD", "POSIX" or "Other"
---@type string
jit.os = ""

---Target architecture name, e.g. "x86", "x64", "arm64"
---@type string
jit.arch = ""

---JIT compiler optimization control
---@type table
jit.opt = {}

---Turns the JIT compiler on for the whole VM, or for a function and optionally its children
---@param func? function|boolean
---@param recursive? boolean
function jit.on(func, recursive) end

---Turns the JIT compiler off for the whole VM, or for a function and optionally its children
---@param func? function|boolean
---@param recursive? boolean
function jit.off(func, recursive) end

---Flushes the whole trace cache, the code of a function, or one trace
---@param func? function|boolean|integer
---@param recursive? boolean
function jit.flush(func, recursive) end

---Returns whether the JIT compiler is on, followed by the enabled CPU features and optimizations
---@return boolean status
---@return string ...
function jit.status() end

---Attaches a handler to a VM event, or detaches it when event is nil
---@param handler function
---@param event? string
function jit.attach(handler, event) end

---Returns the build's security setting for the given mode
---@param mode string
---@return integer
function jit.security(mode) end

---Sets JIT compiler optimization flags and parameters
---@param ... string|integer
function jit.opt.start(...) end
