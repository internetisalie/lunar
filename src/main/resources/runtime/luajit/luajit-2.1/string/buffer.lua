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

---A string buffer object
---@class string.buffer
local buf = {}

---Empties the buffer and keeps its allocation
---@return string.buffer
function buf:reset() end

---Empties the buffer and frees its allocation
---@return string.buffer
function buf:free() end

---Appends strings, numbers or objects with a __tostring metamethod
---@param ... string|number|table
---@return string.buffer
function buf:put(...) end

---Appends formatted output, as string.format
---@param format string
---@param ... any
---@return string.buffer
function buf:putf(format, ...) end

---Appends len bytes from a cdata pointer
---@param cdata ffi.cdata
---@param len integer
---@return string.buffer
function buf:putcdata(cdata, len) end

---Replaces the buffer contents with a string, or with len bytes from a cdata pointer
---@param str string|ffi.cdata
---@param len? integer
---@return string.buffer
function buf:set(str, len) end

---Reserves at least size bytes of write space and returns a pointer to it and its length
---@param size integer
---@return ffi.cdata ptr
---@return integer len
function buf:reserve(size) end

---Commits used bytes of previously reserved write space
---@param used integer
---@return string.buffer
function buf:commit(used) end

---Skips (consumes) len bytes
---@param len integer
---@return string.buffer
function buf:skip(len) end

---Consumes and returns len bytes, or the whole buffer when len is nil, for each argument
---@param ... integer|nil
---@return string ...
function buf:get(...) end

---Returns the buffer contents as a string without consuming them
---@return string
function buf:tostring() end

---Returns a pointer to the buffer data and its length
---@return ffi.cdata ptr
---@return integer len
function buf:ref() end

---Serializes obj and appends it to the buffer
---@param obj any
---@return string.buffer
function buf:encode(obj) end

---Deserializes and consumes one object from the buffer
---@return any
function buf:decode() end

---LuaJIT string buffer library, loaded with require("string.buffer")
local buffer = {}

---Creates a new buffer, optionally with an initial size and options
---@overload fun(options: table): string.buffer
---@param size? integer
---@param options? table
---@return string.buffer
function buffer.new(size, options) end

---Serializes obj into a string
---@param obj any
---@return string
function buffer.encode(obj) end

---Deserializes one object from str
---@param str string
---@return any
function buffer.decode(str) end

return buffer
