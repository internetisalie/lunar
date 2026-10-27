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

---A C data object
---@class ffi.cdata : userdata

---A C type object
---@class ffi.ctype : userdata

---A C library namespace
---@class ffi.namespace : table

---A callback created from a Lua function
---@class ffi.cb : ffi.cdata
local cb = {}

---Frees the resources associated with the callback
function cb:free() end

---Associates a new Lua function with the callback
---@param func function
function cb:set(func) end

---LuaJIT FFI library, loaded with require("ffi")
---@class ffilib
local ffi = {}

---The default C library namespace
---@type ffi.namespace
ffi.C = {}

---Target OS name
---@type string
ffi.os = ""

---Target architecture name
---@type string
ffi.arch = ""

---Adds C declarations for types or external symbols
---@param def string
function ffi.cdef(def) end

---Loads a dynamic library and returns its namespace
---@param name string
---@param global? boolean
---@return ffi.namespace
function ffi.load(name, global) end

---Creates a cdata object for the given ctype
---@param ct string|ffi.ctype|ffi.cdata
---@param nelem? integer
---@param ... any
---@return ffi.cdata
function ffi.new(ct, nelem, ...) end

---Creates a ctype object for the given C type
---@param ct string|ffi.ctype|ffi.cdata
---@param ... any
---@return ffi.ctype
function ffi.typeof(ct, ...) end

---Converts init to a cdata object of the given ctype
---@param ct string|ffi.ctype|ffi.cdata
---@param init any
---@return ffi.cdata
function ffi.cast(ct, init) end

---Creates a ctype with a metatable for its cdata objects
---@param ct string|ffi.ctype|ffi.cdata
---@param metatable table
---@return ffi.ctype
function ffi.metatype(ct, metatable) end

---Attaches a finalizer to a cdata object, or removes it when finalizer is nil
---@param cdata ffi.cdata
---@param finalizer function|nil
---@return ffi.cdata
function ffi.gc(cdata, finalizer) end

---Returns the size of ct in bytes, or nil if the size is unknown
---@param ct string|ffi.ctype|ffi.cdata
---@param nelem? integer
---@return integer|nil
function ffi.sizeof(ct, nelem) end

---Returns the minimum required alignment of ct in bytes
---@param ct string|ffi.ctype|ffi.cdata
---@return integer
function ffi.alignof(ct) end

---Returns the offset in bytes of field relative to the start of ct, plus bit position and size for bit fields
---@param ct string|ffi.ctype|ffi.cdata
---@param field string
---@return integer|nil ofs
---@return integer|nil bpos
---@return integer|nil bsize
function ffi.offsetof(ct, field) end

---Returns true if obj has the C type given by ct
---@param ct string|ffi.ctype|ffi.cdata
---@param obj any
---@return boolean
function ffi.istype(ct, obj) end

---Returns the C errno of the last C call, optionally setting a new value
---@param newerr? integer
---@return integer
function ffi.errno(newerr) end

---Creates a Lua string from the data pointed to by ptr
---@param ptr any
---@param len? integer
---@return string
function ffi.string(ptr, len) end

---Copies len bytes from src to dst, or a whole Lua string when len is omitted
---@param dst any
---@param src any
---@param len? integer
function ffi.copy(dst, src, len) end

---Fills len bytes at dst with the byte value c (default 0)
---@param dst any
---@param len integer
---@param c? integer
function ffi.fill(dst, len, c) end

---Returns true if param (e.g. "32bit", "le", "fpu") applies to the target ABI
---@param param string
---@return boolean
function ffi.abi(param) end

return ffi
