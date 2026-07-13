---Packing/unpacking C-like structures.
---@class struct
struct = {}

---Pack values into a string according to the format.
---@param format string
---@param ... any
---@return string
function struct.pack(format, ...) end

---Unpack values from a string according to the format.
---@param format string
---@param string string
---@param pos? number
---@return any, number
function struct.unpack(format, string, pos) end

---Return the size of the structure for the given format.
---@param format string
---@return number
function struct.size(format) end
