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

---TABLE LIBRARY
---@class table
table = {}

---Concatenates table elements into string
---sep: separator (default empty), i/j: first/last index
---@overload fun(list: table): string
---@overload fun(list: table, sep: string): string
---@param list table
---@param sep? string
---@param i? integer
---@param j? integer
---@return string
function table.concat(list, sep, i, j) end

---Inserts element into table at position pos
---pos defaults to #list+1 (end of table)
---@overload fun(list: table, value: any): nil
---@param list table
---@param pos? integer
---@param value? any
function table.insert(list, pos, value) end

---Removes element from table at position pos
---pos defaults to #list (last element)
---@overload fun(list: table): any
---@param list table
---@param pos? integer
---@return any
function table.remove(list, pos) end

---Sorts table in-place
---comp: comparison function(a, b) returning true if a should come before b
---@param list table
---@param comp? fun(a: any, b: any): boolean
function table.sort(list, comp) end

---Unpacks list[i], list[i+1], ..., list[j]
---Moved from global unpack in Lua 5.1
---@param list table
---@param i? integer
---@param j? integer
---@return ...
function table.unpack(list, i, j) end

---Returns table with values as list elements and field 'n' with count
---@vararg any
---@return table
function table.pack(...) end

