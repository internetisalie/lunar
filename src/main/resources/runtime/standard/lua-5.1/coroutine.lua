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

---@class coroutine
coroutine = {}

---Creates new coroutine with function body
---Returns thread object; coroutine not started yet
---@param f fun(...: any): ...
---@return thread
function coroutine.create(f) end

---Starts or resumes coroutine
---First call uses passed arguments; subsequent calls resume from yield
---Returns true + values yielded, or false + error message
---@param co thread
---@vararg any
---@return boolean success, ...
function coroutine.resume(co, ...) end

---Returns currently running coroutine and true/false
---@return thread, boolean
function coroutine.running() end

---Returns coroutine status: "suspended", "running", "normal", "dead"
---@param co thread
---@return '"suspended"'|'"running"'|'"normal"'|'"dead"'
function coroutine.status(co) end

---Creates iterator that resumes coroutine on each call
---@param co thread
---@return function
function coroutine.wrap(co) end

---Suspends coroutine execution
---Cannot be called from C function, metamethod, or iterator
---Returns values passed to resume that woke it
---@vararg any
---@return ...
function coroutine.yield(...) end
