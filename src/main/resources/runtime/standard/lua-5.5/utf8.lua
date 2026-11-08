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

---UTF8 LIBRARY
---@class utf8
utf8 = {}

---Receives zero or more integers, converts each one to its UTF-8 byte sequence and returns the concatenation
---@vararg integer
---@return string
function utf8.char(...) end

---The pattern "[\0-\x7F\xC2-\xFD][\x80-\xBF]*", which matches exactly one UTF-8 byte sequence
---(assuming that the subject is a valid UTF-8 string)
utf8.charpattern = "[\0-\x7F\xC2-\xFD][\x80-\xBF]*"

---Returns an iterator such that `for p, c in utf8.codes(s) do body end` iterates over all characters of s,
---with p the position (in bytes) and c the code point of each character; raises an error on an invalid byte sequence
---If lax is true, the check that each code point is at most 10FFFF and is not a surrogate is skipped
---@param s string
---@param lax? boolean
---@return function, string, integer
function utf8.codes(s, lax) end

---Returns the code points (as integers) of all characters in s that start between byte positions i and j
---The default for i is 1 and for j is i; raises an error on an invalid byte sequence
---@param s string
---@param i? integer
---@param j? integer
---@param lax? boolean
---@return integer ...
function utf8.codepoint(s, i, j, lax) end

---Returns the number of UTF-8 characters in s that start between positions i and j (defaults 1 and -1)
---On an invalid byte sequence, returns nil plus the position of the first invalid byte
---@param s string
---@param i? integer
---@param j? integer
---@param lax? boolean
---@return integer|nil, integer|nil
function utf8.len(s, i, j, lax) end

---Returns the byte positions where the encoding of the n-th character of s (counting from position i) starts and ends
---A negative n gets characters before position i; the default for i is 1 when n is non-negative and #s + 1 otherwise
---When n is 0, returns the start and end of the character that contains the i-th byte of s
---Returns nil if the specified character is neither in the subject nor right after its end
---@param s string
---@param n integer
---@param i? integer
---@return integer|nil, integer|nil
function utf8.offset(s, n, i) end
