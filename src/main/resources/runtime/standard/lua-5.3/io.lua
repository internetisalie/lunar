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

---IO LIBRARY
---@class io
io = {}

---@class File
local File = {}

---Reads from file according to format
---Formats: "n" (number), "a" (all), "l" (line), "L" (line with newline), integer (bytes)
---@param format? string|integer
---@return string|number|nil
function File:read(format) end

---Writes values to file
---Returns file for chaining
---@vararg any
---@return File
function File:write(...) end

---Returns iterator over lines from file
---format: "n" (number), "a" (all), "l" (line), "L" (line with newline), integer (bytes)
---@param format? string
---@return fun(): string
function File:lines(format) end

---Flushes file buffer
---@return nil
function File:flush() end

---Seeks to position in file
---whence: "set" (absolute), "cur" (current), "end" (from end)
---Returns new position or nil on error
---@param whence? '"set"'|'"cur"'|'"end"'
---@param offset? integer
---@return integer|nil
function File:seek(whence, offset) end

---Closes file
---Returns true on success, nil + error on failure
---@return boolean|nil, string|nil
function File:close() end

---Checks if file is at end of file
---@return boolean|nil
function File:iseof() end

---Opens file with given mode
---Modes: "r" (read), "w" (write), "a" (append), "r+" (read/write), etc.
---Binary modes append "b": "rb", "wb", "ab", etc.
---Returns file handle or nil + error message
---@overload fun(filename: string): File|nil, string|nil
---@param filename string
---@param mode? '"r"'|'"w"'|'"a"'|'"r+"'|'"w+"'|'"a+"'|'"rb"'|'"wb"'|'"ab"'|'"r+b"'|'"w+b"'|'"a+b"'
---@return File|nil, string|nil
function io.open(filename, mode) end

---Sets current input file to filename
---Equivalent to io.input(io.open(filename))
---With no argument, returns current input file
---@overload fun(): File
---@param filename? string
---@return File|nil, string|nil
function io.input(filename) end

---Sets current output file to filename
---Equivalent to io.output(io.open(filename, "w"))
---With no argument, returns current output file
---@overload fun(): File
---@param filename? string
---@return File|nil, string|nil
function io.output(filename) end

---Closes file (defaults to current output)
---Returns true on success, nil + error on failure
---@param file? File
---@return boolean|nil, string|nil
function io.close(file) end

---Reads from current input file according to formats
---@vararg string|integer
---@return ...
function io.read(...) end

---Writes arguments to current output file
---@vararg any
function io.write(...) end

---Flushes current output file
function io.flush() end

---Sets buffering mode for output file
---mode: "full" (buffered), "line" (line-buffered), "no" (unbuffered)
---@param file File
---@param mode '"full"'|'"line"'|'"no"'
---@param size? integer
function io.setvbuf(file, mode, size) end

---Opens a file and returns an iterator that reads lines from it
---Iterator automatically closes file at end
---Equivalent to io.input(filename):lines()
---@overload fun(): fun():string
---@param filename? string
---@return fun():string
function io.lines(filename) end

---Starts program prog in separate process
---Returns file handle to read from (mode "r") or write to (mode "w")
---System-dependent, may not be available
---@overload fun(prog: string): File
---@param prog string
---@param mode? '"r"'|'"w"'
---@return File|nil, string|nil
function io.popen(prog, mode) end

---Creates and returns temporary file handle
---File is automatically deleted when closed
---@return File|nil, string|nil
function io.tmpfile() end

---Returns string describing type of object
---Returns "file" for open files, "closed file" for closed files, nil otherwise
---@param obj any
---@return '"file"'|'"closed file"'|nil
function io.type(obj) end

---Standard input file
---@type File
io.stdin = nil

---Standard output file
---@type File
io.stdout = nil

---Standard error file
---@type File
io.stderr = nil

