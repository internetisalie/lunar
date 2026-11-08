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

---STRING LIBRARY
---@class string
string = {}

---Returns the internal numeric codes of the characters s[i], s[i+1], ..., s[j]
---The default for i is 1; the default for j is i
---@overload fun(s: string): integer
---@overload fun(s: string, i: integer): integer
---@param s string
---@param i? integer
---@param j? integer
---@return integer ...
function string.byte(s, i, j) end

---Returns a string whose characters have the given internal numeric codes
---@vararg integer
---@return string
function string.char(...) end

---Returns a string containing a binary representation of the given function (loadable with load)
---@param f function
---@return string
function string.dump(f) end

---Finds the first match of pattern in s; returns its start and end indices plus any captures, or nil
---If plain is true, pattern is matched as a plain substring
---@overload fun(s: string, pattern: string): integer|nil, integer|nil, ...
---@overload fun(s: string, pattern: string, init: integer): integer|nil, integer|nil, ...
---@param s string
---@param pattern string
---@param init? integer
---@param plain? boolean
---@return integer|nil, integer|nil, ...
function string.find(s, pattern, init, plain) end

---Returns a formatted version of its variable number of arguments (printf-style)
---@param formatstring string
---@vararg any
---@return string
function string.format(formatstring, ...) end

---Returns an iterator that, each time it is called, returns the next captures of pattern over s
---If pattern specifies no captures, the whole match is produced in each call
---@param s string
---@param pattern string
---@return fun(): string|nil, ...
function string.gmatch(s, pattern) end

---Returns a copy of s in which all (or the first n) occurrences of pattern are replaced by repl
---repl: a string (%0-%9 refer to captures), a table, or a function
---Also returns the total number of matches that occurred
---@overload fun(s: string, pattern: string, repl: string|table|function): string, integer
---@param s string
---@param pattern string
---@param repl string|table|function
---@param n? integer
---@return string, integer
function string.gsub(s, pattern, repl, n) end

---Returns the length of s
---@param s string
---@return integer
function string.len(s) end

---Returns a copy of s with all uppercase letters changed to lowercase
---@param s string
---@return string
function string.lower(s) end

---Looks for the first match of pattern in s, starting at init
---Returns the captures from the pattern, or the whole match if it has none; nil if not found
---@param s string
---@param pattern string
---@param init? integer
---@return string|nil, ...
function string.match(s, pattern, init) end

---Returns a string that is the concatenation of n copies of s, separated by sep (default: empty)
---@overload fun(s: string, n: integer): string
---@param s string
---@param n integer
---@param sep? string
---@return string
function string.rep(s, n, sep) end

---Returns s reversed
---@param s string
---@return string
function string.reverse(s) end

---Returns the substring of s from i to j (negative indices count from the end); the default for j is -1
---@overload fun(s: string, i: integer): string
---@param s string
---@param i integer
---@param j? integer
---@return string
function string.sub(s, i, j) end

---Returns a copy of s with all lowercase letters changed to uppercase
---@param s string
---@return string
function string.upper(s) end
