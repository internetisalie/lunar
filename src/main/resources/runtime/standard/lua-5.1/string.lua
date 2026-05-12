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

---@class string
string = {}

---Returns internal numeric codes of characters
---If i not given, defaults to 1
---If j not given, defaults to i
---@overload fun(s: string): integer
---@overload fun(s: string, i: integer): integer
---@param s string
---@param i? integer
---@param j? integer
---@return integer ...
function string.byte(s, i, j) end

---Returns string with character codes converted to characters
---@vararg integer
---@return string
function string.char(...) end

---Serializes Lua value to binary string (can be reloaded with loadstring)
---@param v any
---@return string
function string.dump(v) end

---Finds first occurrence of pattern in string
---If plain=true, treats pattern as literal string
---Returns indices of match and captures, or nil if not found
---@overload fun(s: string, patt: string): integer|nil, ...
---@overload fun(s: string, patt: string, init: integer): integer|nil, ...
---@param s string
---@param patt string
---@param init? integer
---@param plain? boolean
---@return integer|nil, ...
function string.find(s, patt, init, plain) end

---Replaces occurrences of pattern with replacement
---repl: string (with \\0-\\9 for captures), table, or function
---Returns modified string and number of replacements
---@overload fun(s: string, patt: string, repl: string): string, integer
---@overload fun(s: string, patt: string, repl: table): string, integer
---@overload fun(s: string, patt: string, repl: fun(...any): string): string, integer
---@param s string
---@param patt string
---@param repl string|table|function
---@param n? integer max substitutions
---@return string, integer
function string.gsub(s, patt, repl, n) end

---Returns length of string
---@param s string
---@return integer
function string.len(s) end

---Returns string repeated n times
---@param s string
---@param n integer
---@return string
function string.rep(s, n) end

---Returns string reversed
---@param s string
---@return string
function string.reverse(s) end

---Extracts substring from i to j (negative indices count from end)
---Defaults: i=1, j=-1 (end of string)
---@overload fun(s: string): string
---@overload fun(s: string, i: integer): string
---@param s string
---@param i? integer
---@param j? integer
---@return string
function string.sub(s, i, j) end

---Returns string converted to uppercase
---@param s string
---@return string
function string.upper(s) end

---Returns string converted to lowercase
---@param s string
---@return string
function string.lower(s) end

---Returns formatted string (printf-style)
---@param fmt string
---@vararg any
---@return string
function string.format(fmt, ...) end
