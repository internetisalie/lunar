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

---COROUTINE LIBRARY
---@class coroutine
coroutine = {}

---Creates a new coroutine with body f and returns it
---@param f function
---@return thread
function coroutine.create(f) end

---Returns true when the running coroutine can yield
---A coroutine is not yieldable when it is the main thread or inside a non-yieldable C function
---@return boolean
function coroutine.isyieldable() end

---Starts or continues the execution of coroutine co
---Returns true plus the values passed to yield (or returned by the body), or false plus the error message
---@param co thread
---@vararg any
---@return boolean success, ...
function coroutine.resume(co, ...) end

---Returns the running coroutine plus a boolean, true when the running coroutine is the main one
---@return thread, boolean
function coroutine.running() end

---Returns the status of coroutine co: "running", "suspended", "normal" or "dead"
---@param co thread
---@return "running"|"suspended"|"normal"|"dead"
function coroutine.status(co) end

---Creates a new coroutine with body f and returns a function that resumes it each time it is called
---@param f function
---@return function
function coroutine.wrap(f) end

---Suspends the execution of the calling coroutine
---Its arguments are returned as extra results by the resume that woke it
---@vararg any
---@return any ...
function coroutine.yield(...) end
