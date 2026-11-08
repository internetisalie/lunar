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

---OS LIBRARY
---@class os
os = {}

---Returns an approximation of the CPU time used by the program, in seconds
---@return number
function os.clock() end

---Returns a string or a table containing date and time, formatted according to format
---A format starting with "!" uses UTC; "*t" returns a table; the default format is "%c"
---If time is absent, the current time is used
---@overload fun(format: "*t"|"!*t", time?: integer): table
---@param format? string
---@param time? integer
---@return string
function os.date(format, time) end

---Returns the number of seconds from time t1 to time t2
---@param t2 integer
---@param t1 integer
---@return number
function os.difftime(t2, t1) end

---Passes command to be executed by an operating system shell
---Returns true (or nil) for success, then "exit" or "signal", then the exit status or signal number
---Without a command, returns true when a shell is available
---@overload fun(): boolean
---@param command? string
---@return boolean|nil, string, integer
function os.execute(command) end

---Terminates the host program
---code true (the default) means EXIT_SUCCESS, false means EXIT_FAILURE, a number is the exit status
---If close is true, closes the Lua state before exiting
---@param code? boolean|integer
---@param close? boolean
function os.exit(code, close) end

---Returns the value of the process environment variable varname, or nil if it is not defined
---@param varname string
---@return string|nil
function os.getenv(varname) end

---Deletes the file (or empty directory) with the given name
---Returns true on success, or nil plus an error message and error code
---@param filename string
---@return boolean|nil, string|nil, integer|nil
function os.remove(filename) end

---Renames file or directory oldname to newname
---Returns true on success, or nil plus an error message and error code
---@param oldname string
---@param newname string
---@return boolean|nil, string|nil, integer|nil
function os.rename(oldname, newname) end

---Sets the current locale of the program; returns the new locale name, or nil if the request fails
---category: "all" (default), "collate", "ctype", "monetary", "numeric" or "time"
---@param locale? string
---@param category? "all"|"collate"|"ctype"|"monetary"|"numeric"|"time"
---@return string|nil
function os.setlocale(locale, category) end

---Returns the current time, or the time represented by the given table
---The table must have the fields year, month and day, and may have hour, min, sec and isdst
---@param table? table
---@return integer
function os.time(table) end

---Returns a name that can be used for a temporary file
---The file must be opened explicitly and removed when no longer needed
---@return string
function os.tmpname() end
