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

---BIT LIBRARY (NEW IN 5.2)
---@class bit32
bit32 = {}

---Returns x shifted disp bits to the right (arithmetic shift: vacant bits are copies of bit 31)
---A negative disp shifts to the left
---@param x number
---@param disp number
---@return integer
function bit32.arshift(x, disp) end

---Returns the bitwise and of its operands
---@vararg number
---@return integer
function bit32.band(...) end

---Returns the bitwise negation of x
---@param x number
---@return integer
function bit32.bnot(x) end

---Returns the bitwise or of its operands
---@vararg number
---@return integer
function bit32.bor(...) end

---Returns whether the bitwise and of its operands is different from zero
---@vararg number
---@return boolean
function bit32.btest(...) end

---Returns the bitwise exclusive or of its operands
---@vararg number
---@return integer
function bit32.bxor(...) end

---Returns the unsigned number formed by bits field to field + width - 1 of n (width defaults to 1)
---@param n number
---@param field number
---@param width? number
---@return integer
function bit32.extract(n, field, width) end

---Returns a copy of n with bits field to field + width - 1 replaced by the value v (width defaults to 1)
---@param n number
---@param v number
---@param field number
---@param width? number
---@return integer
function bit32.replace(n, v, field, width) end

---Returns x rotated disp bits to the left (a negative disp rotates to the right)
---@param x number
---@param disp number
---@return integer
function bit32.lrotate(x, disp) end

---Returns x shifted disp bits to the left (a negative disp shifts to the right)
---@param x number
---@param disp number
---@return integer
function bit32.lshift(x, disp) end

---Returns x rotated disp bits to the right (a negative disp rotates to the left)
---@param x number
---@param disp number
---@return integer
function bit32.rrotate(x, disp) end

---Returns x shifted disp bits to the right (logical shift: vacant bits are filled with zeros)
---A negative disp shifts to the left
---@param x number
---@param disp number
---@return integer
function bit32.rshift(x, disp) end
