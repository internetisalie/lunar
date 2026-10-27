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

---Bitwise operations on numbers.
---@class bit
bit = {}

---Normalize a number to the range of a signed 32-bit integer.
---@param x number
---@return number
function bit.tobit(x) end

---Convert a number to a hex string.
---@param x number
---@param n? number
---@return string
function bit.tohex(x, n) end

---Bitwise NOT.
---@param x number
---@return number
function bit.bnot(x) end

---Bitwise OR.
---@param x number
---@param ... number
---@return number
function bit.bor(x, ...) end

---Bitwise AND.
---@param x number
---@param ... number
---@return number
function bit.band(x, ...) end

---Bitwise XOR.
---@param x number
---@param ... number
---@return number
function bit.bxor(x, ...) end

---Bitwise left shift.
---@param x number
---@param n number
---@return number
function bit.lshift(x, n) end

---Bitwise logical right shift.
---@param x number
---@param n number
---@return number
function bit.rshift(x, n) end

---Bitwise arithmetic right shift.
---@param x number
---@param n number
---@return number
function bit.arshift(x, n) end

---Bitwise left rotation.
---@param x number
---@param n number
---@return number
function bit.rol(x, n) end

---Bitwise right rotation.
---@param x number
---@param n number
---@return number
function bit.ror(x, n) end

---Byte swap.
---@param x number
---@return number
function bit.bswap(x) end
