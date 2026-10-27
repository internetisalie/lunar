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

---@class os
os = {}

---Executes command in operating system shell
---Returns exit status
---@param command string
---@return integer
function os.execute(command) end

---Terminates program with exit code
---nil/true -> EXIT_SUCCESS, false/number -> that code
---@param code? boolean|integer
---@param close? boolean
function os.exit(code, close) end

---Returns process environment variable value or nil
---@param varname string
---@return string|nil
function os.getenv(varname) end

---Deletes file or empty directory
---Returns true on success, nil + error message on failure
---@param filename string
---@return boolean|nil, string|nil
function os.remove(filename) end

---Renames file from oldname to newname
---Returns true on success, nil + error message on failure
---@param oldname string
---@param newname string
---@return boolean|nil, string|nil
function os.rename(oldname, newname) end

---Returns current time in seconds since epoch
---@return integer
function os.time() end

---Returns formatted date/time string according to format
---If time nil, uses current time
---@overload fun(format: string): string
---@param format string
---@param time? integer
---@return string
function os.date(format, time) end

---Returns an approximation of the CPU time used by the program, in seconds
---@return number
function os.clock() end

---Returns the number of seconds from time t1 to time t2
---@param t2 number
---@param t1? number
---@return number
function os.difftime(t2, t1) end

---Sets the current locale of the program; returns the new locale name, or nil if the request fails
---category: "all" (default), "collate", "ctype", "monetary", "numeric" or "time"
---@param locale? string
---@param category? string
---@return string|nil
function os.setlocale(locale, category) end

---Returns a name that can be used for a temporary file
---The file must be opened explicitly and removed when no longer needed
---@return string
function os.tmpname() end
